package com.farol.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.farol.app.data.RemoteLog
import com.farol.app.data.Settings
import com.farol.app.ui.DashboardActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pausa do Farol num lugar só. Pausar em qualquer canto (botão do painel, notificação do turno,
 * atalho da barra, Ajustes) passa por aqui, e a pausa nunca fica escondida:
 *  - uma notificação fixa "Farol pausado" com o botão Retomar fica na barra enquanto durar;
 *  - se um app de corrida/entrega abrir com o Farol pausado, essa notificação toca de novo
 *    (no máximo a cada [REMIND_MS]);
 *  - iniciar um turno retoma sozinho;
 *  - cada mudança vai para o servidor (ajustes), para dar para conferir de longe.
 */
object PauseControl {

    private const val CH = "farol_pausa"
    const val ID = 43
    private const val REMIND_MS = 10 * 60_000L
    @Volatile private var lastRemindAt = 0L

    fun isPaused(ctx: Context) = !Settings(ctx).enabled

    fun toggle(ctx: Context, origem: String) = set(ctx, paused = !isPaused(ctx), origem = origem)

    fun set(ctx: Context, paused: Boolean, origem: String) {
        val s = Settings(ctx)
        if (s.enabled == !paused) { show(ctx); return }
        s.enabled = !paused
        s.prefs.edit().putLong("pausedAt", if (paused) System.currentTimeMillis() else 0L).apply()
        lastRemindAt = 0L
        show(ctx)
        runCatching { ShiftNotifier.refresh(ctx) }
        runCatching { TileService.requestListeningState(ctx, ComponentName(ctx, FarolTileService::class.java)) }
        runCatching { RemoteLog.get(ctx).settingsChanged(if (paused) "pausa:$origem" else "retomar:$origem") }
    }

    /** Mostra ou tira a notificação "Farol pausado" conforme o estado atual. Chamar na abertura do app também. */
    fun show(ctx: Context, alert: Boolean = false) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!isPaused(ctx)) { nm.cancel(ID); return }
        if (!canNotify(ctx)) return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Farol pausado", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisa enquanto a leitura de ofertas está pausada"; setShowBadge(true)
            })
        }
        val since = Settings(ctx).prefs.getLong("pausedAt", 0L).takeIf { it > 0 }
            ?.let { " desde " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: ""
        val resume = PendingIntent.getBroadcast(ctx, 2, Intent(ctx, PauseReceiver::class.java).putExtra("retomar", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(ctx, 3,
            Intent(ctx, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CH) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        val n = b.setSmallIcon(com.farol.app.R.drawable.ic_tile)
            .setContentTitle("⏸ Farol pausado$since")
            .setContentText(if (alert) "Um app de corrida está aberto e as ofertas NÃO estão sendo lidas." else "As ofertas não estão sendo lidas.")
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "▶ Retomar", resume).build())
            .build()
        runCatching { nm.notify(ID, n) }
    }

    /** Chamado pelo leitor quando um app monitorado aparece com o Farol pausado. */
    fun remindIfDue(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRemindAt < REMIND_MS) return
        lastRemindAt = now
        show(ctx, alert = true)
    }

    private fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
