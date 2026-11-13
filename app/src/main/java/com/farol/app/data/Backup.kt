package com.farol.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup completo num único JSON: diário (turnos, ganhos, abastecimentos, pedidos),
 * histórico de ofertas e todas as configurações (sem senhas nem tokens: a conta fica no cofre do Android).
 */
object Backup {

    private const val VERSION = 1

    fun export(ctx: Context): String {
        fun journalOf(suffix: String) = runCatching { JournalStore.exportJson(ctx, suffix) }.getOrNull()
        fun historyOf(suffix: String) = runCatching { History.exportLines(ctx, suffix) }.getOrElse { emptyList() }
        val journal = journalOf("")
        val history = historyOf("")
        val prefs = prefsJson(Settings(ctx).prefs)
        val car = JSONObject()
            .put("journal", journalOf("_carro") ?: JSONObject())
            .put("history", JSONArray(historyOf("_carro")))
            .put("prefs", prefsJson(ctx.getSharedPreferences("farol_carro", Context.MODE_PRIVATE)))
        return JSONObject()
            .put("app", "farol").put("version", VERSION).put("created", System.currentTimeMillis())
            .put("journal", journal ?: JSONObject())
            .put("history", JSONArray(history))
            .put("prefs", prefs)
            .put("carro", car)
            .toString()
    }

    private fun prefsJson(sp: android.content.SharedPreferences): JSONObject {
        val prefs = JSONObject()
        sp.all.forEach { (k, v) ->
            when (v) {
                is Boolean -> prefs.put(k, JSONObject().put("t", "b").put("v", v))
                is Int -> prefs.put(k, JSONObject().put("t", "i").put("v", v))
                is Long -> prefs.put(k, JSONObject().put("t", "l").put("v", v))
                is Float -> prefs.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                is String -> prefs.put(k, JSONObject().put("t", "s").put("v", v))
            }
        }
        return prefs
    }

    /** @return resumo do que foi restaurado. Lança exceção se o arquivo não for um backup do Farol. */
    fun restore(ctx: Context, json: String): String {
        val o = JSONObject(json)
        require(o.optString("app") == "farol") { "Este arquivo não é um backup do Farol" }
        val journal = o.optJSONObject("journal") ?: JSONObject()
        JournalStore.importJson(ctx, "", journal)
        val h = o.optJSONArray("history") ?: JSONArray()
        History.importLines(ctx, "", (0 until h.length()).map { h.getString(it) })
        restorePrefs(Settings(ctx).prefs, o.optJSONObject("prefs") ?: JSONObject())
        o.optJSONObject("carro")?.let { c ->
            JournalStore.importJson(ctx, "_carro", c.optJSONObject("journal") ?: JSONObject())
            val ch = c.optJSONArray("history") ?: JSONArray()
            History.importLines(ctx, "_carro", (0 until ch.length()).map { ch.getString(it) })
            restorePrefs(ctx.getSharedPreferences("farol_carro", Context.MODE_PRIVATE), c.optJSONObject("prefs") ?: JSONObject())
        }
        val days = journal.optJSONObject("days")?.length() ?: 0
        val fuel = journal.optJSONArray("fuel")?.length() ?: 0
        return "$days dias, $fuel abastecimentos e ${h.length()} ofertas restaurados" +
            if (o.has("carro")) " (moto e carro)" else ""
    }

    private fun restorePrefs(sp: android.content.SharedPreferences, p: JSONObject) {
        val e = sp.edit()
        p.keys().forEach { k ->
            val x = p.getJSONObject(k)
            when (x.optString("t")) {
                "b" -> e.putBoolean(k, x.getBoolean("v"))
                "i" -> e.putInt(k, x.getInt("v"))
                "l" -> e.putLong(k, x.getLong("v"))
                "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                "s" -> e.putString(k, x.getString("v"))
            }
        }
        e.apply()
    }
}
