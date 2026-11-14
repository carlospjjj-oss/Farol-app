package com.farol.app.core

import java.util.Locale

/**
 * Ajustes que quase certamente estão errados (consumo 1 km/l, combustível R$ 0, custo R$ 0,05/km…).
 * Com eles o card mente: tudo fica verde. As mesmas faixas são usadas no servidor (alertas).
 */
object SettingsSanity {

    data class Issue(val field: String, val text: String, val typical: String)

    fun check(p: CostProfile, isCar: Boolean): List<Issue> {
        val out = mutableListOf<Issue>()
        val (kmin, kmax) = if (isCar) 4.0 to 25.0 else 12.0 to 70.0
        if (p.kmPerLiter < kmin || p.kmPerLiter > kmax)
            out += Issue("km_l", "Consumo de ${num(p.kmPerLiter)} km/l", if (isCar) "carro popular: 6 a 14 km/l" else "moto: 25 a 45 km/l")
        if (p.fuelPrice < 2.5 || p.fuelPrice > 10)
            out += Issue("preco", "Combustível a ${Fmt.money(p.fuelPrice)}", "entre R$ 3 e R$ 7 o litro")
        val minCost = if (isCar) 0.25 else 0.08
        if (p.costPerKm < minCost)
            out += Issue("custo", "Custo de ${Fmt.money(p.costPerKm)}/km", if (isCar) "carro: perto de R$ 0,80/km" else "moto: perto de R$ 0,35/km")
        if (p.greenPerKm > 0 && p.yellowPerKm > 0 && p.greenPerKm <= p.yellowPerKm)
            out += Issue("faixas", "Verde (${Fmt.money(p.greenPerKm)}/km) não é maior que o amarelo", "verde maior que o amarelo")
        if (p.yellowPerKm > 0 && p.costPerKm > 0 && p.yellowPerKm < p.costPerKm)
            out += Issue("faixas", "Amarelo (${Fmt.money(p.yellowPerKm)}/km) abaixo do seu custo (${Fmt.money(p.costPerKm)}/km)", "amarelo acima do custo por km")
        return out
    }

    private fun num(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale("pt", "BR"), "%.1f", v)
}
