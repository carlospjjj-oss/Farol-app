package com.farol.app.core

/**
 * Cálculos do GPS sem Android: filtro de pontos ruins e km entre dois horários.
 * O "odômetro do GPS" é guardado como pontos (hora, km acumulado) a cada ~1 minuto.
 */
object GpsMath {

    const val MAX_ACCURACY_M = 35f      // ponto com erro maior que isso é ignorado
    const val MIN_STEP_M = 8.0          // abaixo disso é tremido do GPS parado
    const val MAX_SPEED_MS = 45.0       // ~160 km/h: salto impossível de moto na cidade

    /** Distância a somar entre dois pontos, ou 0 se for ruído/salto. */
    fun step(distanceM: Double, seconds: Double, accuracyM: Float): Double {
        if (accuracyM > MAX_ACCURACY_M || seconds <= 0) return 0.0
        if (distanceM < MIN_STEP_M) return 0.0
        if (distanceM / seconds > MAX_SPEED_MS) return 0.0
        return distanceM
    }

    /** Km rodados entre [from] e [to] usando os pontos (hora, km acumulado), com interpolação. */
    fun kmBetween(points: List<Pair<Long, Double>>, from: Long, to: Long): Double? {
        if (points.size < 2 || to <= from) return null
        val p = points.sortedBy { it.first }
        if (from < p.first().first - 120_000 || to > p.last().first + 120_000) return null
        fun at(t: Long): Double {
            if (t <= p.first().first) return p.first().second
            if (t >= p.last().first) return p.last().second
            val i = p.indexOfFirst { it.first >= t }
            val a = p[i - 1]; val b = p[i]
            val f = (t - a.first).toDouble() / (b.first - a.first).coerceAtLeast(1)
            return a.second + (b.second - a.second) * f
        }
        return (at(to) - at(from)).coerceAtLeast(0.0)
    }
}
