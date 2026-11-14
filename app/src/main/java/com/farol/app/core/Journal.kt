package com.farol.app.core

/**
 * Diário do entregador: turno (km do painel no início/fim), ganhos por app e abastecimentos.
 * Kotlin puro (sem Android) para poder testar os cálculos.
 */

data class DayRecord(
    val date: String,                       // "2026-11-11"
    val kmStart: Double? = null,            // hodômetro no início do turno
    val kmEnd: Double? = null,              // hodômetro no fim do turno
    val startAt: Long? = null,              // epoch ms
    val endAt: Long? = null,
    val earnings: Map<String, Double> = emptyMap(), // app -> R$ ganhos no dia
    val pausedMs: Long = 0L,                // pausas já encerradas (não contam como trabalho)
    val pauseAt: Long? = null,              // pausa em andamento desde
    val orders: Map<String, Double> = emptyMap(),   // "Keeta#7553" -> R$ de cada entrega concluída
    val expenses: List<Expense> = emptyList(),      // gastos do dia (alimentação, manutenção, outros)
    val dayOff: Boolean = false,                    // folga marcada: não conta contra a meta da semana
) {
    val kmDriven: Double? get() = if (kmStart != null && kmEnd != null && kmEnd >= kmStart) kmEnd - kmStart else null

    /**
     * Ganho de cada app no dia: o MAIOR entre o valor lançado/lido da tela inicial e a soma das entregas
     * concluídas. Nunca soma os dois (seria contar a mesma entrega duas vezes): a tela inicial já inclui
     * as entregas, e depois da meia-noite ela zera enquanto a soma das entregas do turno continua.
     */
    val appEarnings: Map<String, Double> get() {
        if (orders.isEmpty()) return earnings
        // Cada fonte é uma "família": "99#149031" (entrega lida pela oferta/tela de concluído) e
        // "99${'$'}saldo:..." (lançamentos do Saldo). Por app vale a MAIOR família (ou o painel), nunca a soma.
        val byFamily = orders.entries.groupBy({ familyOf(it.key) }, { it.value }).mapValues { it.value.sum() }
        val byApp = byFamily.entries.groupBy({ it.key.first }, { it.value }).mapValues { it.value.max() }
        return (earnings.keys + byApp.keys).associateWith { maxOf(earnings[it] ?: 0.0, byApp[it] ?: 0.0) }
    }
    val totalEarnings: Double get() = appEarnings.values.sum()
    val totalExpenses: Double get() = expenses.sumOf { it.value }
    fun ordersOf(app: String) = orders.filterKeys { appOf(it) == app && '$' !in it }

    companion object {
        fun appOf(key: String) = key.substringBefore('$').substringBefore('#')
        fun familyOf(key: String): Pair<String, String> =
            if ('$' in key) appOf(key) to key.substringAfter('$').substringBefore(':') else appOf(key) to "#"
    }
    val isPaused: Boolean get() = pauseAt != null && endAt == null
    val hasShift: Boolean get() = kmStart != null || startAt != null

    /** Tempo trabalhado em ms (do início até o fim, ou até agora), sem as pausas. */
    fun workedMs(now: Long = System.currentTimeMillis()): Long? {
        val s = startAt ?: return null
        val e = endAt ?: now
        val open = pauseAt?.let { (e - it).coerceAtLeast(0) } ?: 0L
        return (e - s - pausedMs - open).coerceAtLeast(0)
    }

    val hours: Double? get() = if (startAt != null && endAt != null && endAt > startAt) workedMs()!! / 3_600_000.0 else null

    fun pause(now: Long) = if (isPaused || endAt != null) this else copy(pauseAt = now)
    fun resume(now: Long) = pauseAt?.let { copy(pausedMs = pausedMs + (now - it).coerceAtLeast(0), pauseAt = null) } ?: this
    /** Exclui o turno, mas mantém os ganhos do dia. */
    fun clearShift() = copy(kmStart = null, kmEnd = null, startAt = null, endAt = null, pausedMs = 0L, pauseAt = null)
}

/** Gasto do dia. [category]: "alimentacao", "manutencao" ou "outros". */
data class Expense(val at: Long, val category: String, val value: Double, val note: String = "") {
    companion object {
        val CATEGORIES = listOf("alimentacao" to "Alimentação", "manutencao" to "Manutenção", "outros" to "Outros")
        fun label(cat: String) = CATEGORIES.firstOrNull { it.first == cat }?.second ?: "Outros"
    }
}

data class FuelEntry(
    val at: Long,            // epoch ms
    val odometer: Double,    // km do painel na hora de abastecer
    val liters: Double,
    val total: Double,       // R$ pagos
    val fullTank: Boolean = true,
) {
    val pricePerLiter: Double get() = if (liters > 0) total / liters else 0.0
}

data class DaySummary(
    val earnings: Double,
    val km: Double?,
    val hours: Double?,
    val cost: Double?,       // km × custo/km
    val net: Double?,
    val perKm: Double?,
    val perHour: Double?,
    val netPerHour: Double?,
    val expenses: Double = 0.0,
    /** Ganhos − custo estimado do km (quando há km) − gastos lançados. */
    val netAfterExpenses: Double = 0.0,
)

data class FuelStats(
    val realKmPerLiter: Double?,   // consumo real, medido de tanque cheio a tanque cheio
    val lastPricePerLiter: Double?,
    val litersMeasured: Double,
    val kmMeasured: Double,
)

data class PeriodSummary(val days: Int, val earnings: Double, val km: Double, val net: Double, val fuelSpent: Double, val expenses: Double = 0.0)

object JournalMath {

    fun summarize(day: DayRecord, costPerKm: Double): DaySummary {
        val e = day.totalEarnings
        val km = day.kmDriven
        val h = day.hours
        val cost = km?.let { it * costPerKm }
        val net = cost?.let { e - it }
        return DaySummary(
            earnings = e,
            km = km,
            hours = h,
            cost = cost,
            net = net,
            perKm = km?.takeIf { it > 0 }?.let { e / it },
            perHour = h?.takeIf { it > 0 }?.let { e / it },
            netPerHour = if (h != null && h > 0 && net != null) (net - day.totalExpenses) / h else null,
            expenses = day.totalExpenses,
            netAfterExpenses = e - (cost ?: 0.0) - day.totalExpenses,
        )
    }

    /**
     * Consumo real pelo método tanque cheio → tanque cheio:
     * km entre dois abastecimentos de tanque cheio ÷ litros colocados no segundo
     * (somando abastecimentos parciais no meio). Usa todos os intervalos válidos.
     */
    fun fuelStats(entries: List<FuelEntry>): FuelStats {
        val sorted = entries.sortedBy { it.odometer }
        var km = 0.0
        var liters = 0.0
        var lastFull: FuelEntry? = null
        var pendingLiters = 0.0
        for (f in sorted) {
            if (lastFull == null) {
                if (f.fullTank) lastFull = f
                continue
            }
            pendingLiters += f.liters
            if (f.fullTank) {
                val d = f.odometer - lastFull.odometer
                if (d > 0 && pendingLiters > 0) { km += d; liters += pendingLiters }
                lastFull = f
                pendingLiters = 0.0
            }
        }
        return FuelStats(
            realKmPerLiter = if (liters > 0 && km > 0) km / liters else null,
            lastPricePerLiter = entries.maxByOrNull { it.at }?.pricePerLiter?.takeIf { it > 0 },
            litersMeasured = liters,
            kmMeasured = km,
        )
    }

    /** Soma dos dias cujo "date" começa com o prefixo, ou que está na lista. */
    fun period(days: List<DayRecord>, fuel: List<FuelEntry>, dates: Set<String>, costPerKm: Double, dateOf: (Long) -> String): PeriodSummary {
        val sel = days.filter { it.date in dates }
        val earnings = sel.sumOf { it.totalEarnings }
        val km = sel.sumOf { it.kmDriven ?: 0.0 }
        return PeriodSummary(
            days = sel.count { it.totalEarnings > 0 || it.kmDriven != null },
            earnings = earnings,
            km = km,
            net = earnings - km * costPerKm - sel.sumOf { it.totalExpenses },
            fuelSpent = fuel.filter { dateOf(it.at) in dates }.sumOf { it.total },
            expenses = sel.sumOf { it.totalExpenses },
        )
    }
}
