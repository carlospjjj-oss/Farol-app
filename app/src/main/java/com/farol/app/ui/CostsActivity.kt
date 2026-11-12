package com.farol.app.ui

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.farol.app.core.CostBreakdown
import com.farol.app.core.Fmt
import com.farol.app.core.MonthlyCosts
import com.farol.app.data.JournalStore
import com.farol.app.data.Settings

/**
 * Calculadora de custos do mês: quanto você gasta com cada coisa → custo por km e % de cada item.
 * "Aplicar ao semáforo" grava o "Outros custos (R$/km)" usado em todas as ofertas.
 */
class CostsActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var k: Kit
    private lateinit var col: LinearLayout
    private lateinit var listBox: LinearLayout

    private var vehicle = "proprio"
    private lateinit var segVehicle: LinearLayout
    private lateinit var etKm: EditText
    private lateinit var etFin: EditText
    private lateinit var finRow: LinearLayout
    private lateinit var etDep: EditText
    private lateinit var etMan: EditText
    private lateinit var etIpva: EditText
    private lateinit var etSeg: EditText
    private lateinit var etOut: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        k = Kit(this)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        build()
        load()
        refresh()
    }

    private fun EditText.num(): Double = text.toString().trim().replace(".", "").replace(',', '.').toDoubleOrNull() ?: 0.0
    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else String.format(java.util.Locale("pt", "BR"), "%.2f", v)

    private fun row(parent: LinearLayout, icon: String, label: String, sub: String, unit: String = "R$/mês"): Pair<LinearLayout, EditText> {
        val f = k.field("0").apply {
            gravity = Gravity.END; setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(k.dp(12), k.dp(10), k.dp(12), k.dp(10))
        }
        val r = k.hbox().apply {
            addView(k.bubble(icon, Palette.ACCENT))
            addView(k.vbox().apply {
                addView(k.text(label, 15, Palette.TEXT, k.medium))
                addView(k.text(sub, 12, Palette.MUTED), k.lp(top = 2))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(12) })
            addView(f, LinearLayout.LayoutParams(k.dp(100), -2).apply { marginStart = k.dp(8) })
            addView(k.text(unit, 11, Palette.MUTED), LinearLayout.LayoutParams(k.dp(46), -2).apply { marginStart = k.dp(6) })
        }
        parent.addView(r, k.lp(top = 14))
        f.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })
        return r to f
    }

    private fun build() {
        col = k.vbox().apply { setPadding(k.dp(18), k.dp(12), k.dp(18), k.dp(170)) }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; addView(col) }

        val back = k.text("←", 20, Palette.TEXT, k.bold).apply {
            gravity = Gravity.CENTER
            background = k.ripple(k.rounded(Palette.SURFACE, 999, Palette.STROKE))
            isClickable = true; setOnClickListener { finish() }
        }
        col.addView(k.hbox().apply {
            addView(back, LinearLayout.LayoutParams(k.dp(44), k.dp(44)))
            addView(k.vbox().apply {
                addView(k.text("Custos do mês", 22, Palette.TEXT, k.bold))
                addView(k.text("Quanto custa rodar cada km de verdade", 13, Palette.MUTED), k.lp(top = 2))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(14) })
        })

        // Veículo
        val cv = k.card()
        cv.addView(k.text("Seu veículo", 17, Palette.TEXT, k.bold))
        segVehicle = k.hbox().apply {
            background = k.rounded(Palette.SURFACE2, 12); setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4))
        }
        cv.addView(segVehicle, k.lp(top = 12))
        val (_, km) = row(cv, "🛵", "Km por mês", "quanto você roda trabalhando", "km")
        etKm = km
        val jr = JournalStore(this)
        val monthKm = jr.days().filter { it.date in jr.lastDays(30) }.mapNotNull { it.kmDriven }
        if (monthKm.size >= 3) {
            val est = monthKm.average() * 26
            cv.addView(k.text("Seus dias registrados dão ~${Fmt.km(monthKm.average())}/dia → ~${Fmt.km(est)}/mês (26 dias). Toque para usar.",
                12, Palette.ACCENT).apply { isClickable = true; setOnClickListener { etKm.setText(fmt(Math.round(est).toDouble())) } }, k.lp(top = 8))
        }
        col.addView(cv, k.lp(top = 18))

        // Gastos
        val cg = k.card()
        cg.addView(k.text("Gastos mensais", 17, Palette.TEXT, k.bold))
        cg.addView(k.text("Combustível é calculado pelo consumo e preço do litro dos Ajustes.", 12, Palette.MUTED), k.lp(top = 4))
        val (fr, fin) = row(cg, "💳", "Financiamento", "parcela mensal"); finRow = fr; etFin = fin
        etDep = row(cg, "📉", "Depreciação", "quanto a moto perde de valor por mês").second
        etMan = row(cg, "🔧", "Manutenção", "óleo, pneus, correia, freio, revisão").second
        etIpva = row(cg, "📄", "IPVA e licenciamento", "valor do ano ÷ 12").second
        etSeg = row(cg, "🛡️", "Seguro", "por mês").second
        etOut = row(cg, "📱", "Outros", "celular, internet, baú, equipamento").second
        col.addView(cg, k.lp(top = 14))

        // Resultado
        listBox = k.card().also { col.addView(it, k.lp(top = 14)) }

        // Botões fixos
        val apply = k.pill("Aplicar ao semáforo") { save(); applyToProfile() }
        val close = k.ghost("✓  Salvar e fechar") { save(); finish() }
        val bar = k.vbox().apply {
            setBackgroundColor(Palette.alpha(Palette.BG, 0xF2))
            setPadding(k.dp(18), k.dp(12), k.dp(18), k.dp(12))
            addView(apply); addView(close, k.lp(top = 8))
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Palette.BG)
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        setContentView(root)
        k.fitSystemBars(root) { _, bottom -> bar.setPadding(k.dp(18), k.dp(12), k.dp(18), k.dp(12) + bottom) }
    }

    private fun renderVehicle() {
        segVehicle.removeAllViews()
        listOf("proprio" to "Próprio", "financiado" to "Financiado", "alugado" to "Alugado").forEach { (v, label) ->
            val on = v == vehicle
            segVehicle.addView(k.text(label, 13, if (on) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
                gravity = Gravity.CENTER; setPadding(0, k.dp(9), 0, k.dp(9))
                if (on) background = k.rounded(Palette.ACCENT, 10)
                isClickable = true; setOnClickListener { vehicle = v; renderVehicle(); refresh() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        finRow.visibility = if (vehicle == "proprio") View.GONE else View.VISIBLE
        ((finRow.getChildAt(1) as LinearLayout).getChildAt(0) as android.widget.TextView).text =
            if (vehicle == "alugado") "Aluguel" else "Financiamento"
    }

    private fun load() {
        val c = settings.monthlyCosts
        vehicle = c.vehicle
        etKm.setText(fmt(c.kmPerMonth)); etFin.setText(fmt(c.financing)); etDep.setText(fmt(c.depreciation))
        etMan.setText(fmt(c.maintenance)); etIpva.setText(fmt(c.ipva)); etSeg.setText(fmt(c.insurance)); etOut.setText(fmt(c.other))
        renderVehicle()
    }

    private fun current() = MonthlyCosts(
        kmPerMonth = etKm.num().coerceAtLeast(1.0), vehicle = vehicle,
        financing = if (vehicle == "proprio") 0.0 else etFin.num(),
        depreciation = etDep.num(), maintenance = etMan.num(), ipva = etIpva.num(), insurance = etSeg.num(), other = etOut.num(),
    )

    private fun refresh() {
        if (!::listBox.isInitialized) return
        val p = settings.profile
        val c = current()
        val items = CostBreakdown.items(c, p.fuelPrice, p.kmPerLiter)
        val total = CostBreakdown.total(items)
        listBox.removeAllViews()
        listBox.addView(k.hbox().apply {
            val t = k.text("Custos mensais", 17, Palette.TEXT, k.bold)
            addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            addView(k.text(Fmt.money(total), 17, Palette.TEXT, k.bold))
        })
        items.forEachIndexed { i, it ->
            if (i > 0) listBox.addView(View(this).apply { setBackgroundColor(Palette.STROKE) }, k.lp(h = k.dp(1), top = 12))
            listBox.addView(k.hbox().apply {
                addView(k.vbox().apply {
                    addView(k.text(it.name, 15, Palette.TEXT, k.medium))
                    addView(k.text("${Fmt.money(it.perKm)}/km · ${it.pct.toInt()}%", 12, Palette.MUTED), k.lp(top = 3))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(k.text(Fmt.money(it.monthly), 15, Palette.TEXT, k.bold))
            }, k.lp(top = 12))
        }
        val perKm = total / c.kmPerMonth
        val other = CostBreakdown.otherPerKm(c)
        listBox.addView(k.text("Custo total: ${Fmt.money(perKm)}/km\n(combustível ${Fmt.money(p.fuelCostPerKm)} + outros ${Fmt.money(other)})",
            15, Palette.ACCENT, k.bold).apply {
            setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12))
            background = k.rounded(Palette.alpha(Palette.ACCENT, 0x1F), 14)
        }, k.lp(top = 16))
        listBox.addView(k.text("Hoje o semáforo usa outros = ${Fmt.money(p.otherCostPerKm)}/km. \"Aplicar\" troca por ${Fmt.money(other)}/km.",
            12, Palette.MUTED), k.lp(top = 8))
    }

    private fun save() { settings.monthlyCosts = current() }

    private fun applyToProfile() {
        val other = Math.round(CostBreakdown.otherPerKm(current()) * 100) / 100.0
        settings.profile = settings.profile.copy(otherCostPerKm = other)
        Toast.makeText(this, "Outros custos = ${Fmt.money(other)}/km. Custo total ${Fmt.money(settings.profile.costPerKm)}/km", Toast.LENGTH_LONG).show()
        refresh()
    }
}
