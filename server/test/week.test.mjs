import { DatabaseSync } from "node:sqlite";
import fs from "node:fs";
import http from "node:http";
import w from "../worker.js";
const sq = new DatabaseSync(":memory:");
class St { constructor(sql,args=[]){this.sql=sql;this.args=args} bind(...a){return new St(this.sql,a)}
  async all(){return {results: sq.prepare(this.sql).all(...this.args)}} async first(){return sq.prepare(this.sql).get(...this.args)}
  async run(){return sq.prepare(this.sql).run(...this.args)} runSync(){const r=sq.prepare(this.sql).run(...this.args);return {meta:{changes:r.changes}}} }
const env={DB:{prepare:s=>new St(s),async batch(l){return l.map(s=>s.runSync())}}}; const ctx={waitUntil(){}};
const call=async(m,path,body,tok)=>{const r=await w.fetch(new Request("https://x"+path,{method:m,headers:{...(tok?{authorization:"Bearer "+tok}:{}),"cf-connecting-ip":"8.8.8.8"},body:body?JSON.stringify(body):undefined}),env,ctx);return [r.status,await r.json()]};
const ok=(c,m)=>console.log((c?"✔":"✘ FALHOU")+" "+m);
const [,a]=await call("POST","/v1/auth/register",{nome:"Carlos",email:"c@x.com",senha:"senha1234",aceite:true});
const D=(n)=>new Date(Date.now()-3*3600e3-n*86400e3).toISOString().slice(0,10);
const days=[];
for(let i=0;i<14;i++){ if(i===3||i===10) continue; const g=i<7?[140,165,120,0,190,150,130][i]:[120,135,110,140,0,125,115][i-7];
  days.push({date:D(i),data:{kmStart:1000,kmEnd:1000+70+i*3,startAt:Date.now()-(i+1)*86400e3,endAt:Date.now()-(i+1)*86400e3+7*3600e3,earnings:{Keeta:g*0.6,iFood:g*0.4},_custo_km:0.45},updated_at:Date.now()}); }
await call("POST","/v1/sync",{modo:"moto",since:0,days},a.token);
// ofertas da semana
const ev=[];
for(let i=0;i<60;i++){ const h=[11,12,13,15,16,16,16,18][i%8]; const app=i%3?"com.sankuai.sailor.courier":"br.com.ifood.driver.app";
  ev.push({t:`${D(i%6)}T${String(h).padStart(2,"0")}:10:00-03:00`,tipo:"offer",modo:"moto",app,preco:8+i%7,km:4+i%3,min:15,cor:(h===16||i%5===0)&&app.includes("sankuai")?"GREEN":(i%4?"RED":"YELLOW")}); }
// saúde: Keeta lendo bem, iFood caiu hoje
ev.push({t:`${D(1)}T20:00:00-03:00`,tipo:"saude",modo:"moto",data:D(1),apps:{Keeta:{ok:30,falhas:1},iFood:{ok:20,falhas:1}}});
ev.push({t:`${D(0)}T12:00:00-03:00`,tipo:"saude",modo:"moto",data:D(0),apps:{Keeta:{ok:10,falhas:0},iFood:{ok:2,falhas:1}}});
ev.push({t:`${D(0)}T15:00:00-03:00`,tipo:"saude",modo:"moto",data:D(0),apps:{Keeta:{ok:18,falhas:1},iFood:{ok:3,falhas:5}}});
await call("POST","/v1/logs",{device:"devc0001",eventos:ev},a.token);
const [,p]=await call("GET","/api/painel?dias=7",null,a.token);
console.log(p.semana.frases.join("\n")); console.log("DICAS:",p.semana.dicas.join(" | "));
ok(p.semana.comparacao.ganho.atual>800 && p.semana.comparacao.ganho.variacao>0,"resumo compara com a semana anterior ("+Math.round(p.semana.comparacao.ganho.variacao*100)+"%)");
const kee=p.saude.find(x=>x.app==="Keeta"), ifd=p.saude.find(x=>x.app==="iFood");
ok(kee.status==="OK" && Math.round(kee.taxa_hoje*100)===95,"Keeta lendo bem (95%), usa o último envio do dia");
ok(ifd.status==="PROBLEMA","iFood com queda forte vira PROBLEMA ("+Math.round(ifd.taxa_hoje*100)+"% hoje)");
