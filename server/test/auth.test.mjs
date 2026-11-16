import { DatabaseSync } from "node:sqlite";
import w from "../worker.js";
const sq = new DatabaseSync(":memory:");
class St { constructor(sql,args=[]){this.sql=sql;this.args=args} bind(...a){return new St(this.sql,a)}
  async all(){return {results: sq.prepare(this.sql).all(...this.args)}} async first(){return sq.prepare(this.sql).get(...this.args)}
  async run(){return sq.prepare(this.sql).run(...this.args)} runSync(){return sq.prepare(this.sql).run(...this.args)} }
const env={DB:{prepare:s=>new St(s),async batch(l){for(const s of l)s.runSync()}},ADMIN_EMAIL:"carlos@x.com",ADMIN_SETUP_KEY:"chave-de-ativacao-longa-123"}; const ctx={waitUntil(){}};
const call=async(m,path,body,tok,hdr={})=>{const r=await w.fetch(new Request("https://x"+path,{method:m,headers:{...(tok?{authorization:"Bearer "+tok}:{}),"cf-connecting-ip":"1.2.3.4",...hdr},body:body?JSON.stringify(body):undefined}),env,ctx);return [r.status,await r.json()]};
const ok=(c,m)=>console.log((c?"✔":"✘ FALHOU")+" "+m);
// legacy anon logs before account
let [st]=await call("POST","/v1/logs",{device:"dev12345",eventos:[{t:"2026-11-13T10:00:00-03:00",tipo:"offer",modo:"moto",app:"br.com.ifood.driver.app",preco:10,km:5,min:20,cor:"GREEN"}]});
ok(st===401,"envio sem login é recusado (acabou a transição)");
let [s1,r1]=await call("POST","/v1/auth/register",{nome:"Carlos",email:"Carlos@x.com",senha:"senha1234",device:"dev12345",aceite:true});
ok(s1===200&&r1.usuario.papel==="user"&&r1.token.length>30,"primeira conta NÃO vira admin pelo cadastro (nem com o e-mail de ADMIN_EMAIL)");
ok((await call("POST","/v1/auth/ativar-admin",{email:"carlos@x.com"}))[0]===403,"ativar admin sem o segredo é recusado");
ok((await call("POST","/v1/auth/ativar-admin",{email:"carlos@x.com"},null,{"x-farol-setup":"errada-errada-errada-errada"}))[0]===403,"segredo errado é recusado");
ok((await call("POST","/v1/auth/ativar-admin",{email:"carlos@x.com"},null,{"x-farol-setup":"chave-de-ativacao-longa-123"}))[0]===200,"segredo certo ativa o primeiro admin");
const tC=(await call("POST","/v1/auth/login",{email:"carlos@x.com",senha:"senha1234"}))[1].token;
ok((await call("GET","/v1/auth/me",null,tC))[1].usuario.papel==="admin","Carlos entra de novo já como admin");
ok((await call("POST","/v1/auth/register",{nome:"X",email:"carlos@x.com",senha:"senha1234",aceite:true}))[0]===409,"e-mail repetido recusado");
ok((await call("POST","/v1/auth/register",{nome:"X",email:"a@b.com",senha:"123",aceite:true}))[0]===400,"senha curta recusada");
let [s2,r2]=await call("POST","/v1/auth/register",{nome:"Joao Silva",email:"joao@x.com",senha:"outrasenha1",device:"devjoao1",aceite:true});
ok(s2===200&&r2.usuario.papel==="user","segunda conta é usuário comum");
ok((await call("POST","/v1/auth/ativar-admin",{email:"joao@x.com"},null,{"x-farol-setup":"chave-de-ativacao-longa-123"}))[0]===409,"com admin já existente, a ativação fica fechada (nem com o segredo)");
const tJ=r2.token;
await call("POST","/v1/logs",{device:"devjoao1",eventos:[{t:"2026-11-13T11:00:00-03:00",tipo:"offer",modo:"carro",app:"com.ubercab.driver",preco:30,km:10,min:25,cor:"RED"}]},tJ);
await call("POST","/v1/logs",{device:"dev12345",eventos:[{t:"2026-11-13T12:00:00-03:00",tipo:"offer",modo:"moto",app:"com.sankuai.sailor.courier",preco:12,km:5,min:10,cor:"GREEN"}]},tC);
ok((await call("POST","/v1/logs",{device:"dev12345",eventos:[{tipo:"x"}]},"tokeninvalidoaaaaaaaaaaaaaaaaaa"))[0]===401,"token inválido → 401");
let [,pJ]=await call("GET","/api/painel?dias=30",null,tJ);
ok(pJ.resumo.eventos===1&&pJ.apps[0].app==="Uber"&&!pJ.admin,"João vê só os dados dele");
let [,pC]=await call("GET","/api/painel?dias=30",null,tC);
ok(pC.admin&&pC.resumo.eventos===1&&!pC.usuarios&&!pC.apps.some(a=>a.app==="Uber"),"admin vê só os PRÓPRIOS números, nunca os do João");
let [,pC2]=await call("GET","/api/painel?dias=30&usuario="+r2.usuario.id,null,tC);
ok(pC2.resumo.eventos===1&&!pC2.apps.some(a=>a.app==="Uber"),"admin pedindo a conta do João continua vendo só a dele");
// páginas com cabeçalhos de segurança
{const r=await w.fetch(new Request("https://x/painel"),env,ctx); ok(r.headers.get("x-frame-options")==="DENY"&&/frame-ancestors 'none'/.test(r.headers.get("content-security-policy")||""),"painel não abre dentro de outro site");}
// sincronização: relógio adiantado não tranca o dia
{const far=Date.now()+400*86400_000;
 await call("POST","/v1/sync",{modo:"moto",since:0,days:[{date:"2026-11-10",updated_at:far,data:{earnings:{Keeta:10}}}]},tC);
 await call("POST","/v1/sync",{modo:"moto",since:0,days:[{date:"2026-11-10",updated_at:Date.now()+2*86400_000,data:{earnings:{Keeta:20}}}]},tC);
 const [,sy]=await call("POST","/v1/sync",{modo:"moto",since:0,days:[]},tC);
 ok(sy.days.find(d=>d.date==="2026-11-10")?.data.earnings.Keeta===20,"dia gravado com relógio adiantado ainda aceita a correção");}
ok((await call("GET","/api/painel?dias=30&usuario="+r1.usuario.id,null,tJ))[1].resumo.eventos===1,"usuário comum não consegue ver outra conta");
ok((await call("GET","/v1/admin/users",null,tJ))[0]===403,"usuário comum não acessa admin");
let [,us]=await call("GET","/v1/admin/users",null,tC); ok(us.usuarios.length===2&&us.usuarios.every(u=>!("ganhos" in u)&&Array.isArray(u.leitura)),"admin lista contas só com o técnico (versão, sinal, leitura)");
for(let i=0;i<8;i++) await call("POST","/v1/auth/login",{email:"joao@x.com",senha:"errada"});
ok((await call("POST","/v1/auth/login",{email:"joao@x.com",senha:"outrasenha1"}))[0]===429,"bloqueio após 8 senhas erradas");
ok((await call("POST","/v1/auth/login",{email:"carlos@x.com",senha:"senha1234",aceite:true}))[0]===200,"login certo do Carlos funciona");
ok((await call("POST","/v1/admin/reset",{user_id:r2.usuario.id,senha:"novasenha99"},tC))[0]===200,"admin redefine senha");
ok((await call("GET","/v1/auth/me",null,tJ))[0]===401,"sessões antigas do João caem após redefinir");
await call("POST","/v1/auth/logout",null,tC); ok((await call("GET","/v1/auth/me",null,tC))[0]===401,"logout invalida o token");
const row=sq.prepare("SELECT senha FROM users WHERE email='carlos@x.com'").get(); ok(row.senha.startsWith("pbkdf2$30000$")&&!row.senha.includes("senha1234"),"senha guardada só como hash");
