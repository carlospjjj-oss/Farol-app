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
