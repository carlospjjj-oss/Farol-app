package com.farol.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.farol.app.ui.DashboardActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Alertas do servidor (Fase 3) da PRÓPRIA conta: turno esquecido, leitura caindo…
 * Os problemas das outras contas não aparecem no app: vão para o bot do Telegram da administração.
 * Busca junto com a sincronização; um alerta novo vira notificação UMA vez por dia.
 */
object AlertsClient {

    data class Alert(val id: String, val user: Long, val name: String, val type: String, val level: String, val text: String, val action: String)

    private const val CH = "farol_alertas"
    private const val NOTIF_ID = 44

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("farol_alertas", Context.MODE_PRIVATE)

    /** Últimos alertas recebidos (para o painel do app). */
    fun cached(ctx: Context): List<Alert> = parse(prefs(ctx).getString("lista", "[]") ?: "[]")
    fun me(ctx: Context): Long = prefs(ctx).getLong("eu", -1L)

    /** Rede: chamar fora da tela. */
    fun refresh(ctx: Context): Boolean = runCatching {
        val token = Account.token(ctx) ?: return false
        val c = URL(Account.server(ctx) + "/v1/me/alertas").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.setRequestProperty("Authorization", "Bearer $token")
        if (c.responseCode != 200) return false
        val o = JSONObject(c.inputStream.bufferedReader().readText())
        val arr = o.optJSONArray("alertas") ?: JSONArray()
        prefs(ctx).edit().putString("lista", arr.toString()).putLong("eu", o.optLong("eu", -1L)).apply()
        notifyNew(ctx, parse(arr.toString()))
        true
    }.getOrDefault(false)

    private fun parse(json: String): List<Alert> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Alert(it.optString("id"), it.optLong("usuario"), it.optString("nome"), it.optString("tipo"),
                it.optString("nivel"), it.optString("texto"), it.optString("acao"))
        } }
    }.getOrDefault(emptyList())

    private fun notifyNew(ctx: Context, list: List<Alert>) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val p = prefs(ctx)
        val seen = runCatching { JSONObject(p.getString("vistos", "{}")!!) }.getOrDefault(JSONObject())
        val mine = me(ctx)
        // Os da própria conta que o celular já mostra de outro jeito (pausa, ajustes, atualização) não viram notificação
        // Só os da própria conta (os das outras contas vão para o bot do Telegram da administração),
        // e sem o que o celular já mostra de outro jeito (pausa, ajustes, atualização)
        val fresh = list.filter { seen.optString(it.id) != today && it.user == mine && it.type !in setOf("ajustes", "pausado", "sinal", "versao") }
        if (fresh.isEmpty()) return
        fresh.forEach { seen.put(it.id, today) }
        p.edit().putString("vistos", seen.toString()).apply()
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CH, "Alertas do Farol", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "O que precisa de atenção nas contas" })
        // Notificação curta: quem tem aviso; o detalhe fica no app (toque em "Ver")
        val who = fresh.groupBy { if (it.user == mine) "você" else it.name.substringBefore(' ') }
            .map { (n, l) -> "${l.size} ${if (n == "você") (if (l.size == 1) "seu" else "seus") else "do $n"}" }
        val lines = listOf(who.joinToString(" · ") + " — toque para ver")
        val open = PendingIntent.getActivity(ctx, 5, Intent(ctx, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CH) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        val n = b.setSmallIcon(com.farol.app.R.drawable.ic_tile)
            .setContentTitle(if (fresh.size == 1) "Farol: 1 aviso novo" else "Farol: ${fresh.size} avisos novos")
            .setContentText(lines.first())
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }
}
