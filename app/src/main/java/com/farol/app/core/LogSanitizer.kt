package com.farol.app.core

/**
 * Limpa o texto antes de ele sair do celular nos logs remotos.
 * - Troca rua/número por "[endereço]", mantendo o bairro/cidade (útil para análise).
 * - Remove sequências longas de dígitos (telefone, CPF, códigos de pedido).
 * - Telas de conversa com o cliente não saem do celular ([isChat]).
 * - Nome do cliente nas telas de entrega vira "[cliente]" ([maskPeople]).
 */
object LogSanitizer {

    private val STREET = Regex(
        """^(r\.?|rua|av\.?|avenida|al\.?|alameda|tv\.?|trav\.?|travessa|estr\.?|estrada|rod\.?|rodovia|pra[cç]a|p[cç]a\.?|largo|lgo\.?|via|viela|beco|servid[aã]o)\s""",
        RegexOption.IGNORE_CASE
    )
    private val LEADING_NUMBER = Regex("""^\s*(n[º°o.]?\s*)?\d+[a-zA-Z]?\s*(-\s*)?""")
    private val LONG_DIGITS = Regex("""\(?\d[\d .\-/()]{7,}\d""")
    private val CEP = Regex("""\b\d{5}-?\d{3}\b""")

    fun mask(text: String): String = text.split(" | ").joinToString(" | ") { maskSegment(it) }

    private fun maskSegment(seg0: String): String {
        var seg = seg0.replace(CEP, "[cep]").replace(LONG_DIGITS, "[num]")
        val trimmed = seg.trim()
        if (STREET.containsMatchIn(trimmed)) {
            val rest = when {
                trimmed.contains(',') -> trimmed.substringAfter(',')
                trimmed.contains(" - ") -> trimmed.substringAfter(" - ")
                else -> ""
            }.replace(LEADING_NUMBER, "").trim().trimStart(',', '-').trim()
            // "Rua A, 10 - Estrada B, 2282, Cidade": a segunda rua também é endereço
            val restMasked = if (STREET.containsMatchIn(rest)) maskSegment(rest).removePrefix("[endereço]").trimStart(',', ' ') else rest
            seg = if (restMasked.isEmpty()) "[endereço]" else "[endereço], $restMasked"
        }
        return seg
    }

    // ---------- dados de terceiros (clientes) ----------

    private val CHAT = Regex(
        """Insira sua mensagem|Mensagens r[aá]pidas|Criar nova frase r[aá]pida|Minha imagem de perfil|Imagem de perfil dele|Chat da entrega\s*\|\s*Voz|Digite uma mensagem|Enviar mensagem""",
        RegexOption.IGNORE_CASE)

    /** Tela de conversa (chat) com o cliente: não é registrada. */
    fun isChat(text: String) = CHAT.containsMatchIn(text)

    private val DELIVERY = Regex(
        """\bCliente\b|Entregue at[eé]|Para finalizar a entrega|Cheguei na entrega|Entregar em at[eé]|c[oó]digo de (verifica[cç][aã]o|entrega)|Entrega em m[aã]os|Venha retirar|\bEntrega\s*\|""",
        RegexOption.IGNORE_CASE)
    // 1 a 4 palavras com inicial maiúscula, só letras (ex.: "Emanoel", "Franciely Gomes", "Laura Maria")
    private val PERSON = Regex("""^\p{Lu}[\p{L}'´`]+(?:\s+(?:d[aeo]s?\s+)?\p{Lu}[\p{L}'´`]*\.?){0,3}$""")
    private val ORDER_NAME = Regex("""(#\d{3,}|C[oó]digo de coleta\s+\d+)\s+\p{Lu}[\p{L}]+(?:\s+\p{Lu}[\p{L}]+)*""")
    private val NOT_NAME = Regex(
        """^(Mapa|Marcador|Rotas|Cliente|Restaurante|Entrega|Coleta|Suporte|Ajuda|Passo|Chamada|Mensagem|Concluir|Detalhes|Central|Bloco|Cheguei|Entregue|Para|Falar|Por|Go|Order|N[uú]mero|Ir|Voz|Texto|Mais|Lida|Limite|Rotat[oó]ria|Mantenha|Vire|Siga|Pedido|Aceitar|Recusar|Google|Waze|Insira|Pe[cç]a|Chegada|Previs[aã]o|Em|Sua|Seu|Voc[eê]|Inclui|Taxa|Outros|Resumo|Painel|Ganhos|Iniciar|Confirmar|Cancelar)\b""",
        RegexOption.IGNORE_CASE)

    /**
     * Nas telas de entrega, troca o nome do cliente por "[cliente]": o texto logo antes do endereço,
     * logo depois de "Entrega |" e o nome que vem junto do número do pedido ("#149031 Laura").
     */
    fun maskPeople(text0: String): String {
        // nome junto do número do pedido / código de coleta: sempre
        val text = text0.replace(ORDER_NAME) { it.groupValues[1] + " [cliente]" }
        if (!DELIVERY.containsMatchIn(text)) return text
        val parts = text.split(" | ").toMutableList()
        for (i in parts.indices) {
            val p = parts[i].trim()
            val next = parts.getOrNull(i + 1)?.trim() ?: ""
            val prev = parts.getOrNull(i - 1)?.trim() ?: ""
            val beforeAddress = next.startsWith("[endereço]") || next.startsWith("#")
            val afterHeader = prev.equals("Entrega", true) || prev.equals("Marcador do mapa", true) && DELIVERY.containsMatchIn(text)
            if ((beforeAddress || afterHeader) && PERSON.matches(p) && !NOT_NAME.containsMatchIn(p) && p.length <= 40) parts[i] = "[cliente]"
            parts[i] = parts[i].replace(ORDER_NAME) { it.groupValues[1] + " [cliente]" }
        }
        return parts.joinToString(" | ")
    }

    /** Tudo o que sai do celular passa por aqui. null = a tela não deve ser registrada. */
    fun forServer(text: String, maskAddresses: Boolean = true): String? {
        if (isChat(text)) return null
        val t = maskPeople(text)
        return if (maskAddresses) mask(t) else t
    }
}
