import { DatabaseSync } from "node:sqlite";
import w from "../worker.js";
const sq = new DatabaseSync(":memory:");
class St { constructor(sql,args=[]){this.sql=sql;this.args=args} bind(...a){return new St(this.sql,a)}
  async all(){return {results: sq.prepare(this.sql).all(...this.args)}} async first(){return sq.prepare(this.sql).get(...this.args)}
  async run(){return sq.prepare(this.sql).run(...this.args)} runSync(){const r=sq.prepare(this.sql).run(...this.args);return {meta:{changes:r.changes}}} }
const env={DB:{prepare:s=>new St(s),async batch(l){return l.map(s=>s.runSync())}}}; const ctx={waitUntil(){}};
const call=async(m,path,body,tok)=>{const r=await w.fetch(new Request("https://x"+path,{method:m,headers:{...(tok?{authorization:"Bearer "+tok}:{}),"cf-connecting-ip":"7.7.7.7"},body:body?JSON.stringify(body):undefined}),env,ctx);return [r.status,await r.json()]};
const ok=(c,m)=>console.log((c?"✔":"✘ FALHOU")+" "+m);
const [,a]=await call("POST","/v1/auth/register",{nome:"Carlos",email:"c@x.com",senha:"senha1234",aceite:true});
const [,b]=await call("POST","/v1/auth/register",{nome:"Joao",email:"j@x.com",senha:"senha1234",aceite:true});
const today=new Date(Date.now()-3*3600e3).toISOString().slice(0,10);
const now=Date.now();
// celular 1 do Carlos manda um dia (turno em andamento) e um abastecimento
let [s1,r1]=await call("POST","/v1/sync",{modo:"moto",since:0,days:[{date:today,data:{kmStart:100,startAt:now-2*3600e3,earnings:{iFood:50,Keeta:30},_custo_km:0.4},updated_at:now}],fuel:[{at:now-1000,data:{at:now-1000,odo:100,liters:5,total:30,full:true},deleted:false,updated_at:now}]},a.token);
ok(s1===200&&r1.recebidos===2&&r1.days.length===1,"envio e retorno do dia/abastecimento");
// celular novo do Carlos: since 0 recebe tudo
let [,r2]=await call("POST","/v1/sync",{modo:"moto",since:0},a.token);
ok(r2.days.length===1&&r2.days[0].data.earnings.iFood===50&&r2.fuel.length===1,"celular novo recebe tudo no primeiro login");
// versão mais velha não sobrescreve
await call("POST","/v1/sync",{modo:"moto",since:0,days:[{date:today,data:{earnings:{iFood:1}},updated_at:now-5000}]},a.token);
let [,r3]=await call("POST","/v1/sync",{modo:"moto",since:0},a.token);
ok(r3.days[0].data.earnings.iFood===50,"dado mais velho não sobrescreve o mais novo");
// cursor: nada novo depois do cursor
let [,r4]=await call("POST","/v1/sync",{modo:"moto",since:r3.cursor},a.token);
ok(r4.days.length===0&&r4.fuel.length===0,"cursor evita mandar de novo");
// apagar abastecimento propaga
await call("POST","/v1/sync",{modo:"moto",since:0,fuel:[{at:now-1000,data:{},deleted:true,updated_at:now+10}]},a.token);
let [,r5]=await call("POST","/v1/sync",{modo:"moto",since:r3.cursor},a.token);
ok(r5.fuel.length===1&&r5.fuel[0].deleted===true,"abastecimento apagado vai para os outros aparelhos");
// João não vê dados do Carlos
let [,rj]=await call("POST","/v1/sync",{modo:"moto",since:0},b.token);
ok(rj.days.length===0&&rj.fuel.length===0,"outra conta não recebe os dados do Carlos");
ok((await call("POST","/v1/sync",{modo:"moto"}))[0]===401,"sem login → 401");
// carro separado
let [,rc]=await call("POST","/v1/sync",{modo:"carro",since:0},a.token); ok(rc.days.length===0,"modo carro separado do moto");
// painel: ganhos ao vivo do dia em andamento
let [,p]=await call("GET","/api/painel?dias=1",null,a.token);
ok(Math.round(p.resumo.ganhos)===80 && p.resumo.horas>1.9 && p.resumo.horas<2.1, "painel mostra ganho do dia em andamento (R$ "+p.resumo.ganhos+", "+p.resumo.horas.toFixed(2)+"h)");
let [,pj]=await call("GET","/api/painel?dias=1",null,b.token); ok(pj.resumo.ganhos===0,"painel do João não soma o Carlos");
