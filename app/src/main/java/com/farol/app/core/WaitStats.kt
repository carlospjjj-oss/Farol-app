package com.farol.app.core

/**
 * Espera média por restaurante, aprendida das suas coletas (fica só no celular: o nome do restaurante
 * é dado de terceiro e não vai para o servidor). Só dá aviso com pelo menos [MIN_SAMPLES] coletas.
 */
class WaitStats(val samples: MutableMap<String, MutableList<Double>> = mutableMapOf()) {

    data class Estimate(val restaurant: String, val avgMin: Double, val samples: Int)

    fun add(restaurant: String, waitMin: Double) {
        if (waitMin < 0 || waitMin > 90) return
        val list = samples.getOrPut(key(restaurant)) { mutableListOf() }
        list += waitMin
        while (list.size > MAX_PER_PLACE) list.removeAt(0)
    }

    fun estimate(restaurant: String?): Estimate? {
        if (restaurant.isNullOrBlank()) return null
        val list = samples[key(restaurant)] ?: return null
        if (list.size < MIN_SAMPLES) return null
        // média aparada: tira o maior e o menor quando dá (um dia atípico não estraga a média)
        val sorted = list.sorted()
        val core = if (sorted.size >= 5) sorted.subList(1, sorted.size - 1) else sorted
        return Estimate(restaurant, core.average(), list.size)
    }

    companion object {
        const val MIN_SAMPLES = 3
        const val MAX_PER_PLACE = 12
        /** Espera a partir da qual aparece o aviso no card. */
        const val WARN_MIN = 8.0

        fun key(name: String) = ProfitCalculator.fold(name).replace(Regex("""[^a-z0-9]+"""), " ").trim()

    }
}

/**
 * Ganho por entrega concluída, somado no dia do turno (sem depender da tela inicial do app).
 *  - Keeta: "Pedido concluído! Seus ganhos | R$ 13,47 | #7553" (valor e número na própria tela).
 *  - 99: a tela de conclusão não mostra o valor. Quando aparece "Concluir" com o número do pedido,
 *    usa o valor da oferta aceita (a última oferta da 99 lida antes, ainda não usada).
 * Cada pedido é identificado por "app#número": a mesma tela vista de novo não soma duas vezes.
 */
class OrderEarnings {

    data class Done(val app: String, val id: String, val value: Double) { val key get() = "$app#$id" }

    private data class Offer(val price: Double, val at: Long)
    private val offers = mutableMapOf<String, MutableList<Offer>>()
    private val used = LinkedHashSet<String>()   // pedidos já contados (a tela de concluir aparece várias vezes)

    fun onOffer(app: String, price: Double, now: Long) {
        if (price <= 0) return
        val l = offers.getOrPut(app) { mutableListOf() }
        if (l.lastOrNull()?.let { it.price == price && now - it.at < 120_000 } == true) return   // mesma oferta relida
        l += Offer(price, now)
        l.removeAll { now - it.at > OFFER_TTL }
    }

    fun onScreen(app: String, text: String, now: Long): Done? = when (app) {
        "Keeta" -> KEETA_DONE.find(text)?.let { m ->
            val v = OfferParser.parseMoney(m.groupValues[1]) ?: return null
            val id = KEETA_ID.find(text.substring(m.range.last))?.groupValues?.get(1) ?: return null
            if (v in 0.5..500.0) Done(app, id, v) else null
        }
        "99" -> if (NINETY_NINE_FINISH.containsMatchIn(text)) {
            val id = NINETY_NINE_ID.find(text)?.groupValues?.get(1)
            val l = offers[app]
            if (id == null || "$app#$id" in used || l.isNullOrEmpty()) null
            else { l.removeAll { now - it.at > OFFER_TTL }; l.removeLastOrNull()?.let { used += "$app#$id"; trimUsed(); Done(app, id, it.price) } }
        } else null
        else -> null
    }

    /** Pedidos já registrados no dia (vindos do diário), para não reusar a oferta depois de reiniciar. */
    fun markUsed(keys: Collection<String>) { used += keys; trimUsed() }
    private fun trimUsed() { while (used.size > 300) used.remove(used.first()) }

    companion object {
        private const val OFFER_TTL = 3 * 3_600_000L
        private val KEETA_DONE = Regex("""Pedido conclu[ií]do!?\s*\|?\s*Seus ganhos\s*\|\s*R\$\s*([\d.]+,\d{2})""", RegexOption.IGNORE_CASE)
        private val KEETA_ID = Regex("""#(\d{3,})""")
        private val NINETY_NINE_FINISH = Regex("""c[oó]digo de verifica[cç][aã]o|Insira o c[oó]digo|\bConcluir\b""", RegexOption.IGNORE_CASE)
        private val NINETY_NINE_ID = Regex("""#(\d{5,})""")
    }
}
