package com.farol.app.core

/** Custos mensais do veículo -> custo por km e participação de cada item. */
data class MonthlyCosts(
    val kmPerMonth: Double = 2400.0,
    val vehicle: String = "proprio",      // proprio | financiado | alugado
    val financing: Double = 0.0,          // parcela do financiamento ou aluguel
    val depreciation: Double = 50.0,
    val maintenance: Double = 120.0,      // óleo, pneus, correia, freio, revisão
    val ipva: Double = 25.0,              // IPVA + licenciamento, por mês
    val insurance: Double = 0.0,
    val other: Double = 60.0,             // celular, plano de dados, equipamento, baú...
)

data class CostItem(val name: String, val monthly: Double, val perKm: Double, val pct: Double)

object CostBreakdown {
    /** Inclui o combustível calculado pelo consumo e preço do litro. */
    fun items(c: MonthlyCosts, fuelPrice: Double, kmPerLiter: Double): List<CostItem> {
        val km = c.kmPerMonth.coerceAtLeast(1.0)
        val fuel = if (kmPerLiter > 0) km / kmPerLiter * fuelPrice else 0.0
        val raw = listOf(
            "Combustível" to fuel,
            (if (c.vehicle == "alugado") "Aluguel" else "Financiamento") to c.financing,
            "Depreciação" to c.depreciation,
            "Manutenção" to c.maintenance,
            "IPVA e licenciamento" to c.ipva,
            "Seguro" to c.insurance,
            "Outros" to c.other,
        ).filter { it.second > 0 }
        val total = raw.sumOf { it.second }.coerceAtLeast(0.01)
        return raw.map { (n, v) -> CostItem(n, v, v / km, v / total * 100) }.sortedByDescending { it.monthly }
    }

    fun total(items: List<CostItem>) = items.sumOf { it.monthly }

    /** "Outros custos (R$/km)" do semáforo = tudo menos o combustível, dividido pelos km do mês. */
    fun otherPerKm(c: MonthlyCosts): Double =
        (c.financing + c.depreciation + c.maintenance + c.ipva + c.insurance + c.other) / c.kmPerMonth.coerceAtLeast(1.0)
}
