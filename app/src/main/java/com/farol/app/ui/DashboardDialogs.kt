package com.farol.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.farol.app.core.DayRecord
import com.farol.app.core.DaySummary
import com.farol.app.core.Fmt
import com.farol.app.core.FuelEntry
import com.farol.app.core.JournalMath
import com.farol.app.core.HourInsights
import com.farol.app.data.History
import com.farol.app.data.JournalStore
import com.farol.app.data.RemoteLog
import com.farol.app.data.Settings
import com.farol.app.service.OfferReaderService
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* Janelas do painel: nome, ganhos manuais, abastecimento e utilidades de formulário. */

internal fun DashboardActivity.dialog(title: String, content: View, ok: String, onOk: () -> Boolean) {
    val wrap = k.vbox(22, 8).apply { addView(content) }
    val d = AlertDialog.Builder(this, com.farol.app.ui.Palette.dialogTheme)
        .setTitle(title).setView(wrap)
        .setPositiveButton(ok, null).setNegativeButton("Cancelar", null).create()
    d.setOnShowListener {
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (onOk()) { d.dismiss(); render(); runCatching { com.farol.app.service.ShiftNotifier.refresh(this) } }
        }
    }
    d.show()
}

internal fun DashboardActivity.askName() {
    val f = k.field("Seu nome", settings.userName).apply {
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
    }
    dialog("Como podemos te chamar?", labeled("Aparece na saudação do painel", f), "Salvar") {
        settings.userName = f.text.toString(); true
    }
}

internal fun DashboardActivity.labeled(label: String, field: EditText) = k.vbox().apply {
    addView(k.text(label, 12, Palette.MUTED, k.medium))
    addView(field, k.lp(top = 6))
}

internal fun DashboardActivity.lastKnownKm(): Double? = JournalMath.lastKnownKm(store.days(), store.fuel())

internal fun DashboardActivity.fail(msg: String): Boolean { toast(msg); return false }

internal fun DashboardActivity.editEarnings(date: String = store.day().date) {
    val d = store.day(date)
    val cur = d.appEarnings
    val fields = apps.associateWith { app -> k.field("0,00", cur[app]?.let { fmtMoney(it) } ?: "") }
    val box = k.vbox()
    box.addView(k.text("O total de 'Ganhos de hoje' de cada app. Mudar um valor aqui vale mais que a soma automática das entregas desse app.", 13, Palette.MUTED))
    apps.forEach { app ->
        box.addView(k.hbox().apply {
            addView(k.dot(Palette.app(app), 10))
            val name = k.text(app, 15, Palette.TEXT, k.medium)
            addView(name, weighted(name, 10))
            addView(fields.getValue(app), LinearLayout.LayoutParams(k.dp(140), -2))
        }, k.lp(top = 10))
    }
    dialog(if (date == store.today()) "Ganhos de hoje" else "Ganhos de ${date.substring(8)}/${date.substring(5, 7)}", ScrollView(this).apply { addView(box) }, "Salvar") {
        val map = fields.mapNotNull { (app, f) -> f.num()?.takeIf { it > 0 }?.let { app to it } }.toMap()
        // App cujo valor você mudou à mão: o seu valor vale (as entregas somadas desse app saem da conta)
        val changed = apps.filter { a -> Math.abs((map[a] ?: 0.0) - (cur[a] ?: 0.0)) > 0.004 }.toSet()
        store.putDay(d.copy(earnings = map, orders = d.orders.filterKeys { DayRecord.appOf(it) !in changed }))
        toast("Ganhos salvos: ${Fmt.money(map.values.sum())}"); true
    }
}

internal fun DashboardActivity.askFuel() {
    val odo = k.field("Km do painel", lastKnownKm()?.let { fmtKm(it) } ?: "")
    val liters = k.field("Litros")
    val total = k.field("R$ pago")
    // 19/11: no tema escuro a marcação quase não aparecia; agora fica na cor de destaque
    val full = CheckBox(this).apply {
        text = "Completei o tanque"; isChecked = true; setTextColor(Palette.TEXT); textSize = 15f
        buttonTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Palette.ACCENT, Palette.MUTED))
    }
    // Data do abastecimento (19/11): hoje por padrão; dá para escolher outro dia para lançar depois
    var fuelDate: String? = null
    val dateRow = k.text("", 14, Palette.ACCENT, k.medium).apply { isClickable = true; setPadding(0, k.dp(6), 0, k.dp(6)) }
    fun renderDate() {
        dateRow.text = "📅  Data: " + (fuelDate?.let { "${it.substring(8)}/${it.substring(5, 7)}/${it.substring(0, 4)}" } ?: "hoje") + "  (tocar para mudar)"
    }
    renderDate()
    dateRow.setOnClickListener {
        val c = java.util.Calendar.getInstance()
        fuelDate?.let { c.set(it.substring(0, 4).toInt(), it.substring(5, 7).toInt() - 1, it.substring(8).toInt()) }
        android.app.DatePickerDialog(this, { _, y, m, d ->
            val chosen = "%04d-%02d-%02d".format(y, m + 1, d)
            fuelDate = chosen.takeIf { it != store.dateOf(System.currentTimeMillis()) }; renderDate()
        }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }
    val lv = labeled("Litros", liters)
    val tv = labeled("Valor pago", total)
    var fuelType = settings.fuelType
    val seg = k.hbox().apply { background = k.rounded(Palette.SURFACE2, 12); setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4)) }
    fun renderSeg() {
        seg.removeAllViews()
        listOf("gasolina" to "⛽ Gasolina", "etanol" to "🌱 Etanol").forEach { (type, label) ->
            val on = type == fuelType
            seg.addView(k.text(label, 14, if (on) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
                gravity = Gravity.CENTER; setPadding(0, k.dp(9), 0, k.dp(9))
                if (on) background = k.rounded(Palette.ACCENT, 10)
                isClickable = true; setOnClickListener { fuelType = type; renderSeg() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }
    renderSeg()
    val box = k.vbox().apply {
        addView(seg)
        addView(labeled("Km do painel", odo), k.lp(top = 12))
        addView(k.hbox().apply { addView(lv, weighted(lv)); addView(tv, weighted(tv, 10)) }, k.lp(top = 12))
        addView(dateRow, k.lp(top = 8))
        addView(full, k.lp(top = 6))
        addView(k.text("Completar o tanque toda vez é o que permite medir o consumo real.", 12, Palette.MUTED), k.lp(top = 2))
    }
    var confirmed = false
    var olderOk = false
    val known = lastKnownKm()
    val lastFuel = JournalMath.lastFuelKm(store.fuel(), known)
    dialog("Abastecimento", box, "Registrar") {
        val o = odo.num() ?: return@dialog fail("Digite o km do painel")
        // Km com um dígito a mais (ex.: 582692 em vez de 58269,2): corrige no campo e pede para conferir
        if (known != null && o - known > JournalMath.MAX_FUEL_SEGMENT_KM) {
            val fix = JournalMath.odometerFix(o, lastFuel ?: known)
            if (fix != null) { odo.setText(fmtKm(fix)); return@dialog fail("Parece que faltou a vírgula: corrigi para ${fmtKm(fix)}. Confira e toque em Registrar.") }
            return@dialog fail("Km muito acima do último registrado (${fmtKm(known)}). Confira o km do painel.")
        }
        // Menor que o último abastecimento: não pode (o consumo é medido de um tanque para o outro)
        if (lastFuel != null && o < lastFuel - 1) return@dialog fail("Km menor que o último abastecimento (${fmtKm(lastFuel)}). Confira o km do painel.")
        // Menor que o km do último turno: pode ser um abastecimento lançado depois. Pede para confirmar uma vez.
        if (known != null && o < known - 1 && !olderOk) {
            olderOk = true
            return@dialog fail("Km menor que o do último turno (${fmtKm(known)}). Se é um abastecimento feito antes, toque em Registrar de novo.")
        }
        val l = liters.num()?.takeIf { it > 0 } ?: return@dialog fail("Digite os litros")
        val t = total.num()?.takeIf { it > 0 } ?: return@dialog fail("Digite o valor pago")
        // Preço com cara do outro combustível (ex.: R$ 5,79 marcado como etanol): pede para conferir uma vez
        val other = if (fuelType == "etanol") "gasolina" else "etanol"
        val ppl = t / l
        val mine = settings.fuel(fuelType).price; val theirs = settings.fuel(other).price
        if (!confirmed && mine > 0 && theirs > 0 && Math.abs(mine - theirs) > 0.8 && Math.abs(ppl - theirs) < Math.abs(ppl - mine)) {
            confirmed = true
            return@dialog fail("${Fmt.money(ppl)}/L parece preço de $other. Confira o combustível marcado e toque em Registrar de novo.")
        }
        // Dia escolhido: grava naquele dia, na hora de agora (a ordem do consumo continua pelo km do painel)
        val at = fuelDate?.let { d -> java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, d.substring(0, 4).toInt()); set(java.util.Calendar.MONTH, d.substring(5, 7).toInt() - 1)
            set(java.util.Calendar.DAY_OF_MONTH, d.substring(8).toInt())
        }.timeInMillis } ?: System.currentTimeMillis()
        store.addFuel(FuelEntry(at, o, l, t, full.isChecked, fuelType))
        // O preço pago vira o preço daquele combustível, e ele passa a ser o do tanque.
        val price = Math.round(t / l * 100) / 100.0
        settings.setFuel(fuelType, settings.fuel(fuelType).copy(price = price))
        settings.fuelType = fuelType
        runCatching { com.farol.app.service.FarolJobs.syncSoon(this) }
        val name = if (fuelType == "etanol") "etanol" else "gasolina"
        toast("Abastecimento registrado: ${Fmt.money(price)}/L. O Farol agora calcula com $name."); true
    }
}

internal fun DashboardActivity.undoFuel() {
    AlertDialog.Builder(this, com.farol.app.ui.Palette.dialogTheme)
        .setMessage("Apagar o último abastecimento registrado?")
        .setPositiveButton("Apagar") { _, _ -> store.removeLastFuel(); render() }
        .setNegativeButton("Cancelar", null).show()
}

internal fun DashboardActivity.applyRealFuel() {
    // Só o combustível do tanque agora, com o consumo e o preço medidos NELE (nada de misturar os dois)
    val type = settings.fuelType
    val fs = JournalMath.fuelStats(store.fuel(), type)
    val cur = settings.fuel(type)
    settings.setFuel(type, com.farol.app.data.Settings.Fuel(
        price = fs.lastPricePerLiter?.let { Math.round(it * 100) / 100.0 } ?: cur.price,
        kml = fs.realKmPerLiter?.let { Math.round(it * 10) / 10.0 } ?: cur.kml,
    ))
    toast("Custo atualizado: ${Fmt.money(settings.profile.costPerKm)}/km")
    render()
}
