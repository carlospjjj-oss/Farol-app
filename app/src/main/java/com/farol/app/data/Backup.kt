package com.farol.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Backup completo num único JSON: diário (turnos, ganhos, abastecimentos, pedidos),
 * histórico de ofertas e todas as configurações (inclusive o token do GitHub).
 */
object Backup {

    private const val VERSION = 1

    fun export(ctx: Context): String {
        val dir = ctx.filesDir
        val journal = File(dir, "journal.json").takeIf { it.exists() }?.readText()?.let { runCatching { JSONObject(it) }.getOrNull() }
        val history = File(dir, "history.jsonl").takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } ?: emptyList()
        val prefs = JSONObject()
        Settings(ctx).prefs.all.forEach { (k, v) ->
            when (v) {
                is Boolean -> prefs.put(k, JSONObject().put("t", "b").put("v", v))
                is Int -> prefs.put(k, JSONObject().put("t", "i").put("v", v))
                is Long -> prefs.put(k, JSONObject().put("t", "l").put("v", v))
                is Float -> prefs.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                is String -> prefs.put(k, JSONObject().put("t", "s").put("v", v))
            }
        }
        return JSONObject()
            .put("app", "farol").put("version", VERSION).put("created", System.currentTimeMillis())
            .put("journal", journal ?: JSONObject())
            .put("history", JSONArray(history))
            .put("prefs", prefs)
            .toString()
    }

    /** @return resumo do que foi restaurado. Lança exceção se o arquivo não for um backup do Farol. */
    fun restore(ctx: Context, json: String): String {
        val o = JSONObject(json)
        require(o.optString("app") == "farol") { "Este arquivo não é um backup do Farol" }
        val dir = ctx.filesDir
        val journal = o.optJSONObject("journal") ?: JSONObject()
        File(dir, "journal.json").writeText(journal.toString())
        val h = o.optJSONArray("history") ?: JSONArray()
        File(dir, "history.jsonl").writeText((0 until h.length()).joinToString("") { h.getString(it) + "\n" })
        val p = o.optJSONObject("prefs") ?: JSONObject()
        val e = Settings(ctx).prefs.edit()
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
        val days = journal.optJSONObject("days")?.length() ?: 0
        val fuel = journal.optJSONArray("fuel")?.length() ?: 0
        return "$days dias, $fuel abastecimentos e ${h.length()} ofertas restaurados"
    }
}
