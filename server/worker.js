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
//   Secrets   → ADMIN_KEY = uma senha sua           (para abrir o painel em /painel)
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
    if (req.method === "GET" && url.pathname === "/painel") {
      return new Response(PANEL_HTML, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
    }
    if (req.method === "GET" && url.pathname === "/api/painel") return panelApi(req, env, url);
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

// ============================== Painel web ==============================

const APP_NAMES = {
  "com.ubercab.driver": "Uber", "com.app99.driver": "99", "sinet.startup.inDriver": "inDrive",
  "br.com.ifood.driver.app": "iFood", "com.sankuai.sailor.courier": "Keeta",
};

function brtDate(offsetDays = 0) {
  return new Date(Date.now() - 3 * 3600_000 - offsetDays * 86400_000).toISOString().slice(0, 10);
}

async function panelApi(req, env, url) {
  if (!env.ADMIN_KEY) return json({ erro: "crie o segredo ADMIN_KEY no painel da Cloudflare" }, 503);
  if (req.headers.get("x-farol-key") !== env.ADMIN_KEY) return json({ erro: "senha errada" }, 401);
  if (!env.DB) return json({ erro: "banco D1 não ligado" }, 500);
  await ensureSchema(env.DB);

  const dias = Math.min(Math.max(parseInt(url.searchParams.get("dias") || "7", 10) || 7, 1), 365);
  const modo = ["moto", "carro"].includes(url.searchParams.get("modo")) ? url.searchParams.get("modo") : null;
  const since = brtDate(dias - 1);
  const db = env.DB;
  const mf = modo ? " AND modo = ?2" : "";
  const bind = (st) => (modo ? st.bind(since, modo) : st.bind(since));
  const all = async (sql) => (await bind(db.prepare(sql)).all()).results || [];

  const [cores, porApp, porHora, ultimas, pedidos, diasRows, totalEv] = await Promise.all([
    all(`SELECT json_extract(data,'$.cor') AS cor, COUNT(*) AS n FROM events
         WHERE tipo='offer' AND substr(t,1,10) >= ?1${mf} GROUP BY cor`),
    all(`SELECT app, COUNT(*) AS n,
           SUM(json_extract(data,'$.cor')='GREEN') AS verdes,
           SUM(json_extract(data,'$.preco')) / NULLIF(SUM(json_extract(data,'$.km')),0) AS r_km,
           SUM(json_extract(data,'$.preco')) * 60.0 / NULLIF(SUM(json_extract(data,'$.min')),0) AS r_h,
           AVG(json_extract(data,'$.preco')) AS preco, AVG(json_extract(data,'$.km')) AS km
         FROM events WHERE tipo='offer' AND substr(t,1,10) >= ?1${mf} GROUP BY app ORDER BY n DESC`),
    all(`SELECT CAST(substr(t,12,2) AS INTEGER) AS h, COUNT(*) AS n,
           SUM(json_extract(data,'$.cor')='GREEN') AS verdes,
           SUM(json_extract(data,'$.preco')) / NULLIF(SUM(json_extract(data,'$.km')),0) AS r_km
         FROM events WHERE tipo='offer' AND substr(t,1,10) >= ?1${mf} GROUP BY h ORDER BY h`),
    all(`SELECT t, app, json_extract(data,'$.preco') AS preco, json_extract(data,'$.km') AS km,
           json_extract(data,'$.min') AS min, json_extract(data,'$.r_km') AS r_km,
           json_extract(data,'$.r_h') AS r_h, json_extract(data,'$.liquido') AS liquido,
           json_extract(data,'$.cor') AS cor
         FROM events WHERE tipo='offer' AND substr(t,1,10) >= ?1${mf} ORDER BY id DESC LIMIT 25`),
    // pedidos não levam "modo": filtra só pela data
    (async () => (await db.prepare(`SELECT app, COUNT(*) AS n, AVG(json_extract(data,'$.min_coleta')) AS coleta,
           AVG(json_extract(data,'$.min_entrega')) AS entrega
         FROM events WHERE tipo='pedido' AND substr(t,1,10) >= ?1 GROUP BY app ORDER BY n DESC`).bind(since).all()).results || [])(),
    // resumo do dia: o último enviado de cada data (se o turno foi editado, vale o mais novo)
    (async () => (await db.prepare(
        `SELECT json_extract(data,'$.resumo') AS r FROM events WHERE id IN (
           SELECT MAX(id) FROM events WHERE tipo='dia' GROUP BY json_extract(data,'$.resumo.data'))
         AND json_extract(data,'$.resumo.data') >= ?1 ORDER BY json_extract(data,'$.resumo.data')`)
      .bind(since).all()).results || [])(),
    all(`SELECT COUNT(*) AS n, MAX(t) AS ultimo FROM events WHERE substr(t,1,10) >= ?1${mf}`),
  ]);

  const name = (a) => APP_NAMES[a] || a || "?";
  const days = diasRows.map((x) => { try { return JSON.parse(x.r); } catch { return null; } }).filter(Boolean);
  const sum = (k) => days.reduce((a, d) => a + (Number(d[k]) || 0), 0);
  const ganhos = sum("ganho_total"), km = sum("km"), horas = sum("horas"), liquido = sum("liquido");

  return json({
    periodo: { dias, desde: since, ate: brtDate(0), modo: modo || "todos" },
    resumo: {
      ganhos, km, horas, liquido, dias_trabalhados: days.length,
      r_km: km > 0 ? ganhos / km : null, r_h: horas > 0 ? ganhos / horas : null,
      eventos: totalEv[0]?.n || 0, ultimo_evento: totalEv[0]?.ultimo || null,
    },
    dias: days.map((d) => ({ data: d.data, ganho: d.ganho_total, km: d.km, horas: d.horas, liquido: d.liquido, ganhos: d.ganhos })),
    cores: Object.fromEntries(cores.map((c) => [c.cor || "?", c.n])),
    apps: porApp.map((a) => ({ ...a, app: name(a.app) })),
    horas: porHora,
    ultimas: ultimas.map((o) => ({ ...o, app: name(o.app) })),
    pedidos: pedidos.map((p) => ({ ...p, app: name(p.app) })),
  });
}

const PANEL_HTML = `<!DOCTYPE html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Farol · Painel</title>
<style>
:root{--bg:#0B0E13;--card:#151A21;--card2:#1C222B;--line:#262D37;--text:#F3F5F8;--muted:#8A94A3;--acc:#FFB020;--green:#22C55E;--yellow:#F5B014;--red:#EF4444}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.4 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
.wrap{max-width:1100px;margin:0 auto;padding:18px 16px 60px}
header{display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between;margin-bottom:16px}
h1{font-size:22px;margin:0}h1 span{color:var(--acc)}
.sub{color:var(--muted);font-size:13px}
.seg{display:inline-flex;background:var(--card);border:1px solid var(--line);border-radius:12px;padding:3px}
.seg button{background:none;border:0;color:var(--muted);font:600 13px system-ui;padding:7px 12px;border-radius:9px;cursor:pointer}
.seg button.on{background:var(--acc);color:#1C1300}
.grid{display:grid;gap:12px}.k{grid-template-columns:repeat(auto-fit,minmax(150px,1fr))}
.two{grid-template-columns:repeat(auto-fit,minmax(320px,1fr))}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:16px}
.ov{color:var(--muted);font-size:12px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;margin-bottom:8px}
.big{font-size:26px;font-weight:800}.small{color:var(--muted);font-size:12px;margin-top:2px}
table{width:100%;border-collapse:collapse;font-size:13px}th{color:var(--muted);text-align:left;font-weight:600;padding:6px 4px;border-bottom:1px solid var(--line)}
td{padding:7px 4px;border-bottom:1px solid var(--line);white-space:nowrap}td.n,th.n{text-align:right}
.dot{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:6px}
.GREEN{background:var(--green)}.YELLOW{background:var(--yellow)}.RED{background:var(--red)}
.bars{display:flex;align-items:flex-end;gap:4px;height:150px;padding-top:6px}
.bar{flex:1;display:flex;flex-direction:column;justify-content:flex-end;align-items:center;min-width:0}
.bar i{display:block;width:100%;border-radius:5px 5px 2px 2px;background:var(--acc)}
.bar b{font-size:10px;color:var(--muted);margin-top:4px;font-weight:500;white-space:nowrap}
.bar em{font-size:10px;color:var(--text);font-style:normal;margin-bottom:3px;white-space:nowrap}
.stack{display:flex;height:14px;border-radius:7px;overflow:hidden;background:var(--card2);margin:8px 0 10px}
.legend{display:flex;gap:14px;flex-wrap:wrap;font-size:13px;color:var(--muted)}
#login{max-width:360px;margin:12vh auto}input{width:100%;background:var(--card2);border:1px solid var(--line);color:var(--text);border-radius:12px;padding:12px;font-size:16px}
.btn{width:100%;margin-top:10px;background:var(--acc);color:#1C1300;border:0;border-radius:12px;padding:12px;font:700 15px system-ui;cursor:pointer}
.err{color:var(--red);font-size:13px;margin-top:8px;min-height:18px}.empty{color:var(--muted);font-size:13px;padding:10px 0}
.scroll{overflow-x:auto}
.bar i{max-width:56px}
@media(max-width:480px){.big{font-size:21px;white-space:nowrap}.wrap{padding:14px 10px 50px}.card{padding:13px}}
</style></head><body>
<div id="login" class="card" hidden>
  <h1>🚦 Farol <span>Painel</span></h1>
  <p class="sub">Digite a senha (ADMIN_KEY) que você criou na Cloudflare.</p>
  <input id="pw" type="password" placeholder="Senha" autocomplete="current-password">
  <button class="btn" id="go">Entrar</button><div class="err" id="err"></div>
</div>
<div class="wrap" id="app" hidden>
  <header>
    <div><h1>🚦 Farol <span>Painel</span></h1><div class="sub" id="info"></div></div>
    <div style="display:flex;gap:8px;flex-wrap:wrap">
      <div class="seg" id="segDias"><button data-v="1">Hoje</button><button data-v="7" class="on">7 dias</button><button data-v="30">30 dias</button></div>
      <div class="seg" id="segModo"><button data-v="" class="on">Todos</button><button data-v="moto">🛵</button><button data-v="carro">🚗</button></div>
    </div>
  </header>
  <div class="grid k" id="kpis"></div>
  <div class="grid two" style="margin-top:12px">
    <div class="card"><div class="ov">Ganhos por dia</div><div id="chartDias"></div></div>
    <div class="card"><div class="ov">Ofertas lidas</div><div id="cores"></div></div>
  </div>
  <div class="grid two" style="margin-top:12px">
    <div class="card"><div class="ov">Por app</div><div class="scroll" id="apps"></div></div>
    <div class="card"><div class="ov">Melhores horários (% verde)</div><div id="chartHoras"></div></div>
  </div>
  <div class="card" style="margin-top:12px"><div class="ov">Últimas ofertas</div><div class="scroll" id="ultimas"></div></div>
  <div class="card" style="margin-top:12px"><div class="ov">Pedidos (tempo medido)</div><div class="scroll" id="pedidos"></div></div>
</div>
<script>
const $=s=>document.querySelector(s);
const brl=v=>v==null||isNaN(v)?"—":"R$ "+Number(v).toLocaleString("pt-BR",{minimumFractionDigits:2,maximumFractionDigits:2});
const num=(v,d=1)=>v==null||isNaN(v)?"—":Number(v).toLocaleString("pt-BR",{minimumFractionDigits:d,maximumFractionDigits:d});
const hm=h=>h==null?"—":Math.floor(h)+"h"+String(Math.round((h%1)*60)).padStart(2,"0");
const esc=s=>String(s??"").replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
let key=null,dias=7,modo="";
try{key=localStorage.getItem("farolKey")}catch(e){}
function seg(id,cb){$(id).addEventListener("click",e=>{const b=e.target.closest("button");if(!b)return;
  $(id).querySelectorAll("button").forEach(x=>x.classList.toggle("on",x===b));cb(b.dataset.v);load();});}
seg("#segDias",v=>dias=+v);seg("#segModo",v=>modo=v);
$("#go").onclick=()=>{key=$("#pw").value.trim();load();};
$("#pw").onkeydown=e=>{if(e.key==="Enter")$("#go").click();};
function showLogin(msg){$("#app").hidden=true;$("#login").hidden=false;$("#err").textContent=msg||"";}
async function load(){
  if(!key)return showLogin();
  let r;try{r=await fetch("/api/painel?dias="+dias+"&modo="+modo,{headers:{"x-farol-key":key}});}catch(e){return showLogin("Sem conexão");}
  const d=await r.json().catch(()=>({erro:"resposta inválida"}));
  if(!r.ok){if(r.status===401){try{localStorage.removeItem("farolKey")}catch(e){}key=null;}return showLogin(d.erro);}
  try{localStorage.setItem("farolKey",key)}catch(e){}
  $("#login").hidden=true;$("#app").hidden=false;render(d);
}
function bars(items,fmtTop,color){
  if(!items.length)return '<div class="empty">Sem dados no período.</div>';
  const max=Math.max(...items.map(i=>i.v||0),1);
  return '<div class="bars">'+items.map(i=>'<div class="bar" title="'+esc(i.title||"")+'"><em>'+(i.v?fmtTop(i.v):"")+'</em><i style="height:'+Math.max(2,(i.v||0)/max*120)+'px;background:'+(i.c||color||"var(--acc)")+'"></i><b>'+esc(i.l)+'</b></div>').join("")+'</div>';
}
function render(d){
  const s=d.resumo,p=d.periodo;
  $("#info").textContent=(p.dias===1?"Hoje":p.desde.split("-").reverse().slice(0,2).join("/")+" a "+p.ate.split("-").reverse().slice(0,2).join("/"))+
    " · "+s.eventos+" eventos"+(s.ultimo_evento?" · último "+s.ultimo_evento.slice(11,16):"");
  const tile=(l,v,sm)=>'<div class="card"><div class="ov">'+l+'</div><div class="big">'+v+'</div>'+(sm?'<div class="small">'+sm+'</div>':"")+'</div>';
  $("#kpis").innerHTML=tile("Ganhos",brl(s.ganhos),s.dias_trabalhados+" turno(s) encerrado(s)")+tile("Líquido",brl(s.liquido),"depois do custo por km")+
    tile("R$/km",brl(s.r_km),num(s.km)+" km")+tile("R$/hora",brl(s.r_h),hm(s.horas)+" trabalhadas");
  $("#chartDias").innerHTML=bars(d.dias.map(x=>({l:x.data.slice(8)+"/"+x.data.slice(5,7),v:x.ganho,title:x.km?num(x.km)+" km":""})),v=>"R$"+Math.round(v));
  const c=d.cores,tot=(c.GREEN||0)+(c.YELLOW||0)+(c.RED||0);
  $("#cores").innerHTML=tot?'<div class="big">'+tot+'</div><div class="stack">'+["GREEN","YELLOW","RED"].map(k=>'<div class="'+k+'" style="width:'+((c[k]||0)/tot*100)+'%"></div>').join("")+
    '</div><div class="legend">'+[["GREEN","verdes"],["YELLOW","amarelas"],["RED","vermelhas"]].map(([k,l])=>'<span><i class="dot '+k+'"></i>'+(c[k]||0)+" "+l+" ("+Math.round((c[k]||0)/tot*100)+"%)</span>").join("")+"</div>":'<div class="empty">Nenhuma oferta lida no período.</div>';
  $("#apps").innerHTML=d.apps.length?'<table><tr><th>App</th><th class="n">Ofertas</th><th class="n">Verdes</th><th class="n">Preço</th><th class="n">Km</th><th class="n">R$/km</th><th class="n">R$/h</th></tr>'+
    d.apps.map(a=>'<tr><td>'+esc(a.app)+'</td><td class="n">'+a.n+'</td><td class="n">'+Math.round((a.verdes||0)/a.n*100)+'%</td><td class="n">'+brl(a.preco)+'</td><td class="n">'+num(a.km)+'</td><td class="n">'+brl(a.r_km)+'</td><td class="n">'+brl(a.r_h)+"</td></tr>").join("")+"</table>":'<div class="empty">Sem ofertas.</div>';
  const hrs=d.horas.filter(h=>h.n>0);
  $("#chartHoras").innerHTML=bars(hrs.map(h=>{const pct=Math.round((h.verdes||0)/h.n*100);return{l:h.h+"h",v:pct,c:pct>=50?"var(--green)":pct>=25?"var(--yellow)":"var(--red)",title:h.n+" ofertas · "+brl(h.r_km)+"/km"}}),v=>v+"%");
  $("#ultimas").innerHTML=d.ultimas.length?'<table><tr><th>Hora</th><th>App</th><th class="n">Preço</th><th class="n">Km</th><th class="n">Min</th><th class="n">R$/km</th><th class="n">R$/h</th><th class="n">Líquido</th></tr>'+
    d.ultimas.map(o=>'<tr><td><i class="dot '+esc(o.cor)+'"></i>'+o.t.slice(8,10)+"/"+o.t.slice(5,7)+" "+o.t.slice(11,16)+'</td><td>'+esc(o.app)+'</td><td class="n">'+brl(o.preco)+'</td><td class="n">'+num(o.km)+'</td><td class="n">'+num(o.min,0)+'</td><td class="n">'+brl(o.r_km)+'</td><td class="n">'+brl(o.r_h)+'</td><td class="n">'+brl(o.liquido)+"</td></tr>").join("")+"</table>":'<div class="empty">Sem ofertas.</div>';
  $("#pedidos").innerHTML=d.pedidos.length?'<table><tr><th>App</th><th class="n">Pedidos</th><th class="n">Até coletar</th><th class="n">Até entregar</th></tr>'+
    d.pedidos.map(x=>'<tr><td>'+esc(x.app)+'</td><td class="n">'+x.n+'</td><td class="n">'+num(x.coleta,0)+' min</td><td class="n">'+num(x.entrega,0)+" min</td></tr>").join("")+"</table>":'<div class="empty">Nenhum pedido medido no período.</div>';
}
load();setInterval(()=>{if(key&&!document.hidden)load()},60000);
</script></body></html>`;
