package com.farol.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.farol.app.core.Fmt
import com.farol.app.core.FuelEntry
import com.farol.app.core.JournalMath
import com.farol.app.data.History
import com.farol.app.data.JournalStore
import com.farol.app.data.RemoteLog
import com.farol.app.data.Settings
import com.farol.app.service.OfferReaderService
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela inicial: resumo do dia, turno (km do painel), ganhos por app,
 * abastecimentos com consumo real, ofertas lidas e semana/mês.
 */
class DashboardActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var store: JournalStore
    private lateinit var history: History
    private lateinit var col: LinearLayout

    private val apps = listOf("iFood", "99", "Keeta", "Uber", "inDrive", "Outros")
    private val earningFields = mutableMapOf<String, EditText>()

    private lateinit var tvStatus: TextView
    private lateinit var kpiEarn: TextView
    private lateinit var kpiKm: TextView
    private lateinit var kpiNet: TextView
    private lateinit var kpiPerKm: TextView
    private lateinit var kpiPerHour: TextView
    private lateinit var tvShift: TextView
    private lateinit var etKmStart: EditText
    private lateinit var etKmEnd: EditText
    private lateinit var tvEarnTotal: TextView
    private lateinit var etFuelOdo: EditText
    private lateinit var etFuelLiters: EditText
    private lateinit var etFuelTotal: EditText
    private lateinit var cbFull: CheckBox
    private lateinit var tvFuel: TextView
    private lateinit var btnUseFuel: Button
    private lateinit var tvOffers: TextView
    private lateinit var tvPeriods: TextView

    private val time = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    private val dateShort = SimpleDateFormat("dd/MM", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        store = JournalStore(this)
        history = History(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---------------- UI helpers ----------------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun section(title: String) = col.addView(TextView(this).apply {
        text = title; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(22), 0, dp(6))
    })

    private fun note(t: String) = TextView(this).apply {
        text = t; alpha = 0.7f; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    }.also { col.addView(it) }

    private fun numberField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setSelectAllOnFocus(true)
    }

    private fun row(vararg views: android.view.View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
    }.also { col.addView(it) }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { onClick() }
    }

    private fun kpi(label: String): Pair<LinearLayout, TextView> {
        val value = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); typeface = Typeface.DEFAULT_BOLD; text = "–"
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0x22FFFFFF) }
            addView(TextView(this@DashboardActivity).apply { text = label; alpha = 0.75f; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) })
            addView(value)
        }
        return box to value
    }

    private fun kpiRow(vararg boxes: LinearLayout) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        boxes.forEachIndexed { i, b ->
            r.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = dp(8) })
        }
        col.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    /** "41.230" -> 41230 (milhar) ; "4,5" ou "4.5" -> 4.5 ; "1.234,56" -> 1234.56 */
    private fun EditText.num(): Double? {
        val t = text.toString().trim().removePrefix("R$").trim()
        return when {
            t.contains(',') -> t.replace(".", "").replace(',', '.').toDoubleOrNull()
            Regex("""\d{1,3}(\.\d{3})+""").matches(t) -> t.replace(".", "").toDoubleOrNull()
            else -> t.toDoubleOrNull()
        }
    }

    private fun fmtNum(v: Double?): String = when {
        v == null -> ""
        v == Math.floor(v) -> v.toLong().toString()
        else -> String.format(Locale("pt", "BR"), "%.2f", v)
    }

    // ---------------- construção ----------------

    private fun buildUi() {
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(48))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        // Cabeçalho
        val title = TextView(this).apply {
            text = "🚦 Farol"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f); typeface = Typeface.DEFAULT_BOLD
        }
        val gear = button("⚙️ Configurações") { startActivity(Intent(this, SettingsActivity::class.java)) }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, -2, 1f)); addView(gear)
        })
        tvStatus = TextView(this).apply { setPadding(0, dp(4), 0, 0); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }
            .also { col.addView(it) }

        // KPIs do dia
        section("Hoje")
        val (b1, v1) = kpi("Ganhos"); val (b2, v2) = kpi("Km rodados"); val (b3, v3) = kpi("Líquido")
        val (b4, v4) = kpi("R$/km real"); val (b5, v5) = kpi("R$/hora")
        kpiEarn = v1; kpiKm = v2; kpiNet = v3; kpiPerKm = v4; kpiPerHour = v5
        kpiRow(b1, b2, b3)
        kpiRow(b4, b5)

        // Turno
        section("Turno (km do painel)")
        etKmStart = numberField("Km inicial")
        etKmEnd = numberField("Km final")
        row(etKmStart, button("▶ Iniciar") { startShift() })
        row(etKmEnd, button("⏹ Encerrar") { endShift() })
        tvShift = note("")

        // Ganhos por app
        section("Ganhos do dia por app")
        note("Digite o total que cada app mostra em 'Ganhos de hoje'. Pode atualizar várias vezes no dia.")
        apps.forEach { app ->
            val et = numberField("R$ 0,00")
            earningFields[app] = et
            row(TextView(this).apply { text = app; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) }, et)
        }
        tvEarnTotal = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(6), 0, dp(4))
        }.also { col.addView(it) }
        col.addView(button("💾 Salvar ganhos") { saveEarnings() })

        // Abastecimento
        section("Abastecimento")
        etFuelOdo = numberField("Km do painel")
        etFuelLiters = numberField("Litros")
        etFuelTotal = numberField("Valor pago R$")
        row(etFuelOdo, etFuelLiters, etFuelTotal)
        cbFull = CheckBox(this).apply { text = "Completei o tanque (necessário para medir o consumo)"; isChecked = true }
            .also { col.addView(it) }
        row(button("⛽ Registrar") { addFuel() }, button("Desfazer último") { undoFuel() })
        tvFuel = note("")
        btnUseFuel = button("Usar consumo e preço reais no Farol") { applyRealFuel() }.also { col.addView(it) }

        // Ofertas lidas
        section("Ofertas lidas pelo Farol hoje")
        tvOffers = note("")

        // Períodos
        section("Semana e mês")
        tvPeriods = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }.also { col.addView(it) }
        row(
            button("Copiar dias (CSV)") { copy("farol-dias.csv", store.daysCsv(settings.profile.costPerKm), "Dias copiados — cole numa planilha") },
            button("Histórico de ofertas") { startActivity(Intent(this, SettingsActivity::class.java)) },
        )
    }

    // ---------------- ações ----------------

    private fun startShift() {
        val km = etKmStart.num() ?: return toast("Digite o km do painel")
        val d = store.day()
        store.putDay(d.copy(kmStart = km, startAt = d.startAt ?: System.currentTimeMillis()))
        toast("Turno iniciado")
        refresh()
    }

    private fun endShift() {
        val km = etKmEnd.num() ?: return toast("Digite o km do painel")
        val d = store.day()
        if (d.kmStart != null && km < d.kmStart) return toast("O km final é menor que o inicial")
        val updated = d.copy(kmEnd = km, endAt = System.currentTimeMillis(), startAt = d.startAt ?: System.currentTimeMillis())
        store.putDay(updated)
        val s = JournalMath.summarize(updated, settings.profile.costPerKm)
        runCatching {
            RemoteLog.get(this).day(JSONObject()
                .put("data", updated.date).put("km", s.km).put("horas", s.hours)
                .put("ganhos", JSONObject(updated.earnings)).put("ganho_total", s.earnings)
                .put("custo", s.cost).put("liquido", s.net).put("r_km", s.perKm).put("r_h", s.perHour)
                .put("ofertas", history.recent(300).count { it.optLong("t") >= (updated.startAt ?: 0L) }))
        }
        toast("Turno encerrado")
        refresh()
    }

    private fun saveEarnings() {
        val map = earningFields.mapNotNull { (app, et) -> et.num()?.takeIf { it > 0 }?.let { app to it } }.toMap()
        store.putDay(store.day().copy(earnings = map))
        toast("Ganhos salvos")
        refresh()
    }

    private fun addFuel() {
        val odo = etFuelOdo.num() ?: return toast("Digite o km do painel")
        val liters = etFuelLiters.num()?.takeIf { it > 0 } ?: return toast("Digite os litros")
        val total = etFuelTotal.num()?.takeIf { it > 0 } ?: return toast("Digite o valor pago")
        store.addFuel(FuelEntry(System.currentTimeMillis(), odo, liters, total, cbFull.isChecked))
        etFuelOdo.setText(""); etFuelLiters.setText(""); etFuelTotal.setText("")
        toast("Abastecimento registrado")
        refresh()
    }

    private fun undoFuel() {
        AlertDialog.Builder(this).setMessage("Apagar o último abastecimento registrado?")
            .setPositiveButton("Apagar") { _, _ -> store.removeLastFuel(); refresh() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun applyRealFuel() {
        val fs = JournalMath.fuelStats(store.fuel())
        val p = settings.profile
        settings.profile = p.copy(
            kmPerLiter = fs.realKmPerLiter?.let { Math.round(it * 10) / 10.0 } ?: p.kmPerLiter,
            fuelPrice = fs.lastPricePerLiter?.let { Math.round(it * 100) / 100.0 } ?: p.fuelPrice,
        )
        toast("Custos atualizados: ${Fmt.money(settings.profile.costPerKm)}/km")
        refresh()
    }

    private fun copy(label: String, txt: String, msg: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, txt))
        toast(msg)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------------- atualização ----------------

    private fun serviceOn(): Boolean {
        val enabled = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, OfferReaderService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me } && OfferReaderService.instance != null
    }

    private fun refresh() {
        val profile = settings.profile
        val day = store.day()
        val s = JournalMath.summarize(day, profile.costPerKm)

        tvStatus.text = (if (serviceOn()) "✅ Lendo ofertas" else "⚠️ Leitor desligado — ative em Configurações") +
            "  •  custo ${Fmt.money(profile.costPerKm)}/km"

        kpiEarn.text = Fmt.money(s.earnings)
        kpiKm.text = s.km?.let { Fmt.km(it) } ?: "–"
        kpiNet.text = s.net?.let { Fmt.money(it) } ?: "–"
        kpiNet.setTextColor(when { s.net == null -> Color.LTGRAY; s.net >= 0 -> 0xFF4CAF50.toInt(); else -> 0xFFE57373.toInt() })
        kpiPerKm.text = s.perKm?.let { Fmt.money(it) } ?: "–"
        kpiPerHour.text = s.perHour?.let { Fmt.money(it) } ?: "–"

        // Turno
        if (etKmStart.text.isEmpty()) etKmStart.setText(fmtNum(day.kmStart))
        if (etKmEnd.text.isEmpty()) etKmEnd.setText(fmtNum(day.kmEnd))
        tvShift.text = buildString {
            if (day.startAt != null) append("Início ${time.format(Date(day.startAt))}") else append("Turno não iniciado.")
            if (day.endAt != null) append("  •  fim ${time.format(Date(day.endAt))}")
            s.hours?.let { append("  •  ${Fmt.min(it * 60)}") }
            s.cost?.let { append("\nCusto estimado do dia: ${Fmt.money(it)}") }
        }
        if (day.kmStart == null) {
            // Sugere o último km conhecido (fim de ontem ou último abastecimento)
            val last = (store.days().mapNotNull { it.kmEnd } + store.fuel().map { it.odometer }).maxOrNull()
            if (last != null && etKmStart.text.isEmpty()) etKmStart.hint = "Km inicial (último: ${fmtNum(last)})"
        }

        // Ganhos
        apps.forEach { app ->
            val et = earningFields.getValue(app)
            if (!et.hasFocus()) et.setText(fmtNum(day.earnings[app]))
        }
        tvEarnTotal.text = "Total do dia: ${Fmt.money(day.totalEarnings)}"

        // Abastecimento
        val fuel = store.fuel()
        val fs = JournalMath.fuelStats(fuel)
        val todayFuel = fuel.filter { store.dateOf(it.at) == day.date }.sumOf { it.total }
        tvFuel.text = buildString {
            if (fuel.isEmpty()) append("Nenhum abastecimento ainda. Registre sempre completando o tanque.")
            else {
                append("Consumo real: ")
                append(fs.realKmPerLiter?.let { "${Fmt.dec(it, 1)} km/L (em ${Fmt.km(fs.kmMeasured)})" }
                    ?: "precisa de 2 abastecimentos com tanque cheio")
                fs.lastPricePerLiter?.let { append("\nÚltimo preço: ${Fmt.money(it)}/L") }
                if (todayFuel > 0) append("\nGasto hoje: ${Fmt.money(todayFuel)}")
                append("\n\nÚltimos:")
                fuel.sortedByDescending { it.at }.take(5).forEach {
                    append("\n• ${dateShort.format(Date(it.at))}  ${fmtNum(it.odometer)} km  ${Fmt.dec(it.liters, 2)} L  ${Fmt.money(it.total)}")
                    if (!it.fullTank) append(" (parcial)")
                }
            }
        }
        btnUseFuel.isEnabled = fs.realKmPerLiter != null || fs.lastPricePerLiter != null
        btnUseFuel.text = "Usar no Farol: " + listOfNotNull(
            fs.realKmPerLiter?.let { "${Fmt.dec(it, 1)} km/L" },
            fs.lastPricePerLiter?.let { "${Fmt.money(it)}/L" }).joinToString(" e ").ifEmpty { "–" }

        // Ofertas
        tvOffers.text = history.todaySummary()

        // Semana e mês
        val days = store.days()
        val w = JournalMath.period(days, fuel, store.lastDays(7), profile.costPerKm, store::dateOf)
        val m = JournalMath.period(days, fuel, store.thisMonth(), profile.costPerKm, store::dateOf)
        fun line(t: String, p: com.farol.app.core.PeriodSummary) =
            "$t (${p.days} dias): ganhos ${Fmt.money(p.earnings)} • ${Fmt.km(p.km)} • líquido ${Fmt.money(p.net)}" +
                (if (p.km > 0) " • ${Fmt.money(p.earnings / p.km)}/km" else "") +
                (if (p.fuelSpent > 0) "\n   combustível pago: ${Fmt.money(p.fuelSpent)}" else "")
        tvPeriods.text = line("Últimos 7 dias", w) + "\n" + line("Este mês", m)
    }
}
