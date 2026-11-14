package com.farol.app.core

import java.util.Calendar

/**
 * Telas de HISTÓRICO de ganhos dos apps: servem para completar dias que já passaram.
 *
 *  - Keeta › Estatísticas › Diárias: "13 de nov. | Ganhos totais | R$ 25,19" -> total do dia 13.
 *  - 99 › Saldo: lista "Bônus | R$5,00 | 23:57 | Entrega Food | R$10,65 | 23:57 | …" -> cada lançamento
 *    vira uma entrada do dia/turno em que aconteceu (lançamento negativo/dinheiro é ignorado).
 *  - iFood: ainda sem padrão (as telas de histórico são registradas para montar o leitor).
 *
 * Os lançamentos do Saldo da 99 entram numa "família" separada das entregas lidas pela oferta
 * (chave "99$..."): o ganho do app é o maior entre as famílias, nunca a soma (não conta duas vezes).
 */
object HistoryReader {

    data class DayTotal(val app: String, val date: String, val value: Double)
    /** Lançamento do Saldo: [minuteOfDay] = hora:minuto mostrado; [key] identifica sem repetir. */
    data class Entry(val app: String, val label: String, val value: Double, val minuteOfDay: Int, val key: String)

    private val MONTHS = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val DAY_DATE = Regex("""^(\d{1,2})\s+de\s+(jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)[a-zç]*\.?(?:\s+de\s+(\d{4}))?$""", RegexOption.IGNORE_CASE)
    private val MONEY = Regex("""^-?\s*R\s*\$\s*-?\s*([\d.]+,\d{2})\s*>?$""")
    private val TIME = Regex("""^(\d{1,2}):(\d{2})(?:\s*[-–]\s*(.+))?$""")

    fun isHistory(app: String, text: String): Boolean = when (app) {
        "Keeta" -> text.contains("Ganhos totais", true) && text.contains("Estat", true)
        "99" -> Regex("""(^|\|\s*)Saldo\s*\|""").containsMatchIn(text) && text.contains("Transfer", true)
        "iFood" -> Regex("""Extrato|Hist[oó]rico de ganhos|Meus ganhos|Ganhos do dia|Repasse""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        else -> false
    }

    /** Keeta › Estatísticas › Diárias: total do dia mostrado (só a aba diária: data de um dia só). */
    fun keetaDay(text: String, today: String): DayTotal? {
        if (!text.contains("Ganhos totais", true)) return null
        val parts = text.split("|").map { it.trim() }
        val iTotal = parts.indexOfFirst { it.equals("Ganhos totais", true) }
        if (iTotal < 0) return null
        // a data do filtro vem antes de "Ganhos totais" (ex.: "13 de nov.")
        val dateSeg = parts.subList(0, iTotal).lastOrNull { DAY_DATE.matches(it) } ?: return null
        val m = DAY_DATE.find(dateSeg)!!
        val value = parts.drop(iTotal + 1).firstNotNullOfOrNull { s -> MONEY.find(s)?.let { OfferParser.parseMoney(it.groupValues[1]) } } ?: return null
        val day = m.groupValues[1].toInt()
        val month = MONTHS.indexOf(m.groupValues[2].lowercase().take(3)) + 1
        var year = m.groupValues[3].toIntOrNull() ?: today.substring(0, 4).toInt()
        var date = "%04d-%02d-%02d".format(year, month, day)
        if (m.groupValues[3].isEmpty() && date > today) { year -= 1; date = "%04d-%02d-%02d".format(year, month, day) }
        if (day !in 1..31 || month !in 1..12 || value < 0 || value > 5000) return null
        return DayTotal("Keeta", date, value)
    }

    /** 99 › Saldo: lançamentos positivos com hora (os de outros dias aparecem com data e são ignorados). */
    fun ninetyNineSaldo(text: String): List<Entry> {
        val parts = text.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val start = parts.indexOfFirst { it.equals("Saldo", true) }.coerceAtLeast(0)
        val out = mutableListOf<Entry>()
        val seen = mutableMapOf<String, Int>()
        var i = start
        while (i < parts.size - 1) {
            val money = MONEY.find(parts[i])
            val time = TIME.find(parts.getOrNull(i + 1) ?: "")
            if (money != null && time != null) {
                val label = parts.getOrNull(i - 1)?.takeIf { MONEY.find(it) == null && TIME.find(it) == null } ?: "Lançamento"
                val negative = parts[i].contains("-")
                val cash = time.groupValues[3].contains("dinheiro", true)
                val v = OfferParser.parseMoney(money.groupValues[1])
                val h = time.groupValues[1].toInt(); val mi = time.groupValues[2].toInt()
                if (!negative && !cash && v != null && v > 0 && v < 1000 && h < 24 && mi < 60 && !label.startsWith("Transfer", true)) {
                    val base = "${"%02d%02d".format(h, mi)}:${Math.round(v * 100)}:${fold(label)}"
                    val n = (seen[base] ?: 0) + 1; seen[base] = n
                    out += Entry("99", label, v, h * 60 + mi, "99\$saldo:$base:$n")
                }
                i += 2; continue
            }
            i++
        }
        return out
    }

    /**
     * Hora do lançamento -> instante. O Saldo só mostra hora:minuto: se a hora é depois de agora,
     * foi ontem.
     */
    fun instantOf(minuteOfDay: Int, now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60); set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis > now + 5 * 60_000L) c.add(Calendar.DAY_OF_YEAR, -1)
        return c.timeInMillis
    }

    /**
     * Em qual dia do diário o lançamento entra: o turno que cobre a hora (com 60 min de folga no fim,
     * a 99 lança a entrega alguns minutos depois). Sem turno que cubra, não entra (não chuta).
     */
    fun dayFor(at: Long, days: List<DayRecord>, now: Long): DayRecord? =
        days.filter { it.startAt != null }.firstOrNull { d ->
            val end = (d.endAt ?: now) + 60 * 60_000L
            at >= d.startAt!! - 10 * 60_000L && at <= end
        }

    private fun fold(s: String) = ProfitCalculator.fold(s).replace(Regex("""[^a-z0-9]+"""), "")
}
