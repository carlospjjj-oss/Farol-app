import { DatabaseSync } from "node:sqlite";
import w from "../worker.js";
const sq = new DatabaseSync(":memory:");
class St { constructor(sql,args=[]){this.sql=sql;this.args=args} bind(...a){return new St(this.sql,a)}
  async all(){return {results: sq.prepare(this.sql).all(...this.args)}} async first(){return sq.prepare(this.sql).get(...this.args)}
  async run(){return sq.prepare(this.sql).run(...this.args)} runSync(){const r=sq.prepare(this.sql).run(...this.args);return {meta:{changes:r.changes}}} }
const env={TELEGRAM_TOKEN:"T0K",DB:{prepare:s=>new St(s),async batch(l){return l.map(s=>s.runSync())}}}; const ctx={waitUntil(){}};
// sem rede: a versão mais nova vem do meta
const sent=[]; let hook=null;
globalThis.fetch = async (u, o) => { u=String(u);
  if (!u.startsWith("https://api.telegram.org/")) return { ok: false };
  const m=u.split("/").pop(), b=JSON.parse(o.body||"{}");
  if (m==="setWebhook") hook=b; if (m==="sendMessage") sent.push(b);
  return { ok:true, json: async()=>({ ok:true, result: m==="getMe"?{username:"farol_adm_bot"}:{} }) }; };
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


// crash do Marcelo e um registro de ganho (não pode aparecer no bot)
await w.fetch(new Request("https://x/v1/logs",{method:"POST",headers:{authorization:"Bearer "+b.token,"cf-connecting-ip":"1.1.1.2"},body:JSON.stringify({device:"devm0001",versao:"6.3.1",eventos:[{t:D(0)+"T09:50:00-03:00",tipo:"crash",erro:"java.lang.IllegalStateException: teste",aparelho_modelo:"moto g54",android:34}]})}),env,ctx);
const tg=async(text,chat=555,secret)=>{const r=await w.fetch(new Request("https://x/v1/telegram",{method:"POST",headers:{"x-telegram-bot-api-secret-token":secret??hook?.secret_token??""},body:JSON.stringify({message:{chat:{id:chat,type:"private"},text}})}),env,ctx);return r.status};
ok(await tg("/avisos",555,"errado")===401,"webhook sem o segredo é recusado");
const [st,on]=await call("POST","/v1/admin/telegram",{},b.token);
ok(st===403,"conta comum não liga o bot");
const [,lig]=await call("POST","/v1/admin/telegram",{},a.token);
ok(lig.ok&&/^\d{6}$/.test(lig.codigo)&&lig.bot==="farol_adm_bot"&&hook.url==="https://x/v1/telegram"&&hook.secret_token.length>20,"admin liga o bot: webhook com segredo e código de 6 dígitos");
await tg("/avisos",999); ok(/privado/.test(sent.at(-1).text),"chat não vinculado não recebe nada");
await tg("/vincular 000000",555); ok(/inválido/.test(sent.at(-1).text),"código errado é recusado");
await tg("/vincular "+lig.codigo,555); ok(/Vinculado/.test(sent.at(-1).text),"código certo vincula");
await tg("/vincular "+lig.codigo,777); ok(/inválido/.test(sent.at(-1).text),"código só vale uma vez");
await tg("/avisos@farol_adm_bot",555); const av=sent.at(-1).text; console.log("---\n"+av+"\n---");
ok(/Marcelo/.test(av)&&/custo/.test(av)&&!/103/.test(av),"/avisos mostra problemas do Marcelo, sem ganhos");
await tg("/contas",555); const ct=sent.at(-1).text; console.log(ct+"\n---");
ok(/Marcelo: v6\.3\.1 \(desatualizado\)/.test(ct)&&/Carlos: v6\.5\.4/.test(ct)&&!/R\$/.test(ct),"/contas: versão e sinal, sem dinheiro");
await tg("/erros",555); ok(/IllegalStateException/.test(sent.at(-1).text)&&/moto g54/.test(sent.at(-1).text),"/erros lista o travamento");
// passada de hora em hora
sent.length=0;
const day=new Date(); day.setUTCHours(14);
await w.scheduled({scheduledTime:day.getTime()},env,{waitUntil:p=>p}); await new Promise(r=>setTimeout(r,50));
ok(sent.some(m=>/problemas novos/.test(m.text))&&sent.every(m=>m.chat_id===555),"primeira passada avisa os problemas novos só ao vinculado");
ok(!sent.some(m=>/travamento/.test(m.text)),"travamentos antigos não são reavisados na primeira passada");
sent.length=0; await w.scheduled({scheduledTime:day.getTime()},env,{waitUntil:p=>p}); await new Promise(r=>setTimeout(r,50));
ok(sent.length===0,"segunda passada sem novidade não manda nada");
// Marcelo corrige os ajustes e retoma
const ajOk={...ajM,ligado:true,etanol:{preco:4.3,km_l:8},custo_km:0.8,verde_km:2.5,amarelo_km:1.6};
await w.fetch(new Request("https://x/v1/logs",{method:"POST",headers:{authorization:"Bearer "+b.token,"cf-connecting-ip":"1.1.1.2"},body:JSON.stringify({device:"devm0001",versao:"6.3.1",eventos:[{t:D(0)+"T11:00:00-03:00",tipo:"ajustes",modo:"carro",ajustes:ajOk},{t:D(0)+"T11:01:00-03:00",tipo:"crash",erro:"NPE nova",aparelho_modelo:"moto g54"}]})}),env,ctx);
await w.scheduled({scheduledTime:day.getTime()},env,{waitUntil:p=>p}); await new Promise(r=>setTimeout(r,50));
console.log(sent.map(m=>m.text).join("\n---\n"));
ok(sent.some(m=>/Resolvido: Marcelo corrigiu os ajustes e retomou/.test(m.text)&&/retomou/.test(m.text)),"avisa o que foi resolvido");
ok(sent.some(m=>/1 travamento\(s\) novo/.test(m.text)&&/NPE nova/.test(m.text)),"avisa travamento novo");
sent.length=0; const am=new Date(); am.setUTCHours(11);
await w.scheduled({scheduledTime:am.getTime()},env,{waitUntil:p=>p}); await new Promise(r=>setTimeout(r,50));
ok(sent.length===1&&/Resumo das 8h/.test(sent[0].text),"resumo das 8h");
sent.length=0; await w.scheduled({scheduledTime:am.getTime()},env,{waitUntil:p=>p}); await new Promise(r=>setTimeout(r,50));
ok(sent.length===0,"resumo só uma vez por dia");
ok(!sent.concat().some(m=>/103|ganh(o|ou) R/.test(m.text)),"nenhuma mensagem com ganhos");
