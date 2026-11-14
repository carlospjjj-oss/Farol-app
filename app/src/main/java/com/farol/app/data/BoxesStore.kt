package com.farol.app.data

import com.farol.app.core.Boxes
import org.json.JSONArray
import org.json.JSONObject

/** Metas das caixinhas e movimentos da caixinha de emergência (nos ajustes de cada modo). */
object BoxesStore {
    fun goals(s: Settings): Map<String, Double> = runCatching {
        val o = JSONObject(s.boxGoalsJson.ifBlank { "{}" })
        o.keys().asSequence().associateWith { o.getDouble(it) }.filterValues { it > 0 }
    }.getOrDefault(emptyMap())

    fun setGoal(s: Settings, key: String, v: Double?) {
        val o = runCatching { JSONObject(s.boxGoalsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
        if (v == null || v <= 0) o.remove(key) else o.put(key, v)
        s.boxGoalsJson = o.toString()
    }

    fun moves(s: Settings): List<Boxes.Move> = runCatching {
        val a = JSONArray(s.emergencyJson.ifBlank { "[]" })
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Boxes.Move(it.getLong("em"), it.getDouble("v"), it.optString("nota")) } }
    }.getOrDefault(emptyList())

    fun addMove(s: Settings, m: Boxes.Move) {
        val a = runCatching { JSONArray(s.emergencyJson.ifBlank { "[]" }) }.getOrDefault(JSONArray())
        a.put(JSONObject().put("em", m.at).put("v", m.value).put("nota", m.note))
        s.emergencyJson = a.toString()
    }
}
