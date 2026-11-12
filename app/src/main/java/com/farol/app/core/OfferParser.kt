package com.farol.app.core

import kotlin.math.abs

/** Oferta extraída do texto da tela, antes de aplicar seus custos. */
data class ParsedOffer(
    val app: String,
    val price: Double,
    val totalKm: Double,
    /** null quando a tela não mostra tempo (o calculador estima pela velocidade média). */
    val totalMin: Double?,
    val distancesKm: List<Double>,
    val durationsMin: List<Double>,
    /** Distância até o passageiro/coleta, quando a tela separa busca e viagem. */
    val pickupKm: Double? = null,
    /** Nota do passageiro (ex.: 4,93 ★), quando aparece. */
    val passengerRating: Double? = null,
    /** Texto normalizado da oferta (para os alertas de bairro). Fora da assinatura. */
    val rawText: String = "",
) {
    /** Usado para não mostrar o mesmo cartão várias vezes enquanto a oferta continua na tela. */
    val signature: String
        get() = "$app|${(price * 100).toLong()}|${(totalKm * 10).toLong()}|${totalMin?.toLong()}"
}

/**
 * Lê o texto bruto de uma tela (nós de acessibilidade ou OCR, juntados com " | ")
 * e tenta achar uma oferta: preço, distâncias e tempos.
 *
 * Não depende do layout exato de cada app; usa padrões que os quatro apps têm em comum:
 *  - preço  "R$ 15,20"  (ignora "+R$ 3,00" de dinâmica e "R$ 1,80/km")
 *  - tempo/distância "4 min (1,2 km)", "Viagem de 18 min (9,4 km)", "800 m", "1 h 5 min"
 *  - quando há uma distância/tempo marcado como "total", usa só ele; senão soma tudo
 *    (busca + viagem), porque o custo é de todo o caminho que você roda.
 */
object OfferParser {

    private val MONEY = Regex(
        """(\+\s*)?R\$\s*(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)(\s*/\s*(?:km|h|hr|hora|min))?""",
        RegexOption.IGNORE_CASE
    )

    private val DIST = Regex(
        """(?<![\d.,])(\d+(?:[.,]\d+)?)\s*(km|quil[oô]metros?|metros?|m)\b""",
        RegexOption.IGNORE_CASE
    )

    // Grupo 1+2: "1 h 5 min" / "18 min"; grupo 3: "2 h" sozinho.
    private val DUR = Regex(
        """(?<![\d.,])(?:(\d+)\s*h(?:oras?|rs?)?\s*(?:e\s*)?)?(\d+)\s*min(?:utos?|s)?\b|(?<![\d.,])(\d+)\s*h(?:oras?|rs?)?\b""",
        RegexOption.IGNORE_CASE
    )

    /** Palavras que indicam que existe uma oferta aberta (botão de aceitar etc.). */
    val OFFER_HINTS = Regex(
        """\baceitar\b|\baccept\b|\brecusar\b|\bpegar\b|oferecer|toque para|nova (?:corrida|entrega|viagem|rota|solicita[çc][ãa]o)""",
        RegexOption.IGNORE_CASE
    )

    /** Valores em R$ precedidos por isto não são o preço da oferta (ganhos do dia, saldo...). */
    private val MONEY_EXCLUDE_CONTEXT = Regex(
        """(hoje|ganhos?|ganhou|saldo|semana|carteira|meta|gorjeta|ped[aá]gio)\W{0,6}$""",
        RegexOption.IGNORE_CASE
    )

    private const val TOTAL_LOOKBEHIND = 22

    private val RATING = Regex(
        """(?<![\d.,])([1-5][.,]\d{1,2})\s*(?:★|⭐|estrelas?)|[★⭐]\s*([1-5][.,]\d{1,2})(?![\d.,])""",
        RegexOption.IGNORE_CASE
    )

    private val PICKUP_CONTEXT = Regex("""(busca|coleta|retirada|at[eé] o passageiro|de dist[aâ]ncia)""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String, app: String, requireHint: Boolean = true): ParsedOffer? {
        val text = normalize(mergeSplitChars(rawText))
        if (requireHint && !OFFER_HINTS.containsMatchIn(text)) return null

        // 1) Valores em dinheiro (candidatos a preço) e texto sem eles, para não confundir
        //    "R$ 12,5" com distância/tempo.
        data class Money(val value: Double, val pos: Int)
        val monies = mutableListOf<Money>()
        val sb = StringBuilder(text)
        for (m in MONEY.findAll(text)) {
            val blank = " ".repeat(m.value.length)
            sb.replace(m.range.first, m.range.last + 1, blank)
            if (m.groupValues[1].isNotEmpty()) continue           // "+R$ 3,00" (dinâmica/bônus)
            if (m.groupValues[3].isNotEmpty()) continue           // "R$ 1,80/km"
            val before = text.substring(maxOf(0, m.range.first - 20), m.range.first)
            if (MONEY_EXCLUDE_CONTEXT.containsMatchIn(before)) continue
            val v = parseMoney(m.groupValues[2]) ?: continue
            if (v in 2.0..3000.0) monies += Money(v, m.range.first)
        }
        if (monies.isEmpty()) return null
        val clean = sb.toString()

        // 2) Distâncias
        data class Measure(val value: Double, val pos: Int, val isTotal: Boolean)
        val dists = DIST.findAll(clean).mapNotNull { m ->
            val n = parseDecimal(m.groupValues[1]) ?: return@mapNotNull null
            val unit = m.groupValues[2].lowercase()
            val km = if (unit.startsWith("k") || unit.startsWith("q")) n else n / 1000.0
            if (km <= 0.0 || km > 600.0) null
            else Measure(km, m.range.first, isTotalAt(clean, m.range.first))
        }.toList()
        if (dists.isEmpty()) return null

        // 3) Tempos
        val durs = DUR.findAll(clean).mapNotNull { m ->
            val g = m.groupValues
            val min = when {
                g[2].isNotEmpty() -> (g[1].toIntOrNull() ?: 0) * 60.0 + g[2].toDouble()
                g[3].isNotEmpty() -> g[3].toDouble() * 60.0
                else -> return@mapNotNull null
            }
            if (min <= 0.0 || min > 600.0) null
            else Measure(min, m.range.first, isTotalAt(clean, m.range.first))
        }.toList()

        val hasTotal = dists.any { it.isTotal }
        val totalKm = dists.firstOrNull { it.isTotal }?.value ?: dists.sumOf { it.value }

        // Busca: a primeira distância quando há busca + viagem separadas (Uber/99),
        // ou a distância com contexto "coleta/busca" (iFood).
        val pickup = dists.firstOrNull { d ->
            !d.isTotal && PICKUP_CONTEXT.containsMatchIn(clean.substring(maxOf(0, d.pos - 25), minOf(clean.length, d.pos + 30)))
        }?.value ?: if (!hasTotal && dists.size >= 2) dists.first().value else null

        val rating = RATING.find(clean)?.let { m ->
            parseDecimal(m.groupValues[1].ifEmpty { m.groupValues[2] })
        }
        val totalMin = if (durs.isEmpty()) null
        else durs.firstOrNull { it.isTotal }?.value ?: durs.sumOf { it.value }

        // 4) Preço = o valor em R$ mais próximo da primeira distância (preferindo o que vem
        //    antes dela). Isso descarta o "ganhos do dia" que a Uber mostra no topo do mapa.
        val firstDistPos = dists.minOf { it.pos }
        val before = monies.filter { it.pos < firstDistPos }
        val price = (before.maxByOrNull { it.pos }
            ?: monies.minByOrNull { abs(it.pos - firstDistPos) })!!.value

        return ParsedOffer(
            app = app,
            price = price,
            totalKm = round2(totalKm),
            totalMin = totalMin,
            distancesKm = dists.map { round2(it.value) },
            durationsMin = durs.map { it.value },
            pickupKm = pickup?.let { round2(it) },
            passengerRating = rating,
            rawText = text,
        )
    }

    private fun isTotalAt(text: String, pos: Int): Boolean =
        text.substring(maxOf(0, pos - TOTAL_LOOKBEHIND), pos).contains("total", ignoreCase = true)

    /**
     * A 99 desenha valores letra por letra: "R | $ | 1 | 2 | , | 5 | 0".
     * Junta sequências de pedaços de 1 caractere (R, $, dígitos, vírgula, ponto) -> "R$12,50".
     */
    fun mergeSplitChars(s: String): String {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        for (p in s.split(" | ")) {
            val t = p.trim()
            if (t.length == 1 && t[0] in "R$0123456789,.") buf.append(t)
            else {
                if (buf.isNotEmpty()) { out += buf.toString(); buf.clear() }
                out += p
            }
        }
        if (buf.isNotEmpty()) out += buf.toString()
        return out.joinToString(" | ")
    }

    fun normalize(s: String): String =
        s.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')
            .replace(Regex("""[ \t]+"""), " ")

    /** "1.234,56" -> 1234.56 ; "15,20" -> 15.2 ; "20" -> 20 ; "15.20" -> 15.2 */
    fun parseMoney(s: String): Double? = when {
        s.contains(',') -> s.replace(".", "").replace(',', '.').toDoubleOrNull()
        Regex("""\d{1,3}(\.\d{3})+""").matches(s) -> s.replace(".", "").toDoubleOrNull()
        else -> s.toDoubleOrNull()
    }

    /** "9,4" ou "9.4" -> 9.4 */
    fun parseDecimal(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    private fun round2(v: Double) = Math.round(v * 100) / 100.0
}
