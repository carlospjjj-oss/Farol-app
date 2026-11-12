# 🚦 Farol — calculadora de ofertas para motorista/entregador (uso pessoal)

Lê a oferta que aparece na tela da **Uber, 99, inDrive ou iFood** e mostra por cima um cartão
verde/amarelo/vermelho com **R$/km, R$/h e lucro líquido** calculado com os **seus** custos.
É a mesma ideia do GigU, Rota Pro, DSW e DRIVH, sem servidor, conta ou anúncio: nada sai do celular.

```
┌──────────────────────────────┐
│ Uber • R$ 15,20              │
│ R$ 1,43/km                   │  ← fundo verde / amarelo / vermelho
│ R$ 41,45/h • 10,6 km • 22 min│
│ Líquido R$ 5,95 (R$ 16,22/h) │
└──────────────────────────────┘
  toque = fecha · arraste = move
```

## Como esses apps funcionam (e como o Farol faz o mesmo)

| Etapa | Técnica | Arquivo |
|---|---|---|
| 1. Saber que chegou uma oferta | `AccessibilityService` recebe eventos de mudança de janela/conteúdo dos apps monitorados | `service/OfferReaderService.kt` |
| 2. Ler o texto da oferta | Percorre a árvore de acessibilidade de **todas** as janelas do app (a oferta costuma ser uma janela sobre o mapa) | `service/ScreenTextCollector.kt` |
| 2b. Plano B | Se o texto não estiver acessível: print via `takeScreenshot()` da própria acessibilidade (Android 11+) e OCR on-device com ML Kit | `service/OcrFallback.kt` |
| 3. Entender a oferta | Regex tolerante a layout: preço `R$`, pares `min (km)`, `m`/`km`, `h`/`min`, "total"; ignora `+R$` (dinâmica), `R$/km` e "ganhos de hoje" | `core/OfferParser.kt` |
| 4. Calcular | custo/km = combustível ÷ consumo + outros custos; R$/km, R$/h, líquido; a cor é a **pior** entre R$/km e R$/h | `core/ProfitCalculator.kt` |
| 5. Mostrar | Janela `TYPE_ACCESSIBILITY_OVERLAY` (dispensa a permissão "sobrepor a outros apps") | `ui/OverlayCard.kt` |

A distância e o tempo **somam busca + viagem**, porque você roda e gasta nos dois trechos.

## Dashboard (tela inicial)

- **Hoje:** ganhos, km rodados, líquido, R$/km real e R$/hora.
- **Turno:** digite o km do painel e toque em *Iniciar*; no fim, o km final e *Encerrar*.
- **Ganhos por app:** iFood, 99, Keeta, Uber, inDrive e Outros, somados num total do dia.
- **Abastecimento:** km do painel, litros e valor. Com dois abastecimentos de tanque cheio, o Farol
  calcula o **consumo real** da moto, e um toque aplica esse consumo e o preço do litro nos custos.
- **Semana e mês:** ganhos, km e líquido acumulados; "Copiar dias (CSV)" para planilha.

## Recursos extras

- **Filtros que deixam o cartão vermelho**, com o motivo escrito (⚠):
  - busca acima de X km (distância até o passageiro ou até a coleta);
  - nota do passageiro abaixo de X;
  - bairros ou ruas de alerta encontrados no texto da oferta (acento e maiúscula não importam).
- **Voz:** fala "Verde. R$ 2,10 por quilômetro. R$ 45 por hora." para você não tirar o olho da rua.
- **Botão rápido:** puxe a barra de notificações → editar → arraste o botão "Farol" para ligar/pausar sem abrir o app.
- **Exportar histórico:** "Copiar CSV" e cole no Google Planilhas ou no Excel.
- O cartão mostra também a **busca** e a **nota do passageiro** quando a oferta exibe esses dados.

## Gerar o APK

**⬇️ Baixar pronto:** [github.com/carlospjjj-oss/farol-app/releases/latest](https://github.com/carlospjjj-oss/farol-app/releases/latest) → *Assets* → `Farol.apk`.
Cada envio de código gera um APK novo ali (workflow `Build APK`, que roda os testes antes).

**Os logs ficam separados**, no repositório **privado** `carlospjjj-oss/Farol`: este repositório público tem só o código.

**Opção B (Android Studio):** *File → Open* nesta pasta → *Run* com o celular conectado por USB.
O Android Studio cria o Gradle wrapper sozinho.

## Instalar e ativar no celular

1. Instale o APK (permita "instalar apps desconhecidos" para o navegador/gerenciador de arquivos).
2. **Android 13+:** *Configurações → Apps → Farol → ⋮ → Permitir configurações restritas*.
   Sem isso, o Android não deixa ligar a acessibilidade de um app instalado fora da Play Store.
   O botão "Liberar configurações restritas" no app abre essa tela.
3. *Acessibilidade → Apps instalados → Farol – leitor de ofertas →* ligar.
4. No Farol, preencha **seus custos e metas**, toque em **Salvar** e teste com 🟢 🟡 🔴.
5. Em alguns aparelhos (Xiaomi, Samsung, Motorola), tire o Farol da otimização de bateria,
   senão o sistema pode desligar o serviço.

## Envio de logs em tempo real (para análise)

Na seção **Envio de logs em tempo real** do app:

1. Escolha **GitHub** (recomendado). O repositório já vem preenchido como `carlospjjj-oss/Farol`.
2. Crie um token: github.com → *Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate*.
   - *Repository access:* **Only select repositories → Farol**
   - *Permissions → Contents:* **Read and write** (nada mais)
   - Cole o token no app e toque em **Enviar teste**. Deve aparecer `✅ enviados 1 eventos`.
3. Pronto: a cada 60 s (configurável), o que o Farol leu vai para `logs/AAAA-MM-DD/*.jsonl` no repositório.

O que é enviado:
- `offer`: oferta reconhecida, com o texto lido e os números calculados;
- `miss`: tela com cara de oferta (botão de aceitar) que o leitor **não entendeu**. É o mais útil para calibrar.

Privacidade: com **Mascarar endereços** ligado (padrão), rua e número viram `[endereço]` (o bairro fica)
e telefone/CEP são removidos antes de sair do celular. Nomes de passageiro não são detectados, então
**mantenha o repositório privado**. Sem sinal, os eventos ficam na fila e saem depois.

Analisar: `git pull && python3 tools/analisar_logs.py --dias 7`. Ou peça numa conversa comigo:
"analisa os logs do Farol" — eu leio direto do repositório e ajusto o leitor.

Opção **Webhook**: faz `POST {"eventos":[...]}` para qualquer URL (n8n, Make, Google Apps Script),
com cabeçalho `Authorization` opcional.

## Calibrar com ofertas reais (importante)

Cada app muda o layout de vez em quando. O leitor foi feito para ser tolerante, mas confirme com ofertas reais:

1. Ligue o **Modo captura** e salve.
2. Fique online e deixe chegar algumas ofertas.
3. **Copiar captura** e veja o que o Farol leu em cada oferta.
   - Aparece `[acessibilidade]` com preço e km → está funcionando pela via principal.
   - Só aparece `[ocr]` → o app esconde o texto da acessibilidade e o OCR está fazendo o trabalho.
   - Nada aparece para aquele app → o nome do pacote está errado: veja **Pacotes vistos** e corrija em *Apps monitorados*.
4. Se uma oferta foi lida mas o cálculo saiu errado, cole o texto em um novo teste em
   `app/src/test/.../OfferParserTest.kt`, ajuste o `OfferParser` até passar e gere o APK de novo.
   (Ou cole o texto aqui no chat comigo que eu ajusto.)

Pacotes padrão (edite se o seu for diferente):
```
com.ubercab.driver=Uber
com.app99.driver=99
sinet.startup.inDriver=inDrive
br.com.ifood.driver.app=iFood
```

## Sobre os números padrão

| Campo | Padrão | Como achar o seu |
|---|---|---|
| Combustível | R$ 6,30/L | preço do posto onde você abastece |
| Consumo | 11 km/L | média real na cidade (encha o tanque, zere o hodômetro) |
| Outros custos | R$ 0,30/km | (manutenção + pneus + seguro + depreciação ou aluguel) ÷ km rodados no mês |
| Verde | ≥ R$ 2,00/km e ≥ R$ 45/h | suas metas |
| Amarelo | ≥ R$ 1,50/km e ≥ R$ 30/h | abaixo disto fica vermelho |

## Limitações conhecidas

- **Termos de uso:** Uber e 99 não autorizam apps de terceiros lendo a tela delas, e há relatos de
  avisos e bloqueios ligados a esse tipo de app. O Farol **só lê**: nunca toca em aceitar/recusar.
  Mesmo assim, o risco existe e é seu.
- Se um app usar `FLAG_SECURE` na tela da oferta, o OCR recebe um print preto. Nesse caso só a
  leitura por acessibilidade funciona, e só se o app expuser o texto.
- **inDrive:** na lista com várias solicitações, o Farol avalia a primeira oferta da tela.
- Não há ajuste por destino (bairro ruim, volta vazia) nem por tarifa dinâmica futura.

## Ideias para depois

- Considerar a volta vazia quando o destino estiver longe da sua base.
- Relatório semanal: aceitas × recusadas, R$/km real × ofertado.

## Estrutura

```
app/src/main/java/com/farol/app/
  core/      OfferParser, ProfitCalculator   ← Kotlin puro, testado
  data/      Settings, History, CaptureLog
  service/   OfferReaderService, ScreenTextCollector, OcrFallback
  ui/        MainActivity, OverlayCard
app/src/test/  testes do parser e do cálculo
```
