# Configurar o Farol no seu celular

Cada marca esconde as telas num lugar diferente. O guia do app (**Ajustes › Guia de instalação**, ou tocando em
**"Leitor desligado"** no painel) já mostra o caminho certo para o seu aparelho. Este documento é a mesma coisa, para
consultar ou mandar para alguém.

> Os passos com ⋮ são o botão de três pontinhos no canto de cima da tela.

## Resumo

| Passo | Samsung (One UI) | Motorola | Xiaomi / Redmi / POCO (HyperOS/MIUI) |
|---|---|---|---|
| Instalar/atualizar | Segurança e privacidade › **Bloqueador automático** › Desativado | Apps › Acesso especial a apps › Instalar apps desconhecidos | Apps › Permissões › Instalar apps desconhecidos |
| Configurações restritas (Android 13+) | Aplicativos › Farol › ⋮ › Permitir configurações restritas | Apps › Farol › ⋮ › Permitir configurações restritas | Apps › Gerenciar apps › Farol › ⋮ › Permitir configurações restritas |
| **Acessibilidade** | Acessibilidade › **Apps instalados** › Farol | Acessibilidade › Farol (em **Apps baixados**) | **Configurações adicionais** › Acessibilidade › **Apps baixados** › Farol |
| Bateria | Aplicativos › Farol › Bateria › Sem restrições | Apps › Farol › Uso da bateria do app › Sem restrições | Apps › Gerenciar apps › Farol › Economia de bateria › Sem restrições |
| Extra | Bateria › Limites de uso em segundo plano: tirar o Farol de "Apps em suspensão" | — | **Início automático** ligado + **cadeado** nos recentes |

## Samsung

1. **Instalar e atualizar.** Configurações › Segurança e privacidade › **Bloqueador automático** › Desativado.
   Com ele ligado a Samsung não deixa instalar nem atualizar apps fora da loja. Se o Play Protect avisar, toque em
   "Instalar mesmo assim".
2. **Configurações restritas** (Android 13+). Tente ligar a acessibilidade do Farol uma vez (vai aparecer
   "Configuração restrita"). Depois: Configurações › Aplicativos › Farol › ⋮ › **Permitir configurações restritas**.
3. **Acessibilidade.** Configurações › Acessibilidade › **Apps instalados** › Farol › Ativado.
   Depois de atualizar o Farol, confira se continua ligado: em alguns Galaxy a atualização desliga.
4. **Bateria.** Configurações › Aplicativos › Farol › Bateria › **Sem restrições**. E em Configurações › Bateria ›
   Limites de uso em segundo plano: o Farol **não** pode estar em "Apps em suspensão" nem "Apps em suspensão profunda".
5. **Notificações.** Configurações › Notificações › Farol › Permitir.

## Motorola

1. **Instalar e atualizar.** Configurações › Apps › Acesso especial a apps › Instalar apps desconhecidos › (o app
   que baixou o Farol) › Permitir. Se o Play Protect avisar: "Mais detalhes" › "Instalar mesmo assim".
2. **Configurações restritas** (Android 13+). Tente ligar a acessibilidade uma vez; depois Configurações › Apps ›
   Farol › ⋮ › **Permitir configurações restritas**.
3. **Acessibilidade.** Configurações › Acessibilidade › **Farol** (na lista "Apps baixados") › Usar Farol.
4. **Bateria.** Configurações › Apps › Farol › Uso da bateria do app › **Sem restrições**.
5. **Notificações.** Configurações › Notificações › Farol › Permitir.

## Xiaomi / Redmi / POCO

1. **Instalar e atualizar.** Configurações › Apps › Permissões › Instalar apps desconhecidos › (o app que baixou o
   Farol) › Permitir. No aviso de segurança, toque em "Continuar" e espere a contagem.
2. **Configurações restritas** (Android 13+). Tente ligar a acessibilidade uma vez; depois Configurações › Apps ›
   Gerenciar apps › Farol › ⋮ › **Permitir configurações restritas**.
3. **Acessibilidade.** Configurações › **Configurações adicionais** › Acessibilidade › **Apps baixados** › Farol ›
   Ativado. A Xiaomi mostra uma contagem de 10 segundos antes de deixar confirmar.
4. **Bateria.** Configurações › Apps › Gerenciar apps › Farol › Economia de bateria (no HyperOS 2/3 pode se chamar
   "Bateria" ou "Energia") › **Sem restrições**. Sem isso a Xiaomi fecha o leitor minutos depois de apagar a tela.
5. **Início automático.** Configurações › Apps › Permissões › Início automático › Farol › Ativado.
6. **Cadeado nos recentes.** Abra os apps recentes, segure o card do Farol e toque no 🔒.
7. **Notificações.** Configurações › Notificações › Farol › Permitir.

## Se o leitor parar sozinho

- O painel mostra **"Leitor desligado"** (vermelho) quando a acessibilidade caiu: toque e siga o guia.
- **"Farol pausado"** (amarelo) é a pausa do próprio Farol: toque para retomar (iniciar turno também retoma).
- Se cair todo dia, quase sempre é bateria (passo 4) ou, na Xiaomi, início automático/cadeado (passos 5 e 6).

Os nomes dos menus mudam um pouco entre versões do sistema. Se algum passo estiver diferente no seu aparelho, mande
um print que o guia é ajustado (o texto fica em `app/src/main/java/com/farol/app/core/DeviceGuide.kt`).
