package com.farol.app.core

/**
 * Regiões (6.10): de onde saem as ofertas e quanto pagam, pelo histórico de ofertas do próprio celular
 * (a região nunca vai para o servidor). Lista em vez de mapa: mesmo dado, sem serviço de mapas.
 */
object RegionStats {
    data class Seen(val t: Long, val key: String, val label: String, val perKm: Double, val green: Boolean)
    data class Line(val key: String, val label: String, val offers: Int, val avgPerKm: Double, val greenPct: Int, val bestHours: IntRange?)

    const val MIN_OFFERS = 5

    /** [hourOf]: hora local de um instante. Melhor faixa = 3 horas seguidas com mais ofertas verdes (mín. 2). */
    fun compute(seen: List<Seen>, hourOf: (Long) -> Int): List<Line> =
        seen.groupBy { it.key }.mapNotNull { (key, l) ->
            if (l.size < MIN_OFFERS) return@mapNotNull null
            val label = l.groupingBy { it.label }.eachCount().maxByOrNull { it.value }!!.key
            val greensByHour = IntArray(24).also { a -> l.filter { it.green }.forEach { a[hourOf(it.t)]++ } }
            // empate: a janela que começa numa hora com verdes ("18h às 21h" em vez de "16h às 19h")
            val best = (0..21).maxByOrNull { h -> (greensByHour[h] + greensByHour[h + 1] + greensByHour[h + 2]) * 10 + if (greensByHour[h] > 0) 1 else 0 }
            val bestSum = best?.let { greensByHour[it] + greensByHour[it + 1] + greensByHour[it + 2] } ?: 0
            Line(key, label, l.size, l.map { it.perKm }.average(), l.count { it.green } * 100 / l.size,
                if (bestSum >= 2) best!!..(best + 3) else null)
        }.sortedByDescending { it.avgPerKm }

    /** Rótulo legível quando só existe a chave (ofertas antigas): "vila prudente" → "Vila Prudente". */
    fun pretty(key: String) = key.split(' ').joinToString(" ") { w ->
        if (w.length <= 2 && w in setOf("de", "da", "do", "das", "dos", "e")) w else w.replaceFirstChar { it.uppercase() }
    }
}
