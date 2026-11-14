package com.farol.app.core

/**
 * Manutenção do veículo por km (manual de gestão, módulo 9) e a "caixinha" da reserva.
 *
 * - Cada item (óleo, correia, pneu…) tem um intervalo em km e o km da última troca. O km atual vem do diário
 *   (km do painel nos turnos e abastecimentos), então o Farol avisa "faltam 300 km" sem ninguém fazer conta.
 * - Os intervalos iniciais são SUGESTÕES para conferir no manual do veículo; o usuário muda à vontade.
 * - Caixinha: os "outros custos" por km (óleo, pneu, desgaste) já saem do líquido de cada dia. Esse dinheiro
 *   vai para a reserva; o que se gasta em manutenção sai dela, e não do líquido do dia (seria contar duas vezes).
 *
 * Kotlin puro, sem Android: testado em MaintenanceTest.
 */
data class MaintItem(
    val id: String,
    val name: String,
    val intervalKm: Double?,      // null = sem intervalo definido (só guarda o histórico)
    val lastKm: Double? = null,   // km do painel na última troca
    val lastAt: Long? = null,
)

object Maintenance {
    enum class Level { OK, SOON, DUE, UNKNOWN }

    data class Status(val item: MaintItem, val level: Level, val remainingKm: Double?, val nextKm: Double?) {
        val text: String get() = when (level) {
            Level.UNKNOWN -> if (item.intervalKm == null) "sem intervalo definido" else "toque para registrar a última troca"
            Level.DUE -> "passou ${Fmt.dec(-(remainingKm ?: 0.0), 0)} km da troca"
            else -> "faltam ${Fmt.dec(remainingKm ?: 0.0, 0)} km (troca com ${Fmt.dec(nextKm ?: 0.0, 0)} km)"
        }
    }

    /** Sugestões para começar (CONFERIR no manual do veículo). */
    fun defaults(mode: String): List<MaintItem> = when (mode) {
        "carro" -> listOf(
            MaintItem("oleo_motor", "Óleo do motor e filtro", 10_000.0),
            MaintItem("filtro_ar", "Filtro de ar", 10_000.0),
            MaintItem("pneus", "Pneus (rodízio/troca)", 10_000.0),
            MaintItem("freios", "Pastilhas de freio", 20_000.0),
            MaintItem("correia", "Correia dentada", 50_000.0),
            MaintItem("revisao", "Revisão", 10_000.0),
        )
        else -> listOf(
            MaintItem("oleo_motor", "Óleo do motor", 3_000.0),
            MaintItem("oleo_transmissao", "Óleo da transmissão", 12_000.0),
            MaintItem("correia", "Correia/CVT", 24_000.0),
            MaintItem("pneu_traseiro", "Pneu traseiro", 12_000.0),
            MaintItem("pneu_dianteiro", "Pneu dianteiro", 18_000.0),
            MaintItem("freios", "Freios (lona/pastilha)", 8_000.0),
            MaintItem("revisao", "Revisão", 6_000.0),
        )
    }

    /** Aviso amarelo: faltando 10% do intervalo ou 300 km (o que for maior). */
    fun status(item: MaintItem, currentKm: Double?): Status {
        val iv = item.intervalKm
        val last = item.lastKm
        if (iv == null || iv <= 0 || last == null || currentKm == null) return Status(item, Level.UNKNOWN, null, null)
        val next = last + iv
        val rem = next - currentKm
        val soon = maxOf(iv * 0.10, 300.0)
        val level = when { rem <= 0 -> Level.DUE; rem <= soon -> Level.SOON; else -> Level.OK }
        return Status(item, level, rem, next)
    }

    /** Mais urgentes primeiro (vencidos, perto, ok, sem dados). */
    fun sorted(items: List<MaintItem>, currentKm: Double?): List<Status> {
        val rank = mapOf(Level.DUE to 0, Level.SOON to 1, Level.OK to 2, Level.UNKNOWN to 3)
        return items.map { status(it, currentKm) }.sortedWith(compareBy({ rank[it.level] }, { it.remainingKm ?: Double.MAX_VALUE }))
    }

    /** Registrar a troca: o item passa a contar do km informado. */
    fun service(items: List<MaintItem>, id: String, km: Double, at: Long) =
        items.map { if (it.id == id) it.copy(lastKm = km, lastAt = at) else it }

    /** Km atual mais confiável que o diário conhece: o maior km do painel já anotado. */
    fun currentKm(days: List<DayRecord>, fuel: List<FuelEntry>): Double? =
        (days.flatMap { listOfNotNull(it.kmStart, it.kmEnd) } + fuel.map { it.odometer } +
            days.flatMap { d -> d.expenses.mapNotNull { it.km } }).maxOrNull()

    data class Reserve(val km: Double, val ratePerKm: Double, val saved: Double, val spent: Double) {
        val balance: Double get() = saved - spent
    }

    /**
     * Caixinha: km rodados (turnos encerrados) × "outros custos" por km − gastos de manutenção lançados.
     * Conta a partir de [since] (data "aaaa-mm-dd"), para começar do zero quando o usuário quiser.
     */
    fun reserve(days: List<DayRecord>, ratePerKm: Double, since: String? = null): Reserve {
        val sel = days.filter { since == null || it.date >= since }
        val km = sel.sumOf { it.kmDriven ?: 0.0 }
        val spent = sel.sumOf { it.maintenanceSpent }
        return Reserve(km, ratePerKm, km * ratePerKm, spent)
    }
}
