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

/** Histórico das ofertas vistas, no banco do modo atual (FarolDb). Fica só no aparelho. */
class History(context: Context) {
    private val ctx = context.applicationContext
    private val db: FarolDb get() = FarolDb.get(ctx)

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
            .put("t", now)
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
        db.insertOffer(db.writableDatabase, o)
        return true
    }

    /** Remove as repetições já gravadas (mesma oferta em menos de 3 min). @return quantas saíram. */
    @Synchronized
    fun removeDuplicates(): Int {
        val rows = mutableListOf<Pair<Long, JSONObject?>>()
        db.readableDatabase.rawQuery("SELECT id, data FROM offers ORDER BY t, id", null).use { c ->
            while (c.moveToNext()) rows += c.getLong(0) to runCatching { JSONObject(c.getString(1)) }.getOrNull()
        }
        val flags = com.farol.app.core.OfferDedup.keepFlags(rows.map { (id, o) ->
            if (o == null) 0L to "x$id"
            else o.optLong("t") to com.farol.app.core.OfferDedup.key(o.optString("app"), o.optDouble("price"), o.optDouble("km"))
        })
        val drop = rows.filterIndexed { i, _ -> !flags[i] }.map { it.first }
        if (drop.isNotEmpty()) {
            val w = db.writableDatabase
            w.beginTransaction()
            try { drop.forEach { w.delete("offers", "id = ?", arrayOf(it.toString())) }; w.setTransactionSuccessful() } finally { w.endTransaction() }
        }
        return drop.size
    }

    /** As [n] mais recentes, da mais nova para a mais antiga. */
    fun recent(n: Int): List<JSONObject> = query("SELECT data FROM offers ORDER BY t DESC, id DESC LIMIT ?", arrayOf(n.toString()))

    /** Ofertas a partir de [sinceMs], da mais nova para a mais antiga. */
    fun since(sinceMs: Long): List<JSONObject> = query("SELECT data FROM offers WHERE t >= ? ORDER BY t DESC, id DESC", arrayOf(sinceMs.toString()))

    fun count(): Int = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM offers", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun query(sql: String, args: Array<String>): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        db.readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) runCatching { JSONObject(c.getString(0)) }.getOrNull()?.let { out += it }
        }
        return out
    }

    fun todaySummary(): String {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val today = since(start)
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
        val rows = query("SELECT data FROM offers ORDER BY t, id", emptyArray()).map { o ->
            listOf(df.format(Date(o.optLong("t"))), o.optString("app"), n(o.optDouble("price")),
                n(o.optDouble("km")), n(o.optDouble("min")), n(o.optDouble("perKm")),
                n(o.optDouble("perHour")), n(o.optDouble("net")), o.optString("rating")).joinToString(";")
        }
        return (listOf("data;app;preco;km;min;r_km;r_h;liquido;cor") + rows).joinToString("\n")
    }

    @Synchronized
    fun clear() { db.writableDatabase.delete("offers", null, null) }

    // ---------- backup ----------
    companion object {
        fun exportLines(ctx: Context, suffix: String): List<String> {
            val out = ArrayList<String>()
            FarolDb.of(ctx, suffix).readableDatabase.rawQuery("SELECT data FROM offers ORDER BY t, id", null).use { c ->
                while (c.moveToNext()) out += c.getString(0)
            }
            return out
        }

        fun importLines(ctx: Context, suffix: String, lines: List<String>) {
            val db = FarolDb.of(ctx, suffix); val w = db.writableDatabase
            w.beginTransaction()
            try {
                w.delete("offers", null, null)
                lines.forEach { l -> runCatching { JSONObject(l) }.getOrNull()?.let { db.insertOffer(w, it) } }
                w.setTransactionSuccessful()
            } finally { w.endTransaction() }
        }
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
