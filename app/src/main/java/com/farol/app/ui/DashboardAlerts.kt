package com.farol.app.ui

import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import com.farol.app.core.SettingsSanity
import com.farol.app.data.AlertsClient

/* "Precisa de atenção": ajustes fora do normal (na hora, no próprio celular) + alertas do servidor. */

private var lastAlertsFetch = 0L

internal fun DashboardActivity.alertsCard() {
    val local = SettingsSanity.check(settings.profile, settings.isCar)
    val mine = AlertsClient.me(self)
    // Do servidor: o que é da própria conta e o app já sabe melhor (ajustes, pausa) sai; o resto fica.
    val remote = AlertsClient.cached(self).filter { !(it.user == mine && it.type in setOf("ajustes", "pausado")) }
    if (local.isEmpty() && remote.isEmpty()) return
    val card = k.card()
    card.addView(k.overline("Precisa de atenção"))
    fun line(level: String, text: String, action: String?) {
        card.addView(k.hbox().apply {
            gravity = Gravity.TOP
            addView(k.dot(if (level == "problema") Palette.RED else Palette.YELLOW, 10), k.lp(w = -2, top = 6))
            addView(k.vbox().apply {
                addView(k.text(text, 14, Palette.TEXT, k.medium).apply { setLineSpacing(0f, 1.15f) })
                if (!action.isNullOrBlank()) addView(k.text("O que fazer: $action", 12, Palette.MUTED), k.lp(top = 3))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
        }, k.lp(top = 12))
    }
    if (local.isNotEmpty()) {
        line("problema", "Ajustes fora do normal: " + local.joinToString("; ") { "${it.text} (normal: ${it.typical})" } +
            ". Assim o card pode mostrar oferta ruim como boa.", null)
        card.addView(k.pill("Corrigir ajustes") { startActivity(Intent(self, SettingsActivity::class.java)) }, k.lp(top = 12))
    }
    remote.forEach { a -> line(a.level, (if (a.user != mine) "${a.name}: " else "") + a.text, a.action) }
    add(card)
}

/** Busca os alertas no servidor de vez em quando (no máximo a cada 10 min) e redesenha. */
internal fun DashboardActivity.refreshAlertsSoon() {
    val now = System.currentTimeMillis()
    if (now - lastAlertsFetch < 10 * 60_000) return
    lastAlertsFetch = now
    Thread { if (AlertsClient.refresh(applicationContext)) runOnUiThread { if (!isFinishing) render() } }.start()
}
