import w from "../worker.js";
import fs from "node:fs";
const ok=(c,m)=>console.log((c?"✔":"✘ FALHOU")+" "+m);
const r=await w.fetch(new Request("https://x/privacidade"),{CONTROLADOR_NOME:"Carlos (Farol)",CONTATO_PRIVACIDADE:"seu-email-de-privacidade@exemplo.com"},{waitUntil(){}});
{const r2=await w.fetch(new Request("https://x/privacidade"),{},{waitUntil(){}}); const h=await r2.text();
 ok(r2.status===200&&/não mostra ganhos, ofertas nem o diário de outras contas/.test(h),"aviso de privacidade diz que o admin não vê números de outras contas");
 ok(/Telegram/.test(h)&&r2.headers.get("x-frame-options")==="DENY","aviso cita o Telegram e não abre dentro de outro site");}
