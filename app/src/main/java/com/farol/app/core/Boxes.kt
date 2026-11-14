package com.farol.app.core

/**
 * Caixinhas (6.10): dinheiro separado para não ser pego de surpresa.
 *  - Manutenção (automática): km rodados × "outros custos"/km − gastos de manutenção (ver [Maintenance.reserve]).
 *  - Combustível (automática): km rodados × combustível/km − o que foi pago nos abastecimentos.
 *    Positivo = o líquido já separou mais do que você gastou na bomba; negativo = gastou mais que o previsto.
 *  - Emergência (manual): você guarda ou tira valores; serve de colchão para semana fraca ou conserto.
 * Cada uma pode ter uma meta (barra de progresso). Kotlin puro: testado em Release610Test.
 */
object Boxes {
    data class Move(val at: Long, val value: Double, val note: String = "")   // + guardou, − tirou

    data class Box(val key: String, val name: String, val emoji: String, val balance: Double, val goal: Double?, val detail: String) {
        val progress: Float? get() = goal?.takeIf { it > 0 }?.let { (balance / it).coerceIn(0.0, 1.0).toFloat() }
    }

    fun fuel(days: List<DayRecord>, fuel: List<FuelEntry>, fuelPerKm: Double, since: String? = null, dateOf: (Long) -> String): Pair<Double, Double> {
        val km = days.filter { since == null || it.date >= since }.sumOf { it.kmDriven ?: 0.0 }
        val paid = fuel.filter { since == null || dateOf(it.at) >= since }.sumOf { it.total }
        return km * fuelPerKm to paid
    }

    fun emergency(moves: List<Move>) = moves.sumOf { it.value }

    fun build(days: List<DayRecord>, fuel: List<FuelEntry>, otherPerKm: Double, fuelPerKm: Double, moves: List<Move>,
              goals: Map<String, Double>, dateOf: (Long) -> String): List<Box> {
        val m = Maintenance.reserve(days, otherPerKm)
        val (saved, paid) = fuel(days, fuel, fuelPerKm, null, dateOf)
        return listOf(
            Box("manutencao", "Manutenção", "🔧", m.balance, goals["manutencao"],
                "${Fmt.money(otherPerKm)}/km × ${Fmt.dec(m.km, 0)} km − ${Fmt.money(m.spent)} em manutenção"),
            Box("combustivel", "Combustível", "⛽", saved - paid, goals["combustivel"],
                "${Fmt.money(fuelPerKm)}/km × ${Fmt.dec(m.km, 0)} km − ${Fmt.money(paid)} abastecidos"),
            Box("emergencia", "Emergência", "🛟", emergency(moves), goals["emergencia"],
                if (moves.isEmpty()) "guarde um pouco por dia para a semana fraca ou um conserto" else "${moves.size} movimentos"),
        )
    }
}
