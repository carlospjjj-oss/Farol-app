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
import com.farol.app.core.DeviceGuide
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
        Themes.apply(this)
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

    private fun step(n: Int, title: String, desc: String, done: Boolean?, action: String? = null, path: String? = null, tip: String? = null, onAction: (() -> Unit)? = null) {
        val c = k.card()
        val badgeColor = when (done) { true -> Palette.GREEN; false -> Palette.ACCENT; null -> Palette.MUTED }
        c.addView(k.hbox().apply {
            addView(k.text(if (done == true) "✓" else "$n", 15, if (done == true) 0xFFFFFFFF.toInt() else Palette.ON_ACCENT, k.bold).apply {
                gravity = Gravity.CENTER
                background = k.rounded(badgeColor, 999)
            }, LinearLayout.LayoutParams(k.dp(32), k.dp(32)))
            addView(k.vbox().apply {
                addView(k.text(title, 16, Palette.TEXT, k.bold))
                if (desc.isNotEmpty()) addView(k.text(desc, 13, Palette.MUTED).apply { setLineSpacing(0f, 1.15f) }, k.lp(top = 4))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(12) })
        })
        // Caminho exato no seu celular (tela por tela)
        if (path != null && done != true) {
            c.addView(k.text(path, 14, Palette.TEXT, k.medium).apply {
                setLineSpacing(0f, 1.2f)
                background = k.rounded(Palette.SURFACE2, 12)
                setPadding(k.dp(12), k.dp(10), k.dp(12), k.dp(10))
                setTextIsSelectable(true)
            }, k.lp(top = 10))
        }
        if (tip != null && done != true) c.addView(k.text("💡 $tip", 12, Palette.MUTED).apply { setLineSpacing(0f, 1.15f) }, k.lp(top = 8))
        if (action != null && onAction != null && done != true) c.addView(k.pill(action, onClick = onAction), k.lp(top = 12))
        col.addView(c, k.lp(top = 12))
    }

    private fun brand(): DeviceGuide.Brand =
        settings.prefs.getString("deviceBrand", null)?.let { n -> DeviceGuide.Brand.values().firstOrNull { it.name == n } }
            ?: DeviceGuide.detect(Build.MANUFACTURER, Build.BRAND)

    private fun openAppDetails() = runCatching {
        startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openAutostart() {
        val tries = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
        )
        for (c in tries) if (runCatching { startActivity(Intent().setComponent(c)) }.isSuccess) return
        openAppDetails()
    }

    private fun render() {
        col.removeAllViews()
        col.addView(k.text("🚦", 40).apply { gravity = Gravity.CENTER })
        col.addView(k.text("Vamos deixar o Farol pronto", 24, Palette.TEXT, k.bold).apply { gravity = Gravity.CENTER }, k.lp(top = 6))
        col.addView(k.text("Siga os passos. Os que já estão certos ficam com ✓.", 14, Palette.MUTED).apply { gravity = Gravity.CENTER }, k.lp(top = 4))

        // Marca do celular: cada uma esconde as telas num lugar diferente
        val b = brand()
        col.addView(k.text("Seu celular: ${b.label} · Android ${Build.VERSION.RELEASE}", 13, Palette.TEXT, k.medium).apply { gravity = Gravity.CENTER }, k.lp(top = 14))
        col.addView(k.hbox().apply {
            gravity = Gravity.CENTER
            DeviceGuide.Brand.values().forEach { opt ->
                val on = opt == b
                addView(k.chip(if (opt == DeviceGuide.Brand.OTHER) "Outro" else opt.label.substringBefore(" /"), if (on) Palette.ACCENT else Palette.MUTED).apply {
                    isSingleLine = true
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    isClickable = true
                    contentDescription = "Mostrar o guia para ${opt.label}"
                    setOnClickListener { settings.prefs.edit().putString("deviceBrand", opt.name).apply(); render() }
                }, k.lp(w = -2).apply { marginStart = k.dp(3); marginEnd = k.dp(3) })
            }
        }, k.lp(top = 8))

        val acc = accessibilityOn()
        var n = 0
        for (g in DeviceGuide.steps(b, Build.VERSION.SDK_INT)) {
            n++
            when (g.step) {
                DeviceGuide.Step.INSTALL -> step(n, "Instalado ✓", "Para as próximas atualizações, deixe assim:", true, path = g.path, tip = g.tip).also {
                    // mostra o caminho mesmo com ✓ (vale para atualizar)
                    (col.getChildAt(col.childCount - 1) as LinearLayout).apply {
                        addView(k.text(g.path, 13, Palette.TEXT, k.medium).apply {
                            background = k.rounded(Palette.SURFACE2, 12); setPadding(k.dp(12), k.dp(10), k.dp(12), k.dp(10))
                        }, k.lp(top = 10))
                        g.tip?.let { addView(k.text("💡 $it", 12, Palette.MUTED), k.lp(top = 8)) }
                    }
                }
                DeviceGuide.Step.RESTRICTED -> step(n, g.title, "", if (acc) true else null, "Abrir informações do Farol", g.path, g.tip) {
                    openAppDetails()
                    Toast.makeText(this, "Toque nos ⋮ no canto de cima › Permitir configurações restritas", Toast.LENGTH_LONG).show()
                }
                DeviceGuide.Step.ACCESSIBILITY -> step(n, g.title, "", acc, "Abrir Acessibilidade", g.path, g.tip) {
                    startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                DeviceGuide.Step.BATTERY -> step(n, g.title, "", batteryOk(), "Liberar bateria", g.path, g.tip) {
                    runCatching {
                        startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                    }.onFailure { openAppDetails() }
                }
                DeviceGuide.Step.AUTOSTART -> step(n, g.title, "", null, "Abrir Início automático", g.path, g.tip) { openAutostart() }
                DeviceGuide.Step.RECENTS -> step(n, g.title, "", null, null, g.path, g.tip)
                DeviceGuide.Step.NOTIFICATIONS -> step(n, g.title, "", notificationsOk(), "Permitir notificações", g.path, g.tip) {
                    if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
                }
            }
        }

        // Farol ligado (não pausado)
        n++
        step(n, "Farol ligado", "Pausado, o Farol não lê nada. Iniciar um turno também religa.", if (settings.enabled) true else false, "Ligar o Farol") {
            com.farol.app.service.PauseControl.set(this, paused = false, origem = "guia"); render()
        }

        // Custos de acordo com o modo (moto ou carro)
        n++
        val p = settings.profile
        if (settings.isCar) {
            val ok = p.kmPerLiter in 5.0..16.0 && p.fuelPrice > 0 && p.otherCostPerKm >= 0.15
            step(n, "Seus custos e faixas (carro)", "Ponto de partida para carro popular flex no etanol: 7 km/L, outros R\$ 0,30/km, verde R\$ 1,90/km e R\$ 45/h, amarelo R\$ 1,40/km e R\$ 30/h. Confira o preço do combustível em Ajustes.",
                if (ok) true else null, "Usar valores de carro") {
                settings.profile = p.copy(kmPerLiter = 7.0, otherCostPerKm = 0.30, greenPerKm = 1.9, yellowPerKm = 1.4,
                    greenPerHour = 45.0, yellowPerHour = 30.0)
                Toast.makeText(this, "Valores de carro aplicados. Confira o preço do combustível.", Toast.LENGTH_LONG).show(); render()
            }
        } else {
            val ok = p.kmPerLiter >= 20 && p.fuelPrice > 0
            step(n, "Seus custos e faixas (moto)", "Ponto de partida: 30 km/L, outros R\$ 0,15/km, verde R\$ 2,00/km, amarelo R\$ 1,40/km. Ajuste depois.",
                if (ok) true else null, "Usar valores de moto") {
                settings.profile = p.copy(kmPerLiter = 30.0, otherCostPerKm = 0.15, greenPerKm = 2.0, yellowPerKm = 1.4,
                    greenPerHour = 30.0, yellowPerHour = 20.0, avgSpeedKmh = 25.0)
                Toast.makeText(this, "Valores de moto aplicados", Toast.LENGTH_SHORT).show(); render()
            }
        }
        n++
        step(n, "Compartilhar telas (opcional)", "Ajuda a ajustar a leitura dos apps ao seu celular. Nomes e endereços de clientes são mascarados; conversas nunca saem do celular. Ajustes › Privacidade.",
            if (com.farol.app.data.Account.shareScreens(this)) true else null, "Abrir Ajustes") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        col.addView(k.pill("Tudo certo, começar") {
            settings.onboarded = true
            finish()
        }, k.lp(top = 18))
        col.addView(k.text("Dá para abrir este guia de novo em Ajustes › Guia de instalação.", 12, Palette.MUTED).apply {
            gravity = Gravity.CENTER
        }, k.lp(top = 10))
        @Suppress("UNUSED_VARIABLE") val unused: View? = null
        @Suppress("UNUSED_VARIABLE") val unused2: CostProfile? = null
    }
}
