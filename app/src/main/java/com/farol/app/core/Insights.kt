package com.farol.app.core

/**
 * Etapas de um pedido, reconhecidas pelo texto das telas do app depois do aceite.
 * Por enquanto só o Keeta (telas confirmadas nos logs de 12/11). iFood e 99 entram quando
 * os logs mostrarem as telas deles.
 */
enum class Phase { COLLECT, DELIVER, IDLE }

object PhaseDetector {

    private val keetaCollect = Regex("""Chegue na loja|Coletar o pedido|Pedido pronto em cerca|O pedido pode estar pronto|N[aã]o saia da loja""", RegexOption.IGNORE_CASE)
    private val keetaDeliver = Regex("""Entregue at[eé]|Para finalizar a entrega|Cheguei na entrega|c[oó]digo de entrega""", RegexOption.IGNORE_CASE)
    private val keetaIdle = Regex("""Procurando pedidos|Pedidos hoje""", RegexOption.IGNORE_CASE)

    fun detect(app: String, text: String): Phase? = when (app) {
        "Keeta" -> when {
            keetaDeliver.containsMatchIn(text) -> Phase.DELIVER
            keetaCollect.containsMatchIn(text) -> Phase.COLLECT
            keetaIdle.containsMatchIn(text) && !text.contains("Entregando (", ignoreCase = true) -> Phase.IDLE
            else -> null
        }
        else -> null
    }

    /** Nome do restaurante na tela de coleta: o primeiro texto "de verdade" depois do mapa. */
    fun restaurant(app: String, text: String): String? {
        if (app != "Keeta") return null
        val parts = text.split(" | ").map { it.trim() }
        val start = parts.indexOfFirst { it.equals("Marcador do mapa", true) }.let { if (it < 0) parts.indexOfFirst { p -> p.startsWith("Mapa do Google", true) } else it }
        if (start < 0) return null
        return parts.drop(start + 1).firstOrNull { p ->
            p.length >= 3 && p.any { it.isLetter() } && !p.startsWith("[") &&
                !Regex("""^(Rotas|Restaurante|Cliente|Novo|Se um pedido|Um pedido|Chat|Suporte|\+R\$)""", RegexOption.IGNORE_CASE).containsMatchIn(p)
        }?.take(40)
    }
}

/** Um pedido acompanhado: aceite (início da coleta) → início da entrega → fim. */
data class DeliveryRecord(
    val app: String,
    val restaurant: String?,
    val collectAt: Long,
    val deliverAt: Long? = null,
    val endAt: Long? = null,
    val km: Double? = null,          // km da oferta aceita (para aprender a velocidade)
) {
    val collectMin: Double? get() = deliverAt?.let { (it - collectAt) / 60_000.0 }
    val deliverMin: Double? get() = if (deliverAt != null && endAt != null) (endAt - deliverAt) / 60_000.0 else null
    val totalMin: Double? get() = endAt?.let { (it - collectAt) / 60_000.0 }
}

/**
 * Máquina de estados: recebe as etapas vistas na tela e fecha os pedidos.
 * Pura (sem Android) para poder testar.
 */
class DeliveryTracker(private val onFinished: (DeliveryRecord) -> Unit) {
    private val open = mutableMapOf<String, DeliveryRecord>()

    fun feed(app: String, phase: Phase, restaurant: String?, now: Long, offerKm: Double? = null) {
        val cur = open[app]
        when (phase) {
            Phase.COLLECT -> when {
                cur == null -> open[app] = DeliveryRecord(app, restaurant, now, km = offerKm)
                cur.deliverAt != null -> {           // já entregava e voltou a coletar: novo pedido
                    onFinished(cur.copy(endAt = now)); open[app] = DeliveryRecord(app, restaurant, now, km = offerKm)
                }
                cur.restaurant == null && restaurant != null -> open[app] = cur.copy(restaurant = restaurant)
            }
            Phase.DELIVER -> when {
                cur == null -> {}                     // pegou o app no meio da entrega: ignora
                cur.deliverAt == null -> open[app] = cur.copy(deliverAt = now)
            }
            Phase.IDLE -> if (cur != null) {
                if (cur.deliverAt != null) onFinished(cur.copy(endAt = now))
                open.remove(app)
            }
        }
        // pedido esquecido aberto (mais de 3 h): descarta
        open.entries.removeAll { now - it.value.collectAt > 3 * 3_600_000L }
    }
}

/** Resumo por faixa de hora das ofertas lidas. */
data class HourStat(val hour: Int, val offers: Int, val avgPerKm: Double, val greens: Int)

object HourInsights {
    /** @param offers pares (hora do dia 0–23, R$/km, verde?) */
    fun byHour(offers: List<Triple<Int, Double, Boolean>>): List<HourStat> =
        offers.groupBy { it.first }.map { (h, list) ->
            HourStat(h, list.size, list.map { it.second }.average(), list.count { it.third })
        }.sortedBy { it.hour }

    /** As melhores horas: com pelo menos [minOffers] ofertas, maior R$/km médio. */
    fun best(stats: List<HourStat>, minOffers: Int = 3, top: Int = 3) =
        stats.filter { it.offers >= minOffers }.sortedByDescending { it.avgPerKm }.take(top)
}
