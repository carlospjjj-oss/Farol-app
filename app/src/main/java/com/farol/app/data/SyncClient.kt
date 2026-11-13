package com.farol.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sincroniza o diário (turnos, ganhos, abastecimentos) dos dois modos com a conta no servidor.
 * Manda só o que mudou; recebe o que mudou em outro aparelho. Vale o mais novo.
 * Roda pelas tarefas de fundo (FarolJobs) e logo depois do login.
 */
object SyncClient {
    private val MODES = listOf("" to "moto", "_carro" to "carro")

    /** @return true se deu tudo certo. */
    @Synchronized
    fun syncAll(ctx: Context): Boolean {
        val token = Account.token(ctx) ?: return false
        val user = Account.user(ctx) ?: return false
        var ok = true
        for ((suffix, modo) in MODES) ok = runCatching { syncMode(ctx, token, user.id, suffix, modo) }.getOrElse { false } && ok
        Settings(ctx).prefs.edit().putLong("lastSyncAt", if (ok) System.currentTimeMillis() else 0L).apply()
        return ok
    }

    private fun syncMode(ctx: Context, token: String, userId: Long, suffix: String, modo: String): Boolean {
        val store = JournalStore(ctx, suffix)
        // Outra conta neste celular: começa do zero com ela e não manda os dados de quem usava antes.
        // Primeira sincronização deste celular: tudo que já está aqui sobe para a conta.
        // Outra conta entrou depois: começa do zero com ela e NÃO manda os dados de quem usava antes.
        val owner = store.meta("sync_user")
        if (owner != userId.toString()) {
            if (owner != null) store.markAllSynced()
            store.setMeta("sync_cursor", "0")
            store.setMeta("sync_user", userId.toString())
        }
        val since = store.meta("sync_cursor")?.toLongOrNull() ?: 0L
        val pending = store.pendingSync()
        val costPerKm = runCatching { Settings(ctx, modo).profile.costPerKm }.getOrNull()

        val body = JSONObject().put("modo", modo).put("since", since)
            .put("days", JSONArray().apply {
                pending.days.forEach { d ->
                    costPerKm?.let { d.data.put("_custo_km", it) }   // o painel calcula o líquido com o custo do modo
                    put(JSONObject().put("date", d.date).put("data", d.data).put("updated_at", d.updatedAt))
                }
            })
            .put("fuel", JSONArray().apply {
                pending.fuel.forEach { f ->
                    put(JSONObject().put("at", f.at).put("data", f.data).put("deleted", f.deleted).put("updated_at", f.updatedAt))
                }
            })

        val res = post(ctx, Account.server(ctx) + "/v1/sync", body, token)
        if (pending.maxUpdated > 0) store.markSynced(pending.maxUpdated)

        val days = res.optJSONArray("days") ?: JSONArray()
        val fuel = res.optJSONArray("fuel") ?: JSONArray()
        store.applyRemote(
            (0 until days.length()).map { i -> days.getJSONObject(i).let { Triple(it.getString("date"), it.getJSONObject("data").apply { remove("_custo_km") }, it.getLong("updated_at")) } },
            (0 until fuel.length()).map { i -> fuel.getJSONObject(i).let { Triple(it.getLong("at"), it.getJSONObject("data"), it.optBoolean("deleted") to it.getLong("updated_at")) } },
        )
        store.setMeta("sync_cursor", res.optLong("cursor", since).toString())
        return true
    }

    private fun post(ctx: Context, url: String, body: JSONObject, token: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 15_000; c.readTimeout = 30_000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("User-Agent", "Farol-app")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code == 401) { Account.clear(ctx); error("sessão expirada: entre de novo") }
            if (code !in 200..299) error("sync HTTP $code ${text.take(120)}")
            return JSONObject(text)
        } finally { c.disconnect() }
    }
}
