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

## Contas (login)

- O app pede e-mail e senha na abertura, e cada conta só vê os próprios dados.
- **A primeira conta criada vira administradora.** Para garantir que seja a sua, crie a variável `ADMIN_EMAIL` com o seu e-mail.
- **Senhas:** são guardadas só como código embaralhado (PBKDF2, 30 mil rodadas). Depois de 8 senhas erradas, a conta bloqueia por 15 minutos.
- **Senha esquecida:** o administrador entra no painel, vai em **Contas → Nova senha** e passa a senha nova para a pessoa.
- **Envios sem login:** enquanto houver celular na versão antiga, eles continuam aceitos. Quando todo mundo estiver na 5.0, crie a variável `REQUIRE_LOGIN` = `1` para recusar esses envios.

## Painel web

Abra `https://farol-logs.SEU-NOME.workers.dev/painel` no celular ou no computador.

- Entre com a mesma conta do app.
- O administrador vê todas as contas, ou uma de cada vez. Os outros veem só os próprios dados.
- `ADMIN_KEY` (Secret) continua opcional, como acesso de emergência.
- O painel mostra:
  - ganhos, líquido, R$/km e R$/h;
  - ganhos por dia;
  - ofertas por cor;
  - números por app;
  - melhores horários;
  - últimas ofertas;
  - tempos dos pedidos.
- Dá para filtrar por Hoje, 7 ou 30 dias e por Moto ou Carro. A página se atualiza sozinha a cada minuto.

## Ver os dados

No painel, abra **D1 → farol → Console** para rodar consultas. Exemplos:

```sql
SELECT tipo, COUNT(*) FROM events GROUP BY tipo;
SELECT t, app, json_extract(data, '$.preco') AS preco, json_extract(data, '$.cor') AS cor
  FROM events WHERE tipo = 'offer' ORDER BY id DESC LIMIT 50;
```

## Privacidade (retenção)

A limpeza roda sozinha uma vez por dia, no primeiro envio do app:

- **Depois de 90 dias:** o **texto** lido das telas e ofertas é apagado. Ficam só os números (preço, km, tempo, cor, app, horário), que alimentam o painel.
- **Depois de 1 ano:** os eventos de "tela", que eram só texto, são apagados.
- **Sessões e controles de envio antigos** também saem.
- **Para rodar na hora:** como administrador, faça POST em `/v1/admin/limpeza`.

## Proteções

- O servidor só aceita o formato do Farol.
- Cada lote pode ter no máximo 512 KB e 500 eventos.
- Cada celular pode enviar no máximo 120 lotes por hora.
- Cada celular tem um código anônimo, e os endereços já saem mascarados do app.
