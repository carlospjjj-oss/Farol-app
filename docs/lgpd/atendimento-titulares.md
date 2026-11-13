# Atendimento aos Titulares (LGPD arts. 18 e 19)

| Pedido | Autoatendimento no app | Se chegar pelo canal |
|---|---|---|
| Acesso / cópia / portabilidade | Ajustes → Privacidade → Baixar meus dados | Pedir que a pessoa use o app logada; ou gerar `/v1/me/export` pela conta dela |
| Correção | Editar no app | Orientar onde editar |
| Exclusão | Ajustes → Privacidade → Excluir minha conta | Confirmar identidade (e-mail cadastrado) e excluir pelo painel |
| Revogar consentimento dos textos | Desligar “Compartilhar textos das telas” | Desligar pela conta da pessoa |
| Oposição, revisão de decisão automatizada, informação | — | Responder pelo canal |

**Prazos:** resposta simplificada imediata; declaração completa em até 15 dias (art. 19, II).
**Depois de uma exclusão:** o painel de administração lista “Contas excluídas pelo titular (`u<id>`)”. Apague também os arquivos `logs/**/*-u<id>-*.jsonl` da cópia técnica no repositório privado.
**Registro:** anote a data do pedido, o tipo, a data da resposta e o que foi feito.
