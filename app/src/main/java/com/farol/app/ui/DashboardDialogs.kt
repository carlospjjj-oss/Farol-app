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
    val d = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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

internal fun DashboardActivity.lastKnownKm(): Double? = (store.days().mapNotNull { it.kmEnd } + store.fuel().map { it.odometer }).maxOrNull()

internal fun DashboardActivity.fail(msg: String): Boolean { toast(msg); return false }

internal fun DashboardActivity.editEarnings() {
    val d = store.day()
    val fields = apps.associateWith { app -> k.field("0,00", d.earnings[app]?.let { fmtMoney(it) } ?: "") }
    val box = k.vbox()
    box.addView(k.text("O total de 'Ganhos de hoje' de cada app.", 13, Palette.MUTED))
    apps.forEach { app ->
        box.addView(k.hbox().apply {
            addView(k.dot(Palette.app(app), 10))
            val name = k.text(app, 15, Palette.TEXT, k.medium)
            addView(name, weighted(name, 10))
            addView(fields.getValue(app), LinearLayout.LayoutParams(k.dp(140), -2))
        }, k.lp(top = 10))
    }
    dialog("Ganhos de hoje", ScrollView(this).apply { addView(box) }, "Salvar") {
        val map = fields.mapNotNull { (app, f) -> f.num()?.takeIf { it > 0 }?.let { app to it } }.toMap()
        store.putDay(d.copy(earnings = map))
        toast("Ganhos salvos: ${Fmt.money(map.values.sum())}"); true
    }
}

internal fun DashboardActivity.askFuel() {
    val odo = k.field("Km do painel", lastKnownKm()?.let { fmtKm(it) } ?: "")
    val liters = k.field("Litros")
    val total = k.field("R$ pago")
    val full = CheckBox(this).apply { text = "Completei o tanque"; isChecked = true; setTextColor(Palette.TEXT) }
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
        addView(full, k.lp(top = 10))
        addView(k.text("Completar o tanque toda vez é o que permite medir o consumo real.", 12, Palette.MUTED), k.lp(top = 2))
    }
    dialog("Abastecimento", box, "Registrar") {
        val o = odo.num() ?: return@dialog fail("Digite o km do painel")
        val l = liters.num()?.takeIf { it > 0 } ?: return@dialog fail("Digite os litros")
        val t = total.num()?.takeIf { it > 0 } ?: return@dialog fail("Digite o valor pago")
        store.addFuel(FuelEntry(System.currentTimeMillis(), o, l, t, full.isChecked))
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
    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setMessage("Apagar o último abastecimento registrado?")
        .setPositiveButton("Apagar") { _, _ -> store.removeLastFuel(); render() }
        .setNegativeButton("Cancelar", null).show()
}

internal fun DashboardActivity.applyRealFuel() {
    val fs = JournalMath.fuelStats(store.fuel())
    val p = settings.profile
    settings.profile = p.copy(
        kmPerLiter = fs.realKmPerLiter?.let { Math.round(it * 10) / 10.0 } ?: p.kmPerLiter,
        fuelPrice = fs.lastPricePerLiter?.let { Math.round(it * 100) / 100.0 } ?: p.fuelPrice,
    )
    toast("Custo atualizado: ${Fmt.money(settings.profile.costPerKm)}/km")
    render()
}
