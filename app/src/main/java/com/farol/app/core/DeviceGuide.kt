package com.farol.app.core

/**
 * Caminho das telas de configuração por marca de celular (Samsung, Motorola, Xiaomi/Redmi/POCO).
 * Cada marca chama as telas de um jeito e esconde a acessibilidade num lugar diferente; aqui fica
 * o texto exato de cada passo. Usado no guia de instalação do app e na documentação.
 */
object DeviceGuide {

    enum class Brand(val label: String) { SAMSUNG("Samsung"), MOTOROLA("Motorola"), XIAOMI("Xiaomi / Redmi / POCO"), OTHER("Outro Android") }

    enum class Step { INSTALL, RESTRICTED, ACCESSIBILITY, BATTERY, AUTOSTART, RECENTS, NOTIFICATIONS }

    data class Guide(val step: Step, val title: String, val path: String, val tip: String? = null)

    fun detect(manufacturer: String?, brand: String?): Brand {
        val s = "${manufacturer.orEmpty()} ${brand.orEmpty()}".lowercase()
        return when {
            "samsung" in s -> Brand.SAMSUNG
            "motorola" in s || "moto" in s || "lenovo" in s -> Brand.MOTOROLA
            "xiaomi" in s || "redmi" in s || "poco" in s -> Brand.XIAOMI
            else -> Brand.OTHER
        }
    }

    /** Passos na ordem em que devem ser feitos. */
    fun steps(b: Brand, sdk: Int): List<Guide> {
        val out = mutableListOf<Guide>()
        out += when (b) {
            Brand.SAMSUNG -> Guide(Step.INSTALL, "Instalar e atualizar",
                "Configurações › Segurança e privacidade › Bloqueador automático › Desativado",
                "Com o Bloqueador automático ligado, a Samsung não deixa instalar nem atualizar o Farol. Se o Play Protect avisar, toque em \"Instalar mesmo assim\".")
            Brand.XIAOMI -> Guide(Step.INSTALL, "Instalar e atualizar",
                "Configurações › Apps › Permissões › Instalar apps desconhecidos › (o app que baixou o Farol) › Permitir",
                "Se aparecer o aviso de segurança da Xiaomi, toque em \"Continuar\"/\"Instalar\" e espere a contagem.")
            Brand.MOTOROLA -> Guide(Step.INSTALL, "Instalar e atualizar",
                "Configurações › Apps › Acesso especial a apps › Instalar apps desconhecidos › (o app que baixou o Farol) › Permitir",
                "Se o Play Protect avisar, toque em \"Mais detalhes\" › \"Instalar mesmo assim\".")
            Brand.OTHER -> Guide(Step.INSTALL, "Instalar e atualizar",
                "Configurações › Apps › Acesso especial › Instalar apps desconhecidos › Permitir", null)
        }
        if (sdk >= 33) out += Guide(Step.RESTRICTED, "Liberar configurações restritas",
            when (b) {
                Brand.SAMSUNG -> "Configurações › Aplicativos › Farol › ⋮ (canto de cima) › Permitir configurações restritas"
                Brand.XIAOMI -> "Configurações › Apps › Gerenciar apps › Farol › ⋮ (canto de cima) › Permitir configurações restritas"
                else -> "Configurações › Apps › Farol › ⋮ (canto de cima) › Permitir configurações restritas"
            },
            "O ⋮ só mostra essa opção depois que você tenta ligar a acessibilidade do Farol uma vez e aparece \"Configuração restrita\". Tente ligar, volte aqui e libere.")
        out += Guide(Step.ACCESSIBILITY, "Ligar o leitor de ofertas",
            when (b) {
                Brand.SAMSUNG -> "Configurações › Acessibilidade › Apps instalados › Farol › Ativado"
                Brand.XIAOMI -> "Configurações › Configurações adicionais › Acessibilidade › Apps baixados › Farol › Ativado"
                Brand.MOTOROLA -> "Configurações › Acessibilidade › Farol (em \"Apps baixados\") › Usar Farol"
                Brand.OTHER -> "Configurações › Acessibilidade › Apps baixados/instalados › Farol › Ativar"
            },
            when (b) {
                Brand.XIAOMI -> "A Xiaomi mostra uma contagem de 10 segundos antes de deixar confirmar: espere e toque em \"Permitir\"."
                Brand.SAMSUNG -> "Depois de atualizar o Farol, confira se continua ligado: em alguns Galaxy a atualização desliga."
                else -> null
            })
        out += Guide(Step.BATTERY, "Bateria sem restrição",
            when (b) {
                Brand.SAMSUNG -> "Configurações › Aplicativos › Farol › Bateria › Sem restrições"
                Brand.XIAOMI -> "Configurações › Apps › Gerenciar apps › Farol › Economia de bateria (ou Bateria) › Sem restrições"
                Brand.MOTOROLA -> "Configurações › Apps › Farol › Uso da bateria do app › Sem restrições"
                Brand.OTHER -> "Configurações › Apps › Farol › Bateria › Sem restrições"
            },
            when (b) {
                Brand.SAMSUNG -> "Confira também: Configurações › Bateria › Limites de uso em segundo plano: o Farol NÃO pode estar em \"Apps em suspensão\" nem \"Apps em suspensão profunda\"."
                Brand.XIAOMI -> "Sem isso a Xiaomi fecha o leitor poucos minutos depois de apagar a tela."
                else -> "Sem isso o Android desliga o leitor no meio do dia."
            })
        if (b == Brand.XIAOMI) {
            out += Guide(Step.AUTOSTART, "Início automático",
                "Configurações › Apps › Permissões › Início automático (ou Início automático em segundo plano) › Farol › Ativado",
                "Faz o leitor voltar sozinho depois de reiniciar o celular ou de a Xiaomi fechar os apps.")
            out += Guide(Step.RECENTS, "Travar o Farol nos recentes",
                "Abra os apps recentes (botão ▢ ou deslize de baixo), segure o card do Farol e toque no cadeado 🔒",
                "Assim a limpeza de memória da Xiaomi não fecha o Farol.")
        }
        if (sdk >= 33) out += Guide(Step.NOTIFICATIONS, "Notificações",
            "Configurações › Notificações › Farol › Permitir", "Para o turno na barra, o aviso de Farol pausado e o resumo da semana.")
        return out
    }
}
