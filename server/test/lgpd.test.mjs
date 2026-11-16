import { DatabaseSync } from "node:sqlite";
import w from "../worker.js";
const sq = new DatabaseSync(":memory:");
class St { constructor(sql,args=[]){this.sql=sql;this.args=args} bind(...a){return new St(this.sql,a)}
  async all(){return {results: sq.prepare(this.sql).all(...this.args)}} async first(){return sq.prepare(this.sql).get(...this.args)}
  async run(){return sq.prepare(this.sql).run(...this.args)} runSync(){const r=sq.prepare(this.sql).run(...this.args);return {meta:{changes:r.changes}}} }
const mirrored=[]; globalThis.fetch=async(u,o)=>{mirrored.push(u);return{ok:true,status:201}};
const env={DB:{prepare:s=>new St(s),async batch(l){return l.map(s=>s.runSync())}},GH_TOKEN:"x",GH_REPO:"a/b",CONTROLADOR_NOME:"Carlos Teste",CONTATO_PRIVACIDADE:"privacidade@exemplo.com"};
const waits=[]; const ctx={waitUntil:p=>waits.push(p)};
const call=async(m,path,body,tok)=>{const r=await w.fetch(new Request("https://x"+path,{method:m,headers:{...(tok?{authorization:"Bearer "+tok}:{}),"cf-connecting-ip":"5.5.5.5"},body:body?JSON.stringify(body):undefined}),env,ctx);const t=await r.text();let j;try{j=JSON.parse(t)}catch{j=t};return [r.status,j,r]};
const ok=(c,m)=>console.log((c?"✔":"✘ FALHOU")+" "+m);
ok((await call("POST","/v1/auth/register",{nome:"A",email:"a@x.com",senha:"senha1234"}))[0]===400,"cadastro sem aceitar a política é recusado");
const [,a]=await call("POST","/v1/auth/register",{nome:"Ana",email:"a@x.com",senha:"senha1234",aceite:true,compartilhar:false});
ok(a.usuario.consent_versao&&a.usuario.compartilhar===false,"cadastro grava versão da política e opção recusada");
// sem opt-in: texto removido, tela/miss descartados, sem cópia no GitHub
await call("POST","/v1/logs",{device:"devana01",eventos:[{tipo:"offer",preco:10,km:5,texto:"Rua X 123"},{tipo:"tela",texto:"Coletar"},{tipo:"miss",texto:"abc"}]},a.token);
await Promise.all(waits); waits.length=0;
let rows=sq.prepare("SELECT tipo,data FROM events").all();
ok(rows.length===1&&rows[0].tipo==="offer"&&!rows[0].data.includes("Rua X"),"sem consentimento: só números ficam, sem texto");
ok(mirrored.length===0,"sem consentimento: nada vai para a cópia do GitHub");
// liga opt-in
await call("POST","/v1/me/consent",{aceite:true,compartilhar:true},a.token);
await call("POST","/v1/logs",{device:"devana01",eventos:[{tipo:"tela",texto:"Coletar pedido"}]},a.token); await Promise.all(waits); waits.length=0;
ok(sq.prepare("SELECT COUNT(*) n FROM events WHERE tipo='tela'").get().n===1 && mirrored.length===1,"com consentimento: tela guardada e copiada");
// revoga: apaga textos
await call("POST","/v1/me/consent",{aceite:true,compartilhar:false},a.token);
ok(sq.prepare("SELECT COUNT(*) n FROM events WHERE tipo='tela'").get().n===0,"revogar o consentimento apaga as telas já guardadas");
ok((await call("POST","/v1/me/consent",{aceite:false},a.token))[0]===400,"não dá para 'aceitar' sem aceite");
// export
await call("POST","/v1/sync",{modo:"moto",since:0,days:[{date:"2026-11-13",data:{earnings:{iFood:9}},updated_at:Date.now()}]},a.token);
const [se,ex,resp]=await call("GET","/v1/me/export",null,a.token);
ok(se===200&&ex.conta.email==="a@x.com"&&!JSON.stringify(ex).includes("pbkdf2")&&ex.diario.length===1&&resp.headers.get("content-disposition").includes("farol-meus-dados"),"baixar meus dados: completo e sem a senha");
// delete
ok((await call("POST","/v1/me/delete",{senha:"errada"},a.token))[0]===401,"excluir conta exige a senha");
const [sd]=await call("POST","/v1/me/delete",{senha:"senha1234"},a.token);
const left=["users","events","user_days","sessions"].map(t=>sq.prepare("SELECT COUNT(*) n FROM "+t).get().n);
ok(sd===200&&left.every(n=>n===0),"excluir conta apaga tudo no servidor ("+left.join(",")+")");
ok(sq.prepare("SELECT COUNT(*) n FROM meta WHERE k LIKE 'exclusao:%'").get().n===1,"registro de exclusão (só o número) para limpar a cópia");
ok((await call("GET","/v1/auth/me",null,a.token))[0]===401,"token não vale mais depois da exclusão");
const [ps,html]=await call("GET","/privacidade");
ok(ps===200&&html.includes("Carlos Teste")&&html.includes("privacidade@exemplo.com")&&html.includes("art. 18")&&html.includes("15/2024"),"política publicada com controlador e canal");
