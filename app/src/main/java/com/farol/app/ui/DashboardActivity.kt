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

/**
 * Tela inicial (dashboard): ganhos do dia em destaque, indicadores, turno,
 * ganhos por app, ofertas lidas, combustível e últimos 7 dias.
 * As entradas de dados abrem janelas rápidas; a tela só mostra informação.
 */
class DashboardActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var store: JournalStore
    private lateinit var history: History
    private lateinit var k: Kit
    private lateinit var col: LinearLayout
    private lateinit var headerBox: LinearLayout

    private val apps = listOf("iFood", "99", "Keeta", "Uber", "inDrive", "Outros")
    private val ptBR = Locale("pt", "BR")
    private val hhmm = SimpleDateFormat("HH:mm", ptBR)
    private val ddmm = SimpleDateFormat("dd/MM", ptBR)
    private var periodMonth = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        store = JournalStore(this)
        history = History(this)
        k = Kit(this)

        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        window.decorView.setBackgroundColor(Palette.BG)

        headerBox = k.vbox().apply { setPadding(k.dp(18), k.dp(12), k.dp(18), k.dp(10)); setBackgroundColor(Palette.BG) }
        col = k.vbox().apply { setPadding(k.dp(18), k.dp(4), k.dp(18), k.dp(40)) }
        val root = k.vbox().apply {
            setBackgroundColor(Palette.BG)
            addView(headerBox, LinearLayout.LayoutParams(-1, -2))
            addView(ScrollView(this@DashboardActivity).apply {
                setBackgroundColor(Palette.BG)
                isVerticalScrollBarEnabled = false
                clipToPadding = false
                addView(col)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
        k.fitSystemBars(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ============================== render ==============================

    private fun render() {
        col.removeAllViews()
        headerBox.removeAllViews()
        val day = store.day()
        val s = JournalMath.summarize(day, settings.profile.costPerKm)
        header()
        hero(day, s)
        kpis(s)
        shiftCard(day, s)
        earningsCard(day)
        offersCard()
        fuelCard()
        periodCard()
        footer()
    }

    private fun add(v: View, top: Int = 14) = col.addView(v, k.lp(top = top))

    private fun weighted(v: View, startMargin: Int = 0) =
        LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(startMargin) }

    // header
    private fun header() {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (h) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
        val date = SimpleDateFormat("EEEE, d 'de' MMMM", ptBR).format(Date()).replaceFirstChar { it.uppercase() }

        val left = k.vbox().apply {
            addView(k.text(date, 13, Palette.MUTED, k.medium))
            addView(k.text("$greeting 👋", 26, Palette.TEXT, k.bold), k.lp(top = 4))
        }
        val gear = k.text("⚙️", 20).apply {
            gravity = Gravity.CENTER
            background = k.ripple(k.rounded(Palette.SURFACE, 999, Palette.STROKE))
            isClickable = true
            setOnClickListener { startActivity(Intent(this@DashboardActivity, SettingsActivity::class.java)) }
        }
        headerBox.addView(k.hbox().apply {
            addView(left, weighted(left))
            addView(gear, LinearLayout.LayoutParams(k.dp(48), k.dp(48)))
        })

        val on = serviceOn()
        val color = if (on) Palette.GREEN else Palette.RED
        val status = k.hbox().apply {
            setPadding(k.dp(12), k.dp(8), k.dp(14), k.dp(8))
            background = k.rounded(Palette.alpha(color, 0x22), 999)
            addView(k.dot(color, 8))
            addView(k.text(if (on) "Lendo ofertas" else "Leitor desligado — toque para ativar", 13, color, k.medium),
                k.lp(w = -2).apply { marginStart = k.dp(8) })
            if (!on) {
                isClickable = true
                setOnClickListener { startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
        }
        headerBox.addView(status, LinearLayout.LayoutParams(-2, -2).apply { topMargin = k.dp(10) })
    }

    // hero
    private fun hero(day: DayRecord, s: DaySummary) {
        val dark = Palette.ON_ACCENT
        val card = k.vbox(22, 22).apply {
            background = k.gradient(0xFFFFB020.toInt(), 0xFFFF7A1A.toInt(), radiusDp = 26)
            elevation = k.dp(6).toFloat()
        }
        card.addView(k.text("GANHOS DE HOJE", 12, Palette.alpha(dark, 0xB3), k.bold).apply { letterSpacing = 0.1f })
        card.addView(k.text(Fmt.money(s.earnings), 44, dark, k.black).apply { letterSpacing = -0.02f }, k.lp(top = 6))

        fun heroPill(t: String) = k.text(t, 13, dark, k.bold).apply {
            setPadding(k.dp(10), k.dp(6), k.dp(10), k.dp(6))
            background = k.rounded(Palette.alpha(0xFFFFFFFF.toInt(), 0x55), 999)
        }
        val sub = k.hbox()
        sub.addView(heroPill("Líquido ${s.net?.let { Fmt.money(it) } ?: "—"}"))
        val n = day.earnings.count { it.value > 0 }
        if (n > 0) sub.addView(heroPill("$n ${if (n == 1) "app" else "apps"}"), k.lp(w = -2).apply { marginStart = k.dp(8) })
        if (n == 0) sub.addView(heroPill("Toque para lançar"), k.lp(w = -2).apply { marginStart = k.dp(8) })
        card.addView(sub, k.lp(top = 14))

        card.isClickable = true
        card.setOnClickListener { editEarnings() }
        add(card, 8)
    }

    // kpis
    private fun kpis(s: DaySummary) {
        fun tile(emoji: String, tint: Int, label: String, value: String, hint: String? = null) = k.vbox(14, 14).apply {
            background = k.rounded(Palette.SURFACE, 20, Palette.STROKE)
            addView(k.bubble(emoji, tint))
            addView(k.text(value, 20, Palette.TEXT, k.bold), k.lp(top = 12))
            addView(k.text(label, 12, Palette.MUTED, k.medium), k.lp(top = 4))
            addView(k.text(hint ?: " ", 11, Palette.MUTED), k.lp(top = 2))
        }
        fun row(a: View, b: View) = k.hbox().apply {
            addView(a, weighted(a)); addView(b, weighted(b, 12))
        }
        add(row(
            tile("🛵", Palette.ACCENT, "Km rodados", s.km?.let { Fmt.km(it) } ?: "—"),
            tile("💰", Palette.GREEN, "R$/km real", s.perKm?.let { Fmt.money(it) } ?: "—"),
        ))
        add(row(
            tile("⏱️", 0xFF60A5FA.toInt(), "R$/hora", s.perHour?.let { Fmt.money(it) } ?: "—", s.hours?.let { Fmt.min(it * 60) }),
            tile("⛽", Palette.RED, "Custo estimado", s.cost?.let { Fmt.money(it) } ?: "—", "${Fmt.money(settings.profile.costPerKm)}/km"),
        ), 12)
    }

    // shift
    private fun shiftCard(day: DayRecord, s: DaySummary) {
        val card = k.card()
        card.addView(k.overline("Turno"))
        when {
            day.kmStart == null -> {
                card.addView(k.text("Pronto para rodar?", 18, Palette.TEXT, k.bold), k.lp(top = 8))
                card.addView(k.text("Anote o km do painel da moto para medir o dia.", 13, Palette.MUTED), k.lp(top = 4))
                card.addView(k.pill("▶  Iniciar turno") { askStart() }, k.lp(top = 14))
            }
            day.kmEnd == null -> {
                val since = day.startAt?.let { hhmm.format(Date(it)) } ?: "—"
                val elapsed = day.startAt?.let { Fmt.min((System.currentTimeMillis() - it) / 60_000.0) } ?: ""
                card.addView(k.hbox().apply {
                    addView(k.dot(Palette.GREEN, 10))
                    addView(k.text("Em turno desde $since", 18, Palette.TEXT, k.bold), k.lp(w = -2).apply { marginStart = k.dp(10) })
                }, k.lp(top = 8))
                card.addView(k.text("$elapsed  •  km inicial ${fmtKm(day.kmStart)}", 13, Palette.MUTED), k.lp(top = 4))
                card.addView(k.pill("⏹  Encerrar turno", Palette.RED, 0xFFFFFFFF.toInt()) { askEnd() }, k.lp(top = 14))
            }
            else -> {
                val a = day.startAt?.let { hhmm.format(Date(it)) } ?: "—"
                val b = day.endAt?.let { hhmm.format(Date(it)) } ?: "—"
                card.addView(k.text("Turno encerrado  ✓", 18, Palette.TEXT, k.bold), k.lp(top = 8))
                card.addView(k.text("$a – $b  •  ${s.km?.let { Fmt.km(it) } ?: ""}  •  ${fmtKm(day.kmStart)} → ${fmtKm(day.kmEnd)}",
                    13, Palette.MUTED), k.lp(top = 4))
                card.addView(k.ghost("Editar km") { askEditShift() }, k.lp(top = 14))
            }
        }
        add(card, 18)
    }

    // earnings
    private fun earningsCard(day: DayRecord) {
        val card = k.card()
        card.addView(k.hbox().apply {
            val t = k.overline("Ganhos por app")
            addView(t, weighted(t))
            addView(k.text(Fmt.money(day.totalEarnings), 15, Palette.TEXT, k.bold))
        })
        val max = (day.earnings.values.maxOrNull() ?: 0.0).coerceAtLeast(0.01)
        val shown = apps.filter { (day.earnings[it] ?: 0.0) > 0 }.sortedByDescending { day.earnings[it] }
        if (shown.isEmpty()) card.addView(k.text("Nenhum ganho ainda. Abra a tela inicial de cada app que o Farol lê o \"ganhos de hoje\" sozinho, ou lance à mão.", 13, Palette.MUTED), k.lp(top = 12))
        shown.forEach { app ->
            val v = day.earnings[app] ?: 0.0
            card.addView(k.hbox().apply {
                addView(k.dot(Palette.app(app), 10))
                val name = k.text(app, 15, Palette.TEXT, k.medium)
                addView(name, k.lp(w = -2).apply { marginStart = k.dp(10) })
                val autoAt = settings.autoAt(day.date, app)
                if (autoAt > 0) addView(k.chip("auto ${hhmm.format(Date(autoAt))}", Palette.GREEN).apply {
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                }, k.lp(w = -2).apply { marginStart = k.dp(8) })
                addView(View(this@DashboardActivity), LinearLayout.LayoutParams(0, 1, 1f))
                addView(k.text(Fmt.money(v), 15, Palette.TEXT, k.bold))
            }, k.lp(top = 14))
            card.addView(k.bar((v / max).toFloat(), Palette.app(app)), k.lp(h = k.dp(6), top = 8))
        }
        card.addView(k.pill("✏️  Lançar ganhos") { editEarnings() }, k.lp(top = 16))
        add(card)
    }

    // offers
    private fun offersCard() {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val today = history.recent(1000).filter { it.optLong("t") >= start }
        val g = today.count { it.optString("rating") == "GREEN" }
        val y = today.count { it.optString("rating") == "YELLOW" }
        val r = today.size - g - y

        val card = k.card()
        card.addView(k.hbox().apply {
            val t = k.overline("Ofertas lidas hoje")
            addView(t, weighted(t))
            addView(k.text("${today.size}", 15, Palette.TEXT, k.bold))
        })
        card.addView(k.hbox().apply {
            addView(k.chip("● $g verdes", Palette.GREEN))
            addView(k.chip("● $y amarelas", Palette.YELLOW), k.lp(w = -2).apply { marginStart = k.dp(8) })
            addView(k.chip("● $r vermelhas", Palette.RED), k.lp(w = -2).apply { marginStart = k.dp(8) })
        }, k.lp(top = 12))
        if (today.isNotEmpty()) {
            val avg = today.map { it.optDouble("perKm") }.filter { !it.isNaN() }.average()
            card.addView(k.text("Média ofertada: ${Fmt.money(avg)}/km", 13, Palette.MUTED), k.lp(top = 12))
            today.take(3).forEach { o ->
                val c = when (o.optString("rating")) { "GREEN" -> Palette.GREEN; "YELLOW" -> Palette.YELLOW; else -> Palette.RED }
                card.addView(k.hbox().apply {
                    addView(k.dot(c, 8))
                    val left = k.text("${hhmm.format(Date(o.optLong("t")))}  ${o.optString("app")}", 13, Palette.TEXT)
                    addView(left, weighted(left, 10))
                    addView(k.text("${Fmt.money(o.optDouble("price"))} · ${Fmt.km(o.optDouble("km"))} · ${Fmt.money(o.optDouble("perKm"))}/km",
                        12, Palette.MUTED))
                }, k.lp(top = 10))
            }
        }
        add(card)
    }

    // fuel
    private fun fuelCard() {
        val fuel = store.fuel()
        val fs = JournalMath.fuelStats(fuel)
        val month = store.thisMonth()
        val monthSpent = fuel.filter { store.dateOf(it.at) in month }.sumOf { it.total }

        val card = k.card()
        card.addView(k.overline("Combustível"))
        card.addView(k.hbox().apply {
            val left = k.vbox().apply {
                addView(k.text(fs.realKmPerLiter?.let { "${Fmt.dec(it, 1)} km/L" } ?: "— km/L", 24, Palette.TEXT, k.bold))
                addView(k.text(if (fs.realKmPerLiter != null) "consumo real em ${Fmt.km(fs.kmMeasured)}"
                    else "precisa de 2 abastecimentos de tanque cheio", 12, Palette.MUTED), k.lp(top = 4))
            }
            addView(left, weighted(left))
            addView(k.vbox().apply {
                gravity = Gravity.END
                addView(k.text(fs.lastPricePerLiter?.let { "${Fmt.money(it)}/L" } ?: "—", 16, Palette.TEXT, k.bold))
                addView(k.text("no mês: ${Fmt.money(monthSpent)}", 12, Palette.MUTED), k.lp(w = -2, top = 4))
            })
        }, k.lp(top = 10))

        fuel.sortedByDescending { it.at }.take(3).forEach {
            card.addView(k.text("${ddmm.format(Date(it.at))}   ${fmtKm(it.odometer)} km   ${Fmt.dec(it.liters, 2)} L   ${Fmt.money(it.total)}" +
                if (it.fullTank) "" else "  (parcial)", 12, Palette.MUTED), k.lp(top = 8))
        }

        val row = k.hbox()
        val fill = k.pill("⛽  Abastecer") { askFuel() }
        row.addView(fill, weighted(fill))
        if (fs.realKmPerLiter != null || fs.lastPricePerLiter != null) {
            val use = k.ghost("Usar no Farol") { applyRealFuel() }
            row.addView(use, weighted(use, 10))
        }
        card.addView(row, k.lp(top = 16))
        if (fuel.isNotEmpty()) {
            card.addView(k.text("Desfazer último abastecimento", 12, Palette.MUTED).apply {
                gravity = Gravity.CENTER; setPadding(0, k.dp(12), 0, 0)
                isClickable = true; setOnClickListener { undoFuel() }
            })
        }
        add(card)
    }

    // period
    private fun periodCard() {
        val days = store.days()
        val fuel = store.fuel()
        val sel = if (periodMonth) store.thisMonth() else store.lastDays(7)
        val p = JournalMath.period(days, fuel, sel, settings.profile.costPerKm, store::dateOf)

        val card = k.card()
        card.addView(k.hbox().apply {
            background = k.rounded(Palette.SURFACE2, 12)
            setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4))
            listOf("7 dias" to false, "Este mês" to true).forEach { (label, month) ->
                val active = month == periodMonth
                val tab = k.text(label, 13, if (active) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, k.dp(8), 0, k.dp(8))
                    if (active) background = k.rounded(Palette.ACCENT, 10)
                    isClickable = true
                    setOnClickListener { periodMonth = month; render() }
                }
                addView(tab, weighted(tab))
            }
        })

        fun stat(label: String, value: String) = k.vbox().apply {
            addView(k.text(value, 17, Palette.TEXT, k.bold))
            addView(k.text(label, 12, Palette.MUTED), k.lp(top = 3))
        }
        fun pair(a: View, b: View) = k.hbox().apply { addView(a, weighted(a)); addView(b, weighted(b)) }
        card.addView(pair(stat("Ganhos", Fmt.money(p.earnings)), stat("Líquido", Fmt.money(p.net))), k.lp(top = 16))
        card.addView(pair(stat("Km", Fmt.km(p.km)), stat("R$/km", if (p.km > 0) Fmt.money(p.earnings / p.km) else "—")), k.lp(top = 14))
        card.addView(k.text("${p.days} dias trabalhados" + if (p.fuelSpent > 0) "  •  combustível pago ${Fmt.money(p.fuelSpent)}" else "",
            12, Palette.MUTED), k.lp(top = 12))

        val cal = Calendar.getInstance()
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val wd = SimpleDateFormat("EEE", ptBR)
        val keys = (6 downTo 0).map { i -> (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) } }
        val byDate = days.associateBy { it.date }
        val chart = BarChartView(this).apply {
            values = keys.map { (byDate[dayFmt.format(it.time)]?.totalEarnings ?: 0.0).toFloat() }
            labels = keys.map { wd.format(it.time).take(3).replace(".", "") }
            valueFormatter = { "R$ ${it.toInt()}" }
        }
        card.addView(k.overline("Ganhos por dia"), k.lp(top = 20))
        card.addView(chart, k.lp(top = 8))
        add(card)
    }

    private fun footer() {
        add(k.ghost("📋  Copiar dias para planilha (CSV)") { copy(store.daysCsv(settings.profile.costPerKm)) }, 18)
    }

    // ============================== diálogos ==============================

    private fun dialog(title: String, content: View, ok: String, onOk: () -> Boolean) {
        val wrap = k.vbox(22, 8).apply { addView(content) }
        val d = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setView(wrap)
            .setPositiveButton(ok, null).setNegativeButton("Cancelar", null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (onOk()) { d.dismiss(); render() } }
        }
        d.show()
    }

    private fun labeled(label: String, field: EditText) = k.vbox().apply {
        addView(k.text(label, 12, Palette.MUTED, k.medium))
        addView(field, k.lp(top = 6))
    }

    private fun lastKnownKm(): Double? = (store.days().mapNotNull { it.kmEnd } + store.fuel().map { it.odometer }).maxOrNull()

    private fun fail(msg: String): Boolean { toast(msg); return false }

    private fun askStart() {
        val f = k.field("Km do painel", lastKnownKm()?.let { fmtKm(it) } ?: "")
        dialog("Iniciar turno", labeled("Km do painel agora", f), "Iniciar") {
            val km = f.num() ?: return@dialog fail("Digite o km do painel")
            val d = store.day()
            store.putDay(d.copy(kmStart = km, startAt = d.startAt ?: System.currentTimeMillis()))
            toast("Turno iniciado. Boas corridas!"); true
        }
    }

    private fun askEnd() {
        val d = store.day()
        val f = k.field("Km do painel")
        dialog("Encerrar turno", labeled("Km do painel agora (início: ${fmtKm(d.kmStart)})", f), "Encerrar") {
            val km = f.num() ?: return@dialog fail("Digite o km do painel")
            if (d.kmStart != null && km < d.kmStart) return@dialog fail("O km final é menor que o inicial")
            val up = d.copy(kmEnd = km, endAt = System.currentTimeMillis())
            store.putDay(up)
            sendDaySummary(up)
            toast("Turno encerrado"); true
        }
    }

    private fun askEditShift() {
        val d = store.day()
        val a = k.field("Km inicial", fmtKm(d.kmStart))
        val b = k.field("Km final", fmtKm(d.kmEnd))
        val box = k.vbox().apply {
            addView(labeled("Km inicial", a)); addView(labeled("Km final", b), k.lp(top = 12))
        }
        dialog("Editar km do turno", box, "Salvar") {
            val s = a.num(); val e = b.num()
            if (s != null && e != null && e < s) return@dialog fail("O km final é menor que o inicial")
            store.putDay(d.copy(kmStart = s, kmEnd = e)); true
        }
    }

    private fun editEarnings() {
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

    private fun askFuel() {
        val odo = k.field("Km do painel", lastKnownKm()?.let { fmtKm(it) } ?: "")
        val liters = k.field("Litros")
        val total = k.field("R$ pago")
        val full = CheckBox(this).apply { text = "Completei o tanque"; isChecked = true; setTextColor(Palette.TEXT) }
        val lv = labeled("Litros", liters)
        val tv = labeled("Valor pago", total)
        val box = k.vbox().apply {
            addView(labeled("Km do painel", odo))
            addView(k.hbox().apply { addView(lv, weighted(lv)); addView(tv, weighted(tv, 10)) }, k.lp(top = 12))
            addView(full, k.lp(top = 10))
            addView(k.text("Completar o tanque toda vez é o que permite medir o consumo real.", 12, Palette.MUTED), k.lp(top = 2))
        }
        dialog("Abastecimento", box, "Registrar") {
            val o = odo.num() ?: return@dialog fail("Digite o km do painel")
            val l = liters.num()?.takeIf { it > 0 } ?: return@dialog fail("Digite os litros")
            val t = total.num()?.takeIf { it > 0 } ?: return@dialog fail("Digite o valor pago")
            store.addFuel(FuelEntry(System.currentTimeMillis(), o, l, t, full.isChecked))
            toast("Abastecimento registrado: ${Fmt.money(t / l)}/L"); true
        }
    }

    private fun undoFuel() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage("Apagar o último abastecimento registrado?")
            .setPositiveButton("Apagar") { _, _ -> store.removeLastFuel(); render() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun applyRealFuel() {
        val fs = JournalMath.fuelStats(store.fuel())
        val p = settings.profile
        settings.profile = p.copy(
            kmPerLiter = fs.realKmPerLiter?.let { Math.round(it * 10) / 10.0 } ?: p.kmPerLiter,
            fuelPrice = fs.lastPricePerLiter?.let { Math.round(it * 100) / 100.0 } ?: p.fuelPrice,
        )
        toast("Custo atualizado: ${Fmt.money(settings.profile.costPerKm)}/km")
        render()
    }

    // ============================== utilidades ==============================

    private fun sendDaySummary(d: DayRecord) {
        val s = JournalMath.summarize(d, settings.profile.costPerKm)
        runCatching {
            RemoteLog.get(this).day(JSONObject()
                .put("data", d.date).put("km", s.km).put("horas", s.hours)
                .put("ganhos", JSONObject(d.earnings)).put("ganho_total", s.earnings)
                .put("custo", s.cost).put("liquido", s.net).put("r_km", s.perKm).put("r_h", s.perHour)
                .put("ofertas", history.recent(500).count { it.optLong("t") >= (d.startAt ?: 0L) }))
        }
    }

    private fun serviceOn(): Boolean {
        val enabled = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, OfferReaderService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me } && OfferReaderService.instance != null
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

    private fun fmtKm(v: Double?): String = v?.let {
        if (it == Math.floor(it)) String.format(ptBR, "%,d", it.toLong()) else String.format(ptBR, "%,.1f", it)
    } ?: "—"

    private fun fmtMoney(v: Double) = String.format(ptBR, "%.2f", v)

    private fun copy(txt: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("farol", txt))
        toast("Copiado — cole numa planilha")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
