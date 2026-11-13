package com.farol.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import com.farol.app.data.RemoteLog
import com.farol.app.data.Settings
import com.farol.app.service.GpsService

/**
 * Tela de abertura: 🛵 Moto ou 🚗 Carro.
 * Cada modo tem custos, faixas, apps, turnos, ganhos, abastecimentos e histórico separados.
 */
class ModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val k = Kit(this)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG

        val current = settings.vehicleMode
        fun option(mode: String, emoji: String, title: String, sub: String) = k.vbox(20, 26).apply {
            gravity = Gravity.CENTER
            val on = mode == current
            background = k.ripple(k.rounded(if (on) Palette.alpha(Palette.ACCENT, 0x26) else Palette.SURFACE, 26,
                if (on) Palette.ACCENT else Palette.STROKE).apply { if (on) setStroke(k.dp(2), Palette.ACCENT) })
            addView(k.text(emoji, 64).apply { gravity = Gravity.CENTER })
            addView(k.text(title, 24, Palette.TEXT, k.bold).apply { gravity = Gravity.CENTER }, k.lp(top = 8))
            addView(k.text(sub, 13, Palette.MUTED).apply { gravity = Gravity.CENTER }, k.lp(top = 6))
            if (on) addView(k.chip("último usado", Palette.ACCENT), LinearLayout.LayoutParams(-2, -2).apply { topMargin = k.dp(10) })
            isClickable = true
            setOnClickListener { choose(settings, mode) }
        }

        val root = k.vbox(22, 0).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Palette.BG)
            addView(k.text("🚦 Farol", 30, Palette.TEXT, k.black).apply { gravity = Gravity.CENTER })
            addView(k.text("O que você vai rodar hoje?", 15, Palette.MUTED).apply { gravity = Gravity.CENTER }, k.lp(top = 6))
            addView(option("moto", "🛵", "Moto", "Entregas: iFood, 99 Entrega, Keeta"), k.lp(top = 28))
            addView(option("carro", "🚗", "Carro", "Corridas: Uber, 99, inDrive"), k.lp(top = 14))
            addView(k.text("Cada modo guarda seus custos, faixas, ganhos e histórico separados.", 12, Palette.MUTED)
                .apply { gravity = Gravity.CENTER }, k.lp(top = 20))
        }
        setContentView(root)
        k.fitSystemBars(root)
    }

    private fun choose(settings: Settings, mode: String) {
        if (mode != settings.vehicleMode) {
            // Trocar de modo no meio do turno: o GPS para (cada modo tem o seu)
            GpsService.stop(this)
            settings.vehicleMode = mode
            runCatching { RemoteLog.get(this).settingsChanged("troca de modo") }
        }
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
