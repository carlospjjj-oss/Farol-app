package com.farol.app.core

/**
 * Lê o "ganhos de hoje" que cada app mostra na tela inicial (painel).
 * É mais confiável do que somar corrida por corrida: já inclui bônus, gorjetas e extras.
 *
 * Exemplos reais (12/11):
 *   Keeta: "Painel | R$ 42,60 | 5 | Entregando (1) | ... | R$ 42,60 | Ganhos | 5 | Pedidos hoje"
 *   99:    "Painel Editar | R$17,85 | R$17,85 | / (solicitação) dia Solicitações >"   (OCR)
 *   iFood: "... Rotas pra entregar agora | SOS | R | $ | 0 | , | 0"                 (valor letra por letra)
 */
object EarningsReader {

    private const val MONEY = """R\$\s*(\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)"""

    private val patterns: Map<String, List<Regex>> = mapOf(
        "Keeta" to listOf(
            Regex("""$MONEY\s*\|\s*Ganhos\s*\|\s*\d+\s*\|\s*Pedidos hoje""", RegexOption.IGNORE_CASE),
            Regex("""Painel\s*\|\s*$MONEY""", RegexOption.IGNORE_CASE),
        ),
        "99" to listOf(
            Regex("""Painel(?:\s*\|?\s*Editar)?\s*\|\s*$MONEY""", RegexOption.IGNORE_CASE),
            Regex("""$MONEY\s*\|\s*$MONEY\s*\|\s*/?\s*\(?solicita""", RegexOption.IGNORE_CASE),
        ),
        "iFood" to listOf(
            Regex("""(?:SOS|Rotas pra entregar agora)\s*\|\s*$MONEY\s*$""", RegexOption.IGNORE_CASE),
            Regex("""Ganhos de hoje\s*\|?\s*$MONEY""", RegexOption.IGNORE_CASE),
        ),
        "Uber" to listOf(
            Regex("""Ganhos de hoje\s*\|?\s*$MONEY""", RegexOption.IGNORE_CASE),
        ),
    )

    /** Valor de "ganhos de hoje" na tela, ou null se a tela não for o painel do app. */
    fun read(app: String, rawText: String): Double? {
        val text = OfferParser.normalize(OfferParser.mergeSplitChars(rawText)).trim()
        // Tela de oferta não é painel (evita confundir o valor da oferta com o do dia)
        if (Regex("""\baceitar\b(?!\s+corridas)|\bpegar\b|\brecusar\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        for (rx in patterns[app] ?: return null) {
            val m = rx.find(text) ?: continue
            val v = OfferParser.parseMoney(m.groupValues[1]) ?: continue
            if (v in 0.0..5000.0) return v
        }
        return null
    }
}
