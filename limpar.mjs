const CHAT_RE = /Insira sua mensagem|Mensagens r[aá]pidas|Criar nova frase r[aá]pida|Minha imagem de perfil|Imagem de perfil dele|Chat da entrega\s*\|\s*Voz|Digite uma mensagem|Enviar mensagem/i;
const DELIVERY_RE = /\bCliente\b|Entregue at[eé]|Para finalizar a entrega|Cheguei na entrega|Entregar em at[eé]|c[oó]digo de (verifica[cç][aã]o|entrega)|Entrega em m[aã]os|Venha retirar|\bEntrega\s*\|/i;
const PERSON_RE = /^\p{Lu}[\p{L}'´`]+(?:\s+(?:d[aeo]s?\s+)?\p{Lu}[\p{L}'´`]*\.?){0,3}$/u;
const ORDER_NAME_RE = /(#\d{3,}|C[oó]digo de coleta\s+\d+)\s+\p{Lu}\p{L}+(?:\s+\p{Lu}\p{L}+)*/gu;
const NOT_NAME_RE = /^(Mapa|Marcador|Rotas|Cliente|Restaurante|Entrega|Coleta|Suporte|Ajuda|Passo|Chamada|Mensagem|Concluir|Detalhes|Central|Bloco|Cheguei|Entregue|Para|Falar|Por|Go|Order|N[uú]mero|Ir|Voz|Texto|Mais|Lida|Limite|Rotat[oó]ria|Mantenha|Vire|Siga|Pedido|Aceitar|Recusar|Google|Waze|Insira|Pe[cç]a|Chegada|Previs[aã]o|Em|Sua|Seu|Voc[eê]|Inclui|Taxa|Outros|Resumo|Painel|Ganhos|Iniciar|Confirmar|Cancelar)\b/i;
function maskPeople(text) {
  if (typeof text !== "string") return text;
  text = text.replace(ORDER_NAME_RE, "$1 [cliente]");   // nome junto do nº do pedido / código de coleta: sempre
  if (!DELIVERY_RE.test(text)) return text;
  const parts = text.split(" | ");
  for (let i = 0; i < parts.length; i++) {
    const p = parts[i].trim(), next = (parts[i + 1] || "").trim(), prev = (parts[i - 1] || "").trim();
    const beforeAddress = next.startsWith("[endereço]") || next.startsWith("#");
    const afterHeader = /^entrega$/i.test(prev) || /^marcador do mapa$/i.test(prev);
    if ((beforeAddress || afterHeader) && PERSON_RE.test(p) && !NOT_NAME_RE.test(p) && p.length <= 40) parts[i] = "[cliente]";
    parts[i] = parts[i].replace(ORDER_NAME_RE, "$1 [cliente]");
  }
  return parts.join(" | ");
}
// Rua que sobrou depois do "[endereço]" (versões antigas do app): "[endereço], Estrada X, 2282, Cidade"
const LEFTOVER_STREET_RE = /\[endereço\],\s*(?:r\.?|rua|av\.?|avenida|al\.?|alameda|tv\.?|trav\.?|travessa|estr\.?|estrada|rod\.?|rodovia|pra[cç]a|largo|via|viela|beco)\s[^,|]*(?:,\s*\d+[a-zA-Z]?)?/gi;
/** Limpa um evento no lugar. Retorna false se ele não deve ser guardado (tela de chat). */
function protegerTerceiros(e) {
  for (const k of ["texto", "texto_acessibilidade"]) {
    if (typeof e[k] !== "string") continue;
    if (CHAT_RE.test(e[k])) { if (e.tipo === "tela") return false; delete e[k]; continue; }
    e[k] = maskPeople(e[k]).replace(LEFTOVER_STREET_RE, "[endereço]");
  }
  if (e.tipo === "pedido") delete e.restaurante;   // nome do restaurante fica só no celular
  return true;
}


import fs from "node:fs";
const rows = JSON.parse(fs.readFileSync(process.argv[2], "utf8"))[0].results;
const out = []; let del = 0, upd = 0;
const q = (s) => "'" + s.replace(/'/g, "''") + "'";
for (const r of rows) {
  let e; try { e = JSON.parse(r.data); } catch { continue; }
  if (!protegerTerceiros(e)) { out.push(`DELETE FROM events WHERE id = ${r.id};`); del++; continue; }
  const n = JSON.stringify(e); if (n !== r.data) { out.push(`UPDATE events SET data = ${q(n)} WHERE id = ${r.id};`); upd++; }
}
fs.writeFileSync("limpar.sql", out.join("\n") + "\n");
console.log(JSON.stringify({ lidos: rows.length, apagar: del, mascarar: upd }));
