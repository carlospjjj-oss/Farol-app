package com.farol.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.farol.app.core.CostProfile
import com.farol.app.core.Fmt
import com.farol.app.core.Rating
import com.farol.app.data.CaptureLog
import com.farol.app.data.History
import com.farol.app.data.RemoteLog
import com.farol.app.data.Settings
import com.farol.app.service.OfferReaderService

/** Seções do cartão (tamanho, posição) e da leitura (OCR, apps monitorados, descoberta). */
internal fun SettingsActivity.buildReadingSections() {
    // Aparência
    val cTheme = card("Aparência", "Tema do app")
    val segTheme = k.hbox().apply { background = k.rounded(Palette.SURFACE2, 12); setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4)) }
    listOf("escuro" to "🌙 Escuro", "claro" to "☀️ Claro", "sistema" to "📱 Automático").forEach { (mode, label) ->
        val on = settings.theme == mode
        segTheme.addView(k.text(label, 13, if (on) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
            gravity = Gravity.CENTER; setPadding(0, k.dp(9), 0, k.dp(9))
            if (on) background = k.rounded(Palette.ACCENT, 10)
            isClickable = true
            setOnClickListener {
                if (settings.theme != mode) { save(silent = true); settings.theme = mode; recreate() }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }
    cTheme.addView(segTheme, k.lp(top = 12))
    hint(cTheme, "Automático segue o tema do celular. O card das ofertas fica sempre escuro, para aparecer bem sobre qualquer app.")

    // Cartão
    val cCard = card("Cartão flutuante")
    cCard.addView(k.text("Tamanho", 12, Palette.MUTED, k.medium), k.lp(top = 12))
    segSize = k.hbox().apply { background = k.rounded(Palette.SURFACE2, 12); setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4)) }
    cCard.addView(segSize, k.lp(top = 6))
    swCompact = toggle(cCard, "Modo compacto", "Só a cor, o R$/km bem grande e o preço: para ler de relance no guidão")
    swEnabled = toggle(cCard, "Farol ligado", "Pausa a leitura sem desligar a acessibilidade")
    swVibrate = toggle(cCard, "Vibrar na oferta verde")
    swSpeak = toggle(cCard, "Falar o resumo", "\"Verde. R$ 2,10 por quilômetro...\"")
    etHide = numRow(cCard, "Esconder após", "seg")
    cCard.addView(k.text("Testar o cartão", 12, Palette.MUTED, k.medium), k.lp(top = 16))
    cCard.addView(k.hbox().apply {
        listOf(Rating.GREEN to Palette.GREEN, Rating.YELLOW to Palette.YELLOW, Rating.RED to Palette.RED).forEachIndexed { i, (r, c) ->
            val label = when (r) { Rating.GREEN -> "Verde"; Rating.YELLOW -> "Amarelo"; Rating.RED -> "Vermelho" }
            addView(k.pill(label, Palette.alpha(c, 0x33), c) { test(r) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = k.dp(8) })
        }
    }, k.lp(top = 8))

    // Leitura
    val cRead = card("Leitura das ofertas")
    swAutoEarn = toggle(cRead, "Ganhos automáticos", "Lê o 'ganhos de hoje' na tela inicial de cada app e preenche o dashboard")
    swHint = toggle(cRead, "Só com botão de aceitar", "Evita ler telas que não são oferta (recomendado)")
    swOcr = toggle(cRead, "OCR de reserva", "Lê a imagem quando o app esconde o texto (Android 11+)")
    swCapture = toggle(cRead, "Modo captura", "Grava o texto lido para calibrar")
    swScreens = toggle(cRead, "Registrar telas após oferta", "Envia nos logs as telas de aceite, coleta e entrega por 90 min (para somar ganhos sozinho no futuro)")
    etTargets = textField("pacote=Nome", multi = true, mono = true).apply { minLines = 4 }
    labeledText(cRead, "Apps monitorados (pacote=Nome, um por linha)", etTargets)
    tvRecent = k.text("", 12, Palette.MUTED).also { cRead.addView(it, k.lp(top = 8)) }
    hint(cRead, "Por segurança, o Farol só lê os apps desta lista. Para descobrir o pacote de um app novo, toque abaixo e abra o app em até 2 minutos.")
    buttons(cRead, "🔎 Descobrir app novo (2 min)" to {
        val s = com.farol.app.service.OfferReaderService.instance
        if (s == null) toast("Ligue o Farol na acessibilidade primeiro")
        else { s.discover(120); toast("Abra o app novo agora. Depois volte aqui e veja \"Apps vistos recentemente\".") }
    })
}
