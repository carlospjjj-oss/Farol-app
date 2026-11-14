package com.farol.app.core

import kotlin.math.roundToInt

/**
 * Explica a cor da oferta em uma frase curta + até 2 motivos ("+ busca curta 600 m", "− espera ~18 min").
 * Regras fixas, no celular, sem internet e sem IA: o mesmo número sempre dá a mesma explicação.
 */
object OfferExplainer {

    data class Why(val headline: String, val plus: List<String>, val minus: List<String>) {
        /** Tudo numa linha (histórico, voz). */
        val oneLine: String get() = (listOf(headline) + plus.map { "+ $it" } + minus.map { "− $it" }).joinToString(" · ")
    }

    private data class Factor(val text: String, val score: Double, val key: String)

    fun explain(r: OfferResult, p: CostProfile): Why {
        val o = r.offer
        val plus = mutableListOf<Factor>()
        val minus = mutableListOf<Factor>()

        // R$/km e R$/h contra as suas faixas
        if (p.greenPerKm > 0 && r.perKm >= p.greenPerKm) plus += Factor("${money(r.perKm)}/km", r.perKm / p.greenPerKm, "km")
        if (p.yellowPerKm > 0 && r.perKm < p.yellowPerKm) minus += Factor("${money(r.perKm)}/km", 1 + (p.yellowPerKm - r.perKm) / p.yellowPerKm, "km")
        if (p.greenPerHour > 0 && r.perHour >= p.greenPerHour) plus += Factor("R$/h acima da meta", r.perHour / p.greenPerHour * 0.9, "h")
        if (p.yellowPerHour > 0 && r.perHour < p.yellowPerHour) minus += Factor("R$/h abaixo do mínimo", 1 + (p.yellowPerHour - r.perHour) / p.yellowPerHour * 0.9, "h")

        // Busca (deslocamento até a coleta/passageiro)
        o.pickupKm?.let { pk ->
            val longKm = if (p.maxPickupKm > 0) p.maxPickupKm else 2.5
            when {
                pk <= 1.0 -> plus += Factor("busca curta ${dist(pk)}", 1.1, "busca")
                pk > longKm -> minus += Factor("busca longa ${dist(pk)}", 1.3 + (pk - longKm) / longKm, "busca")
            }
        }
        // Espera do restaurante
        r.waitMin?.takeIf { it >= WaitStats.WARN_MIN }?.let { w -> minus += Factor("espera ~${w.roundToInt()} min", 1.2 + w / 30.0, "espera") }
        // Prejuízo
        if (r.net < 0) minus += Factor("não paga o custo", 3.0, "lucro")
        // Nota do passageiro entre o mínimo e o "verde"
        val st = o.passengerRating
        val starsYellow = st != null && p.greenStars > 0 && st < p.greenStars
        if (starsYellow) minus += Factor("nota ${Fmt.dec(st!!, 2)}★", 1.0, "nota")
        // Seus alertas (busca máxima, valor mínimo, bairro…) pesam mais que tudo
        r.warnings.forEach { w -> minus += Factor(w, 5.0, "aviso:$w") }
        if (r.favorites.isNotEmpty()) plus += Factor("⭐ ${r.favorites.first()}", 0.8, "fav")

        plus.sortByDescending { it.score }
        minus.sortByDescending { it.score }

        val headline: String
        val usedKey: String?
        when (r.rating) {
            Rating.GREEN -> {
                if (p.greenPerKm > 0 && r.perKm >= p.greenPerKm) {
                    headline = "Boa: ${money(r.perKm)}/km, acima da sua meta de ${money(p.greenPerKm)}"; usedKey = "km"
                } else {
                    headline = "Boa: R$ ${r.perHour.roundToInt()}/h, acima da sua meta de R$ ${p.greenPerHour.roundToInt()}/h"; usedKey = "h"
                }
            }
            Rating.RED -> {
                val w = r.warnings.firstOrNull()
                when {
                    w != null -> { headline = "Ruim: " + warningText(w); usedKey = "aviso:$w" }
                    r.ratingKm == Rating.RED -> {
                        headline = "Ruim: ${money(r.perKm)}/km, abaixo do seu mínimo de ${money(p.yellowPerKm)}"; usedKey = "km"
                    }
                    r.waitMin != null && (r.perHourNoWait ?: 0.0) >= p.yellowPerHour -> {
                        headline = "Ruim: a espera de ~${r.waitMin.roundToInt()} min derruba o R$/h para R$ ${r.perHour.roundToInt()}"; usedKey = "espera"
                    }
                    else -> {
                        headline = "Ruim: R$ ${r.perHour.roundToInt()}/h, abaixo do seu mínimo de R$ ${p.yellowPerHour.roundToInt()}/h"; usedKey = "h"
                    }
                }
            }
            Rating.YELLOW -> when {
                r.waitMin != null && r.ratingKm == Rating.GREEN && r.ratingHour != Rating.GREEN && (r.perHourNoWait ?: 0.0) >= p.greenPerHour -> {
                    headline = "Média: o km paga bem, mas a espera derruba o R$/h"; usedKey = null
                }
                starsYellow && r.ratingKm == Rating.GREEN && r.ratingHour == Rating.GREEN -> {
                    headline = "Média: valores bons, mas nota ${Fmt.dec(st!!, 2)}★ abaixo de ${Fmt.dec(p.greenStars, 2)}"; usedKey = "nota"
                }
                r.ratingKm.ordinal <= r.ratingHour.ordinal -> {
                    headline = "Média: ${money(r.perKm)}/km, abaixo da sua meta de ${money(p.greenPerKm)}"; usedKey = null
                }
                else -> {
                    headline = "Média: R$ ${r.perHour.roundToInt()}/h, abaixo da sua meta de R$ ${p.greenPerHour.roundToInt()}/h"; usedKey = null
                }
            }
        }

        // Até 2 motivos, sem repetir o que a frase já disse
        val p2 = plus.filter { it.key != usedKey }
        val m2 = minus.filter { it.key != usedKey }
        val (pick, mick) = when (r.rating) {
            Rating.GREEN -> p2.take(if (m2.isEmpty()) 2 else 1) to m2.take(if (p2.isEmpty()) 2 else 1)
            Rating.RED -> p2.take(if (m2.isEmpty()) 1 else 0) to m2.take(2)
            Rating.YELLOW -> p2.take(if (m2.isEmpty()) 2 else 1) to m2.take(if (p2.isEmpty()) 2 else 1)
        }
        return Why(headline, pick.map { it.text }, mick.map { it.text })
    }

    private fun warningText(w: String): String = when {
        w.startsWith("busca") -> "$w, acima do seu máximo"
        w.startsWith("nota") -> "passageiro com $w, abaixo do seu mínimo"
        w.startsWith("valor") -> "valor abaixo do seu mínimo"
        w.startsWith("lucro") -> w
        w.startsWith("distância") || w.startsWith("tempo") -> "$w, acima do seu limite"
        w == "área de risco" -> "área de risco"
        else -> "passa por \"$w\" (seu alerta)"
    }

    private fun money(v: Double) = Fmt.money(v)
    private fun dist(km: Double) = if (km < 1.0) "${((km * 1000) / 10).roundToInt() * 10} m" else Fmt.km(km)
}
