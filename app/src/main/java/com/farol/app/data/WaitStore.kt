package com.farol.app.data

import android.content.Context
import com.farol.app.core.WaitStats
import org.json.JSONArray
import org.json.JSONObject

/**
 * Espera média por restaurante, guardada SÓ no celular (o nome do restaurante é dado de terceiro:
 * não vai para o servidor nem para o backup na nuvem).
 */
class WaitStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("farol_espera", Context.MODE_PRIVATE)
    private var cache: WaitStats? = null

    @Synchronized fun stats(): WaitStats = cache ?: load().also { cache = it }

    @Synchronized fun add(restaurant: String, waitMin: Double) {
        val s = stats(); s.add(restaurant, waitMin)
        val o = JSONObject(); s.samples.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        prefs.edit().putString("dados", o.toString()).apply()
    }

    fun estimate(restaurant: String?) = stats().estimate(restaurant)

    fun clear() { prefs.edit().clear().apply(); cache = null }

    private fun load(): WaitStats {
        val o = runCatching { JSONObject(prefs.getString("dados", "{}")!!) }.getOrNull() ?: return WaitStats()
        val m = mutableMapOf<String, MutableList<Double>>()
        o.keys().forEach { k -> o.optJSONArray(k)?.let { a -> m[k] = (0 until a.length()).map { a.getDouble(it) }.toMutableList() } }
        return WaitStats(m)
    }
}
