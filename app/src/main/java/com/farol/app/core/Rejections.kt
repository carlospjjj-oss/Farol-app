package com.farol.app.core

/**
 * Lê os avisos de recusa que os próprios apps mostram.
 *  99:    "rejeitou 7 vez(es) seguidas | Se rejeitar novamente, sua conta ficará restrita"
 *         "Conta pausada por excesso de rejeições"
 *  iFood: "Muitas rotas rejeitadas | Parada técnica de 14 minutos"
 */
object RejectionReader {
    data class Signal(val streak: Int?, val paused: Boolean)

    private val streakRx = Regex("""rejeitou\s+(\d+)\s+vez""", RegexOption.IGNORE_CASE)
    private val pausedRx = Regex("""conta (?:pausada|restrita|ficou restrita)|parada t[eé]cnica|muitas rotas rejeitadas""", RegexOption.IGNORE_CASE)

    fun read(text: String): Signal? {
        val n = streakRx.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val paused = pausedRx.containsMatchIn(text) && !text.contains("Se rejeitar novamente", ignoreCase = true)
        return if (n == null && !paused) null else Signal(n, paused)
    }
}
