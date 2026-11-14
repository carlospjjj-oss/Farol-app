package com.farol.app.core

import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

enum class Rating { RED, YELLOW, GREEN }

/** Seus números. Tudo editável na tela principal do app. */
data class CostProfile(
    val fuelPrice: Double = 6.30,        // R$ por litro
    val kmPerLiter: Double = 11.0,       // consumo médio na cidade
    val otherCostPerKm: Double = 0.30,   // manutenção, pneu, depreciação, seguro... (R$/km)
    val greenPerKm: Double = 2.00,       // R$/km bruto a partir do qual é verde
    val yellowPerKm: Double = 1.50,      // abaixo disto é vermelho
    val greenPerHour: Double = 45.0,     // R$/h bruto verde
    val yellowPerHour: Double = 30.0,    // abaixo disto é vermelho
    val avgSpeedKmh: Double = 25.0,      // para estimar o tempo quando a tela não mostra
    val maxPickupKm: Double = 0.0,       // busca acima disto = vermelho (0 = desligado)
    val minPassengerRating: Double = 0.0,// nota abaixo disto = vermelho (0 = desligado)
    val greenStars: Double = 0.0,        // nota a partir disto = verde; entre as duas = amarelo (0 = desligado)
    val minPrice: Double = 0.0,          // valor da corrida abaixo disto = vermelho
    val minNet: Double = 0.0,            // lucro líquido abaixo disto = vermelho
    val minNetPct: Double = 0.0,         // lucro abaixo deste % do valor = vermelho
    val maxTotalKm: Double = 0.0,        // distância total acima disto = vermelho
    val maxMinutes: Double = 0.0,        // tempo total acima disto = vermelho
    val alertWords: List<String> = emptyList(), // bairros/ruas que você não quer
    val favoriteWords: List<String> = emptyList(), // bairros bons / perto de casa (só destaca, não muda a cor)
) {
    val fuelCostPerKm: Double get() = if (kmPerLiter > 0) fuelPrice / kmPerLiter else 0.0
    val costPerKm: Double get() = fuelCostPerKm + otherCostPerKm
}

data class OfferResult(
    val offer: ParsedOffer,
    val perKm: Double,          // R$/km bruto (o número que todo motorista compara)
    val perHour: Double,        // R$/h bruto
    val cost: Double,           // custo estimado do percurso todo
    val net: Double,            // lucro líquido estimado
    val netPerHour: Double,
    val minutes: Double,
    val timeEstimated: Boolean, // true se o tempo veio da velocidade média
    val ratingKm: Rating,
    val ratingHour: Rating,
    val rating: Rating,         // o pior dos dois (ou vermelho, se houver alerta)
    val warnings: List<String> = emptyList(),
    val favorites: List<String> = emptyList(),
    /** Espera média do restaurante somada ao tempo (null = não se aplica / sem dados suficientes). */
    val waitMin: Double? = null,
    val waitPlace: String? = null,
    /** R$/h sem a espera (para mostrar quanto ela custa). */
    val perHourNoWait: Double? = null,
    /** Por que a oferta é boa ou ruim (frase curta + até 2 motivos). */
    val why: OfferExplainer.Why? = null,
)

object ProfitCalculator {

    /**
     * @param waitMin espera média do restaurante (aprendida das suas coletas); entra no tempo total,
     * então derruba o R$/h quando o restaurante costuma demorar.
     */
    fun evaluate(offer: ParsedOffer, p: CostProfile, waitMin: Double? = null, waitPlace: String? = null): OfferResult {
        val km = offer.totalKm.coerceAtLeast(0.1)
        val estimated = offer.totalMin == null || offer.totalMin <= 0.0
        val baseMinutes = if (estimated) km / p.avgSpeedKmh.coerceAtLeast(1.0) * 60.0 else offer.totalMin!!
        val wait = waitMin?.takeIf { it > 0 }
        val minutes = baseMinutes + (wait ?: 0.0)
        val hours = (minutes / 60.0).coerceAtLeast(1.0 / 60.0)

        val perKm = offer.price / km
        val perHour = offer.price / hours
        val cost = km * p.costPerKm
        val net = offer.price - cost

        val rKm = rate(perKm, p.greenPerKm, p.yellowPerKm)
        val rHour = rate(perHour, p.greenPerHour, p.yellowPerHour)

        val warnings = buildList {
            val pk = offer.pickupKm
            if (p.maxPickupKm > 0 && pk != null && pk > p.maxPickupKm) add("busca ${Fmt.km(pk)}")
            val st = offer.passengerRating
            if (p.minPassengerRating > 0 && st != null && st < p.minPassengerRating) add("nota ${Fmt.dec(st, 2)}★")
            if (p.minPrice > 0 && offer.price < p.minPrice) add("valor < ${Fmt.money(p.minPrice)}")
            val netNow = offer.price - km * p.costPerKm
            if (p.minNet > 0 && netNow < p.minNet) add("lucro < ${Fmt.money(p.minNet)}")
            if (p.minNetPct > 0 && offer.price > 0 && netNow / offer.price * 100 < p.minNetPct)
                add("lucro ${(netNow / offer.price * 100).toInt()}% < ${p.minNetPct.toInt()}%")
            if (p.maxTotalKm > 0 && offer.totalKm > p.maxTotalKm) add("distância ${Fmt.km(offer.totalKm)}")
            if (p.maxMinutes > 0 && minutes > p.maxMinutes) add("tempo ${Fmt.min(minutes)}")
            if (Regex("""[áa]rea de risco""", RegexOption.IGNORE_CASE).containsMatchIn(offer.rawText)) add("área de risco")
            if (p.alertWords.isNotEmpty()) {
                val hay = fold(offer.rawText)
                p.alertWords.firstOrNull { w -> w.length >= 3 && hay.contains(fold(w)) }?.let { add(it) }
            }
        }
        var base = if (rKm.ordinal <= rHour.ordinal) rKm else rHour
        // Nota entre o mínimo e o "verde": no máximo amarelo
        val st = offer.passengerRating
        if (st != null && p.greenStars > 0 && st < p.greenStars && base == Rating.GREEN) base = Rating.YELLOW

        val result = OfferResult(
            offer = offer,
            perKm = perKm,
            perHour = perHour,
            cost = cost,
            net = net,
            netPerHour = net / hours,
            minutes = minutes,
            timeEstimated = estimated,
            ratingKm = rKm,
            ratingHour = rHour,
            rating = if (warnings.isEmpty()) base else Rating.RED,
            warnings = warnings,
            favorites = if (p.favoriteWords.isEmpty()) emptyList() else {
                val hay = fold(offer.rawText)
                p.favoriteWords.filter { w -> w.length >= 3 && hay.contains(fold(w)) }
            },
            waitMin = wait,
            waitPlace = if (wait != null) waitPlace else null,
            perHourNoWait = if (wait != null) offer.price / (baseMinutes / 60.0).coerceAtLeast(1.0 / 60.0) else null,
        )
        return result.copy(why = OfferExplainer.explain(result, p))
    }

    /** minúsculas e sem acento: "São Bento" == "sao bento" */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun rate(v: Double, green: Double, yellow: Double) = when {
        v >= green -> Rating.GREEN
        v >= yellow -> Rating.YELLOW
        else -> Rating.RED
    }
}

object Fmt {
    private val brl = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val dec1 = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 1; maximumFractionDigits = 1
    }
    fun money(v: Double): String = brl.format(v).replace(' ', ' ')
    fun km(v: Double): String = "${dec1.format(v)} km"
    fun dec(v: Double, digits: Int): String = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = digits; maximumFractionDigits = digits
    }.format(v)
    fun min(v: Double): String {
        val m = Math.round(v).toInt()
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }
}
