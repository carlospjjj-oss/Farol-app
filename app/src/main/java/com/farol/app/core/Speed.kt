package com.farol.app.core

/**
 * Velocidade média "de entrega" aprendida com os seus dados: inclui ida até a loja, espera,
 * trânsito e entrega. Usada para estimar o tempo quando o app não mostra (99, Keeta).
 */
object SpeedLearner {
    const val MIN_KMH = 5.0
    const val MAX_KMH = 40.0
    const val MAX_SAMPLES = 40
    const val MIN_SAMPLES = 5

    /** Amostra a partir de km e minutos; null se for absurda. */
    fun sample(km: Double, minutes: Double): Double? {
        if (km < 0.5 || minutes < 3) return null
        val v = km / (minutes / 60.0)
        return if (v in MIN_KMH..MAX_KMH) v else null
    }

    fun add(samples: List<Double>, v: Double): List<Double> = (samples + v).takeLast(MAX_SAMPLES)

    /** Mediana das amostras, ou null se ainda houver poucas. */
    fun learned(samples: List<Double>): Double? {
        if (samples.size < MIN_SAMPLES) return null
        val s = samples.sorted()
        val m = s.size / 2
        val med = if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
        return Math.round(med * 10) / 10.0
    }
}
