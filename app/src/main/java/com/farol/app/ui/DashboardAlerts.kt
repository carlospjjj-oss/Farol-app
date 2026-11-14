package com.farol.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.farol.app.core.SettingsSanity
import com.farol.app.data.AlertsClient

/*
 * Avisos: no painel fica UMA linha curta ("⚠ 1 aviso · Ver"); o detalhe abre ao tocar.
 * Só os da própria conta. Os problemas das outras contas (administração do app) vão para o bot do Telegram.
 */

private var lastAlertsFetch = 0L

private data class Aviso(val level: String, val text: String, val action: String?, val fixSettings: Boolean = false)

/** Avisos da própria conta, já sem o que não faz sentido mostrar. */
private fun DashboardActivity.avisos(): List<Aviso> {
    val mine = AlertsClient.me(self)
    val own = mutableListOf<Aviso>()
    val local = SettingsSanity.check(settings.profile, settings.isCar)
    if (local.isNotEmpty()) own += Aviso("problema",
        "Ajustes fora do normal: " + local.joinToString("; ") { "${it.text} (normal: ${it.typical})" },
        "Corrigir em Ajustes", fixSettings = true)
    val installed = com.farol.app.service.Updater.installedName(self)
    AlertsClient.cached(self).forEach { a ->
        if (a.user != mine) return@forEach
        when (a.type) {
            "ajustes", "pausado", "sinal" -> return@forEach            // o próprio celular sabe melhor (e já mostra)
            "versao" -> if (!isOlder(installed, a.text)) return@forEach  // já atualizou: o servidor ainda não soube
        }
        own += Aviso(a.level, a.text, a.action)
    }
    return own
}

/** "Está na 6.5.1; a mais nova é a 6.6." → a instalada aqui é mais velha que a "mais nova"? */
private fun isOlder(installed: String, text: String): Boolean {
    val newest = Regex("""mais nova é a ([\d.]+)""").find(text)?.groupValues?.get(1) ?: return true
    val a = installed.split(".").map { it.toIntOrNull() ?: 0 }; val b = newest.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) { val d = (a.getOrNull(i) ?: 0) - (b.getOrNull(i) ?: 0); if (d != 0) return d < 0 }
    return false
}

internal fun DashboardActivity.alertsCard() {
    val own = avisos()
    val total = own.size
    if (total == 0) return
    val worst = if (own.any { it.level == "problema" }) Palette.RED else Palette.YELLOW
    val who = own.first().text.substringBefore(":").take(40) + if (total > 1) " e mais ${total - 1}" else ""
    val bar = k.hbox().apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12))
        background = k.rounded(Palette.alpha(worst, 0x22), 16)
        addView(k.dot(worst, 10))
        val t = k.text("$total ${if (total == 1) "aviso" else "avisos"} · $who", 14, Palette.TEXT, k.medium)
        addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
        addView(k.text("Ver", 14, worst, k.bold))
        isClickable = true
        contentDescription = "Ver avisos"
        setOnClickListener { showAvisos() }
    }
    add(bar, 10)
}

private fun DashboardActivity.showAvisos() {
    val own = avisos()
    val box = k.vbox().apply { setPadding(k.dp(20), k.dp(8), k.dp(20), k.dp(8)) }
    fun item(a: Aviso) {
        val c = if (a.level == "problema") Palette.RED else Palette.YELLOW
        box.addView(k.text("● ${a.text}", 14, Palette.TEXT, k.medium).apply {
            setLineSpacing(0f, 1.15f)
            val sp = android.text.SpannableString(text); sp.setSpan(android.text.style.ForegroundColorSpan(c), 0, 1, 0); text = sp
        }, k.lp(top = 10))
        if (!a.action.isNullOrBlank()) box.addView(k.text(a.action, 12, Palette.MUTED).apply { setPadding(k.dp(16), 0, 0, 0) }, k.lp(top = 2))
    }
    own.forEach(::item)
    val b = AlertDialog.Builder(this, Palette.dialogTheme)
        .setTitle("Avisos")
        .setView(ScrollView(this).apply { addView(box) })
        .setNegativeButton("Fechar", null)
    if (own.any { it.fixSettings }) b.setPositiveButton("Corrigir ajustes") { _, _ -> startActivity(Intent(this, SettingsActivity::class.java)) }
    b.show()
}

/** Busca os alertas no servidor de vez em quando (no máximo a cada 10 min) e redesenha. */
internal fun DashboardActivity.refreshAlertsSoon() {
    val now = System.currentTimeMillis()
    if (now - lastAlertsFetch < 10 * 60_000) return
    lastAlertsFetch = now
    Thread { if (AlertsClient.refresh(applicationContext)) runOnUiThread { if (!isFinishing) render() } }.start()
}
