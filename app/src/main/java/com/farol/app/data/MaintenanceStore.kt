package com.farol.app.data

import com.farol.app.core.MaintItem
import com.farol.app.core.Maintenance
import org.json.JSONArray
import org.json.JSONObject

/** Itens de manutenção guardados nos ajustes de cada modo (moto e carro separados). */
object MaintenanceStore {
    fun load(settings: Settings): List<MaintItem> = fromJson(settings.maintenanceJson, if (settings.isCar) "carro" else "moto")
    fun save(settings: Settings, items: List<MaintItem>) { settings.maintenanceJson = toJson(items) }

    fun toJson(items: List<MaintItem>): String = JSONArray().apply {
        items.forEach { i ->
            put(JSONObject().put("id", i.id).put("nome", i.name)
                .apply { i.intervalKm?.let { put("intervalo", it) }; i.lastKm?.let { put("ultimo_km", it) }; i.lastAt?.let { put("em", it) } })
        }
    }.toString()

    fun fromJson(json: String, mode: String): List<MaintItem> {
        if (json.isBlank()) return Maintenance.defaults(mode)
        return runCatching {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { n ->
                a.optJSONObject(n)?.let { o ->
                    MaintItem(o.getString("id"), o.optString("nome", o.getString("id")),
                        o.optDouble("intervalo").takeIf { !it.isNaN() },
                        o.optDouble("ultimo_km").takeIf { !it.isNaN() },
                        if (o.has("em")) o.optLong("em") else null)
                }
            }
        }.getOrElse { Maintenance.defaults(mode) }
    }
}
