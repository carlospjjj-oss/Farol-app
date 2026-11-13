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

/* Cartões do painel principal (cabeçalho, ganhos, ofertas, pedidos, horários, combustível, período). */

// header
internal fun DashboardActivity.header() {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (h) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    val date = SimpleDateFormat("EEEE, d 'de' MMMM", ptBR).format(Date()).replaceFirstChar { it.uppercase() }

    val modeChip = k.chip("${settings.modeLabel}  ⇄", Palette.ACCENT).apply {
        isClickable = true
        setOnClickListener { startActivity(Intent(self, ModeActivity::class.java)); finish() }
    }
    val left = k.vbox().apply {
        addView(k.hbox().apply {
            addView(k.text(date, 13, Palette.MUTED, k.medium))
            addView(modeChip, k.lp(w = -2).apply { marginStart = k.dp(10) })
        })
        val name = settings.userName.substringBefore(' ')
        val hello = if (name.isEmpty()) "$greeting 👋" else "$greeting, $name 👋"
        addView(k.text(hello, 26, Palette.TEXT, k.bold).apply {
            isClickable = true
            setOnClickListener { askName() }   // toque na saudação para trocar o nome
        }, k.lp(top = 6))
    }
    val gear = k.text("⚙️", 20).apply {
        gravity = Gravity.CENTER
        background = k.ripple(k.rounded(Palette.SURFACE, 999, Palette.STROKE))
        isClickable = true
        setOnClickListener { startActivity(Intent(self, SettingsActivity::class.java)) }
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
internal fun DashboardActivity.hero(day: DayRecord, s: DaySummary) {
    val dark = Palette.ON_ACCENT
    val card = k.vbox(22, 22).apply {
        background = k.gradient(0xFFD6F75A.toInt(), Palette.ACCENT2, radiusDp = 26)
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

    val goal = settings.dailyGoal
    if (goal > 0) {
        val frac = (s.earnings / goal).toFloat()
        val left = goal - s.earnings
        card.addView(k.bar(frac, dark).apply { background = k.rounded(Palette.alpha(0xFFFFFFFF.toInt(), 0x55), 999) },
            k.lp(h = k.dp(8), top = 16))
        card.addView(k.text(if (left <= 0) "🎯 Meta de ${Fmt.money(goal)} batida!" else "🎯 Meta ${Fmt.money(goal)} · faltam ${Fmt.money(left)}",
            13, dark, k.bold), k.lp(top = 8))
    }
    card.isClickable = true
    card.setOnClickListener { editEarnings() }
    add(card, 8)
}

// kpis
internal fun DashboardActivity.kpis(s: DaySummary) {
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

// earnings
internal fun DashboardActivity.earningsCard(day: DayRecord) {
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
            addView(View(self), LinearLayout.LayoutParams(0, 1, 1f))
            addView(k.text(Fmt.money(v), 15, Palette.TEXT, k.bold))
        }, k.lp(top = 14))
        card.addView(k.bar((v / max).toFloat(), Palette.app(app)), k.lp(h = k.dp(6), top = 8))
    }
    card.addView(k.pill("✏️  Lançar ganhos") { editEarnings() }, k.lp(top = 16))
    add(card)
}

// offers
internal fun DashboardActivity.offersCard() {
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
    card.addView(k.ghost("📋  Ver histórico de ofertas") {
        startActivity(Intent(self, HistoryActivity::class.java))
    }, k.lp(top = 16))
    add(card)
}

// tempo por pedido
internal fun DashboardActivity.deliveriesCard() {
    val all = store.deliveries()
    val todayKey = store.today()
    val today = all.filter { store.dateOf(it.collectAt) == todayKey }
    val last14 = store.lastDays(14)
    val recent = all.filter { store.dateOf(it.collectAt) in last14 }

    val card = k.card()
    card.addView(k.hbox().apply {
        val t = k.overline("Tempo por pedido")
        addView(t, weighted(t))
        addView(k.text("${today.size} hoje", 15, Palette.TEXT, k.bold))
    })
    if (recent.isEmpty()) {
        card.addView(k.text(if (settings.isCar) "Ainda sem corridas medidas. As etapas das corridas entram quando os logs mostrarem as telas de Uber, 99 e inDrive."
            else "Ainda sem pedidos medidos. O Farol acompanha as telas de coleta e entrega do Keeta (iFood e 99 em breve).",
            13, Palette.MUTED), k.lp(top = 10))
        add(card); return
    }
    val base = if (today.isNotEmpty()) today else recent
    val col = base.mapNotNull { it.collectMin }
    val del = base.mapNotNull { it.deliverMin }
    fun stat(label: String, value: String) = k.vbox().apply {
        addView(k.text(value, 18, Palette.TEXT, k.bold))
        addView(k.text(label, 12, Palette.MUTED), k.lp(top = 3))
    }
    val a = stat("até coletar (média)", if (col.isEmpty()) "—" else Fmt.min(col.average()))
    val b = stat("para entregar (média)", if (del.isEmpty()) "—" else Fmt.min(del.average()))
    card.addView(k.hbox().apply { addView(a, weighted(a)); addView(b, weighted(b)) }, k.lp(top = 14))
    card.addView(k.text(if (today.isNotEmpty()) "Pedidos de hoje" else "Últimos 14 dias (nenhum hoje ainda)", 12, Palette.MUTED), k.lp(top = 8))

    // Restaurantes mais demorados (14 dias, pelo menos 1 pedido)
    val slow = recent.filter { it.restaurant != null && it.collectMin != null }
        .groupBy { it.restaurant!! }
        .map { (r, l) -> Triple(r, l.mapNotNull { it.collectMin }.average(), l.size) }
        .sortedByDescending { it.second }.take(3)
    if (slow.isNotEmpty()) {
        card.addView(k.overline("Restaurantes mais demorados"), k.lp(top = 16))
        slow.forEach { (r, m, n) ->
            val c = when { m >= 15 -> Palette.RED; m >= 10 -> Palette.YELLOW; else -> Palette.GREEN }
            card.addView(k.hbox().apply {
                addView(k.dot(c, 8))
                val name = k.text(r, 13, Palette.TEXT)
                addView(name, weighted(name, 10))
                addView(k.text("${Fmt.min(m)} · ${n}x", 12, Palette.MUTED))
            }, k.lp(top = 10))
        }
    }
    add(card)
}

// melhores horários
internal fun DashboardActivity.hoursCard() {
    val since = System.currentTimeMillis() - 14L * 24 * 3_600_000
    val cal = Calendar.getInstance()
    val offers = history.recent(5000).filter { it.optLong("t") >= since }.map {
        cal.timeInMillis = it.optLong("t")
        Triple(cal.get(Calendar.HOUR_OF_DAY), it.optDouble("perKm"), it.optString("rating") == "GREEN")
    }.filter { !it.second.isNaN() }

    val card = k.card()
    card.addView(k.hbox().apply {
        val t = k.overline("Melhores horários")
        addView(t, weighted(t))
        addView(k.text("14 dias", 13, Palette.MUTED, k.medium))
    })
    if (offers.size < 10) {
        card.addView(k.text("Preciso de mais ofertas lidas para mostrar os horários (${offers.size} até agora).", 13, Palette.MUTED), k.lp(top = 10))
        add(card); return
    }
    val stats = HourInsights.byHour(offers)
    val best = HourInsights.best(stats)
    val first = maxOf(6, stats.minOf { it.hour }); val last = minOf(23, stats.maxOf { it.hour })
    val hours = (first..last).toList()
    val byH = stats.associateBy { it.hour }
    val chart = BarChartView(this).apply {
        values = hours.map { (byH[it]?.avgPerKm ?: 0.0).toFloat() }
        labels = hours.map { "$it" }
        highlight = best.map { hours.indexOf(it.hour) }.filter { it >= 0 }.toSet()
        valueFormatter = { String.format(ptBR, "%.2f", it) }
    }
    card.addView(k.text("R$/km médio ofertado por hora do dia", 12, Palette.MUTED), k.lp(top = 10))
    card.addView(chart, k.lp(top = 6))
    if (best.isNotEmpty()) {
        card.addView(k.overline("Onde vale mais"), k.lp(top = 14))
        best.forEach { h ->
            card.addView(k.hbox().apply {
                addView(k.dot(Palette.ACCENT, 8))
                val l = k.text("${h.hour}h – ${h.hour + 1}h", 14, Palette.TEXT, k.medium)
                addView(l, weighted(l, 10))
                addView(k.text("${Fmt.money(h.avgPerKm)}/km · ${h.offers} ofertas · ${h.greens} 🟢", 12, Palette.MUTED))
            }, k.lp(top = 10))
        }
    }
    val busiest = stats.maxByOrNull { it.offers }
    busiest?.let { card.addView(k.text("Mais ofertas: ${it.hour}h – ${it.hour + 1}h (${it.offers})", 12, Palette.MUTED), k.lp(top = 12)) }
    add(card)
}

// fuel
internal fun DashboardActivity.fuelCard() {
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
internal fun DashboardActivity.periodCard() {
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

internal fun DashboardActivity.footer() {
    add(k.ghost("📋  Copiar dias para planilha (CSV)") { copy(store.daysCsv(settings.profile.costPerKm)) }, 18)
}
