package com.farol.app.core

import java.util.Calendar
import kotlin.math.abs

/**
 * Região de coleta (origem) e de entrega (destino) de uma oferta, tiradas do endereço que o app mostra.
 * Fica só no celular (o bairro do cliente não vai para o servidor).
 *
 *  - Keeta: "Coleta | <loja> | <rua>, <bairro>, <cidade> - SP" … "Entrega até | 23:07 | <rua>, <cidade>, SP"
 *  - iFood: "<loja> | <rua>, <bairro>,<cidade>,SP,BR | <rua>, <bairro do cliente> | Rejeitar"
 *  - 99 (corrida): "(7 min 2 km) <rua>, <nº>, <bairro>" (embarque) e "(17 min 9,2 km) <rua>, <nº>, <bairro>" (destino)
 * Outros formatos (99 Entrega pela imagem, Uber, inDrive) ficam sem região: melhor nada do que chutar.
 */
object RegionReader {

    data class Place(val key: String, val label: String, val city: String?) {
        val isCityOnly: Boolean get() = city != null && fold(city) == key
    }

    private val STREET = Regex(
        """^(\[endere[cç]o\]|(r\.|rua|av\.?|avenida|al\.?|alameda|estr\.?|estrada|rod\.?|rodovia|pra[cç]a|travessa|tv\.?|largo|viela|via|vila\s+\d)\s)""",
        RegexOption.IGNORE_CASE)
    private val TRIP_MARK = Regex("""^(?:\S{1,2}\s+)?\(?\s*\d+\s*min\s*\(?\s*\d+(?:[.,]\d+)?\s*k?m\s*\)?\s*""", RegexOption.IGNORE_CASE)
    private val CITIES = listOf(
        "São Paulo", "Santo André", "São Caetano do Sul", "São Bernardo do Campo", "Diadema", "Mauá", "Ribeirão Pires",
        "Rio Grande da Serra", "Guarulhos", "Osasco", "Barueri", "Carapicuíba", "Taboão da Serra", "Embu das Artes",
        "Cotia", "Itapecerica da Serra", "Mogi das Cruzes", "Suzano", "Poá", "Ferraz de Vasconcelos", "Itaquaquecetuba",
        "Arujá", "Santana de Parnaíba", "Caieiras", "Franco da Rocha", "Mairiporã", "Embu-Guaçu")
    private val CITY_KEYS = CITIES.associateBy { fold(it) }
    private val GENERIC_END = Regex(""",\s*(vila|jardim|parque|cidade|conjunto|jd\.?|vl\.?)$""", RegexOption.IGNORE_CASE)

    fun fold(s: String) = ProfitCalculator.fold(s).replace(Regex("""[^a-z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /** (origem, destino) — cada um pode faltar. */
    fun parse(app: String, text: String): Pair<Place?, Place?> {
        val parts = joinWrapped(text.split("|").map { it.trim() }.filter { it.isNotEmpty() })
        return when (app) {
            "Keeta" -> {
                val iCol = parts.indexOfFirst { it.equals("Coleta", true) }
                val iEnt = parts.indexOfFirst { it.startsWith("Entrega até", true) }
                val origin = if (iCol >= 0) parts.subList(iCol + 1, if (iEnt > iCol) iEnt else parts.size).firstOrNull(::isAddress)?.let(::place) else null
                val dest = if (iEnt >= 0) parts.drop(iEnt + 1).firstOrNull(::isAddress)?.let(::place) else null
                origin to dest
            }
            "iFood" -> {
                val places = parts.takeWhile { !it.equals("Rejeitar", true) }.filter(::isAddress).mapNotNull(::place)
                // a loja vem com a cidade; o cliente, só com o bairro
                places.firstOrNull { it.city != null && !it.isCityOnly } to places.lastOrNull { it.city == null }
            }
            "99" -> {
                val trips = parts.filter { TRIP_MARK.containsMatchIn(it) && TRIP_MARK.replace(it, "").isNotBlank() }
                    .map { TRIP_MARK.replace(it, "").trim() }.mapNotNull(::place)
                if (trips.size == 2) trips[0] to trips[1] else null to null   // lista com várias ofertas: não dá para saber qual
            }
            else -> null to null
        }
    }

    /** "Avenida X," | "950, Vila Y" → uma linha só (OCR quebra o endereço). */
    private fun joinWrapped(parts: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < parts.size) {
            var p = parts[i]
            // "…, Vila" | "Amorim": o OCR quebrou o nome do bairro (ou a vírgula ficou no fim da linha)
            while (i + 1 < parts.size && (p.endsWith(",") || GENERIC_END.containsMatchIn(p))) { i++; p = "$p ${parts[i]}" }
            out += p; i++
        }
        return out
    }

    private fun isAddress(seg: String) = STREET.containsMatchIn(TRIP_MARK.replace(seg, "").trim())

    private fun place(addr: String): Place? {
        val comps = addr.split(Regex(""",|\s-\s""")).map { it.trim() }.drop(1)   // o 1º é a rua
            .filter { c -> c.isNotEmpty() && !c.any { it.isDigit() } && !c.startsWith("[") && !STREET.containsMatchIn(c) &&
                fold(c) !in setOf("sp", "br", "brasil", "cep", "s") && c.length >= 3 && !c.startsWith("...") }
            .map { it.trimEnd('.', ' ') }
        if (comps.isEmpty()) return null
        val city = comps.firstNotNullOfOrNull { CITY_KEYS[fold(it)] }
        val hood = comps.firstOrNull { CITY_KEYS[fold(it)] == null && it.any { ch -> ch.isLetter() } }
        val label = pretty(hood ?: city ?: return null)
        return Place(fold(label), label, city)
    }

    private fun pretty(s: String): String {
        val small = setOf("de", "da", "do", "das", "dos", "e")
        return s.lowercase().split(Regex("""\s+""")).mapIndexed { i, w ->
            if (i > 0 && w in small) w else w.replaceFirstChar { it.uppercase() }
        }.joinToString(" ")
    }
}

/**
 * Chance de pegar corrida de volta saindo do destino: quantas ofertas você já recebeu COM COLETA naquela região,
 * no mesmo horário (±1 h), nos últimos 30 dias. Conta só o seu histórico, no celular.
 */
object ReturnChance {

    data class Seen(val at: Long, val originKey: String?, val originCity: String?, val green: Boolean)
    data class Chance(val level: Level, val count: Int, val green: Int, val place: RegionReader.Place) {
        val text: String get() = when (level) {
            Level.BOA -> "Destino ${place.label}: boa chance de volta ($count ofertas saindo de lá nesse horário em 30 dias)"
            Level.MEDIA -> "Destino ${place.label}: alguma chance de volta ($count ${if (count == 1) "oferta" else "ofertas"} nesse horário em 30 dias)"
            Level.POUCA -> "Destino ${place.label}: quase nenhuma oferta saindo de lá nesse horário. Pode voltar vazio"
        }
    }
    enum class Level { BOA, MEDIA, POUCA }

    const val MIN_HISTORY = 30
    private const val WINDOW_DAYS = 30

    fun estimate(history: List<Seen>, dest: RegionReader.Place?, now: Long): Chance? {
        if (dest == null) return null
        val since = now - WINDOW_DAYS * 86_400_000L
        val recent = history.filter { it.at >= since && (it.originKey != null || it.originCity != null) }
        if (recent.size < MIN_HISTORY) return null   // ainda aprendendo
        val cityOnly = dest.isCityOnly
        if (cityOnly) {
            // destino só com a cidade, e é a cidade onde você mais pega pedido: não diz nada de útil
            val home = recent.mapNotNull { it.originCity }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            if (home != null && RegionReader.fold(home) == dest.key) return null
        }
        val hour = hourOf(now)
        val matches = recent.filter { s ->
            val sameHour = abs(hourOf(s.at) - hour).let { d -> minOf(d, 24 - d) } <= 1
            val samePlace = if (cityOnly) s.originCity?.let { RegionReader.fold(it) } == dest.key else s.originKey == dest.key
            sameHour && samePlace
        }
        val n = matches.size
        val level = when { n >= 6 -> Level.BOA; n >= 2 -> Level.MEDIA; else -> Level.POUCA }
        return Chance(level, n, matches.count { it.green }, dest)
    }

    private fun hourOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
}
