package com.farol.app.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Metas: você define só a meta do DIA e quantos dias trabalha por semana; a semana e o mês saem daí.
 * O que conta é o líquido (ganhos − custo do km − gastos lançados).
 * Dia sem trabalho que já passou (ou marcado como folga) não conta contra você: o que falta é
 * dividido só pelos dias de trabalho que ainda restam.
 */
object GoalMath {

    data class Progress(
        val target: Double,
        val done: Double,
        val workDaysLeft: Int,     // incluindo hoje, se hoje ainda é dia de trabalho
        val daysOff: Int,          // folgas no período até ontem
    ) {
        val remaining: Double get() = (target - done).coerceAtLeast(0.0)
        val pct: Int get() = if (target > 0) (done / target * 100).roundToInt().coerceIn(0, 999) else 0
        val perDayNeeded: Double? get() = if (remaining <= 0 || workDaysLeft <= 0) null else remaining / workDaysLeft
    }

    data class Goals(val day: Progress, val week: Progress, val month: Progress)

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Líquido do dia: ganhos − custo do km (quando há km) − gastos. */
    fun dayNet(d: DayRecord, costPerKm: Double): Double =
        d.totalEarnings - (d.kmDriven?.let { it * costPerKm } ?: 0.0) - d.totalExpenses

    private fun worked(d: DayRecord?) = d != null && !d.dayOff && (d.hasShift || d.totalEarnings > 0)

    fun compute(days: List<DayRecord>, today: String, dailyGoal: Double, workDaysPerWeek: Int, costPerKm: Double): Goals {
        val byDate = days.associateBy { it.date }
        val wd = workDaysPerWeek.coerceIn(1, 7)
        val cal = Calendar.getInstance().apply { time = fmt.parse(today)!! }
        fun net(date: String) = byDate[date]?.let { dayNet(it, costPerKm) } ?: 0.0
        val todayRec = byDate[today]
        val todayOff = todayRec?.dayOff == true

        // ----- semana (segunda a domingo)
        val wCal = cal.clone() as Calendar
        val dow = (wCal.get(Calendar.DAY_OF_WEEK) + 5) % 7      // segunda = 0 … domingo = 6
        wCal.add(Calendar.DAY_OF_YEAR, -dow)
        val weekDates = (0 until 7).map { i -> fmt.format((wCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }.time) }
        val pastW = weekDates.filter { it < today }
        val workedBefore = pastW.count { worked(byDate[it]) }
        val offW = pastW.size - workedBefore
        val calLeftW = weekDates.count { it > today } + if (todayOff) 0 else 1
        val week = Progress(
            target = dailyGoal * wd,
            done = weekDates.filter { it <= today }.sumOf { net(it) },
            workDaysLeft = minOf(calLeftW, (wd - workedBefore).coerceAtLeast(0)),
            daysOff = offW,
        )

        // ----- mês
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val prefix = today.substring(0, 8)
        val monthDates = (1..daysInMonth).map { prefix + it.toString().padStart(2, '0') }
        val workDaysMonth = (daysInMonth * wd / 7.0).roundToInt()
        val pastM = monthDates.take(dom - 1)
        val workedBeforeM = pastM.count { worked(byDate[it]) }
        val month = Progress(
            target = dailyGoal * workDaysMonth,
            done = monthDates.take(dom).sumOf { net(it) },
            workDaysLeft = minOf(daysInMonth - dom + (if (todayOff) 0 else 1), (workDaysMonth - workedBeforeM).coerceAtLeast(0)),
            daysOff = pastM.size - workedBeforeM,
        )

        val day = Progress(target = if (todayOff) 0.0 else dailyGoal, done = net(today), workDaysLeft = if (todayOff) 0 else 1, daysOff = 0)
        return Goals(day, week, month)
    }
}
