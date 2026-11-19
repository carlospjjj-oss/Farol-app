# Instruções do projeto Farol

- Idioma: português do Brasil, linguagem simples.
- Fluxo de versão: desenvolver em `fase1` → testes locais e no emulador (prints no ramo `prints`) → fast-forward
  para `main` (publica a release; se `server/worker.js` mudou, publica o servidor).
- **A cada versão publicada, atualizar as três cópias do documento mestre:**
  1. `docs/PROJETO.md` (este repositório);
  2. o documento do Claude: https://claude.ai/artifact/6CZzyRGabUH5TiHcsch2SE ;
  3. a página do Notion: https://www.notion.so/41819e43f6b18192ab72e0f481010611 (página privada "Farol — Documento mestre do projeto").
  Atualizar também o README (selo da versão e linha do histórico).
- Nunca colocar segredos no repositório; chaves só nos segredos do GitHub e da Cloudflare.
- Dados de clientes (nome, chat, endereço) nunca saem do celular; nome de restaurante fica só no aparelho.
- **Dado sensível que chegou aos logs: sempre limpar, sem perguntar** (autorização do Carlos em 19/11/2026):
  corrigir a máscara no app e no servidor, limpar o banco D1 e a cópia dos logs no GitHub privado (reescrevendo o
  histórico), conferir numa cópia nova e avisar o Carlos depois.
- **Até dezembro/2026: só correção de bugs, nada de função nova** (decisão do Carlos em 14/11/2026). O roadmap
  (https://claude.ai/artifact/TNNmE3furDGK1t5AbZFCT2) é só mapeamento; será revisto em dezembro.
