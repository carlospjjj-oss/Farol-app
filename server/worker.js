// Farol · servidor de logs (Cloudflare Worker + banco D1)
//
// O celular manda os lotes para cá SEM chave nenhuma. Aqui:
//   1) confere o formato, o tamanho e o limite de envios por celular;
//   2) grava cada evento no banco D1 (ligação "DB");
//   3) se houver o segredo GH_TOKEN, guarda uma cópia no repositório privado
//      (logs/AAAA-MM-DD/HHMMSS-<celular>.jsonl), igual ao formato antigo.
//
// Configuração no painel da Cloudflare (Worker → Settings):
//   Bindings  → D1 database, nome da variável: DB
//   Variables → GH_REPO = carlospjjj-oss/Farol   (texto)
//   Secrets   → GH_TOKEN = github_pat_...        (segredo, opcional)
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
];

let schemaReady = false;
async function ensureSchema(db) {
  if (schemaReady) return;
  await db.batch(SCHEMA.map((s) => db.prepare(s)));
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
    if (req.method !== "POST" || url.pathname !== "/v1/logs") return json({ erro: "rota inválida" }, 404);
    if (!env.DB) return json({ erro: "banco D1 não ligado (Bindings → DB)" }, 500);

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
    await ensureSchema(db);

    const now = Date.now();
    const recent = await db
      .prepare("SELECT COUNT(*) AS n FROM batches WHERE device = ? AND received_at > ?")
      .bind(device, now - 3600_000).first();
    if ((recent?.n || 0) >= MAX_BATCHES_PER_HOUR) return json({ erro: "muitos envios, tente mais tarde" }, 429);

    const s = (v, n) => (v == null ? null : String(v).slice(0, n));
    const ins = db.prepare(
      "INSERT INTO events (device, received_at, t, tipo, modo, app, versao, data) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
    const stmts = eventos.map((e) =>
      ins.bind(device, now, s(e.t, 40), s(e.tipo, 20), s(e.modo, 10), s(e.app, 40), versao, JSON.stringify(e)));
    stmts.push(db.prepare("INSERT INTO batches (device, received_at, n) VALUES (?, ?, ?)").bind(device, now, eventos.length));
    await db.batch(stmts);

    if (env.GH_TOKEN && env.GH_REPO) ctx.waitUntil(mirrorToGitHub(env, device, eventos).catch(() => {}));

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
