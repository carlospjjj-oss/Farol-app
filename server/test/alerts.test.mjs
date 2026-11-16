import { DatabaseSync } from "node:sqlite";
import w from "../worker.js";
const sq = new DatabaseSync(":memory:");
class St { constructor(sql,args=[]){this.sql=sql;this.args=args} bind(...a){return new St(this.sql,a)}
  async all(){return {results: sq.prepare(this.sql).all(...this.args)}} async first(){return sq.prepare(this.sql).get(...this.args)}
  async run(){return sq.prepare(this.sql).run(...this.args)} runSync(){const r=sq.prepare(this.sql).run(...this.args);return {meta:{changes:r.changes}}} }
const env={DB:{prepare:s=>new St(s),async batch(l){return l.map(s=>s.runSync())}}}; const ctx={waitUntil(){}};
// sem rede: a versão mais nova vem do meta
globalThis.fetch = async () => ({ ok: false });
const call=async(m,path,body,tok)=>{const r=await w.fetch(new Request("https://x"+path,{method:m,headers:{...(tok?{authorization:"Bearer "+tok}:{}),"cf-connecting-ip":"8.8.8.8"},body:body?JSON.stringify(body):undefined}),env,ctx);return [r.status,await r.json()]};
const ok=(c,m)=>{console.log((c?"✔":"✘ FALHOU")+" "+m); if(!c) process.exitCode=1;};
const [,a]=await call("POST","/v1/auth/register",{nome:"Carlos",email:"c@x.com",senha:"senha1234",aceite:true});
const [,b]=await call("POST","/v1/auth/register",{nome:"Marcelo",email:"m@x.com",senha:"senha1234",aceite:true});
sq.prepare("UPDATE users SET papel='admin' WHERE email='c@x.com'").run();   // o admin agora é ativado à parte (ativar-admin)
sq.prepare("INSERT INTO meta (k,v) VALUES ('ultima_versao', ?)").run(JSON.stringify({versao:"6.5.4",em:Date.now()}));
const D=(n)=>new Date(Date.now()-3*3600e3-n*86400e3).toISOString().slice(0,10);
// Carlos: em dia e certo
const ajC={modo:"moto",ligado:true,tanque:"gasolina",gasolina:{preco:6.2,km_l:32},etanol:{preco:4,km_l:25},custo_km:0.36,verde_km:2,amarelo_km:1.4};
await w.fetch(new Request("https://x/v1/logs",{method:"POST",headers:{authorization:"Bearer "+a.token,"cf-connecting-ip":"1.1.1.1"},body:JSON.stringify({device:"devc0001",versao:"6.5.4",eventos:[{t:D(0)+"T10:00:00-03:00",tipo:"ajustes",modo:"moto",ajustes:ajC}]})}),env,ctx);
// Marcelo: versão velha, custos zerados, pausado, turno aberto
const ajM={modo:"carro",ligado:false,tanque:"etanol",gasolina:{preco:4,km_l:1},etanol:{preco:0,km_l:1},custo_km:0.05,verde_km:1.5,amarelo_km:1};
await w.fetch(new Request("https://x/v1/logs",{method:"POST",headers:{authorization:"Bearer "+b.token,"cf-connecting-ip":"1.1.1.2"},body:JSON.stringify({device:"devm0001",versao:"6.3.1",eventos:[{t:D(0)+"T09:48:00-03:00",tipo:"ajustes",modo:"carro",ajustes:ajM}]})}),env,ctx);
await call("POST","/v1/sync",{modo:"carro",since:0,days:[{date:D(2),data:{kmStart:19747.7,startAt:Date.now()-50*3600e3,earnings:{"99":103.13},_custo_km:0.05},updated_at:Date.now()}]},b.token);

const [,me]=await call("GET","/v1/me/alertas",null,b.token);
const t=me.alertas.map(x=>x.tipo);
ok(me.alertas.every(x=>x.usuario===me.eu),"usuário comum só vê os próprios alertas");
ok(t.includes("versao")&&t.includes("ajustes")&&t.includes("pausado")&&t.includes("turno"),"Marcelo: versão, ajustes, pausado e turno aberto ("+t.join(",")+")");
ok(me.alertas[0].nivel==="problema","problema vem primeiro");
console.log("  "+me.alertas.find(x=>x.tipo==="ajustes").texto);
const [,adm]=await call("GET","/v1/me/alertas",null,a.token);
ok(adm.alertas.some(x=>x.nome==="Marcelo")&&!adm.alertas.some(x=>x.nome==="Carlos"),"admin vê os alertas do Marcelo; Carlos sem alertas");
const [,p]=await call("GET","/api/painel?dias=7",null,a.token);
ok(p.alertas===undefined,"painel não traz mais \"Precisa de atenção\" (vai para o Telegram e para o app)");
