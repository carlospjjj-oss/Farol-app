# Relatório de Impacto à Proteção de Dados (RIPD) — Farol

**Versão:** 2026-11-13 · **Escopo:** app Android, servidor (Cloudflare Workers + D1), painel web, cópia técnica no GitHub.

## 1. Por que fazer um RIPD
O Farol usa o **serviço de acessibilidade** para ler telas de outros apps e faz **avaliação automatizada** (cor do cartão). São tecnologias que podem ser invasivas se forem mal usadas. Por isso documentamos os riscos, mesmo num tratamento de pequena escala.

## 2. Natureza, escala e contexto
- **Titulares:** motoristas e entregadores adultos, usuários do app (poucas contas).
- **Dados:** cadastrais, operacionais (turnos, ganhos, ofertas) e, com consentimento, textos de telas. Sem dados sensíveis e sem localização no servidor.
- **Contexto:** o próprio titular usa o app para decidir sobre o trabalho dele. Não existe relação com empregador nem com os apps de mobilidade.

## 3. Riscos e medidas

| Risco | Prob. | Impacto | Medidas adotadas | Risco residual |
|---|---|---|---|---|
| Acessibilidade lendo conteúdo de outros apps (mensagens, bancos) | Baixa | Alto | Filtro por pacote no Android (só apps monitorados); conferência de que a tela está em primeiro plano; modo descoberta só por 2 min sob comando | Baixo |
| Vazamento do banco no servidor | Baixa | Médio | Senhas em PBKDF2; tokens só como hash; escopo por conta; textos opcionais e apagados em 90 dias; sem localização | Baixo |
| Textos com dados de terceiros (nomes de lojas, bairros) | Média | Baixo | Mascaramento de endereço, telefone e CEP no aparelho; opt-in; anonimização antes de virar teste | Baixo |
| APK falso ou atualização adulterada | Baixa | Alto | Chave rotacionada (v3); app confere SHA-256 e certificado fixo; chave só nos Secrets | Baixo |
| Acesso indevido à conta (senha fraca ou adivinhada) | Média | Médio | Mínimo de 8 caracteres; bloqueio após 8 erros por 15 min; logout derruba sessões; troca de senha derruba outros aparelhos | Baixo |
| Decisão automatizada prejudicar o titular | Baixa | Baixo | Só recomendação; critérios definidos pelo próprio titular; nada é compartilhado com os apps | Muito baixo |
| Transferência internacional (EUA) | — | Baixo | Provedores com garantias contratuais (art. 33); minimização | Baixo |
| Retenção excessiva | Média | Baixo | Limpeza diária automática; exclusão da conta imediata; relatórios de travamento até 180 dias | Baixo |

## 4. Necessidade e proporcionalidade
Cada dado no servidor existe para uma função que o titular usa (diário, painel, restaurar no celular novo). O único tratamento que não é essencial (textos de telas) fica separado, é opcional e pode ser revogado.

## 5. Conclusão
Com as medidas acima, o risco residual é **baixo** e o tratamento é compatível com a LGPD. **Reavaliar** se surgir: distribuição pública do app, novo dado (por exemplo, localização no servidor), novo operador ou uso de IA em servidor sobre os textos.
