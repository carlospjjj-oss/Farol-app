// Farol · servidor de logs (Cloudflare Worker + banco D1)
//
// Contas: cada pessoa entra no app com e-mail e senha (POST /v1/auth/*).
// O celular manda os lotes com o token da sessão (nunca com chave do GitHub). Aqui:
//   1) confere a sessão, o formato, o tamanho e o limite de envios por celular;
//   2) grava cada evento no banco D1 (ligação "DB");
//   3) se houver o segredo GH_TOKEN, guarda uma cópia no repositório privado
//      (logs/AAAA-MM-DD/HHMMSS-<celular>.jsonl), igual ao formato antigo.
//
// Configuração no painel da Cloudflare (Worker → Settings):
//   Bindings  → D1 database, nome da variável: DB
//   Variables → GH_REPO = carlospjjj-oss/Farol   (texto)
//   Secrets   → GH_TOKEN = github_pat_...        (segredo, opcional)
//   Variables → ADMIN_EMAIL = seu e-mail            (essa conta vira administradora)
//   Variables → REQUIRE_LOGIN = 1                   (recusa envios sem login; ligue depois que todos atualizarem)
//   Secrets   → ADMIN_KEY = uma senha sua           (opcional: acesso de emergência ao painel)
//
// A tabela é criada sozinha no primeiro envio.

const MAX_BODY = 512 * 1024;      // 512 KB por lote
const MAX_EVENTS = 500;           // eventos por lote
const MAX_BATCHES_PER_HOUR = 120; // por celular
const DEVICE_RE = /^[A-Za-z0-9_-]{6,64}$/;

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS events (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     device TEXT NOT NULL,
     received_at INTEGER NOT NULL,
     t TEXT, tipo TEXT, modo TEXT, app TEXT, versao TEXT,
     data TEXT NOT NULL)`,
  `CREATE INDEX IF NOT EXISTS ev_device_time ON events(device, received_at)`,
  `CREATE INDEX IF NOT EXISTS ev_tipo_time ON events(tipo, t)`,
  `CREATE TABLE IF NOT EXISTS batches (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     device TEXT NOT NULL, received_at INTEGER NOT NULL, n INTEGER NOT NULL)`,
  `CREATE INDEX IF NOT EXISTS b_device_time ON batches(device, received_at)`,
  `CREATE TABLE IF NOT EXISTS users (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     email TEXT NOT NULL UNIQUE, nome TEXT, senha TEXT NOT NULL,
     papel TEXT NOT NULL DEFAULT 'user', ativo INTEGER NOT NULL DEFAULT 1, criado INTEGER NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS sessions (
     token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL, criado INTEGER NOT NULL,
     visto INTEGER NOT NULL, device TEXT, origem TEXT)`,
  `CREATE INDEX IF NOT EXISTS s_user ON sessions(user_id)`,
  `CREATE TABLE IF NOT EXISTS attempts (chave TEXT NOT NULL, ts INTEGER NOT NULL)`,
  `CREATE INDEX IF NOT EXISTS a_key_ts ON attempts(chave, ts)`,
];
// Colunas novas em tabelas que já existiam (dá erro se já existir: ignorado).
const MIGRATIONS = [
  `ALTER TABLE events ADD COLUMN user_id INTEGER`,
  `ALTER TABLE batches ADD COLUMN user_id INTEGER`,
  `CREATE INDEX IF NOT EXISTS ev_user_time ON events(user_id, t)`,
];

let schemaReady = false;
async function ensureSchema(db) {
  if (schemaReady) return;
  await db.batch(SCHEMA.map((s) => db.prepare(s)));
  for (const m of MIGRATIONS) { try { await db.prepare(m).run(); } catch (_) { /* já aplicada */ } }
  schemaReady = true;
}

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json; charset=utf-8" } });

export default {
  async fetch(req, env, ctx) {
    const url = new URL(req.url);

    if (req.method === "GET" && (url.pathname === "/" || url.pathname === "/v1/ping")) {
      return json({ ok: true, servico: "farol-logs", banco: !!env.DB, copia_github: !!env.GH_TOKEN });
    }
    if (req.method === "GET" && url.pathname === "/painel") {
      return new Response(PANEL_HTML, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
    }
    if (!env.DB) return json({ erro: "banco D1 não ligado (Bindings → DB)" }, 500);
    await ensureSchema(env.DB);
    if (url.pathname.startsWith("/v1/auth/") || url.pathname.startsWith("/v1/admin/")) return authRoutes(req, env, url);
    if (req.method === "GET" && url.pathname === "/api/painel") return panelApi(req, env, url);
    if (req.method !== "POST" || url.pathname !== "/v1/logs") return json({ erro: "rota inválida" }, 404);

    // Sessão: token inválido → 401 (o app pede login de novo). Sem token → aceito só no período de transição.
    const sess = await sessionFrom(req, env);
    if (sess === "invalid") return json({ erro: "sessão expirada, entre de novo" }, 401);
    if (!sess && env.REQUIRE_LOGIN === "1") return json({ erro: "faça login no app" }, 401);
    const userId = sess ? sess.user_id : null;

    const len = Number(req.headers.get("content-length") || 0);
    if (len > MAX_BODY) return json({ erro: "lote grande demais" }, 413);
    const text = await req.text();
    if (text.length > MAX_BODY) return json({ erro: "lote grande demais" }, 413);

    let body;
    try { body = JSON.parse(text); } catch { return json({ erro: "JSON inválido" }, 400); }
    const device = String(body.device || "");
    const versao = String(body.versao || "").slice(0, 20);
    const eventos = Array.isArray(body.eventos) ? body.eventos : null;
    if (!DEVICE_RE.test(device)) return json({ erro: "device inválido" }, 400);
    if (!eventos || eventos.length === 0) return json({ erro: "sem eventos" }, 400);
    if (eventos.length > MAX_EVENTS) return json({ erro: "eventos demais no lote" }, 413);
    if (!eventos.every((e) => e && typeof e === "object" && !Array.isArray(e))) return json({ erro: "evento inválido" }, 400);

    const db = env.DB;
    const now = Date.now();
    const recent = await db
      .prepare("SELECT COUNT(*) AS n FROM batches WHERE device = ? AND received_at > ?")
      .bind(device, now - 3600_000).first();
    if ((recent?.n || 0) >= MAX_BATCHES_PER_HOUR) return json({ erro: "muitos envios, tente mais tarde" }, 429);

    const s = (v, n) => (v == null ? null : String(v).slice(0, n));
    const ins = db.prepare(
      "INSERT INTO events (device, user_id, received_at, t, tipo, modo, app, versao, data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
    const stmts = eventos.map((e) =>
      ins.bind(device, userId, now, s(e.t, 40), s(e.tipo, 20), s(e.modo, 10), s(e.app, 40), versao, JSON.stringify(e)));
    stmts.push(db.prepare("INSERT INTO batches (device, user_id, received_at, n) VALUES (?, ?, ?, ?)").bind(device, userId, now, eventos.length));
    await db.batch(stmts);

    if (env.GH_TOKEN && env.GH_REPO) ctx.waitUntil(mirrorToGitHub(env, userId ? "u" + userId : device, eventos).catch(() => {}));

    return json({ ok: true, gravados: eventos.length });
  },
};

// Cópia no repositório privado, para análise (a chave fica só aqui, nunca no app).
async function mirrorToGitHub(env, device, eventos) {
  const d = new Date(Date.now() - 3 * 3600_000); // horário de Brasília
  const day = d.toISOString().slice(0, 10);
  const hms = d.toISOString().slice(11, 19).replace(/:/g, "");
  const rand = Math.random().toString(36).slice(2, 6);
  const path = `logs/${day}/${hms}-${device.slice(0, 8)}-${rand}.jsonl`;
  const content = eventos.map((e) => JSON.stringify(e)).join("\n") + "\n";
  const bytes = new TextEncoder().encode(content);
  let bin = "";
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  const b64 = btoa(bin);
  const r = await fetch(`https://api.github.com/repos/${env.GH_REPO}/contents/${path}`, {
    method: "PUT",
    headers: {
      authorization: `Bearer ${env.GH_TOKEN}`,
      accept: "application/vnd.github+json",
      "x-github-api-version": "2022-11-28",
      "user-agent": "farol-logs-worker",
    },
    body: JSON.stringify({ message: `log: ${eventos.length} eventos [skip ci]`, content: b64 }),
  });
  if (!r.ok) throw new Error(`GitHub ${r.status}`);
}


// ============================== Contas ==============================

const PBKDF2_ITER = 30000;            // ~7 ms: cabe no limite de CPU do plano grátis
const SESSION_DAYS = 180;             // sessão do app dura 6 meses sem uso
const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,190}\.[^\s@]{2,}$/;
const enc = new TextEncoder();
const b64 = (u8) => btoa(String.fromCharCode(...u8));
const unb64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
const b64url = (u8) => b64(u8).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

async function pbkdf2(senha, salt, iter) {
  const k = await crypto.subtle.importKey("raw", enc.encode(senha), "PBKDF2", false, ["deriveBits"]);
  return new Uint8Array(await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations: iter }, k, 256));
}
async function hashSenha(senha) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  return `pbkdf2$${PBKDF2_ITER}$${b64(salt)}$${b64(await pbkdf2(senha, salt, PBKDF2_ITER))}`;
}
function sameBytes(a, b) {            // comparação em tempo constante
  if (a.length !== b.length) return false;
  let d = 0; for (let i = 0; i < a.length; i++) d |= a[i] ^ b[i];
  return d === 0;
}
async function confereSenha(senha, guardada) {
  const [alg, iter, salt, hash] = String(guardada).split("$");
  if (alg !== "pbkdf2") return false;
  return sameBytes(await pbkdf2(senha, unb64(salt), Number(iter)), unb64(hash));
}
async function sha256hex(s) {
  const h = new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(s)));
  return [...h].map((x) => x.toString(16).padStart(2, "0")).join("");
}

/** Sessão a partir do "Authorization: Bearer". null = sem token; "invalid" = token ruim/expirado. */
async function sessionFrom(req, env) {
  const h = req.headers.get("authorization") || "";
  const m = h.match(/^Bearer\s+([A-Za-z0-9_-]{20,100})$/);
  if (!m) return null;
  const th = await sha256hex(m[1]);
  const row = await env.DB.prepare(
    `SELECT s.user_id, s.visto, u.email, u.nome, u.papel, u.ativo FROM sessions s JOIN users u ON u.id = s.user_id
     WHERE s.token_hash = ?`).bind(th).first();
  const now = Date.now();
  if (!row || !row.ativo || now - row.visto > SESSION_DAYS * 86400_000) return "invalid";
  if (now - row.visto > 3600_000) await env.DB.prepare("UPDATE sessions SET visto = ? WHERE token_hash = ?").bind(now, th).run();
  return { ...row, token_hash: th };
}

async function tooMany(db, chave, max, janelaMs) {
  const r = await db.prepare("SELECT COUNT(*) AS n FROM attempts WHERE chave = ? AND ts > ?").bind(chave, Date.now() - janelaMs).first();
  return (r?.n || 0) >= max;
}
const marca = (db, chave) => db.prepare("INSERT INTO attempts (chave, ts) VALUES (?, ?)").bind(chave, Date.now()).run();

async function novaSessao(env, user, device, origem) {
  const raw = crypto.getRandomValues(new Uint8Array(32));
  const token = b64url(raw);
  const now = Date.now();
  const db = env.DB;
  const stmts = [db.prepare("INSERT INTO sessions (token_hash, user_id, criado, visto, device, origem) VALUES (?, ?, ?, ?, ?, ?)")
    .bind(await sha256hex(token), user.id, now, now, device || null, origem || "app")];
  // O que este celular mandou antes de ter conta passa a ser desta conta.
  if (device && DEVICE_RE.test(device)) {
    stmts.push(db.prepare("UPDATE events SET user_id = ? WHERE device = ? AND user_id IS NULL").bind(user.id, device));
    stmts.push(db.prepare("UPDATE batches SET user_id = ? WHERE device = ? AND user_id IS NULL").bind(user.id, device));
  }
  stmts.push(db.prepare("DELETE FROM attempts WHERE ts < ?").bind(now - 86400_000));
  await db.batch(stmts);
  return { token, usuario: { id: user.id, email: user.email, nome: user.nome, papel: user.papel } };
}

async function authRoutes(req, env, url) {
  const db = env.DB;
  const ip = req.headers.get("cf-connecting-ip") || "?";
  const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};
  const p = url.pathname;

  if (p === "/v1/auth/register" && req.method === "POST") {
    const email = String(body.email || "").trim().toLowerCase();
    const nome = String(body.nome || "").trim().slice(0, 40);
    const senha = String(body.senha || "");
    if (!EMAIL_RE.test(email)) return json({ erro: "e-mail inválido" }, 400);
    if (senha.length < 8) return json({ erro: "a senha precisa ter pelo menos 8 caracteres" }, 400);
    if (senha.length > 200) return json({ erro: "senha longa demais" }, 400);
    if (!nome) return json({ erro: "digite seu nome" }, 400);
    if (await tooMany(db, "reg:" + ip, 5, 3600_000)) return json({ erro: "muitas contas criadas daqui, tente mais tarde" }, 429);
    await marca(db, "reg:" + ip);
    if (await db.prepare("SELECT 1 FROM users WHERE email = ?").bind(email).first()) return json({ erro: "já existe conta com esse e-mail" }, 409);
    const primeiro = !(await db.prepare("SELECT 1 FROM users LIMIT 1").first());
    const papel = primeiro || (env.ADMIN_EMAIL && email === env.ADMIN_EMAIL.toLowerCase()) ? "admin" : "user";
    const r = await db.prepare("INSERT INTO users (email, nome, senha, papel, criado) VALUES (?, ?, ?, ?, ?) RETURNING id")
      .bind(email, nome, await hashSenha(senha), papel, Date.now()).first();
    return json(await novaSessao(env, { id: r.id, email, nome, papel }, body.device, body.origem));
  }

  if (p === "/v1/auth/login" && req.method === "POST") {
    const email = String(body.email || "").trim().toLowerCase();
    const senha = String(body.senha || "");
    if (await tooMany(db, "login:" + email, 8, 15 * 60_000) || await tooMany(db, "ip:" + ip, 30, 15 * 60_000))
      return json({ erro: "muitas tentativas. Espere 15 minutos." }, 429);
    const u = await db.prepare("SELECT * FROM users WHERE email = ?").bind(email).first();
    const ok = u ? await confereSenha(senha, u.senha) : (await hashSenha(senha), false); // mesmo tempo com ou sem conta
    if (!ok) { await marca(db, "login:" + email); await marca(db, "ip:" + ip); return json({ erro: "e-mail ou senha incorretos" }, 401); }
    if (!u.ativo) return json({ erro: "conta desativada" }, 403);
    return json(await novaSessao(env, u, body.device, body.origem));
  }

  const sess = await sessionFrom(req, env);
  if (!sess || sess === "invalid") return json({ erro: "faça login" }, 401);

  if (p === "/v1/auth/me") return json({ usuario: { id: sess.user_id, email: sess.email, nome: sess.nome, papel: sess.papel } });
  if (p === "/v1/auth/logout" && req.method === "POST") {
    await db.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(sess.token_hash).run();
    return json({ ok: true });
  }
  if (p === "/v1/auth/password" && req.method === "POST") {
    const u = await db.prepare("SELECT senha FROM users WHERE id = ?").bind(sess.user_id).first();
    if (!(await confereSenha(String(body.atual || ""), u.senha))) return json({ erro: "senha atual incorreta" }, 401);
    if (String(body.nova || "").length < 8) return json({ erro: "a senha nova precisa ter 8 caracteres" }, 400);
    await db.batch([
      db.prepare("UPDATE users SET senha = ? WHERE id = ?").bind(await hashSenha(String(body.nova)), sess.user_id),
      db.prepare("DELETE FROM sessions WHERE user_id = ? AND token_hash != ?").bind(sess.user_id, sess.token_hash),
    ]);
    return json({ ok: true });
  }

  // ---- administração ----
  if (sess.papel !== "admin") return json({ erro: "só para administrador" }, 403);
  if (p === "/v1/admin/users") {
    const r = await db.prepare(
      `SELECT u.id, u.email, u.nome, u.papel, u.ativo, u.criado,
         (SELECT MAX(t) FROM events e WHERE e.user_id = u.id) AS ultimo,
         (SELECT COUNT(*) FROM events e WHERE e.user_id = u.id) AS eventos
       FROM users u ORDER BY u.id`).all();
    return json({ usuarios: r.results || [] });
  }
  if (p === "/v1/admin/reset" && req.method === "POST") {
    const nova = String(body.senha || "");
    if (nova.length < 8) return json({ erro: "a senha precisa ter 8 caracteres" }, 400);
    await db.batch([
      db.prepare("UPDATE users SET senha = ? WHERE id = ?").bind(await hashSenha(nova), Number(body.user_id)),
      db.prepare("DELETE FROM sessions WHERE user_id = ?").bind(Number(body.user_id)),
    ]);
    return json({ ok: true });
  }
  if (p === "/v1/admin/toggle" && req.method === "POST") {
    if (Number(body.user_id) === sess.user_id) return json({ erro: "não dá para desativar a própria conta" }, 400);
    await db.batch([
      db.prepare("UPDATE users SET ativo = 1 - ativo WHERE id = ?").bind(Number(body.user_id)),
      db.prepare("DELETE FROM sessions WHERE user_id = ?").bind(Number(body.user_id)),
    ]);
    return json({ ok: true });
  }
  return json({ erro: "rota inválida" }, 404);
}

// ============================== Painel web ==============================

const APP_NAMES = {
  "com.ubercab.driver": "Uber", "com.app99.driver": "99", "sinet.startup.inDriver": "inDrive",
  "br.com.ifood.driver.app": "iFood", "com.sankuai.sailor.courier": "Keeta",
};

function brtDate(offsetDays = 0) {
  return new Date(Date.now() - 3 * 3600_000 - offsetDays * 86400_000).toISOString().slice(0, 10);
}

async function panelApi(req, env, url) {
  // Quem pode ver: sessão de login (cada um vê o seu; admin vê todos) ou ADMIN_KEY de emergência.
  let scopeUser = null, isAdmin = false, me = null;
  const key = req.headers.get("x-farol-key");
  if (key && env.ADMIN_KEY && key.length === env.ADMIN_KEY.length && sameBytes(enc.encode(key), enc.encode(env.ADMIN_KEY))) {
    isAdmin = true;
  } else {
    const sess = await sessionFrom(req, env);
    if (!sess || sess === "invalid") return json({ erro: "faça login" }, 401);
    me = { id: sess.user_id, nome: sess.nome, email: sess.email, papel: sess.papel };
    isAdmin = sess.papel === "admin";
    if (!isAdmin) scopeUser = sess.user_id;
  }
  if (isAdmin) {
    const u = url.searchParams.get("usuario");
    if (u === "anon") scopeUser = "anon"; else if (u && /^\d+$/.test(u)) scopeUser = Number(u);
  }

  const dias = Math.min(Math.max(parseInt(url.searchParams.get("dias") || "7", 10) || 7, 1), 365);
  const modo = ["moto", "carro"].includes(url.searchParams.get("modo")) ? url.searchParams.get("modo") : null;
  const since = brtDate(dias - 1);
  const db = env.DB;

  // Monta WHERE só com os filtros usados (o D1 recusa parâmetros que sobram).
  const q = async (select, where, rest = "", { useModo = true } = {}) => {
    const conds = [...where, "substr(t,1,10) >= ?"], args = [since];
    if (useModo && modo) { conds.push("modo = ?"); args.push(modo); }
    if (scopeUser === "anon") conds.push("user_id IS NULL");
    else if (scopeUser != null) { conds.push("user_id = ?"); args.push(scopeUser); }
    return (await db.prepare(`${select} FROM events WHERE ${conds.join(" AND ")} ${rest}`).bind(...args).all()).results || [];
  };

  const [cores, porApp, porHora, ultimas, pedidos, diasRows, totalEv] = await Promise.all([
    q(`SELECT json_extract(data,'$.cor') AS cor, COUNT(*) AS n`, ["tipo='offer'"], "GROUP BY cor"),
    q(`SELECT app, COUNT(*) AS n, SUM(json_extract(data,'$.cor')='GREEN') AS verdes,
         SUM(json_extract(data,'$.preco')) / NULLIF(SUM(json_extract(data,'$.km')),0) AS r_km,
         SUM(json_extract(data,'$.preco')) * 60.0 / NULLIF(SUM(json_extract(data,'$.min')),0) AS r_h,
         AVG(json_extract(data,'$.preco')) AS preco, AVG(json_extract(data,'$.km')) AS km`, ["tipo='offer'"], "GROUP BY app ORDER BY n DESC"),
    q(`SELECT CAST(substr(t,12,2) AS INTEGER) AS h, COUNT(*) AS n, SUM(json_extract(data,'$.cor')='GREEN') AS verdes,
         SUM(json_extract(data,'$.preco')) / NULLIF(SUM(json_extract(data,'$.km')),0) AS r_km`, ["tipo='offer'"], "GROUP BY h ORDER BY h"),
    q(`SELECT t, app, json_extract(data,'$.preco') AS preco, json_extract(data,'$.km') AS km,
         json_extract(data,'$.min') AS min, json_extract(data,'$.r_km') AS r_km, json_extract(data,'$.r_h') AS r_h,
         json_extract(data,'$.liquido') AS liquido, json_extract(data,'$.cor') AS cor`, ["tipo='offer'"], "ORDER BY id DESC LIMIT 25"),
    // pedidos e resumo do dia não levam "modo"
    q(`SELECT app, COUNT(*) AS n, AVG(json_extract(data,'$.min_coleta')) AS coleta, AVG(json_extract(data,'$.min_entrega')) AS entrega`,
      ["tipo='pedido'"], "GROUP BY app ORDER BY n DESC", { useModo: false }),
    // o último resumo de cada data (se o turno foi editado, vale o mais novo) — por conta
    q(`SELECT json_extract(data,'$.resumo') AS r, COALESCE(user_id, device) AS dono, id`, ["tipo='dia'"], "ORDER BY id", { useModo: false }),
    q(`SELECT COUNT(*) AS n, MAX(t) AS ultimo`, [], ""),
  ]);

  const name = (a) => APP_NAMES[a] || a || "?";
  const lastByDay = new Map();
  for (const x of diasRows) { try { const r = JSON.parse(x.r); if (r && r.data >= since) lastByDay.set(x.dono + "|" + r.data, r); } catch (_) {} }
  const perDate = new Map();   // várias contas no mesmo dia (visão do admin): soma
  for (const r of lastByDay.values()) {
    const c = perDate.get(r.data) || { data: r.data, ganho_total: 0, km: 0, horas: 0, liquido: 0 };
    c.ganho_total += Number(r.ganho_total) || 0; c.km += Number(r.km) || 0; c.horas += Number(r.horas) || 0; c.liquido += Number(r.liquido) || 0;
    perDate.set(r.data, c);
  }
  const days = [...perDate.values()].sort((a, b) => a.data.localeCompare(b.data));
  const sum = (k) => days.reduce((a, d) => a + (Number(d[k]) || 0), 0);
  const ganhos = sum("ganho_total"), km = sum("km"), horas = sum("horas"), liquido = sum("liquido");

  let usuarios;
  if (isAdmin) usuarios = (await db.prepare("SELECT id, nome, email FROM users ORDER BY nome").all()).results || [];

  return json({
    eu: me, admin: isAdmin, usuarios, escopo: scopeUser,
    periodo: { dias, desde: since, ate: brtDate(0), modo: modo || "todos" },
    resumo: {
      ganhos, km, horas, liquido, dias_trabalhados: days.length,
      r_km: km > 0 ? ganhos / km : null, r_h: horas > 0 ? ganhos / horas : null,
      eventos: totalEv[0]?.n || 0, ultimo_evento: totalEv[0]?.ultimo || null,
    },
    dias: days.map((d) => ({ data: d.data, ganho: d.ganho_total, km: d.km, horas: d.horas, liquido: d.liquido })),
    cores: Object.fromEntries(cores.map((c) => [c.cor || "?", c.n])),
    apps: porApp.map((a) => ({ ...a, app: name(a.app) })),
    horas: porHora,
    ultimas: ultimas.map((o) => ({ ...o, app: name(o.app) })),
    pedidos: pedidos.map((p) => ({ ...p, app: name(p.app) })),
  });
}

const PANEL_HTML = `<!DOCTYPE html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#07090D">
<title>Farol · Painel</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
:root{--bg:#07090D;--card:rgba(22,27,35,.72);--card2:#1A2029;--line:rgba(255,255,255,.07);--text:#F3F5F8;--muted:#8A94A3;--dim:#5B6472;
--acc:#FFB020;--acc2:#FF7A1A;--green:#22C55E;--yellow:#F5B014;--red:#EF4444;--r:20px}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--text);font:15px/1.45 Inter,system-ui,-apple-system,Segoe UI,Roboto,sans-serif;font-feature-settings:"tnum" 1,"cv11" 1;min-height:100vh;overflow-x:hidden}
.glow{position:fixed;inset:-20vh -10vw auto;height:70vh;pointer-events:none;z-index:0;
 background:radial-gradient(40% 50% at 15% 10%,rgba(255,176,32,.16),transparent 70%),radial-gradient(35% 45% at 90% 0%,rgba(34,197,94,.08),transparent 70%)}
.wrap{position:relative;z-index:1;max-width:1180px;margin:0 auto;padding:22px 18px 70px}
/* header */
header{display:flex;flex-wrap:wrap;gap:14px;align-items:center;justify-content:space-between;margin-bottom:22px}
.brand{display:flex;align-items:center;gap:12px}
.logo{width:42px;height:42px;border-radius:13px;background:linear-gradient(160deg,#232A35,#12161C);border:1px solid var(--line);display:grid;place-items:center;box-shadow:0 8px 24px rgba(0,0,0,.35)}
.brand h1{font-size:20px;margin:0;font-weight:800;letter-spacing:-.02em}.brand h1 b{color:var(--acc)}
.sub{color:var(--muted);font-size:13px}
.ctrls{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
.seg{display:inline-flex;background:rgba(255,255,255,.04);border:1px solid var(--line);border-radius:14px;padding:4px;backdrop-filter:blur(10px)}
.seg button{background:none;border:0;color:var(--muted);font:600 13px Inter,system-ui;padding:8px 13px;border-radius:10px;cursor:pointer;transition:.2s}
.seg button:hover{color:var(--text)}
.seg button.on{background:linear-gradient(135deg,var(--acc),var(--acc2));color:#1C1300;box-shadow:0 4px 14px rgba(255,150,30,.35)}
.live{display:inline-flex;align-items:center;gap:7px;font-size:12px;color:var(--muted);padding:8px 12px;border-radius:999px;border:1px solid var(--line);background:rgba(255,255,255,.03)}
.sel{color:var(--text);font:600 13px Inter,system-ui;padding:9px 12px;appearance:none;cursor:pointer}
.sel option{background:#151A21}
.acct{cursor:pointer;font:500 12px Inter,system-ui;color:var(--muted)}.acct:hover{color:var(--text)}
.users{display:grid;gap:8px}.u{display:grid;grid-template-columns:1fr auto;gap:10px;align-items:center;padding:12px 14px;border-radius:14px;background:rgba(255,255,255,.025);border:1px solid var(--line)}
.u .b{display:flex;gap:6px;flex-wrap:wrap}.u button{background:rgba(255,255,255,.05);border:1px solid var(--line);color:var(--text);border-radius:10px;padding:7px 10px;font:600 12px Inter,system-ui;cursor:pointer}
.u .off{opacity:.5}.tagA{font-size:11px;font-weight:700;color:#1C1300;background:var(--acc);padding:1px 7px;border-radius:999px;margin-left:6px}
.live i{width:7px;height:7px;border-radius:50%;background:var(--green);box-shadow:0 0 0 0 rgba(34,197,94,.6);animation:pulse 2s infinite}
@keyframes pulse{70%{box-shadow:0 0 0 7px rgba(34,197,94,0)}100%{box-shadow:0 0 0 0 rgba(34,197,94,0)}}
/* cards */
.grid{display:grid;gap:14px}
.card{position:relative;background:var(--card);border:1px solid var(--line);border-radius:var(--r);padding:18px;backdrop-filter:blur(14px);
 box-shadow:0 1px 0 rgba(255,255,255,.04) inset,0 18px 40px rgba(0,0,0,.25);animation:rise .5s both}
@keyframes rise{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}
.ov{color:var(--muted);font-size:12px;font-weight:600;display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:12px}
.ov .tag{font-size:11px;color:var(--dim);font-weight:500}
.row1{grid-template-columns:1.35fr 1fr}.row2{grid-template-columns:1.6fr 1fr}
.k4{grid-template-columns:repeat(4,1fr)}
@media(max-width:900px){.row1,.row2{grid-template-columns:1fr}.k4{grid-template-columns:repeat(2,1fr)}}
/* hero */
.hero{overflow:hidden;background:linear-gradient(135deg,rgba(255,176,32,.20),rgba(255,122,26,.06) 45%,rgba(22,27,35,.75) 75%);border-color:rgba(255,176,32,.22)}
.hero:after{content:"";position:absolute;right:-60px;top:-60px;width:220px;height:220px;border-radius:50%;background:radial-gradient(circle,rgba(255,176,32,.25),transparent 70%)}
.hero .lbl{font-size:13px;color:#F7D9A0;font-weight:600}
.hero .val{font-size:46px;font-weight:800;letter-spacing:-.03em;line-height:1.05;margin:6px 0 4px}
.hero .val small{font-size:22px;font-weight:700;opacity:.8;margin-right:4px}
.chips{display:flex;flex-wrap:wrap;gap:8px;margin-top:16px}
.chip{display:inline-flex;align-items:center;gap:7px;padding:8px 12px;border-radius:12px;background:rgba(0,0,0,.25);border:1px solid rgba(255,255,255,.06);font-size:13px;font-weight:600}
.chip span{color:var(--muted);font-weight:500}
.pos{color:var(--green)}.neg{color:var(--red)}
/* kpi */
.kpi .ic{width:34px;height:34px;border-radius:11px;display:grid;place-items:center;font-size:16px;margin-bottom:12px}
.kpi .v{font-size:24px;font-weight:800;letter-spacing:-.02em}
.kpi .s{font-size:12px;color:var(--muted);margin-top:2px}
.kpi svg.spark{position:absolute;right:14px;top:16px;width:84px;height:30px;opacity:.9}
/* donut */
.donut{display:flex;align-items:center;gap:20px}
.donut svg{flex:none}
.leg{flex:1;display:grid;gap:10px}
.leg .it{display:grid;grid-template-columns:auto 1fr auto;gap:8px;align-items:center;font-size:13px}
.leg .it b{font-weight:700}.leg .bar{grid-column:1/-1;height:5px;border-radius:3px;background:rgba(255,255,255,.06);overflow:hidden}
.leg .bar i{display:block;height:100%;border-radius:3px}
.dot{width:9px;height:9px;border-radius:50%;display:inline-block}
.GREEN{background:var(--green)}.YELLOW{background:var(--yellow)}.RED{background:var(--red)}
/* charts */
.chart{width:100%;height:auto;display:block;overflow:visible}
.chart text{fill:var(--muted);font:500 11px Inter,system-ui}
.chart .vlab{fill:var(--text);font-weight:700}
.best{display:flex;align-items:center;gap:10px;padding:12px 14px;border-radius:14px;background:rgba(34,197,94,.08);border:1px solid rgba(34,197,94,.18);margin-bottom:14px;font-size:13px}
.best b{font-size:18px;color:var(--green)}
/* apps */
.apps{grid-template-columns:repeat(auto-fill,minmax(230px,1fr))}
.app .hd{display:flex;align-items:center;gap:10px;margin-bottom:14px}
.app .badge{width:34px;height:34px;border-radius:11px;display:grid;place-items:center;font-weight:800;font-size:13px;color:#0B0E13}
.app .nm{font-weight:700}.app .cnt{font-size:12px;color:var(--muted)}
.app .big{font-size:26px;font-weight:800;letter-spacing:-.02em}.app .big small{font-size:13px;color:var(--muted);font-weight:500}
.meter{height:7px;border-radius:4px;background:rgba(255,255,255,.06);margin:12px 0 6px;overflow:hidden}
.meter i{display:block;height:100%;border-radius:4px;background:linear-gradient(90deg,var(--green),#4ADE80)}
.app .ft{display:flex;justify-content:space-between;font-size:12px;color:var(--muted);margin-top:10px}
.app .ft b{color:var(--text);font-weight:600}
/* offers list */
.offs{display:grid;gap:8px}
.off{display:grid;grid-template-columns:auto 1fr auto;gap:12px;align-items:center;padding:12px 14px;border-radius:14px;background:rgba(255,255,255,.025);border:1px solid var(--line);transition:.2s}
.off:hover{background:rgba(255,255,255,.05)}
.lamp{width:10px;height:38px;border-radius:6px}
.lamp.GREEN{box-shadow:0 0 14px rgba(34,197,94,.5)}.lamp.RED{box-shadow:0 0 14px rgba(239,68,68,.35)}.lamp.YELLOW{box-shadow:0 0 14px rgba(245,176,20,.4)}
.off .t1{font-weight:600;display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.pill{font-size:11px;font-weight:700;padding:2px 8px;border-radius:999px;color:#0B0E13}
.off .when{color:var(--dim);font-size:12px;font-weight:500}
.off .t2{font-size:12px;color:var(--muted);margin-top:3px}
.off .pr{text-align:right;font-weight:800;font-size:17px}.off .pr small{display:block;font-size:11px;color:var(--muted);font-weight:500}
.more{margin-top:10px;width:100%;background:rgba(255,255,255,.04);border:1px solid var(--line);color:var(--text);border-radius:12px;padding:11px;font:600 13px Inter,system-ui;cursor:pointer}
.empty{color:var(--muted);font-size:13px;padding:18px;text-align:center;border:1px dashed var(--line);border-radius:14px}
section{margin-top:26px}section>h2{font-size:15px;font-weight:700;margin:0 0 12px;display:flex;align-items:center;gap:8px}
section>h2 span{color:var(--dim);font-weight:500;font-size:12px}
/* login */
#login{max-width:380px;margin:14vh auto;padding:28px;text-align:center;position:relative;z-index:1}
#login h1{font-size:22px;margin:14px 0 4px}#login h1 b{color:var(--acc)}
input{width:100%;background:rgba(0,0,0,.3);border:1px solid var(--line);color:var(--text);border-radius:14px;padding:14px;font:16px Inter,system-ui;margin-top:18px;outline:none}
input:focus{border-color:rgba(255,176,32,.5)}
.btn{width:100%;margin-top:10px;background:linear-gradient(135deg,var(--acc),var(--acc2));color:#1C1300;border:0;border-radius:14px;padding:14px;font:700 15px Inter,system-ui;cursor:pointer}
.err{color:var(--red);font-size:13px;margin-top:10px;min-height:18px}
footer{margin-top:34px;text-align:center;color:var(--dim);font-size:12px}
@media(max-width:520px){.wrap{padding:16px 12px 60px}.hero .val{font-size:38px}.kpi .v{font-size:20px}.kpi svg.spark{width:60px}.card{padding:15px}.donut{gap:14px}}
</style></head><body>
<div class="glow"></div>
<svg width="0" height="0" style="position:absolute"><defs>
<symbol id="lt" viewBox="0 0 24 24"><rect x="7" y="1.5" width="10" height="21" rx="4" fill="#0B0E13" stroke="#3A4250"/><circle cx="12" cy="6.3" r="2.1" fill="#EF4444"/><circle cx="12" cy="12" r="2.1" fill="#F5B014"/><circle cx="12" cy="17.7" r="2.1" fill="#22C55E"/></symbol>
<linearGradient id="ga" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFB020" stop-opacity=".45"/><stop offset="1" stop-color="#FFB020" stop-opacity="0"/></linearGradient>
</defs></svg>
<div id="login" class="card" hidden>
  <div class="logo" style="margin:0 auto;width:56px;height:56px"><svg width="30" height="30"><use href="#lt"/></svg></div>
  <h1>Farol <b>Painel</b></h1>
  <div class="sub">Entre com a mesma conta do app.</div>
  <input id="em" type="email" placeholder="E-mail" autocomplete="username">
  <input id="pw" type="password" placeholder="Senha" autocomplete="current-password" style="margin-top:10px">
  <button class="btn" id="go">Entrar</button><div class="err" id="err"></div>
</div>
<div class="wrap" id="app" hidden>
  <header>
    <div class="brand"><div class="logo"><svg width="24" height="24"><use href="#lt"/></svg></div>
      <div><h1>Farol <b>Painel</b></h1><div class="sub" id="info"></div></div></div>
    <div class="ctrls">
      <div class="seg" id="segDias"><button data-v="1">Hoje</button><button data-v="7" class="on">7 dias</button><button data-v="30">30 dias</button></div>
      <div class="seg" id="segModo"><button data-v="" class="on">Todos</button><button data-v="moto">🛵 Moto</button><button data-v="carro">🚗 Carro</button></div>
      <select id="selUser" class="seg sel" hidden></select>
      <span class="live"><i></i><span id="upd">ao vivo</span></span>
      <button class="live acct" id="acct" title="Sair">👤 <span id="who"></span> · Sair</button>
    </div>
  </header>
  <div class="grid row1">
    <div class="card hero" id="hero"></div>
    <div class="card" id="cores"></div>
  </div>
  <div class="grid k4" style="margin-top:14px" id="kpis"></div>
  <div class="grid row2" style="margin-top:14px">
    <div class="card"><div class="ov">Ganhos por dia <span class="tag">turnos encerrados</span></div><div id="chartDias"></div></div>
    <div class="card"><div class="ov">Horários <span class="tag">% de ofertas verdes</span></div><div id="chartHoras"></div></div>
  </div>
  <section><h2>Por app</h2><div class="grid apps" id="apps"></div></section>
  <section><h2>Últimas ofertas <span id="offCount"></span></h2><div class="card"><div class="offs" id="ultimas"></div></div></section>
  <section><h2>Pedidos <span>tempo medido pelo app</span></h2><div class="grid apps" id="pedidos"></div></section>
  <section id="secUsers" hidden><h2>Contas <span>só o administrador vê</span></h2><div class="card"><div class="users" id="users"></div></div></section>
  <footer>Farol · dados do seu servidor Cloudflare</footer>
</div>
<script>
const $=s=>document.querySelector(s);
const APPC={"iFood":"#EA1D2C","99":"#FFD000","Keeta":"#2BD49B","Uber":"#E5E7EB","inDrive":"#B6F36A"};
const appc=a=>APPC[a]||"#8B5CF6";
const COR={GREEN:"#22C55E",YELLOW:"#F5B014",RED:"#EF4444"};
const fmt=(v,d=2)=>Number(v).toLocaleString("pt-BR",{minimumFractionDigits:d,maximumFractionDigits:d});
const brl=v=>v==null||isNaN(v)?"—":"R$ "+fmt(v);
const num=(v,d=1)=>v==null||isNaN(v)?"—":fmt(v,d);
const hm=h=>h==null||!h?"—":Math.floor(h)+"h"+String(Math.round((h%1)*60)).padStart(2,"0");
const esc=s=>String(s??"").replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const dm=s=>s.slice(8,10)+"/"+s.slice(5,7);
let tok=null,dias=7,modo="",usuario="",showAll=false,last=null;
try{tok=localStorage.getItem("farolTok");localStorage.removeItem("farolKey")}catch(e){}
const H=()=>({"authorization":"Bearer "+tok,"content-type":"application/json"});
function seg(id,cb){$(id).addEventListener("click",e=>{const b=e.target.closest("button");if(!b)return;
  $(id).querySelectorAll("button").forEach(x=>x.classList.toggle("on",x===b));cb(b.dataset.v);load();});}
seg("#segDias",v=>dias=+v);seg("#segModo",v=>modo=v);
$("#go").onclick=async()=>{
  $("#err").textContent="";$("#go").disabled=true;
  try{const r=await fetch("/v1/auth/login",{method:"POST",headers:{"content-type":"application/json"},
      body:JSON.stringify({email:$("#em").value.trim(),senha:$("#pw").value,origem:"painel"})});
    const d=await r.json();if(!r.ok)throw new Error(d.erro||"erro");
    tok=d.token;try{localStorage.setItem("farolTok",tok)}catch(e){};$("#pw").value="";load();
  }catch(e){$("#err").textContent=e.message}finally{$("#go").disabled=false}};
$("#pw").onkeydown=e=>{if(e.key==="Enter")$("#go").click();};
$("#acct").onclick=async()=>{if(!confirm("Sair do painel?"))return;try{await fetch("/v1/auth/logout",{method:"POST",headers:H()})}catch(e){}
  tok=null;try{localStorage.removeItem("farolTok")}catch(e){};showLogin();};
$("#selUser").onchange=e=>{usuario=e.target.value;load();};
function showLogin(msg){$("#app").hidden=true;$("#login").hidden=false;$("#err").textContent=msg||"";}
async function load(){
  if(!tok)return showLogin();
  let r;try{r=await fetch("/api/painel?dias="+dias+"&modo="+modo+"&usuario="+usuario,{headers:H()});}catch(e){return showLogin("Sem conexão");}
  const d=await r.json().catch(()=>({erro:"resposta inválida"}));
  if(!r.ok){if(r.status===401){try{localStorage.removeItem("farolTok")}catch(e){}tok=null;}return showLogin(d.erro);}
  $("#login").hidden=true;$("#app").hidden=false;last=d;render(d);
  if(d.admin)loadUsers();
}
function spark(vals,color){
  vals=vals.filter(v=>v!=null&&!isNaN(v));if(vals.length<2)return"";
  const mx=Math.max(...vals),mn=Math.min(...vals),W=84,H=30,sp=(mx-mn)||1;
  const pts=vals.map((v,i)=>[i/(vals.length-1)*W,H-3-(v-mn)/sp*(H-6)]);
  const d="M"+pts.map(p=>p.map(n=>n.toFixed(1)).join(",")).join("L");
  return '<svg class="spark" viewBox="0 0 84 30"><path d="'+d+'" fill="none" stroke="'+color+'" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="'+pts.at(-1)[0]+'" cy="'+pts.at(-1)[1]+'" r="2.6" fill="'+color+'"/></svg>';
}
function smooth(pts){ // curva suave (Catmull-Rom → Bézier)
  if(pts.length<2)return"";let d="M"+pts[0][0]+","+pts[0][1];
  for(let i=0;i<pts.length-1;i++){const p0=pts[i-1]||pts[i],p1=pts[i],p2=pts[i+1],p3=pts[i+2]||p2;
    d+=" C"+(p1[0]+(p2[0]-p0[0])/6).toFixed(1)+","+(p1[1]+(p2[1]-p0[1])/6).toFixed(1)+" "+(p2[0]-(p3[0]-p1[0])/6).toFixed(1)+","+(p2[1]-(p3[1]-p1[1])/6).toFixed(1)+" "+p2[0].toFixed(1)+","+p2[1].toFixed(1);}
  return d;
}
function areaChart(items){
  if(!items.length)return '<div class="empty">Nenhum turno encerrado no período.<br>Os ganhos entram quando você encerra o turno no app.</div>';
  const W=Math.max(320,Math.min(640,($("#chartDias").clientWidth||640))),H=W<480?190:210,P=28,B=26,mx=Math.max(...items.map(i=>i.v||0),1)*1.15;
  const x=i=>items.length===1?W/2:P+i*(W-2*P)/(items.length-1), y=v=>H-B-(v||0)/mx*(H-B-18);
  const pts=items.map((it,i)=>[x(i),y(it.v)]);
  let g="";for(let k=1;k<=3;k++){const yy=(H-B)-(H-B-18)*k/3;g+='<line x1="0" x2="'+W+'" y1="'+yy+'" y2="'+yy+'" stroke="rgba(255,255,255,.05)"/>';}
  const line=items.length===1?"":smooth(pts);
  const area=items.length===1?"":line+" L"+pts.at(-1)[0]+","+(H-B)+" L"+pts[0][0]+","+(H-B)+" Z";
  const step=Math.ceil(items.length/8);
  return '<svg class="chart" viewBox="0 0 '+W+' '+H+'">'+g+(area?'<path d="'+area+'" fill="url(#ga)"/><path d="'+line+'" fill="none" stroke="#FFB020" stroke-width="3" stroke-linecap="round"/>':'')+
   pts.map((p,i)=>'<g><title>'+esc(items[i].title)+'</title>'+(items.length===1?'<rect x="'+(p[0]-22)+'" y="'+p[1]+'" width="44" height="'+(H-B-p[1])+'" rx="10" fill="url(#ga)" stroke="#FFB020" stroke-width="2"/>':'')+
     '<circle cx="'+p[0]+'" cy="'+p[1]+'" r="5" fill="#07090D" stroke="#FFB020" stroke-width="3"/>'+
     ((i%step===0||i===pts.length-1)?'<text class="vlab" x="'+p[0]+'" y="'+(p[1]-12)+'" text-anchor="middle">R$'+Math.round(items[i].v||0)+'</text><text x="'+p[0]+'" y="'+(H-6)+'" text-anchor="middle">'+items[i].l+'</text>':'')+'</g>').join("")+'</svg>';
}
function hoursChart(hrs){
  const valid=hrs.filter(h=>h.n>0);
  if(!valid.length)return '<div class="empty">Sem ofertas no período.</div>';
  const best=valid.filter(h=>h.n>=3).sort((a,b)=>(b.verdes/b.n)-(a.verdes/a.n))[0];
  const lo=Math.min(...valid.map(h=>h.h)),hi=Math.max(...valid.map(h=>h.h));
  const by=Object.fromEntries(valid.map(h=>[h.h,h]));const cols=[];for(let h=lo;h<=hi;h++)cols.push(by[h]||{h,n:0,verdes:0});
  const W=360,H=170,B=22,bw=(W/cols.length)*.68,gap=W/cols.length;
  const bars=cols.map((c,i)=>{const pct=c.n?Math.round(c.verdes/c.n*100):0,hh=c.n?Math.max(6,pct/100*(H-B-24)):3,
    col=!c.n?"rgba(255,255,255,.08)":pct>=50?COR.GREEN:pct>=25?COR.YELLOW:COR.RED,xx=i*gap+(gap-bw)/2;
    return '<g><title>'+c.h+'h · '+c.n+' ofertas · '+pct+'% verdes</title><rect x="'+xx+'" y="'+(H-B-hh)+'" width="'+bw+'" height="'+hh+'" rx="'+Math.min(6,bw/2)+'" fill="'+col+'" opacity="'+(best&&c.h===best.h?1:.8)+'"/>'+
      (c.n&&cols.length<=14?'<text class="vlab" x="'+(xx+bw/2)+'" y="'+(H-B-hh-6)+'" text-anchor="middle">'+pct+'%</text>':'')+
      ((cols.length<=14||c.h%2===0)?'<text x="'+(xx+bw/2)+'" y="'+(H-5)+'" text-anchor="middle">'+c.h+'h</text>':'')+'</g>';}).join("");
  return (best?'<div class="best">⭐ <div>Melhor horário<br><b>'+best.h+'h</b> <span class="sub">'+Math.round(best.verdes/best.n*100)+'% verdes · '+brl(best.r_km)+'/km</span></div></div>':'')+
    '<svg class="chart" viewBox="0 0 '+W+' '+H+'">'+bars+'</svg>';
}
function donut(c){
  const tot=(c.GREEN||0)+(c.YELLOW||0)+(c.RED||0);
  if(!tot)return '<div class="ov">Ofertas lidas</div><div class="empty">Nenhuma oferta lida no período.</div>';
  const R=52,C=2*Math.PI*R;let off=0;
  const segs=["GREEN","YELLOW","RED"].map(k=>{const f=(c[k]||0)/tot,len=f*C;const s='<circle r="'+R+'" cx="70" cy="70" fill="none" stroke="'+COR[k]+'" stroke-width="16" stroke-dasharray="'+Math.max(0,len-2)+' '+(C-len+2)+'" stroke-dashoffset="'+(-off)+'" stroke-linecap="butt"/>';off+=len;return s;}).join("");
  const leg=[["GREEN","Verdes"],["YELLOW","Amarelas"],["RED","Vermelhas"]].map(([k,l])=>{const p=Math.round((c[k]||0)/tot*100);
    return '<div class="it"><span class="dot '+k+'"></span><span>'+l+'</span><b>'+(c[k]||0)+' <span class="sub">· '+p+'%</span></b><div class="bar"><i style="width:'+p+'%;background:'+COR[k]+'"></i></div></div>';}).join("");
  return '<div class="ov">Ofertas lidas <span class="tag">cor do card</span></div><div class="donut"><svg width="140" height="140" viewBox="0 0 140 140"><g transform="rotate(-90 70 70)"><circle r="'+R+'" cx="70" cy="70" fill="none" stroke="rgba(255,255,255,.05)" stroke-width="16"/>'+segs+
    '</g><text x="70" y="68" text-anchor="middle" fill="#F3F5F8" style="font:800 28px Inter,system-ui">'+tot+'</text><text x="70" y="88" text-anchor="middle" fill="#8A94A3" style="font:500 11px Inter,system-ui">ofertas</text></svg><div class="leg">'+leg+'</div></div>';
}
function offerRow(o){
  return '<div class="off"><div class="lamp '+esc(o.cor)+'"></div><div><div class="t1"><span class="pill" style="background:'+appc(o.app)+'">'+esc(o.app)+'</span><span class="when">'+dm(o.t)+' · '+o.t.slice(11,16)+'</span></div>'+
    '<div class="t2">'+num(o.km)+' km · '+num(o.min,0)+' min · <b style="color:var(--text)">'+brl(o.r_km)+'/km</b> · '+brl(o.r_h)+'/h</div></div>'+
    '<div class="pr">'+brl(o.preco)+'<small>líquido '+brl(o.liquido)+'</small></div></div>';
}
function renderOffers(){
  const d=last;if(!d)return;const list=showAll?d.ultimas:d.ultimas.slice(0,8);
  $("#offCount").textContent=d.ultimas.length?"· "+d.ultimas.length+" mais recentes":"";
  $("#ultimas").innerHTML=d.ultimas.length?list.map(offerRow).join("")+(d.ultimas.length>8?'<button class="more" id="more">'+(showAll?"Mostrar menos":"Ver todas ("+d.ultimas.length+")")+'</button>':""):'<div class="empty">Sem ofertas no período.</div>';
  const m=$("#more");if(m)m.onclick=()=>{showAll=!showAll;renderOffers();};
}
function render(d){
  const s=d.resumo,p=d.periodo,days=d.dias;
  $("#who").textContent=d.eu?(d.eu.nome||d.eu.email).split(" ")[0]:"admin";
  const sel=$("#selUser");sel.hidden=!d.admin;
  if(d.admin){sel.innerHTML='<option value="">👥 Todas as contas</option>'+(d.usuarios||[]).map(u=>'<option value="'+u.id+'">'+esc(u.nome||u.email)+'</option>').join("")+'<option value="anon">Sem conta (antigos)</option>';sel.value=usuario;}
  $("#info").textContent=(p.dias===1?"Hoje, "+dm(p.ate):dm(p.desde)+" a "+dm(p.ate))+" · "+s.eventos+" eventos";
  $("#upd").textContent=s.ultimo_evento?"último dado "+s.ultimo_evento.slice(11,16):"ao vivo";
  const liq=s.liquido;
  $("#hero").innerHTML='<div class="lbl">💰 Ganhos no período</div><div class="val"><small>R$</small>'+fmt(s.ganhos||0)+'</div>'+
    '<div class="sub">'+s.dias_trabalhados+' turno(s) encerrado(s)'+(days.length?' · média '+brl(s.ganhos/days.length)+'/dia':'')+'</div>'+
    '<div class="chips"><span class="chip"><span>Líquido</span><b class="'+(liq>=0?"pos":"neg")+'">'+brl(liq)+'</b></span><span class="chip">🛣️ '+num(s.km)+' <span>km</span></span><span class="chip">⏱️ '+hm(s.horas)+'</span></div>';
  $("#cores").innerHTML=donut(d.cores);
  const tile=(ic,bg,l,v,sm,sp,col)=>'<div class="card kpi">'+sp+'<div class="ic" style="background:'+bg+'">'+ic+'</div><div class="s">'+l+'</div><div class="v">'+v+'</div><div class="s">'+sm+'</div></div>';
  $("#kpis").innerHTML=
    tile("📍","rgba(255,176,32,.14)","R$ por km",brl(s.r_km),num(s.km)+" km rodados",spark(days.map(x=>x.km?x.ganho/x.km:null),"#FFB020"))+
    tile("⏱️","rgba(96,165,250,.14)","R$ por hora",brl(s.r_h),hm(s.horas)+" trabalhadas",spark(days.map(x=>x.horas?x.ganho/x.horas:null),"#60A5FA"))+
    tile("✅","rgba(34,197,94,.14)","Líquido",brl(liq),"depois do custo/km",spark(days.map(x=>x.liquido),"#22C55E"))+
    tile("📊","rgba(167,139,250,.14)","Ofertas lidas",String((d.cores.GREEN||0)+(d.cores.YELLOW||0)+(d.cores.RED||0)),(d.cores.GREEN||0)+" verdes","");
  $("#chartDias").innerHTML=areaChart(days.map(x=>({l:dm(x.data),v:x.ganho,title:dm(x.data)+" · "+brl(x.ganho)+(x.km?" · "+num(x.km)+" km":"")+(x.horas?" · "+hm(x.horas):"")})));
  $("#chartHoras").innerHTML=hoursChart(d.horas);
  $("#apps").innerHTML=d.apps.length?d.apps.map(a=>{const pct=Math.round((a.verdes||0)/a.n*100);
    return '<div class="card app"><div class="hd"><div class="badge" style="background:'+appc(a.app)+'">'+esc(a.app.slice(0,2))+'</div><div><div class="nm">'+esc(a.app)+'</div><div class="cnt">'+a.n+' ofertas</div></div></div>'+
      '<div class="big">'+brl(a.r_km)+' <small>/km</small></div><div class="meter"><i style="width:'+pct+'%"></i></div><div class="sub">'+pct+'% verdes</div>'+
      '<div class="ft"><span>Preço <b>'+brl(a.preco)+'</b></span><span>Km <b>'+num(a.km)+'</b></span><span><b>'+brl(a.r_h)+'</b>/h</span></div></div>';}).join(""):'<div class="empty">Sem ofertas no período.</div>';
  renderOffers();
  $("#pedidos").innerHTML=d.pedidos.length?d.pedidos.map(x=>'<div class="card app"><div class="hd"><div class="badge" style="background:'+appc(x.app)+'">'+esc(x.app.slice(0,2))+'</div><div><div class="nm">'+esc(x.app)+'</div><div class="cnt">'+x.n+' pedidos</div></div></div>'+
    '<div class="ft" style="margin-top:0"><span>Até coletar <b>'+num(x.coleta,0)+' min</b></span><span>Até entregar <b>'+num(x.entrega,0)+' min</b></span></div></div>').join(""):'<div class="empty">Nenhum pedido medido no período.</div>';
}
async function loadUsers(){
  const r=await fetch("/v1/admin/users",{headers:H()}).catch(()=>null);if(!r||!r.ok)return;
  const d=await r.json();$("#secUsers").hidden=false;
  $("#users").innerHTML=d.usuarios.map(u=>'<div class="u"><div class="'+(u.ativo?"":"off")+'"><b>'+esc(u.nome||"—")+'</b>'+(u.papel==="admin"?'<span class="tagA">admin</span>':'')+
    '<div class="sub">'+esc(u.email)+' · '+u.eventos+' eventos'+(u.ultimo?' · último '+dm(u.ultimo)+' '+u.ultimo.slice(11,16):'')+(u.ativo?'':' · desativada')+'</div></div>'+
    '<div class="b"><button data-a="reset" data-id="'+u.id+'">Nova senha</button>'+(u.papel==="admin"?'':'<button data-a="toggle" data-id="'+u.id+'">'+(u.ativo?"Desativar":"Ativar")+'</button>')+'</div></div>').join("");
}
$("#users").addEventListener("click",async e=>{const b=e.target.closest("button");if(!b)return;const id=+b.dataset.id;
  if(b.dataset.a==="reset"){const s=prompt("Nova senha para esta conta (mínimo 8 caracteres):");if(!s)return;
    const r=await fetch("/v1/admin/reset",{method:"POST",headers:H(),body:JSON.stringify({user_id:id,senha:s})});const d=await r.json();alert(r.ok?"Senha trocada. Passe a senha nova para a pessoa.":d.erro);}
  else{if(!confirm("Confirmar?"))return;const r=await fetch("/v1/admin/toggle",{method:"POST",headers:H(),body:JSON.stringify({user_id:id})});if(!r.ok)alert((await r.json()).erro);loadUsers();}});
load();setInterval(()=>{if(tok&&!document.hidden)load()},60000);
</script></body></html>
`;
