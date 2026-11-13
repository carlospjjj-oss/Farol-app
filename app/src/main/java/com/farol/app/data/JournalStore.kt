package com.farol.app.data

import android.content.Context
import com.farol.app.core.DayRecord
import com.farol.app.core.DeliveryRecord
import com.farol.app.core.FuelEntry
import org.json.JSONArray
import org.json.JSONObject
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Diário (turnos, ganhos por app, abastecimentos, pedidos) no banco do modo atual (FarolDb).
 * Cada mudança marca updated_at: é assim que a sincronização com a conta sabe o que mandar.
 */
class JournalStore(context: Context, private val suffix: String? = null) {

    private val ctx = context.applicationContext
    /** Banco do modo atual, ou de um modo específico ("" moto, "_carro" carro) para a sincronização. */
    private val db: FarolDb get() = suffix?.let { FarolDb.of(ctx, it) } ?: FarolDb.get(ctx)
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

    // ---------- dias ----------

    fun days(): List<DayRecord> {
        val out = ArrayList<DayRecord>()
        db.readableDatabase.rawQuery("SELECT date, data FROM days ORDER BY date", null).use { c ->
            while (c.moveToNext()) runCatching { out += dayFromJson(c.getString(0), JSONObject(c.getString(1))) }
        }
        return out
    }

    fun day(date: String = today()): DayRecord =
        db.readableDatabase.rawQuery("SELECT data FROM days WHERE date = ?", arrayOf(date)).use { c ->
            if (c.moveToFirst()) runCatching { dayFromJson(date, JSONObject(c.getString(0))) }.getOrNull() else null
        } ?: DayRecord(date)

    @Synchronized
    fun putDay(r: DayRecord) {
        db.writableDatabase.insertWithOnConflict("days", null, ContentValues().apply {
            put("date", r.date); put("data", dayToJson(r).toString()); put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- abastecimentos ----------

    fun fuel(): List<FuelEntry> {
        val out = ArrayList<FuelEntry>()
        db.readableDatabase.rawQuery("SELECT data FROM fuel WHERE deleted = 0 ORDER BY at", null).use { c ->
            while (c.moveToNext()) runCatching { out += fuelFromJson(JSONObject(c.getString(0))) }
        }
        return out
    }

    @Synchronized
    fun addFuel(f: FuelEntry) {
        db.writableDatabase.insertWithOnConflict("fuel", null, ContentValues().apply {
            put("at", f.at); put("data", fuelToJson(f).toString()); put("updated_at", System.currentTimeMillis()); put("deleted", 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Apaga o último abastecimento (marca como apagado, para a sincronização apagar também na conta). */
    @Synchronized
    fun removeLastFuel() {
        val w = db.writableDatabase
        val at = w.rawQuery("SELECT at FROM fuel WHERE deleted = 0 ORDER BY at DESC LIMIT 1", null).use { if (it.moveToFirst()) it.getLong(0) else null } ?: return
        w.update("fuel", ContentValues().apply { put("deleted", 1); put("updated_at", System.currentTimeMillis()) }, "at = ?", arrayOf(at.toString()))
    }

    // ---------- pedidos ----------

    fun deliveries(): List<DeliveryRecord> {
        val out = ArrayList<DeliveryRecord>()
        db.readableDatabase.rawQuery("SELECT data FROM deliveries ORDER BY collect_at", null).use { c ->
            while (c.moveToNext()) runCatching {
                val it = JSONObject(c.getString(0))
                out += DeliveryRecord(it.getString("app"), it.optString("rest").ifEmpty { null }, it.getLong("c"),
                    it.optLong("d").takeIf { v -> v > 0 }, it.optLong("e").takeIf { v -> v > 0 })
            }
        }
        return out
    }

    @Synchronized
    fun addDelivery(r: DeliveryRecord) {
        val w = db.writableDatabase
        w.insert("deliveries", null, ContentValues().apply {
            put("collect_at", r.collectAt)
            put("data", JSONObject().put("app", r.app).put("rest", r.restaurant ?: "").put("c", r.collectAt)
                .put("d", r.deliverAt ?: 0L).put("e", r.endAt ?: 0L).toString())
        })
        // guarda só os últimos 2.000 pedidos
        w.execSQL("DELETE FROM deliveries WHERE id NOT IN (SELECT id FROM deliveries ORDER BY collect_at DESC LIMIT 2000)")
    }

    // ---------- sincronização com a conta ----------

    data class PendingDay(val date: String, val data: JSONObject, val updatedAt: Long)
    data class PendingFuel(val at: Long, val data: JSONObject, val deleted: Boolean, val updatedAt: Long)
    data class Pending(val days: List<PendingDay>, val fuel: List<PendingFuel>, val maxUpdated: Long)

    /** O que mudou desde a última sincronização. */
    fun pendingSync(): Pending {
        val r = db.readableDatabase
        var max = 0L
        val d = ArrayList<PendingDay>()
        r.rawQuery("SELECT date, data, updated_at FROM days WHERE updated_at > synced_at", null).use { c ->
            while (c.moveToNext()) { d += PendingDay(c.getString(0), JSONObject(c.getString(1)), c.getLong(2)); max = maxOf(max, c.getLong(2)) }
        }
        val f = ArrayList<PendingFuel>()
        r.rawQuery("SELECT at, data, deleted, updated_at FROM fuel WHERE updated_at > synced_at", null).use { c ->
            while (c.moveToNext()) { f += PendingFuel(c.getLong(0), JSONObject(c.getString(1)), c.getInt(2) == 1, c.getLong(3)); max = maxOf(max, c.getLong(3)) }
        }
        return Pending(d, f, max)
    }

    fun meta(k: String) = db.meta(k)
    fun setMeta(k: String, v: String) = db.setMeta(k, v)

    /** Outra conta entrou neste celular: o que já está aqui não vai para ela (fica só local). */
    fun markAllSynced() {
        val w = db.writableDatabase
        w.execSQL("UPDATE days SET synced_at = updated_at")
        w.execSQL("UPDATE fuel SET synced_at = updated_at")
    }

    fun markSynced(upTo: Long) {
        val w = db.writableDatabase
        w.execSQL("UPDATE days SET synced_at = updated_at WHERE updated_at <= ?", arrayOf(upTo))
        w.execSQL("UPDATE fuel SET synced_at = updated_at WHERE updated_at <= ?", arrayOf(upTo))
    }

    /** Dados vindos da conta (celular novo ou outro aparelho). Só entra o que for mais novo que o local. */
    @Synchronized
    fun applyRemote(days: List<Triple<String, JSONObject, Long>>, fuel: List<Triple<Long, JSONObject, Pair<Boolean, Long>>>) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            for ((date, data, upd) in days) {
                val local = w.rawQuery("SELECT updated_at FROM days WHERE date = ?", arrayOf(date)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
                if (upd > local) w.insertWithOnConflict("days", null, ContentValues().apply {
                    put("date", date); put("data", data.toString()); put("updated_at", upd); put("synced_at", upd)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            for ((at, data, meta) in fuel) {
                val (deleted, upd) = meta
                val local = w.rawQuery("SELECT updated_at FROM fuel WHERE at = ?", arrayOf(at.toString())).use { if (it.moveToFirst()) it.getLong(0) else -1L }
                if (upd > local) w.insertWithOnConflict("fuel", null, ContentValues().apply {
                    put("at", at); put("data", data.toString()); put("updated_at", upd); put("synced_at", upd); put("deleted", if (deleted) 1 else 0)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            w.setTransactionSuccessful()
        } finally { w.endTransaction() }
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

    private fun fuelFromJson(it: JSONObject) =
        FuelEntry(it.getLong("at"), it.getDouble("odo"), it.getDouble("liters"), it.getDouble("total"), it.optBoolean("full", true))

    private fun fuelToJson(f: FuelEntry) = JSONObject().put("at", f.at).put("odo", f.odometer).put("liters", f.liters)
        .put("total", f.total).put("full", f.fullTank)


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

    companion object {
        /** Exporta no formato antigo {days, fuel, deliveries} (backup). */
        fun exportJson(ctx: Context, suffix: String): JSONObject {
            val r = FarolDb.of(ctx, suffix).readableDatabase
            val days = JSONObject()
            r.rawQuery("SELECT date, data FROM days", null).use { c -> while (c.moveToNext()) days.put(c.getString(0), JSONObject(c.getString(1))) }
            val fuel = JSONArray()
            r.rawQuery("SELECT data FROM fuel WHERE deleted = 0 ORDER BY at", null).use { c -> while (c.moveToNext()) fuel.put(JSONObject(c.getString(0))) }
            val del = JSONArray()
            r.rawQuery("SELECT data FROM deliveries ORDER BY collect_at", null).use { c -> while (c.moveToNext()) del.put(JSONObject(c.getString(0))) }
            return JSONObject().put("days", days).put("fuel", fuel).put("deliveries", del)
        }

        /** Restaura um backup (substitui o diário daquele modo). */
        fun importJson(ctx: Context, suffix: String, o: JSONObject) {
            val db = FarolDb.of(ctx, suffix); val w = db.writableDatabase
            w.beginTransaction()
            try {
                w.delete("days", null, null); w.delete("fuel", null, null); w.delete("deliveries", null, null)
                db.importJournal(w, o, System.currentTimeMillis())
                w.setTransactionSuccessful()
            } finally { w.endTransaction() }
        }
    }
}
