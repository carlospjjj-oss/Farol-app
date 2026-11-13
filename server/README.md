# Servidor de logs do Farol (Cloudflare)

O celular manda os logs para um endereço seu, **sem chave nenhuma no app**.
O servidor grava tudo num banco de dados (D1) e guarda uma cópia no repositório
privado `Farol` para análise. A chave do GitHub fica só no servidor, como segredo.

```
Farol (celular) ──► farol-logs.SEU-NOME.workers.dev ──► banco D1
                                   └──► cópia em logs/ no repositório privado
```

Plano grátis: 100 mil envios por dia e 5 GB de banco. O Farol usa uma fração mínima disso.

## Passo a passo (uns 15 minutos; no computador é mais fácil)

1. **Criar a conta.** Entre em `dash.cloudflare.com/sign-up` e cadastre e-mail e senha. Depois confirme o e-mail.
2. **Criar o banco.** No menu da esquerda, abra **Storage & databases → D1**, toque em **Create**, use o nome `farol` e confirme.
3. **Criar o servidor.** No menu, abra **Workers & Pages → Create → Start with Hello World**, use o nome `farol-logs` e toque em **Deploy**.
4. **Colar o código.** Toque em **Edit code** e apague tudo o que está lá.
   - Copie o arquivo `server/worker.js` deste repositório: no GitHub, abra o arquivo, toque em **Raw** e copie tudo.
   - Cole no editor e toque em **Deploy**.
5. **Ligar o banco.** Volte ao worker e abra **Bindings → Add binding → D1 database**.
   - Variable name: `DB`
   - Banco: `farol`
   - Salve.
6. **Variáveis.** Em **Settings → Variables and Secrets**, adicione:
   - Tipo **Text**: `GH_REPO` = `carlospjjj-oss/Farol`
   - Tipo **Secret**: `GH_TOKEN` = o token do GitHub (fine-grained, só o repositório Farol, Contents: Read and write)
7. **Testar o servidor.** Abra no navegador `https://farol-logs.SEU-NOME.workers.dev`. Tem que aparecer:
   `{"ok":true,"servico":"farol-logs","banco":true,"copia_github":true}`
8. **No Farol.** Abra **Ajustes → Logs → Servidor**, cole o endereço e toque em **Salvar** e depois em **Enviar teste**.
   - Se aparecer ✅, está funcionando.
   - Depois disso, apague o token do GitHub do app (aba GitHub). Ele não é mais necessário no celular.

## Ver os dados

No painel, abra **D1 → farol → Console** para rodar consultas. Exemplos:

```sql
SELECT tipo, COUNT(*) FROM events GROUP BY tipo;
SELECT t, app, json_extract(data, '$.preco') AS preco, json_extract(data, '$.rating') AS cor
  FROM events WHERE tipo = 'offer' ORDER BY id DESC LIMIT 50;
```

## Proteções

- O servidor só aceita o formato do Farol.
- Cada lote pode ter no máximo 512 KB e 500 eventos.
- Cada celular pode enviar no máximo 120 lotes por hora.
- Cada celular tem um código anônimo, e os endereços já saem mascarados do app.
