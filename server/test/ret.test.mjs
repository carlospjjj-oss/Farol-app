import { DatabaseSync } from "node:sqlite";
import w from "../worker.js";
const sq = new DatabaseSync(":memory:");
class St { constructor(sql,args=[]){this.sql=sql;this.args=args} bind(...a){return new St(this.sql,a)}
  async all(){return {results: sq.prepare(this.sql).all(...this.args)}} async first(){return sq.prepare(this.sql).get(...this.args)}
  async run(){return sq.prepare(this.sql).run(...this.args)} runSync(){const r=sq.prepare(this.sql).run(...this.args);return {meta:{changes:r.changes}}} }
const env={DB:{prepare:s=>new St(s),async batch(l){return l.map(s=>s.runSync())}}}; const waits=[]; const ctx={waitUntil:p=>waits.push(p)};
const call=async(m,path,body,tok)=>{const r=await w.fetch(new Request("https://x"+path,{method:m,headers:{...(tok?{authorization:"Bearer "+tok}:{}),"cf-connecting-ip":"9.9.9.9"},body:body?JSON.stringify(body):undefined}),env,ctx);return [r.status,await r.json()]};
const ok=(c,m)=>console.log((c?"✔":"✘ FALHOU")+" "+m);
const [,reg]=await call("POST","/v1/auth/register",{nome:"C",email:"c@x.com",senha:"senha1234",device:"dev12345",aceite:true,compartilhar:true});
sq.prepare("UPDATE users SET papel='admin' WHERE email='c@x.com'").run();
await call("POST","/v1/logs",{device:"dev12345",eventos:[{t:"2026-01-01T10:00:00-03:00",tipo:"offer",preco:10,km:5,cor:"GREEN",texto:"R$ 10 | Rua X"},{t:"2026-01-01T10:01:00-03:00",tipo:"tela",texto:"Coletar pedido"}]},reg.token);
await Promise.all(waits); waits.length=0;
// envelhece: 1 evento com 100 dias, outro com 400 dias
const d=86400000, now=Date.now();
sq.prepare("UPDATE events SET received_at=? WHERE tipo='offer'").run(now-100*d);
sq.prepare("UPDATE events SET received_at=? WHERE tipo='tela'").run(now-400*d);
sq.prepare("INSERT INTO events (device,user_id,received_at,t,tipo,data) VALUES ('dev12345',1,?,?, 'offer', ?)").run(now-10*d,"2026-11-01T10:00:00-03:00",JSON.stringify({preco:8,texto:"recente"}));
let [st,res]=await call("POST","/v1/admin/limpeza",null,reg.token);
ok(st===200,"limpeza manual roda para o admin");
const rows=sq.prepare("SELECT tipo, data FROM events ORDER BY id").all().map(r=>({tipo:r.tipo,d:JSON.parse(r.data)}));
ok(rows.length===2&&!rows.some(r=>r.tipo==="tela"),"tela com mais de 1 ano é apagada");
ok(rows[0].d.preco===10&&rows[0].d.texto===undefined,"oferta com mais de 90 dias perde o texto e mantém os números");
ok(rows[1].d.texto==="recente","oferta recente mantém o texto");
// automático: não roda de novo no mesmo dia
await call("POST","/v1/logs",{device:"dev12345",eventos:[{tipo:"teste",texto:"x"}]},reg.token); ok((await waits[0])===null,"a limpeza automática não roda duas vezes no mesmo dia");
