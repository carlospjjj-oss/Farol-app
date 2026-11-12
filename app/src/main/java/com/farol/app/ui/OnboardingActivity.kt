package com.farol.app.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.farol.app.core.CostProfile
import com.farol.app.data.Settings
import com.farol.app.service.OfferReaderService

/**
 * Primeira abertura guiada: cada passo com o status (✓ ou pendente) e um botão que leva direto à tela certa.
 * Também abre por Ajustes → "Guia de instalação".
 */
class OnboardingActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var k: Kit
    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        k = Kit(this)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        col = k.vbox().apply { setPadding(k.dp(18), k.dp(16), k.dp(18), k.dp(40)) }
        val root = k.vbox().apply {
            setBackgroundColor(Palette.BG)
            addView(ScrollView(this@OnboardingActivity).apply { isVerticalScrollBarEnabled = false; addView(col) },
                LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
        k.fitSystemBars(root)
    }

    override fun onResume() { super.onResume(); render() }

    private fun accessibilityOn(): Boolean {
        val enabled = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, OfferReaderService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun batteryOk(): Boolean = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun notificationsOk(): Boolean = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun step(n: Int, title: String, desc: String, done: Boolean?, action: String? = null, onAction: (() -> Unit)? = null) {
        val c = k.card()
        val badgeColor = when (done) { true -> Palette.GREEN; false -> Palette.ACCENT; null -> Palette.MUTED }
        c.addView(k.hbox().apply {
            addView(k.text(if (done == true) "✓" else "$n", 15, if (done == true) 0xFFFFFFFF.toInt() else Palette.ON_ACCENT, k.bold).apply {
                gravity = Gravity.CENTER
                background = k.rounded(badgeColor, 999)
            }, LinearLayout.LayoutParams(k.dp(32), k.dp(32)))
            addView(k.vbox().apply {
                addView(k.text(title, 16, Palette.TEXT, k.bold))
                addView(k.text(desc, 13, Palette.MUTED).apply { setLineSpacing(0f, 1.15f) }, k.lp(top = 4))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(12) })
        })
        if (action != null && onAction != null && done != true) c.addView(k.pill(action, onClick = onAction), k.lp(top = 12))
        col.addView(c, k.lp(top = 12))
    }

    private fun render() {
        col.removeAllViews()
        col.addView(k.text("🚦", 40).apply { gravity = Gravity.CENTER })
        col.addView(k.text("Vamos deixar o Farol pronto", 24, Palette.TEXT, k.bold).apply { gravity = Gravity.CENTER }, k.lp(top = 6))
        col.addView(k.text("Siga os passos. Os que já estão certos ficam com ✓.", 14, Palette.MUTED).apply { gravity = Gravity.CENTER }, k.lp(top = 4))

        val acc = accessibilityOn()
        step(1, "Instalado ✓", "Para atualizar no futuro: desligue o Play Protect só durante a instalação (Play Store → perfil → Play Protect → ⚙️) e ligue de novo.", true)
        step(2, "Liberar configurações restritas", "Android 13+: em Apps → Farol → ⋮ → Permitir configurações restritas. Só aparece depois de tentar ligar a acessibilidade uma vez.",
            if (acc) true else null, "Abrir informações do app") {
            startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Toque nos ⋮ no canto → Permitir configurações restritas", Toast.LENGTH_LONG).show()
        }
        step(3, "Ligar o leitor de ofertas", "Acessibilidade → Apps instalados → Farol – leitor de ofertas → ligar.", acc, "Abrir Acessibilidade") {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        step(4, "Bateria sem restrição", "Sem isso o Android desliga o leitor no meio do dia. Na Samsung, confira também 'Apps em suspensão'.", batteryOk(), "Liberar bateria") {
            runCatching {
                startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }.onFailure { startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        }
        step(5, "Notificações", "Para o resumo do turno na barra e o resumo da semana.", notificationsOk(), "Permitir notificações") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        val p = settings.profile
        val motoOk = p.kmPerLiter >= 20
        step(6, "Seus custos e faixas", "Comece pelo atalho de moto (consumo 30 km/L, outros R$ 0,15/km, verde R$ 2,00/km, amarelo R$ 1,40/km) e ajuste depois.",
            if (motoOk) true else null, "Usar valores de moto") {
            settings.profile = p.copy(kmPerLiter = 30.0, otherCostPerKm = 0.15, greenPerKm = 2.0, yellowPerKm = 1.4,
                greenPerHour = 30.0, yellowPerHour = 20.0, avgSpeedKmh = 25.0)
            Toast.makeText(this, "Valores de moto aplicados", Toast.LENGTH_SHORT).show()
            render()
        }
        step(7, "Envio de logs (opcional)", "Para eu analisar as leituras e ajustar o Farol: Ajustes → Envio de logs → GitHub.",
            if (settings.remoteMode != "off") true else null, "Abrir Ajustes") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        col.addView(k.pill("Tudo certo, começar") {
            settings.onboarded = true
            finish()
        }, k.lp(top = 18))
        col.addView(k.text("Dá para abrir este guia de novo em Ajustes → Guia de instalação.", 12, Palette.MUTED).apply {
            gravity = Gravity.CENTER
        }, k.lp(top = 10))
        @Suppress("UNUSED_VARIABLE") val unused: View? = null
        @Suppress("UNUSED_VARIABLE") val unused2: CostProfile? = null
    }
}
