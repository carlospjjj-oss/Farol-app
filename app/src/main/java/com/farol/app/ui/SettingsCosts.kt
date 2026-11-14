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

/** Seções de custos, combustível, meta, faixas do semáforo, condições, tempo/distância e filtros. */
internal fun SettingsActivity.buildCostSections() {
    // Custos
    val cCost = card("Seus custos", "Quanto custa rodar cada km")
    cCost.addView(k.hbox().apply {
        listOf("🛵 Moto" to "moto", "🚗 Carro" to "carro", "🚲 Bike" to "bike").forEachIndexed { i, (label, preset) ->
            addView(k.chip(label, Palette.ACCENT).apply {
                isClickable = true; setOnClickListener { applyPreset(preset) }
            }, k.lp(w = -2).apply { if (i > 0) marginStart = k.dp(8) })
        }
    }, k.lp(top = 12))
    cCost.addView(k.text("No tanque agora", 13, Palette.MUTED, k.medium), k.lp(top = 16))
    segFuel = k.hbox().apply {
        background = k.rounded(Palette.SURFACE2, 12)
        setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4))
    }
    cCost.addView(segFuel, k.lp(top = 6))
    etFuel = numRow(cCost, "⛽ Gasolina", "R$/L")
    etKml = numRow(cCost, "Consumo com gasolina", "km/L", "média real na cidade")
    etFuelE = numRow(cCost, "🌱 Etanol", "R$/L")
    etKmlE = numRow(cCost, "Consumo com etanol", "km/L", "costuma ser uns 70% do da gasolina")
    tvFuelCmp = k.text("", 13, Palette.MUTED).apply {
        setPadding(k.dp(14), k.dp(10), k.dp(14), k.dp(10))
        background = k.rounded(Palette.SURFACE2, 12)
    }
    cCost.addView(tvFuelCmp, k.lp(top = 12))
    etOther = numRow(cCost, "Outros custos", "R$/km", "óleo, pneu, manutenção, desgaste")
    tvCost = k.text("", 15, Palette.ACCENT, k.bold).apply {
        setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12))
        background = k.rounded(Palette.alpha(Palette.ACCENT, 0x1F), 14)
    }
    cCost.addView(tvCost, k.lp(top = 14))
    onChange(etFuel, etKml, etFuelE, etKmlE, etOther) { showCost() }
    cCost.addView(k.ghost("🧮  Calcular custos do mês") {
        save(silent = true)
        startActivity(Intent(this, CostsActivity::class.java))
    }, k.lp(top = 12))

    // Meta
    val cGoal = card("Metas", "Meta do dia em líquido (ganhos − custo do km − gastos). Semana e mês saem dela")
    etGoal = numRow(cGoal, "Quero ganhar por dia (líquido)", "R$", "0 = sem meta")
    etWorkDays = numRow(cGoal, "Dias de trabalho por semana", "dias", "1 a 7 · folga não conta contra a meta")

    // Semáforo
    val cLight = card("Suas faixas", "A cor final é a pior entre R$/km, R$/h e nota")
    bandsBox = k.vbox().also { cLight.addView(it, k.lp(top = 14)) }
    fun lightHeader(color: Int, label: String) = cLight.addView(k.hbox().apply {
        addView(k.dot(color, 12))
        addView(k.text(label, 14, color, k.bold), k.lp(w = -2).apply { marginStart = k.dp(8) })
    }, k.lp(top = 16))
    lightHeader(Palette.GREEN, "Verde a partir de")
    etGreenKm = numRow(cLight, "Por km", "R$/km")
    etGreenH = numRow(cLight, "Por hora", "R$/h")
    lightHeader(Palette.YELLOW, "Amarelo a partir de")
    etYellowKm = numRow(cLight, "Por km", "R$/km")
    etYellowH = numRow(cLight, "Por hora", "R$/h")
    lightHeader(Palette.RED, "Vermelho: abaixo do amarelo")
    cLight.addView(k.text("Nota do passageiro (quando o app mostra)", 13, Palette.MUTED, k.medium), k.lp(top = 16))
    etGreenStars = numRow(cLight, "Aceitar a partir de", "★", "0 = não usa")
    etMinRating = numRow(cLight, "Atenção a partir de", "★", "abaixo disso é vermelho")
    etSpeed = numRow(cLight, "Velocidade média", "km/h", "usada quando a oferta não mostra o tempo (~)")
    swAutoSpeed = toggle(cLight, "Aprender com meus pedidos", "Cada app usa a velocidade dos SEUS pedidos nele (aceite até o fim). Sem 5 pedidos medidos, usa o valor acima")
    tvSpeed = k.text("", 13, Palette.ACCENT, k.medium).also { cLight.addView(it, k.lp(top = 8)) }
    swGps = toggle(cLight, "📍 Medir pelo GPS (durante o turno)", "Mede km do dia e velocidade real de cada pedido. Só sugere no dashboard: nada muda sem você tocar em \"Usar\"")
    swGps.setOnCheckedChangeListener { _, on ->
        if (on && !com.farol.app.service.GpsService.hasPermission(this))
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 12)
    }
    onChange(etGreenKm, etYellowKm, etGreenH, etYellowH, etGreenStars, etMinRating) { renderBands() }

    // Condições mínimas
    val cMin = card("Condições mínimas", "Abaixo disso o cartão fica vermelho (0 = não usa)")
    etMinPrice = numRow(cMin, "Valor da corrida", "R$")
    etMinNet = numRow(cMin, "Lucro líquido mínimo", "R$")
    etMinNetPct = numRow(cMin, "Lucro mínimo", "%", "do valor da corrida")

    // Tempo e distância
    val cTD = card("Tempo e distância", "Acima disso o cartão fica vermelho (0 = não usa)")
    etMaxPickup = numRow(cTD, "Busca máxima", "km", "até o restaurante ou passageiro")
    etMaxKm = numRow(cTD, "Distância total máxima", "km")
    etMaxMin = numRow(cTD, "Tempo total máximo", "min")

    // Filtros
    val cFilter = card("Bairros", "Alerta deixa o cartão vermelho; favorito ganha uma ⭐")
    etAlerts = textField("ex.: Jardim Exemplo, Vila Tal", multi = true)
    labeledText(cFilter, "🔴 Bairros ou ruas de alerta (separados por vírgula)", etAlerts)
    etFavorites = textField("ex.: São Caetano, Vila Tal", multi = true)
    labeledText(cFilter, "⭐ Bairros favoritos: perto de casa ou bons (só destaca, não muda a cor)", etFavorites)
}
