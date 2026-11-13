package com.farol.app.data

import android.content.Context
import com.farol.app.core.DayRecord
import com.farol.app.core.DeliveryRecord
import com.farol.app.core.FuelEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Diário (turnos, ganhos por app, abastecimentos) salvo em journal.json, só no aparelho. */
class JournalStore(context: Context) {

    private val ctx = context.applicationContext
    /** journal.json (moto) ou journal_carro.json (carro). */
    private val file: File get() = File(ctx.filesDir, "journal${Settings(ctx).fileSuffix}.json")
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun dateOf(ms: Long): String = dayFmt.format(Date(ms))
    fun today(): String = dateOf(System.currentTimeMillis())

    /** Datas dos últimos [n] dias, incluindo hoje. */
    fun lastDays(n: Int): Set<String> {
        val c = Calendar.getInstance()
        return (0 until n).map { i ->
            if (i > 0) c.add(Calendar.DAY_OF_YEAR, -1)
            dayFmt.format(c.time)
        }.toSet()
    }

    /** Datas do mês atual até hoje. */
    fun thisMonth(): Set<String> {
        val c = Calendar.getInstance()
        val n = c.get(Calendar.DAY_OF_MONTH)
        return lastDays(n)
    }

    // ---------- leitura/gravação ----------

    @Synchronized
    private fun load(): JSONObject =
        runCatching { JSONObject(file.readText()) }.getOrNull() ?: JSONObject()

    @Synchronized
    private fun save(o: JSONObject) {
        val tmp = File(file.parentFile, "journal${Settings(ctx).fileSuffix}.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    fun days(): List<DayRecord> {
        val d = load().optJSONObject("days") ?: return emptyList()
        return d.keys().asSequence().map { k -> dayFromJson(k, d.getJSONObject(k)) }.sortedBy { it.date }.toList()
    }

    fun day(date: String = today()): DayRecord =
        load().optJSONObject("days")?.optJSONObject(date)?.let { dayFromJson(date, it) } ?: DayRecord(date)

    @Synchronized
    fun putDay(r: DayRecord) {
        val root = load()
        val d = root.optJSONObject("days") ?: JSONObject().also { root.put("days", it) }
        d.put(r.date, dayToJson(r))
        save(root)
    }

    fun fuel(): List<FuelEntry> {
        val a = load().optJSONArray("fuel") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            FuelEntry(it.getLong("at"), it.getDouble("odo"), it.getDouble("liters"), it.getDouble("total"), it.optBoolean("full", true))
        }
    }

    @Synchronized
    fun addFuel(f: FuelEntry) {
        val root = load()
        val a = root.optJSONArray("fuel") ?: JSONArray().also { root.put("fuel", it) }
        a.put(JSONObject().put("at", f.at).put("odo", f.odometer).put("liters", f.liters)
            .put("total", f.total).put("full", f.fullTank))
        save(root)
    }

    @Synchronized
    fun removeLastFuel() {
        val root = load()
        val a = root.optJSONArray("fuel") ?: return
        if (a.length() > 0) a.remove(a.length() - 1)
        save(root)
    }

    fun deliveries(): List<DeliveryRecord> {
        val a = load().optJSONArray("deliveries") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            DeliveryRecord(it.getString("app"), it.optString("rest").ifEmpty { null }, it.getLong("c"),
                it.optLong("d").takeIf { v -> v > 0 }, it.optLong("e").takeIf { v -> v > 0 })
        }
    }

    @Synchronized
    fun addDelivery(r: DeliveryRecord) {
        val root = load()
        val a = root.optJSONArray("deliveries") ?: JSONArray().also { root.put("deliveries", it) }
        a.put(JSONObject().put("app", r.app).put("rest", r.restaurant ?: "").put("c", r.collectAt)
            .put("d", r.deliverAt ?: 0L).put("e", r.endAt ?: 0L))
        // guarda só os últimos 600 pedidos
        if (a.length() > 600) { val keep = JSONArray(); for (i in a.length() - 600 until a.length()) keep.put(a.get(i)); root.put("deliveries", keep) }
        save(root)
    }

    /** CSV dos dias (separador ; e vírgula decimal) para planilha. */
    fun daysCsv(costPerKm: Double): String {
        fun n(v: Double?) = v?.let { String.format(Locale("pt", "BR"), "%.2f", it) } ?: ""
        val apps = days().flatMap { it.earnings.keys }.distinct().sorted()
        val header = (listOf("data", "km_inicial", "km_final", "km", "horas") + apps.map { "ganho_$it" } +
            listOf("ganho_total", "custo", "liquido")).joinToString(";")
        val rows = days().map { d ->
            val km = d.kmDriven
            (listOf(d.date, n(d.kmStart), n(d.kmEnd), n(km), n(d.hours)) + apps.map { n(d.earnings[it]) } +
                listOf(n(d.totalEarnings), n(km?.times(costPerKm)), n(km?.let { d.totalEarnings - it * costPerKm })))
                .joinToString(";")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    // ---------- json ----------

    private fun dayFromJson(date: String, o: JSONObject): DayRecord {
        val e = o.optJSONObject("earnings")
        return DayRecord(
            date = date,
            kmStart = o.optDoubleOrNull("kmStart"),
            kmEnd = o.optDoubleOrNull("kmEnd"),
            startAt = o.optLongOrNull("startAt"),
            endAt = o.optLongOrNull("endAt"),
            earnings = e?.keys()?.asSequence()?.associateWith { e.getDouble(it) } ?: emptyMap(),
            pausedMs = o.optLong("pausedMs", 0L),
            pauseAt = o.optLongOrNull("pauseAt"),
        )
    }

    private fun dayToJson(r: DayRecord) = JSONObject().apply {
        r.kmStart?.let { put("kmStart", it) }
        r.kmEnd?.let { put("kmEnd", it) }
        r.startAt?.let { put("startAt", it) }
        r.endAt?.let { put("endAt", it) }
        if (r.pausedMs > 0) put("pausedMs", r.pausedMs)
        r.pauseAt?.let { put("pauseAt", it) }
        put("earnings", JSONObject().apply { r.earnings.forEach { (k, v) -> put(k, v) } })
    }

    private fun JSONObject.optDoubleOrNull(k: String) = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null
    private fun JSONObject.optLongOrNull(k: String) = if (has(k) && !isNull(k)) optLong(k) else null
}
