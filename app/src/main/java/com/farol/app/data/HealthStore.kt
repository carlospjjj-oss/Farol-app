package com.farol.app.data

import android.content.Context
import com.farol.app.core.ReaderHealth
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Contadores da saúde do leitor por dia e app (guardados no aparelho; só números vão ao servidor). */
class HealthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("farol_saude", Context.MODE_PRIVATE)
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dedup = ReaderHealth.MissDedup()

    private fun today() = dayFmt.format(Date())

    @Synchronized private fun bump(app: String, field: String) {
        val d = today()
        val o = runCatching { JSONObject(prefs.getString(d, "{}")!!) }.getOrElse { JSONObject() }
        val a = o.optJSONObject(app) ?: JSONObject().also { o.put(app, it) }
        a.put(field, a.optInt(field) + 1)
        prefs.edit().putString(d, o.toString()).putBoolean("changed", true).apply()
        // guarda só os últimos 14 dias
        prefs.all.keys.filter { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }.sorted().dropLast(14)
            .forEach { prefs.edit().remove(it).apply() }
    }

    /** Oferta nova lida com sucesso. */
    fun ok(app: String) = bump(app, "ok")

    /** Tela com cara de oferta que o leitor não entendeu (a mesma tela conta uma vez a cada 20 s). */
    fun miss(app: String, text: String) { if (dedup.isNew(app, text, System.currentTimeMillis())) bump(app, "falhas") }

    fun day(date: String = today()): JSONObject = runCatching { JSONObject(prefs.getString(date, "{}")!!) }.getOrElse { JSONObject() }

    /** Mudou desde o último envio? (consome a marca) */
    fun takeChanged(): Boolean { val c = prefs.getBoolean("changed", false); if (c) prefs.edit().putBoolean("changed", false).apply(); return c }
}
