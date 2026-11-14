package com.farol.app.core

/**
 * Indicadores do manual de gestão (módulos 10 e 11): ticket médio, entregas por hora e comparação por app
 * e por faixa de horário. Só usa o que o diário já tem: ganhos de cada entrega, hora da conclusão e turnos.
 * Kotlin puro: testado em IndicatorsTest.
 */
object Indicators {
    /** Faixas do manual: almoço, tarde, jantar e o resto. */
    enum class Slot(val label: String, val from: Int, val to: Int) {
        ALMOCO("Almoço (11h–15h)", 11, 15), TARDE("Tarde (15h–18h)", 15, 18),
        JANTAR("Jantar (18h–23h)", 18, 23), OUTRO("Outros horários", 23, 11);
        fun has(h: Int) = if (from < to) h in from until to else h >= from || h < to
    }

    /** Abaixo disso a linha aparece como "juntando dados" (não dá para concluir com poucas entregas). */
    const val MIN_SAMPLE = 10

    data class AppLine(val app: String, val deliveries: Int, val ordersValue: Double, val earnings: Double) {
        val ticket: Double? get() = if (deliveries > 0) ordersValue / deliveries else null
    }
    data class SlotLine(val slot: Slot, val deliveries: Int, val value: Double, val hours: Double) {
        val perHour: Double? get() = if (hours >= 0.5) value / hours else null
        val enough: Boolean get() = deliveries >= MIN_SAMPLE
    }
    data class Result(
        val days: Int, val earnings: Double, val deliveries: Int, val hours: Double, val km: Double,
        val apps: List<AppLine>, val slots: List<SlotLine>,
    ) {
        val ticket: Double? get() = apps.sumOf { it.deliveries }.takeIf { it > 0 }?.let { n -> apps.sumOf { it.ordersValue } / n }
        val perHour: Double? get() = if (hours >= 0.5) earnings / hours else null
        val deliveriesPerHour: Double? get() = if (hours >= 0.5 && deliveries > 0) deliveries / hours else null
        val perKm: Double? get() = if (km > 0) earnings / km else null
    }

    /**
     * [hourOf] converte epoch ms na hora local (0–23); fica fora para o cálculo não depender do fuso do celular.
     * As horas de cada faixa saem dos turnos (início→fim, descontando as pausas na proporção).
     */
    fun compute(days: List<DayRecord>, hourOf: (Long) -> Int): Result {
        val worked = days.filter { it.hasShift || it.totalEarnings > 0 }
        val orders = worked.flatMap { d -> d.orders.filterKeys { '$' !in it }.map { (k, v) -> Triple(k, v, d.orderAt[k]) } }
        val apps = worked.flatMap { it.appEarnings.keys }.distinct().map { app ->
            val mine = orders.filter { DayRecord.appOf(it.first) == app }
            AppLine(app, mine.size, mine.sumOf { it.second }, worked.sumOf { it.appEarnings[app] ?: 0.0 })
        }.filter { it.earnings > 0 || it.deliveries > 0 }.sortedByDescending { it.earnings }

        val slotHours = DoubleArray(Slot.values().size)
        for (d in worked) {
            val s = d.startAt ?: continue
            val e = d.endAt ?: continue
            if (e <= s) continue
            val factor = (d.workedMs(e) ?: 0L).toDouble() / (e - s)   // tira as pausas na proporção
            var t = s
            while (t < e) {   // passos de 15 min bastam para dividir o turno entre as faixas
                val step = minOf(15 * 60_000L, e - t)
                slotHours[Slot.values().indexOfFirst { it.has(hourOf(t)) }] += step / 3_600_000.0 * factor
                t += step
            }
        }
        val slots = Slot.values().mapIndexed { i, slot ->
            val mine = orders.filter { it.third != null && slot.has(hourOf(it.third!!)) }
            SlotLine(slot, mine.size, mine.sumOf { it.second }, slotHours[i])
        }
        return Result(
            days = worked.size, earnings = worked.sumOf { it.totalEarnings }, deliveries = orders.size,
            hours = worked.sumOf { d -> d.hours ?: 0.0 }, km = worked.sumOf { it.kmDriven ?: 0.0 }, apps = apps, slots = slots,
        )
    }
}
