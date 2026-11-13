package com.farol.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.farol.app.core.Fmt
import com.farol.app.core.HourInsights
import com.farol.app.core.JournalMath
import com.farol.app.data.History
import com.farol.app.data.JournalStore
import com.farol.app.data.Settings
import com.farol.app.ui.DashboardActivity
import com.farol.app.ui.FarolWidget
import java.util.Calendar

/**
 * Notificações do Farol:
 *  - fixa, durante o turno: "⏱ 3h12 · 💰 R$ 86,40 · 12 ofertas (🟢 4)" com botão Pausar/Retomar
 *  - resumo da semana, domingo a partir das 20h (uma vez por semana)
 */
object ShiftNotifier {

    private const val CH_SHIFT = "farol_turno"
    private const val CH_WEEK = "farol_semana"
    const val ID_SHIFT = 41
    private const val ID_WEEK = 42

    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        nm(ctx).createNotificationChannel(NotificationChannel(CH_SHIFT, "Turno", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Resumo do turno em andamento"; setShowBadge(false)
        })
        nm(ctx).createNotificationChannel(NotificationChannel(CH_WEEK, "Resumo da semana", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context) = PendingIntent.getActivity(ctx, 0,
        Intent(ctx, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun builder(ctx: Context, ch: String) =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, ch) else @Suppress("DEPRECATION") Notification.Builder(ctx)

    /** Notificação usada pelo GPS (serviço em primeiro plano precisa de uma). */
    fun buildForeground(ctx: Context): Notification { channels(ctx); return build(ctx) }

    /** Atualiza (ou remove) a notificação do turno e o widget. Barato: pode chamar a cada oferta. */
    fun refresh(ctx: Context) {
        runCatching { FarolWidget.updateAll(ctx) }
        val settings = Settings(ctx)
        val day = JournalStore(ctx).day()
        val active = day.kmStart != null && day.kmEnd == null
        val gps = GpsService.running
        if (!gps && (!settings.shiftNotification || !active || !canNotify(ctx))) { nm(ctx).cancel(ID_SHIFT); return }
        channels(ctx)
        runCatching { nm(ctx).notify(ID_SHIFT, build(ctx)) }
    }

    private fun build(ctx: Context): Notification {
        val settings = Settings(ctx)
        val day = JournalStore(ctx).day()

        val elapsed = day.workedMs()?.let { Fmt.min(it / 60_000.0) + if (day.isPaused) " (pausado)" else "" } ?: "—"
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        val offers = History(ctx).recent(1000).filter { it.optLong("t") >= start }
        val greens = offers.count { it.optString("rating") == "GREEN" }
        val goal = settings.dailyGoal
        val title = "⏱ $elapsed  ·  💰 ${Fmt.money(day.totalEarnings)}" +
            if (goal > 0) "  ·  🎯 ${((day.totalEarnings / goal) * 100).toInt().coerceAtMost(999)}%" else ""
        val gpsKm = if (GpsService.running) runCatching { com.farol.app.data.GpsStore.today(ctx).km }.getOrNull() else null
        val text = "${offers.size} ofertas hoje (🟢 $greens)" +
            (gpsKm?.let { "  ·  📍 ${Fmt.km(it)}" } ?: "") +
            (if (settings.enabled) "" else "  ·  ⏸ Farol pausado")

        val toggle = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, PauseReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return builder(ctx, CH_SHIFT)
            .setSmallIcon(com.farol.app.R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .addAction(Notification.Action.Builder(null, if (settings.enabled) "⏸ Pausar Farol" else "▶ Retomar Farol", toggle).build())
            .build()
    }

    /** Domingo a partir das 20h: resumo da semana (uma vez por semana). */
    fun maybeWeekly(ctx: Context) {
        val settings = Settings(ctx)
        if (!settings.weeklySummary || !canNotify(ctx)) return
        val c = Calendar.getInstance()
        if (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || c.get(Calendar.HOUR_OF_DAY) < 20) return
        val key = "${c.get(Calendar.YEAR)}-${c.get(Calendar.WEEK_OF_YEAR)}"
        if (settings.lastWeeklySent == key) return

        val store = JournalStore(ctx)
        val p = JournalMath.period(store.days(), store.fuel(), store.lastDays(7), settings.profile.costPerKm, store::dateOf)
        if (p.days == 0) return
        val bestDay = store.days().filter { it.date in store.lastDays(7) }.maxByOrNull { it.totalEarnings }
        val since = System.currentTimeMillis() - 7L * 24 * 3_600_000
        val cal = Calendar.getInstance()
        val offers = History(ctx).recent(5000).filter { it.optLong("t") >= since }.map {
            cal.timeInMillis = it.optLong("t"); Triple(cal.get(Calendar.HOUR_OF_DAY), it.optDouble("perKm"), it.optString("rating") == "GREEN")
        }.filter { !it.second.isNaN() }
        val bestHour = HourInsights.best(HourInsights.byHour(offers), top = 1).firstOrNull()

        val body = buildString {
            append("Ganhos ${Fmt.money(p.earnings)} em ${p.days} dias · líquido ${Fmt.money(p.net)}")
            if (p.km > 0) append("\n${Fmt.km(p.km)} · ${Fmt.money(p.earnings / p.km)}/km")
            bestDay?.let { append("\nMelhor dia: ${it.date.substring(8)}/${it.date.substring(5, 7)} com ${Fmt.money(it.totalEarnings)}") }
            bestHour?.let { append("\nMelhor horário: ${it.hour}h–${it.hour + 1}h (${Fmt.money(it.avgPerKm)}/km)") }
        }
        channels(ctx)
        val n = builder(ctx, CH_WEEK)
            .setSmallIcon(com.farol.app.R.drawable.ic_tile)
            .setContentTitle("🚦 Sua semana no Farol")
            .setContentText(body.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        runCatching { nm(ctx).notify(ID_WEEK, n) }
        settings.lastWeeklySent = key
    }
}

/** Botão "Pausar/Retomar" da notificação. */
class PauseReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val s = Settings(ctx)
        s.enabled = !s.enabled
        ShiftNotifier.refresh(ctx)
    }
}
