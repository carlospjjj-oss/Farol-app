package com.farol.app.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Voltando para casa com "Destino definido" na 99 (ideia do Carlos, 19/11; exceção ao congelamento, 20/11).
 *
 * Com o destino casa ligado, a 99 só manda pedidos que levam na direção de casa. Parte dos km da oferta
 * seria rodada de qualquer jeito (vazio, voltando). Esses km não devem pesar contra a oferta.
 *
 * Estimativa conservadora (a oferta não traz o trajeto): no máximo METADE dos km da oferta conta como
 * "no caminho", e nunca mais que a distância de estrada até casa (linha reta × 1,3). O tempo não muda.
 * Casa e posição atual ficam só no celular.
 */
object HomeRoute {
    const val ROAD_FACTOR = 1.3
    const val ON_WAY_SHARE = 0.5
    const val MIN_ON_WAY_KM = 0.3
    /** O "Destino definido" visto na tela da 99 vale por 20 min. */
    const val ACTIVE_MS = 20 * 60_000L

    /** Texto da 99 com o destino ligado (barra "Destino definido" e a tela "Receba chamadas com destino definido"). */
    val MODE = Regex("""destino definido""", RegexOption.IGNORE_CASE)

    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(a))
    }

    /** Km da oferta que já iam ser rodados voltando para casa (0 quando não vale a pena considerar). */
    fun onWayKm(offerKm: Double, straightHomeKm: Double): Double {
        if (offerKm <= 0 || straightHomeKm <= 0) return 0.0
        val v = min(offerKm * ON_WAY_SHARE, straightHomeKm * ROAD_FACTOR)
        return if (v < MIN_ON_WAY_KM) 0.0 else v
    }

    /** A oferta como ela pesa voltando para casa: menos km, o mesmo tempo. */
    fun adjusted(offer: ParsedOffer, onWayKm: Double, avgSpeedKmh: Double): ParsedOffer {
        val minutes = offer.totalMin?.takeIf { it > 0 } ?: (offer.totalKm / avgSpeedKmh.coerceAtLeast(1.0) * 60.0)
        return offer.copy(totalKm = (offer.totalKm - onWayKm).coerceAtLeast(0.5), totalMin = minutes)
    }
}
