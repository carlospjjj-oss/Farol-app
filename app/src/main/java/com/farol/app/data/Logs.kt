package com.farol.app.data

import android.content.Context
import com.farol.app.core.Fmt
import com.farol.app.core.OfferResult
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Histórico das ofertas vistas, uma por linha (JSON). Fica só no aparelho. */
class History(context: Context) {
    private val file = File(context.filesDir, "history.jsonl")
    private val maxLines = 5000

    private val recentKeys = mutableMapOf<String, Long>()

    /** @return false se a mesma oferta já foi gravada nos últimos 3 minutos. */
    @Synchronized
    fun add(r: OfferResult, source: String = ""): Boolean {
        val now = System.currentTimeMillis()
        val key = com.farol.app.core.OfferDedup.key(r.offer.app, r.offer.price, r.offer.totalKm)
        recentKeys[key]?.let { if (now - it <= com.farol.app.core.OfferDedup.WINDOW_MS) { recentKeys[key] = now; return false } }
        recentKeys[key] = now
        recentKeys.entries.removeAll { now - it.value > com.farol.app.core.OfferDedup.WINDOW_MS }
        val o = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("app", r.offer.app)
            .put("price", r.offer.price)
            .put("km", r.offer.totalKm)
            .put("min", r.minutes)
            .put("estimated", r.timeEstimated)
            .put("perKm", r.perKm)
            .put("perHour", r.perHour)
            .put("cost", r.cost)
            .put("net", r.net)
            .put("rating", r.rating.name)
            .put("ratingKm", r.ratingKm.name)
            .put("ratingHour", r.ratingHour.name)
            .put("source", source)
        r.offer.pickupKm?.let { o.put("pickup", it) }
        r.offer.passengerRating?.let { o.put("stars", it) }
        if (r.warnings.isNotEmpty()) o.put("warnings", org.json.JSONArray(r.warnings))
        file.appendText(o.toString() + "\n")
        trimIfNeeded()
        return true
    }

    /** Remove do arquivo as repetições já gravadas (mesma oferta em menos de 3 min). @return quantas saíram. */
    @Synchronized
    fun removeDuplicates(): Int {
        if (!file.exists()) return 0
        val lines = file.readLines().filter { it.isNotBlank() }
        val objs = lines.map { runCatching { JSONObject(it) }.getOrNull() }
        val flags = com.farol.app.core.OfferDedup.keepFlags(objs.map { o ->
            if (o == null) 0L to "x${System.nanoTime()}"
            else o.optLong("t") to com.farol.app.core.OfferDedup.key(o.optString("app"), o.optDouble("price"), o.optDouble("km"))
        })
        val kept = lines.filterIndexed { i, _ -> flags[i] }
        if (kept.size != lines.size) file.writeText(kept.joinToString("") { it + "\n" })
        return lines.size - kept.size
    }

    @Synchronized
    fun recent(n: Int): List<JSONObject> {
        if (!file.exists()) return emptyList()
        return file.readLines().takeLast(n).reversed().mapNotNull {
            runCatching { JSONObject(it) }.getOrNull()
        }
    }

    fun todaySummary(): String {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val today = recent(maxLines).filter { it.optLong("t") >= start }
        if (today.isEmpty()) return "Nenhuma oferta lida hoje."
        val avgKm = today.map { it.optDouble("perKm") }.average()
        val greens = today.count { it.optString("rating") == "GREEN" }
        val yellows = today.count { it.optString("rating") == "YELLOW" }
        val reds = today.size - greens - yellows
        return "Hoje: ${today.size} ofertas • média ${Fmt.money(avgKm)}/km\n" +
            "🟢 $greens   🟡 $yellows   🔴 $reds"
    }

    fun format(o: JSONObject): String {
        val time = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(o.optLong("t")))
        val dot = when (o.optString("rating")) { "GREEN" -> "🟢"; "YELLOW" -> "🟡"; else -> "🔴" }
        return "$dot $time ${o.optString("app")}  ${Fmt.money(o.optDouble("price"))}  " +
            "${Fmt.km(o.optDouble("km"))}  ${Fmt.money(o.optDouble("perKm"))}/km  " +
            "líq ${Fmt.money(o.optDouble("net"))}"
    }

    /** CSV para colar em planilha (separador ; e vírgula decimal, padrão BR). */
    fun toCsv(): String {
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        fun n(v: Double) = String.format(Locale("pt", "BR"), "%.2f", v)
        val rows = recent(maxLines).reversed().map { o ->
            listOf(df.format(Date(o.optLong("t"))), o.optString("app"), n(o.optDouble("price")),
                n(o.optDouble("km")), n(o.optDouble("min")), n(o.optDouble("perKm")),
                n(o.optDouble("perHour")), n(o.optDouble("net")), o.optString("rating")).joinToString(";")
        }
        return (listOf("data;app;preco;km;min;r_km;r_h;liquido;cor") + rows).joinToString("\n")
    }

    @Synchronized
    fun clear() { file.delete() }

    private fun trimIfNeeded() {
        val lines = file.readLines()
        if (lines.size > maxLines * 1.2) file.writeText(lines.takeLast(maxLines).joinToString("\n", postfix = "\n"))
    }
}

/**
 * "Modo captura": grava o texto bruto que o Farol enxerga em cada app.
 * Serve para calibrar o leitor: se uma oferta não for reconhecida, o texto dela está aqui
 * e vira um novo caso em OfferParserTest.
 */
class CaptureLog(context: Context) {
    private val file = File(context.filesDir, "capture.txt")
    private val maxBytes = 300_000
    private var lastHash = 0

    @Synchronized
    fun append(pkg: String, source: String, text: String) {
        val h = (pkg + text).hashCode()
        if (h == lastHash) return
        lastHash = h
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        file.appendText("=== $time $pkg [$source]\n$text\n\n")
        if (file.length() > maxBytes) {
            val txt = file.readText()
            file.writeText(txt.takeLast(maxBytes / 2))
        }
    }

    @Synchronized
    fun read(): String = if (file.exists()) file.readText() else ""

    @Synchronized
    fun clear() { file.delete(); lastHash = 0 }
}
