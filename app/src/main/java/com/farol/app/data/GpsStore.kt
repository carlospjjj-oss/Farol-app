package com.farol.app.data

import android.content.Context
import com.farol.app.core.GpsMath
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Odômetro do GPS" de cada dia: metros rodados, tempo em movimento e pontos (hora, km acumulado)
 * a cada ~1 minuto, para saber quantos km foram rodados em cada pedido.
 * Fica em memória e é gravado em gps.json no máximo a cada 60 s.
 */
object GpsStore {

    data class Day(val meters: Double, val movingSec: Double, val points: List<Pair<Long, Double>>) {
        val km get() = meters / 1000.0
        val movingKmh: Double? get() = if (movingSec > 120) km / (movingSec / 3600.0) else null
    }

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var date = ""
    private var meters = 0.0
    private var moving = 0.0
    private val points = mutableListOf<Pair<Long, Double>>()
    private var lastPointAt = 0L
    private var lastSaveAt = 0L

    private fun file(ctx: Context) = File(ctx.filesDir, "gps.json")

    @Synchronized
    private fun ensureDay(ctx: Context) {
        val today = dayFmt.format(Date())
        if (today == date) return
        if (date.isNotEmpty()) save(ctx)
        date = today
        val d = read(ctx, today)
        meters = d.meters; moving = d.movingSec
        points.clear(); points.addAll(d.points)
    }

    /** Soma um trecho já filtrado ([GpsMath.step]). */
    @Synchronized
    fun add(ctx: Context, stepM: Double, seconds: Double) {
        ensureDay(ctx)
        if (stepM > 0) { meters += stepM; moving += seconds }
        val now = System.currentTimeMillis()
        if (now - lastPointAt >= 60_000 || points.isEmpty()) {
            points += now to meters / 1000.0
            lastPointAt = now
        }
        if (now - lastSaveAt >= 60_000) save(ctx)
    }

    @Synchronized
    fun save(ctx: Context) {
        if (date.isEmpty()) return
        lastSaveAt = System.currentTimeMillis()
        val root = runCatching { JSONObject(file(ctx).readText()) }.getOrNull() ?: JSONObject()
        val pts = JSONArray()
        points.forEach { pts.put(JSONArray().put(it.first).put(it.second)) }
        root.put(date, JSONObject().put("m", meters).put("mv", moving).put("p", pts))
        // guarda só os últimos 60 dias
        val keys = root.keys().asSequence().toList().sorted()
        keys.dropLast(60).forEach { root.remove(it) }
        file(ctx).writeText(root.toString())
    }

    @Synchronized
    fun today(ctx: Context): Day { ensureDay(ctx); return Day(meters, moving, points.toList()) }

    fun read(ctx: Context, day: String): Day {
        if (day == date) return Day(meters, moving, points.toList())
        val o = runCatching { JSONObject(file(ctx).readText()).optJSONObject(day) }.getOrNull() ?: return Day(0.0, 0.0, emptyList())
        val a = o.optJSONArray("p") ?: JSONArray()
        return Day(o.optDouble("m", 0.0), o.optDouble("mv", 0.0),
            (0 until a.length()).map { val p = a.getJSONArray(it); p.getLong(0) to p.getDouble(1) })
    }

    /** Km rodados entre dois horários de hoje (null se o GPS não estava medindo). */
    fun kmBetween(ctx: Context, from: Long, to: Long): Double? = GpsMath.kmBetween(today(ctx).points, from, to)
}
