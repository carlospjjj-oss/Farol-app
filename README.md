# 🚦 Farol — calculadora de ofertas para entregador e motorista (uso pessoal)

Lê a oferta que aparece na tela do **iFood, 99, Keeta, Uber ou inDrive** e mostra por cima um cartão
verde/amarelo/vermelho com **R$/km, R$/h e lucro líquido** calculado com os **seus** custos.
Também funciona como diário do dia: turno, ganhos por app, abastecimento, tempo por pedido e melhores horários.
Tudo fica no celular; os logs (opcionais) vão para um repositório **privado** seu.

**Versão atual: 2.2**

```
┌──┬────────────────────────────────┐
│✓ │ R$ 6,56  ★ 4,97          IFOOD │   faixa e borda na cor do veredito
│  │ Tempo    Km       Busca        │   ✓ verde   ! amarelo   ✕ vermelho
│  │ 9 min    2,1 km   0,4 km       │
│  │ R$/km    R$/h     Lucro        │   R$/km e R$/h coloridos separadamente
│  │ 2,48     34,70    5,21 (79%)   │
└──┴────────────────────────────────┘   toque = fecha · arraste = move
```

---

## ⬇️ Baixar

**[github.com/carlospjjj-oss/farol-app/releases/latest](https://github.com/carlospjjj-oss/farol-app/releases/latest)** → *Assets* → `Farol.apk`

Cada alteração no código gera um APK novo ali (o GitHub Actions roda os testes antes).
Todas as versões são assinadas com a **mesma chave**, então atualizam por cima sem perder nada.

---

## 📲 Instalar (primeira vez)

1. **Play Protect:** no Brasil, o Google bloqueia a instalação de apps baixados que usam acessibilidade.
   Abra a **Play Store → foto do perfil → Play Protect → ⚙️** e desligue *"Verificar apps com o Play Protect"*,
   instale o `Farol.apk` e **ligue de novo**. (Pelo computador também dá: `adb install -r Farol.apk`.)
2. **Configurações restritas (Android 13+):** tente ligar o Farol em Acessibilidade uma vez (vai ser bloqueado),
   depois vá em *Configurações → Apps → Farol → ⋮ → Permitir configurações restritas*.
3. **Acessibilidade:** *Acessibilidade → Apps instalados → Farol – leitor de ofertas →* ligar.
4. **Bateria:** *Apps → Farol → Bateria → Sem restrições*. Na Samsung, confira também que o Farol **não** está em
   *Bateria → Limites de uso em segundo plano → Apps em suspensão*. Sem isso o Android desliga o leitor.

O selo no topo do dashboard mostra **🟢 Lendo ofertas** quando está tudo certo.

---

## ⚙️ Primeiro ajuste (⚙️ no canto superior direito)

Toque no atalho **🛵 Moto** e ajuste:

| Campo | Lead 110 (exemplo) | Observação |
|---|---|---|
| Combustível | **5,79** R$/L | o preço que você paga |
| Consumo | **30** km/L | depois o Farol mede o real |
| Outros custos | **0,15** R$/km | óleo, pneu, correia, IPVA, desgaste |
| 🟢 Verde a partir de | **2,00** R$/km e **30** R$/h | |
| 🟡 Amarelo a partir de | **1,40** R$/km e **20** R$/h | abaixo disso é vermelho |
| Velocidade média | **25** km/h | usada quando a oferta não mostra o tempo (~) |
| Busca máxima | **3** km | opcional |

- **Use vírgula** nos decimais (`1,80`). O Farol avisa se aparecer algo como `180`.
- **Suas faixas:** uma tabela mostra de uma vez onde começa o Ruim, a Atenção e o Aceitar para R$/km, R$/hora
  e nota do passageiro, e se atualiza enquanto você digita.
- **Condições mínimas:** valor da corrida, lucro líquido mínimo (R$) e lucro mínimo (%).
  **Tempo e distância:** busca máxima, distância total máxima e tempo máximo. Tudo com 0 = não usa.
- **🧮 Calcular custos do mês:** financiamento/aluguel, depreciação, manutenção, IPVA, seguro e outros, mais os km
  do mês → custo por km e % de cada item. **Aplicar ao semáforo** grava o "outros custos".
- **Hodômetro com décimo:** se o último número do painel for décimo de km, digite com vírgula: `58081,7`.
  Se esquecer, o Farol percebe (ex.: "947 km" em um dia) e oferece corrigir para 94,7 km com um toque.

---

## 🗓️ No dia a dia

| Quando | O que fazer |
|---|---|
| Ao sair | **▶ Iniciar turno** e digite o km do painel |
| Oferta chegou | O cartão aparece sozinho. Só olhar |
| Abasteceu | **⛽ Abastecer**: km do painel, litros, valor. Complete o tanque para medir o consumo real |
| Durante o dia | Abra a tela inicial de cada app de vez em quando: o Farol lê o "ganhos de hoje" sozinho |
| No fim | **⏹ Encerrar turno**. O Farol mostra "Rodou hoje: X km" antes de confirmar |

> ⚠️ **Vermelho quer dizer "paga pouco", não "recuse sempre".** 99 e iFood pausam a conta por excesso de
> recusas (aconteceu em 12/11). Aceite algumas amarelas para manter a taxa de aceitação.

---

## 📊 Dashboard (tela inicial)

- **Ganhos de hoje** em destaque, com o líquido. Toque para lançar ou corrigir à mão.
- **Km rodados, R$/km real, R$/hora e custo** do dia.
- **Turno** com um botão só (iniciar → encerrar), com checagem de km suspeito.
- **Ganhos por app** com barras. O selo verde **"auto 14:32"** indica que o valor veio do painel do próprio app.
- **Ofertas lidas hoje:** quantas verdes, amarelas e vermelhas, a média ofertada e as 3 últimas.
- **📋 Histórico de ofertas** (botão no cartão de ofertas): todas as ofertas lidas, agrupadas por dia,
  com filtros **Hoje / 7 dias / 30 dias**, por **app** e por **cor**, resumo (R$/km e preço médios, cores, por app)
  e os detalhes de cada oferta ao tocar (busca, nota, custo, líquido, avisos).
- **⏱️ Tempo por pedido** *(Keeta)*: média até coletar e para entregar, e os **restaurantes mais demorados**.
- **🕐 Melhores horários:** R$/km médio ofertado por hora do dia (14 dias) e as 3 melhores faixas.
- **Combustível:** consumo real (tanque cheio → tanque cheio), preço do litro e gasto no mês;
  "Usar no Farol" aplica o consumo e o preço nos custos.
- **7 dias / Este mês** com gráfico dos ganhos por dia e **Copiar dias (CSV)** para planilha.

---

## 📱 Situação por app

| App | Leitura da oferta | Ganhos automáticos | Tempo por pedido |
|---|---|---|---|
| **Keeta** (`com.sankuai.sailor.courier`) | ✅ texto direto (instantâneo) | ✅ painel "R$ X · Pedidos hoje" | ✅ |
| **iFood** (`br.com.ifood.driver.app`) | ✅ texto direto | ✅ tela inicial | ⏳ aguardando logs |
| **99** (`com.app99.driver`) | ✅ por OCR (~1 s, a 99 esconde o texto) | ⏳ aguardando confirmação do painel | ⏳ aguardando logs |
| Uber / inDrive | ⚪ previsto, ainda sem ofertas reais nos logs | — | — |

Valores que o Farol **ignora** ao procurar o preço: `+R$ 3` de dinâmica, "Inclui R$ 3,00 Bônus/extra",
"Promo R$ 5,00", "R$ 1,80/km", "Ganhe até R$ 84", ganhos do dia e preço de combustível do 99 Abastece.

---

## 📤 Envio de logs (para análise e calibração)

Os logs vão para o repositório **privado** `carlospjjj-oss/Farol` (este repositório público tem **só o código**).

1. Crie um token em *github.com → Settings → Developer settings → Fine-grained tokens*:
   **Only select repositories → Farol** e **Contents: Read and write**.
2. No Farol: *⚙️ → Envio de logs → GitHub*, cole o token e toque em **Enviar teste** (deve aparecer ✅).

O que é enviado (em lotes de ~1 minuto; sem sinal, fica na fila):

| Evento | Conteúdo |
|---|---|
| `offer` | oferta reconhecida: texto lido e números calculados |
| `miss` | tela com botão de aceitar que o leitor **não** entendeu |
| `tela` | telas por até 90 min depois de uma oferta (aceite, coleta, entrega) |
| `pedido` | pedido medido: app, restaurante, minutos até coletar e para entregar |
| `dia` | resumo ao encerrar o turno: km inicial/final, ganhos por app, custo, líquido |

Endereços, telefones e CEP saem como `[endereço]`, `[num]`, `[cep]`. **Nomes de clientes podem aparecer**:
mantenha o repositório `Farol` privado. Para analisar: peça *"analisa os logs do Farol"* numa conversa,
ou rode `python3 tools/analisar_logs.py --dias 7` dentro do repositório de logs.

---

## 🔧 Como funciona

| Etapa | Técnica | Arquivo |
|---|---|---|
| Perceber a oferta | `AccessibilityService` recebe as mudanças de tela dos apps monitorados | `service/OfferReaderService.kt` |
| Ler o texto | Árvore de acessibilidade só do app que está **na frente** (sem misturar dois apps) | `service/ScreenTextCollector.kt` |
| Plano B | Print pela própria acessibilidade + OCR on-device (ML Kit), imagem reduzida, novas tentativas | `service/OcrFallback.kt` |
| Entender a oferta | Regex tolerante a layout: preço, "km total", "min (km)", busca, nota; ignora bônus/promo | `core/OfferParser.kt` |
| Calcular | custo/km = combustível ÷ consumo + outros; cor = a pior entre R$/km e R$/h; filtros | `core/ProfitCalculator.kt` |
| Ganhos automáticos | Lê o "ganhos de hoje" do painel de cada app (guarda o maior valor do dia) | `core/EarningsReader.kt` |
| Tempo por pedido | Etapas coleta → entrega → livre pelo texto das telas | `core/Insights.kt` |
| Diário | Turno, ganhos, abastecimentos, pedidos em `journal.json` | `data/JournalStore.kt`, `core/Journal.kt` |
| Cartão | `TYPE_ACCESSIBILITY_OVERLAY` (dispensa "sobrepor a outros apps") | `ui/OverlayCard.kt` |
| Telas | Dashboard e Ajustes desenhados em código, tema escuro | `ui/DashboardActivity.kt`, `ui/SettingsActivity.kt`, `ui/Kit.kt` |

---

## 🛠️ Desenvolvimento

```
app/src/main/java/com/farol/app/
  core/      OfferParser, ProfitCalculator, EarningsReader, Insights, Journal, CostBreakdown, LogSanitizer  ← Kotlin puro
  data/      Settings, JournalStore, History, CaptureLog, RemoteLog
  service/   OfferReaderService, ScreenTextCollector, OcrFallback, FarolTileService
  ui/        DashboardActivity, HistoryActivity, SettingsActivity, CostsActivity, OverlayCard, Kit
app/src/test/  71 testes, quase todos com textos REAIS de ofertas e telas dos logs
```

- Build local: abra no Android Studio, ou `gradle testDebugUnitTest assembleDebug`.
- Toda leitura errada encontrada nos logs vira um teste em `OfferParserTest` antes de ser corrigida.
- A chave de assinatura fixa (`app/farol.jks`) fica no repositório por ser um app de uso pessoal.
  Quem tiver a chave poderia assinar um APK "do Farol"; instale apenas os APKs desta página.

## 📝 Histórico

| Versão | Principais mudanças |
|---|---|
| 2.2 | "Suas faixas" em tabela; nota em 3 faixas; condições mínimas; tempo e distância máximos; calculadora de custos do mês |
| 2.1 | Tela de Histórico de ofertas com filtros, resumo e detalhes |
| 2.0.1 | iFood: "Promo R$ 5,00" não é mais lido como preço |
| 2.0 | Tempo por pedido (Keeta) e restaurantes mais demorados; melhores horários |
| 1.9.x | Ganhos automáticos pelo painel dos apps; Keeta "km total" e "extra incluído"; checagem de km do turno e hodômetro com décimo |
| 1.8 | 99: bônus incluído não é preço; OCR mais rápido com novas tentativas; Keeta na lista |
| 1.7 | Registro de telas após oferta; layout respeitando as barras do Android 15 |
| 1.5 – 1.6 | Visual novo do dashboard, do cartão e dos Ajustes |
| 1.4 | Dashboard com turno, ganhos por app e abastecimento |
| 1.3 | Chave de assinatura fixa (atualiza por cima) |
| 1.0 – 1.2 | Leitor, cartão, filtros, voz, botão rápido e envio de logs |

## ⚠️ Limitações

- **Termos de uso:** os apps de entrega não autorizam apps de terceiros lendo a tela. O Farol **só lê**, nunca toca
  em aceitar/recusar, mas o risco é seu.
- Telas com `FLAG_SECURE` saem pretas no OCR.
- Na lista com várias ofertas (inDrive, "de olho" do Keeta), o Farol avalia a oferta em destaque.
