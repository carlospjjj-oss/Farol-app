package com.farol.app.ui

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.farol.app.core.DayRecord
import com.farol.app.core.Expense
import com.farol.app.core.Fmt
import com.farol.app.core.GoalMath
import java.util.Date

/* Gastos do dia e metas (dia / semana / mês). */

internal fun DashboardActivity.goals(): GoalMath.Goals? {
    val goal = settings.dailyGoal
    if (goal <= 0) return null
    return GoalMath.compute(store.days(), store.day().date, goal, settings.workDaysPerWeek, settings.profile.costPerKm)
}

// gastos
internal fun DashboardActivity.expensesCard(day: DayRecord) {
    val card = k.card()
    card.addView(k.hbox().apply {
        val t = k.overline("Gastos do dia")
        addView(t, weighted(t))
        if (day.expenses.isNotEmpty()) addView(k.text(Fmt.money(day.totalExpenses), 15, Palette.TEXT, k.bold))
    })
    if (day.expenses.isEmpty()) {
        card.addView(k.text("Almoço, café, uma peça, um lava-rápido… lance aqui e o líquido do dia já desconta. O combustível já entra no custo por km.",
            13, Palette.MUTED), k.lp(top = 10))
    }
    day.expenses.sortedBy { it.at }.forEach { e ->
        card.addView(k.hbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            val emoji = when (e.category) { "alimentacao" -> "🍽️"; "manutencao" -> "🔧"; else -> "•" }
            addView(k.text(emoji, 15), k.lp(w = -2))
            val name = k.text(Expense.label(e.category) + (if (e.note.isNotBlank()) " · ${e.note}" else ""), 14, Palette.TEXT, k.medium)
            addView(name, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
            addView(k.text(Fmt.money(e.value), 14, Palette.TEXT, k.bold))
            addView(k.text("✕", 15, Palette.MUTED).apply {
                setPadding(k.dp(14), k.dp(8), k.dp(4), k.dp(8))
                contentDescription = "Apagar gasto"
                isClickable = true
                setOnClickListener {
                    AlertDialog.Builder(self, Palette.dialogTheme)
                        .setMessage("Apagar ${Expense.label(e.category)} de ${Fmt.money(e.value)}?")
                        .setPositiveButton("Apagar") { _, _ ->
                            val d = store.day(day.date)
                            store.putDay(d.copy(expenses = d.expenses - e)); syncSoon(); render()
                        }
                        .setNegativeButton("Cancelar", null).show()
                }
            })
        }, k.lp(top = 10))
    }
    card.addView(k.pill("＋  Lançar gasto") { askExpense() }, k.lp(top = 14))
    add(card)
}

internal fun DashboardActivity.askExpense() {
    var cat = "alimentacao"
    val seg = k.hbox()
    fun renderSeg() {
        seg.removeAllViews()
        Expense.CATEGORIES.forEachIndexed { i, (key, label) ->
            val on = key == cat
            val emoji = when (key) { "alimentacao" -> "🍽️"; "manutencao" -> "🔧"; else -> "•••" }
            seg.addView(k.vbox().apply {
                gravity = Gravity.CENTER
                setPadding(0, k.dp(10), 0, k.dp(10))
                background = k.rounded(if (on) Palette.alpha(Palette.ACCENT, 0x30) else Palette.SURFACE2, 14, if (on) Palette.ACCENT else Palette.STROKE)
                addView(k.text(emoji, 18).apply { gravity = Gravity.CENTER })
                addView(k.text(label, 13, Palette.TEXT, k.bold).apply { gravity = Gravity.CENTER }, k.lp(top = 4))
                isClickable = true
                contentDescription = label
                setOnClickListener { cat = key; renderSeg() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = k.dp(8) })
        }
    }
    renderSeg()
    val value = k.field("R$ 0,00").apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
    val note = k.field("Ex.: marmita, óleo, estacionamento")
    val day = store.day()
    val box = k.vbox().apply {
        addView(seg)
        addView(labeled("Valor", value), k.lp(top = 14))
        addView(labeled("Observação (opcional)", note), k.lp(top = 12))
        val s = com.farol.app.core.JournalMath.summarize(day, settings.profile.costPerKm)
        addView(k.text("Líquido do dia agora: ${Fmt.money(s.netAfterExpenses)}", 12, Palette.MUTED), k.lp(top = 10))
    }
    dialog("Novo gasto", box, "Salvar") {
        val v = value.num()?.takeIf { it > 0 && it < 10_000 } ?: return@dialog fail("Digite o valor")
        val d = store.day()
        store.putDay(d.copy(expenses = d.expenses + Expense(System.currentTimeMillis(), cat, v, note.text.toString().trim().take(60))))
        syncSoon()
        toast("Gasto de ${Fmt.money(v)} lançado"); true
    }
}

private fun DashboardActivity.syncSoon() = runCatching { com.farol.app.service.FarolJobs.syncSoon(this) }

// metas
internal fun DashboardActivity.goalsCard(day: DayRecord) {
    val g = goals()
    val card = k.card()
    card.addView(k.overline("Metas"))
    if (g == null) {
        card.addView(k.text("Defina a meta do dia em Ajustes › Metas: a semana e o mês saem dela, sem contar suas folgas.", 13, Palette.MUTED), k.lp(top = 10))
        card.addView(k.ghost("Definir meta") { startActivity(android.content.Intent(self, SettingsActivity::class.java)) }, k.lp(top = 12))
        add(card); return
    }
    fun block(title: String, p: GoalMath.Progress, hint: String?) {
        card.addView(k.hbox().apply {
            val t = k.text(title, 16, Palette.TEXT, k.bold)
            addView(t, weighted(t))
            addView(k.text("${Fmt.money(p.done)} / ${Fmt.money(p.target)}", 14, Palette.TEXT, k.bold))
        }, k.lp(top = 14))
        card.addView(k.bar((if (p.target > 0) p.done / p.target else 0.0).toFloat(), Palette.ACCENT), k.lp(h = k.dp(8), top = 8))
        val line = when {
            p.remaining <= 0 -> "🎯 Batida! (${p.pct}%)"
            p.perDayNeeded != null -> "${p.pct}% · faltam ${Fmt.money(p.remaining)} · ~${Fmt.money(p.perDayNeeded!!)} por dia nos ${p.workDaysLeft} ${if (p.workDaysLeft == 1) "dia" else "dias"} de trabalho que restam"
            else -> "${p.pct}% · faltam ${Fmt.money(p.remaining)}"
        }
        card.addView(k.text(line + (hint ?: ""), 12, Palette.MUTED), k.lp(top = 6))
    }
    val offHint = if (g.week.daysOff > 0) " · ${g.week.daysOff} ${if (g.week.daysOff == 1) "folga" else "folgas"} sem contar contra você" else ""
    block("Semana", g.week, offHint)
    val month = java.text.SimpleDateFormat("MMMM", java.util.Locale("pt", "BR")).format(Date()).replaceFirstChar { it.uppercase() }
    block(month, g.month, null)

    // Folga: só faz sentido num dia sem turno e sem ganhos
    if (!day.hasShift && day.totalEarnings <= 0) {
        val lbl = if (day.dayOff) "Hoje é folga ✓ (toque para desfazer)" else "Hoje é folga"
        card.addView(k.ghost(lbl) {
            store.putDay(store.day(day.date).copy(dayOff = !day.dayOff)); syncSoon(); render()
        }, k.lp(top = 14))
    }
    add(card)
}

@Suppress("unused") private val keep: Class<View> = View::class.java
