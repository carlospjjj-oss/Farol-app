// Reaplica a proteção de terceiros (regra nova do servidor, 6.10.7) nos eventos já guardados.
import fs from "node:fs";
const src = fs.readFileSync(new URL("./worker_src.js", import.meta.url), "utf8");
const a = src.indexOf("const CHAT_RE"), b = src.indexOf("const SCHEMA");
const protegerTerceiros = new Function(src.slice(a, b) + "\nreturn protegerTerceiros;")();
const rows = JSON.parse(fs.readFileSync(process.argv[2], "utf8"))[0].results;
const out = []; let del = 0, upd = 0;
const q = (s) => "'" + s.replace(/'/g, "''") + "'";
for (const r of rows) {
  let e; try { e = JSON.parse(r.data); } catch { continue; }
  const before = JSON.stringify(e);
  if (!protegerTerceiros(e)) { out.push(`DELETE FROM events WHERE id = ${r.id};`); del++; continue; }
  const after = JSON.stringify(e);
  if (after !== before) { out.push(`UPDATE events SET data = ${q(after)} WHERE id = ${r.id};`); upd++; }
}
fs.writeFileSync("limpar.sql", out.join("\n"));
console.log(`linhas: ${rows.length} · atualizadas: ${upd} · apagadas: ${del}`);
