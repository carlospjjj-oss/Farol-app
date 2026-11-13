package com.farol.app.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.farol.app.R
import com.farol.app.core.Fmt
import com.farol.app.data.JournalStore
import com.farol.app.data.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget da tela inicial: ganhos do dia, meta e estado do turno. Toque abre o Farol. */
class FarolWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, views(ctx)) }
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, FarolWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { mgr.updateAppWidget(it, views(ctx)) }
        }

        private fun views(ctx: Context): RemoteViews {
            val day = JournalStore(ctx).day()
            val s = Settings(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_farol)
            v.setTextViewText(R.id.w_earn, Fmt.money(day.totalEarnings))
            val goal = s.dailyGoal
            v.setTextViewText(R.id.w_goal, if (goal > 0) {
                val left = goal - day.totalEarnings
                if (left <= 0) "🎯 Meta batida!" else "🎯 faltam ${Fmt.money(left)}"
            } else "ganhos de hoje")
            val hhmm = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
            v.setTextViewText(R.id.w_shift, when {
                day.kmStart == null -> "▶ Toque para iniciar o turno"
                day.kmEnd == null && day.isPaused -> "⏸ Turno pausado"
                day.kmEnd == null -> "● Em turno desde ${day.startAt?.let { hhmm.format(Date(it)) } ?: "—"}"
                else -> "✓ Turno encerrado"
            })
            v.setTextViewText(R.id.w_status, if (s.enabled) "🚦 Farol ligado" else "⏸ Farol pausado")
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, DashboardActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.w_root, open)
            return v
        }
    }
}
