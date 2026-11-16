package com.farol.app.ui

import android.app.DatePickerDialog
import android.view.Gravity
import com.farol.app.core.Fmt
import com.farol.app.core.JournalMath
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Financeiro › dia escolhido (6.10.9, exceção ao congelamento pedida pelo Carlos em 16/11):
 * ‹ dia › para ver os ganhos de qualquer dia, com os botões de corrigir turno e ganhos daquele dia.
 */
private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun shift(date: String, days: Int): String =
    ISO.format(Calendar.getInstance().apply { time = ISO.parse(date)!!; add(Calendar.DAY_OF_YEAR, days) }.time)

internal fun DashboardActivity.financeDate(): String {
    val today = store.day().date
    return finDay?.takeIf { it < today } ?: today
}

internal fun DashboardActivity.dayPickerCard() {
    val today = store.day().date
    val date = financeDate()
    val d = store.day(date)
    val s = JournalMath.summarize(d, settings.profile.costPerKm)
    val card = k.card()

    val label = if (date == today) "Hoje" else
        SimpleDateFormat("EEE, dd/MM", ptBR).format(ISO.parse(date)!!).replaceFirstChar { it.uppercase() }
    fun go(to: String?) { finDay = to?.takeIf { it < today }; render() }
    card.addView(k.hbox().apply {
        gravity = Gravity.CENTER_VERTICAL
        val prev = k.text("‹", 26, Palette.TEXT, k.bold).apply {
            setPadding(k.dp(14), 0, k.dp(14), 0); isClickable = true
            setOnClickListener { go(shift(date, -1)) }
        }
        val mid = k.text("📅  $label", 16, Palette.TEXT, k.bold).apply {
            gravity = Gravity.CENTER; isClickable = true
            setOnClickListener {
                val c = Calendar.getInstance().apply { time = ISO.parse(date)!! }
                DatePickerDialog(this@dayPickerCard, { _, y, m, dd ->
                    go("%04d-%02d-%02d".format(y, m + 1, dd))
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }
        }
        val next = k.text("›", 26, if (date < today) Palette.TEXT else Palette.STROKE, k.bold).apply {
            setPadding(k.dp(14), 0, k.dp(14), 0); isClickable = date < today
            setOnClickListener { go(shift(date, 1)) }
        }
        addView(prev); addView(mid, weighted(mid)); addView(next)
    })

    fun stat(name: String, value: String) = k.vbox().apply {
        addView(k.text(value, 17, Palette.TEXT, k.bold))
        addView(k.text(name, 12, Palette.MUTED), k.lp(top = 3))
    }
    fun pair(a: android.view.View, b: android.view.View) = k.hbox().apply { addView(a, weighted(a)); addView(b, weighted(b)) }
    if (s.earnings <= 0 && !d.hasShift) {
        card.addView(k.text("Sem turno nem ganhos neste dia.", 13, Palette.MUTED), k.lp(top = 14))
    } else {
        card.addView(pair(stat("Ganhos", Fmt.money(s.earnings)), stat("Líquido", Fmt.money(s.netAfterExpenses))), k.lp(top = 14))
        card.addView(pair(stat("Km", s.km?.let { Fmt.km(it) } ?: "—"), stat("R$/km", s.perKm?.let { Fmt.money(it) } ?: "—")), k.lp(top = 12))
        val apps = d.appEarnings.filterValues { it > 0 }.entries.sortedByDescending { it.value }
            .joinToString("  •  ") { "${it.key} ${Fmt.money(it.value)}" }
        val hrs = s.hours?.let { "${Fmt.min(it * 60)} trabalhadas" }
        listOfNotNull(apps.ifEmpty { null }, hrs).forEach { card.addView(k.text(it, 12, Palette.MUTED), k.lp(top = 8)) }
    }
    card.addView(k.hbox().apply {
        val a = k.ghost("✏️  Turno") { askEditShift(date) }
        val b = k.ghost("💰  Ganhos") { editEarnings(date) }
        addView(a, weighted(a)); addView(b, weighted(b, 10))
    }, k.lp(top = 14))
    if (date != today) card.addView(k.text("Voltar para hoje", 13, Palette.ACCENT, k.medium).apply {
        gravity = Gravity.CENTER; isClickable = true; setPadding(0, k.dp(10), 0, 0)
        setOnClickListener { go(null) }
    })
    add(card)
}
