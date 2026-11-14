package com.farol.app.ui

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.farol.app.core.DayRecord
import com.farol.app.core.Fmt
import com.farol.app.core.Indicators
import com.farol.app.core.JournalMath
import com.farol.app.core.Maintenance
import com.farol.app.data.MaintenanceStore
import java.util.Date

/*
 * Abas do painel (6.9): a tela longa virou 5 abas com a barra embaixo, no visual do Farol.
 *   Início      — o que importa durante o turno: avisos, ganhos e meta, km/R$, turno, evolução do dia, ganhos por app
 *   Corridas    — entregas concluídas (hoje/semana/mês), ofertas lidas, tempo por pedido
 *   Financeiro  — resumo do período, gastos, combustível, planilha
 *   Análises    — indicadores (ticket, entregas/h, por app e horário) e melhores horários
 *   Mais        — metas, manutenção e caixinha, ajustes e atalhos
 */
internal enum class Tab(val key: String, val label: String, val emoji: String, val title: String) {
    INICIO("inicio", "Início", "🏠", ""),
    CORRIDAS("corridas", "Corridas", "🛵", "Corridas"),
    FINANCEIRO("financeiro", "Financeiro", "💰", "Financeiro"),
    ANALISES("analises", "Análises", "📊", "Análises"),
    MAIS("mais", "Mais", "☰", "Mais");
    companion object { fun of(k: String?) = values().firstOrNull { it.key == k } ?: INICIO }
}

internal var DashboardActivity.tab: Tab
    get() = Tab.of(settings.prefs.getString("dashTab", null))
    set(v) = settings.prefs.edit().putString("dashTab", v.key).apply()

/** Conteúdo da aba atual (o cabeçalho e a barra ficam fora da rolagem). */
internal fun DashboardActivity.renderTab(day: DayRecord) {
    val s = JournalMath.summarize(day, settings.profile.costPerKm)
    when (tab) {
        Tab.INICIO -> {
            alertsCard()
            hero(day, s)
            kpis(s)
            shiftCard(day, s)
            gpsCard(day)
            dayCurveCard(day)
            earningsCard(day)
        }
        Tab.CORRIDAS -> {
            ridesCard()
            offersCard()
            deliveriesCard()
        }
        Tab.FINANCEIRO -> {
            periodCard()
            expensesCard(day)
            fuelCard()
            footer()
        }
        Tab.ANALISES -> {
            indicatorsCard()
            hoursCard()
        }
        Tab.MAIS -> {
            goalsCard(day)
            maintenanceCard()
            moreMenu()
        }
    }
}

/** Barra de abas fixa embaixo. Pontinho vermelho: manutenção vencida (Mais). */
internal fun DashboardActivity.renderNav() {
    navBar.removeAllViews()
    val maintDue = runCatching {
        Maintenance.sorted(MaintenanceStore.load(settings), currentKm()).any { it.level == Maintenance.Level.DUE }
    }.getOrDefault(false)
    Tab.values().forEach { t ->
        val on = t == tab
        val item = k.vbox().apply {
            gravity = Gravity.CENTER
            setPadding(0, k.dp(6), 0, k.dp(6))
            contentDescription = t.label
            isClickable = true
            setOnClickListener {
                if (tab != t) { tab = t; render(); scroll.scrollTo(0, 0) }
            }
            val icon = k.text(if (t == Tab.CORRIDAS && settings.isCar) "🚗" else t.emoji, 19).apply {
                gravity = Gravity.CENTER
                setPadding(k.dp(14), k.dp(3), k.dp(14), k.dp(3))
                if (on) background = k.rounded(Palette.alpha(Palette.ACCENT, 0x2E), 999)
            }
            val iconBox = android.widget.FrameLayout(self).apply {
                addView(icon, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                if (t == Tab.MAIS && maintDue) addView(k.dot(Palette.RED, 8), android.widget.FrameLayout.LayoutParams(k.dp(8), k.dp(8), Gravity.TOP or Gravity.END))
            }
            addView(iconBox, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(k.text(t.label, 11, if (on) Palette.ACCENT else Palette.MUTED, if (on) k.bold else k.medium).apply {
                gravity = Gravity.CENTER; maxLines = 1
            }, k.lp(top = 2))
        }
        navBar.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
    }
}

// evolução do dia
internal fun DashboardActivity.dayCurveCard(day: DayRecord) {
    val pts = Indicators.dayCurve(day, System.currentTimeMillis())
    if (pts.size < 3) return   // precisa de pelo menos 2 entregas com hora
    val card = k.card()
    card.addView(k.hbox().apply {
        val t = k.overline("Evolução do dia")
        addView(t, weighted(t))
        addView(k.text(Fmt.money(pts.last().second), 15, Palette.TEXT, k.bold))
    })
    card.addView(AreaChartView(this).apply {
        points = pts
        xLabel = { hhmm.format(Date(it)) }
        yLabel = { Fmt.money(it) }
    }, k.lp(top = 10))
    add(card)
}

// corridas: entregas concluídas no período
internal var DashboardActivity.ridesPeriod: Int   // 0 = hoje, 1 = semana (7 dias), 2 = mês
    get() = settings.prefs.getInt("ridesPeriod", 0)
    set(v) = settings.prefs.edit().putInt("ridesPeriod", v).apply()

internal fun DashboardActivity.ridesCard() {
    val dates = when (ridesPeriod) { 0 -> setOf(store.today()); 1 -> store.lastDays(7); else -> store.thisMonth() }
    val days = store.days().filter { it.date in dates }
    val now = System.currentTimeMillis()
    val total = days.sumOf { it.totalEarnings }
    val orders = days.flatMap { d -> d.orders.filterKeys { '$' !in it }.map { (k, v) -> Triple(d, k, v) } }
    val hours = days.sumOf { d -> d.workedMs(now)?.let { it / 3_600_000.0 } ?: 0.0 }

    val card = k.card()
    card.addView(k.hbox().apply {
        background = k.rounded(Palette.SURFACE2, 12)
        setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4))
        listOf("Hoje", "7 dias", "Mês").forEachIndexed { i, label ->
            val active = i == ridesPeriod
            val t = k.text(label, 13, if (active) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
                gravity = Gravity.CENTER; setPadding(0, k.dp(8), 0, k.dp(8))
                if (active) background = k.rounded(Palette.ACCENT, 10)
                isClickable = true
                setOnClickListener { ridesPeriod = i; render() }
            }
            addView(t, weighted(t))
        }
    })
    fun tile(label: String, value: String) = k.vbox().apply {
        setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12))
        background = k.rounded(Palette.SURFACE2, 14)
        addView(k.text(value, 18, Palette.TEXT, k.bold))
        addView(k.text(label, 12, Palette.MUTED), k.lp(top = 2))
    }
    fun row(a: View, b: View) = k.hbox().apply { addView(a, weighted(a)); addView(b, weighted(b, 10)) }
    val avg = if (orders.isNotEmpty()) orders.sumOf { it.third } / orders.size else null
    card.addView(row(tile("Total", Fmt.money(total)), tile("Entregas lidas", orders.size.toString())), k.lp(top = 14))
    card.addView(row(tile("Média por entrega", avg?.let { Fmt.money(it) } ?: "—"),
        tile("Média por hora", if (hours >= 0.5) Fmt.money(total / hours) else "—")), k.lp(top = 10))

    val list = orders.sortedByDescending { (d, k, _) -> d.orderAt[k] ?: 0L }.take(if (ridesPeriod == 0) 30 else 15)
    if (list.isEmpty()) {
        card.addView(k.text("Nenhuma entrega lida no período. O Farol registra cada entrega concluída da Keeta e da 99 sozinho; os outros apps entram pelo histórico ou à mão.",
            13, Palette.MUTED), k.lp(top = 14))
    } else {
        card.addView(k.overline(if (ridesPeriod == 0) "Entregas de hoje" else "Últimas entregas"), k.lp(top = 18))
        list.forEach { (d, key, v) ->
            val app = DayRecord.appOf(key)
            val at = d.orderAt[key]
            card.addView(k.hbox().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(k.dp(12), k.dp(10), k.dp(12), k.dp(10))
                background = k.rounded(Palette.SURFACE2, 14)
                addView(k.text(app.take(2), 13, Palette.ON_ACCENT, k.bold).apply {
                    gravity = Gravity.CENTER
                    background = k.rounded(Palette.app(app), 10)
                }, LinearLayout.LayoutParams(k.dp(36), k.dp(36)))
                val mid = k.vbox().apply {
                    addView(k.text(app, 14, Palette.TEXT, k.medium))
                    addView(k.text((at?.let { (if (ridesPeriod == 0) "" else ddmm.format(Date(it)) + " ") + hhmm.format(Date(it)) } ?: d.date.substring(8) + "/" + d.date.substring(5, 7)) +
                        "  ·  #" + key.substringAfter('#').takeLast(4), 12, Palette.MUTED), k.lp(top = 2))
                }
                addView(mid, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(12) })
                addView(k.text(Fmt.money(v), 15, Palette.TEXT, k.bold))
            }, k.lp(top = 8))
        }
    }
    add(card)
}

// mais: atalhos
internal fun DashboardActivity.moreMenu() {
    val card = k.card()
    card.addView(k.overline("Atalhos"))
    fun item(emoji: String, label: String, sub: String, onClick: () -> Unit) = card.addView(k.hbox().apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, k.dp(12), 0, k.dp(12))
        isClickable = true
        setOnClickListener { onClick() }
        addView(k.bubble(emoji, Palette.ACCENT))
        val col = k.vbox().apply {
            addView(k.text(label, 15, Palette.TEXT, k.medium))
            addView(k.text(sub, 12, Palette.MUTED), k.lp(top = 2))
        }
        addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(12) })
        addView(k.text("›", 22, Palette.MUTED))
    }, k.lp(top = 4))
    item("⚙️", "Ajustes", "custos, faixas do semáforo, apps lidos, conta") { startActivity(Intent(self, SettingsActivity::class.java)) }
    item("📋", "Histórico de ofertas", "todas as ofertas lidas, com a cor e o motivo") { startActivity(Intent(self, HistoryActivity::class.java)) }
    item(if (settings.isCar) "🛵" else "🚗", "Trocar de modo", "agora: ${settings.modeLabel}") { startActivity(Intent(self, ModeActivity::class.java)); finish() }
    item("📱", "Guia do celular", "ligar a leitura e evitar que o celular desligue o Farol") { startActivity(Intent(self, OnboardingActivity::class.java)) }
    add(card)
}
