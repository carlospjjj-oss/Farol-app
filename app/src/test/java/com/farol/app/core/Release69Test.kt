package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 6.9: evolução do dia (ganho acumulado pelas entregas com hora). */
class Release69Test {
    private val h = 3_600_000L

    @Test fun curveStartsAtShiftAndAccumulates() {
        val s = 1_000_000_000_000L
        val d = DayRecord("2026-11-14", startAt = s, endAt = s + 5 * h,
            orders = mapOf("Keeta#1" to 10.0, "Keeta#2" to 14.0, "99\$saldo:x" to 8.0),
            orderAt = mapOf("Keeta#1" to s + h, "Keeta#2" to s + 3 * h))
        val c = Indicators.dayCurve(d, s + 9 * h)
        assertEquals(listOf(s to 0.0, s + h to 10.0, s + 3 * h to 24.0), c.take(3))
        // o total do dia (maior família por app: Keeta 24 + 99 8 = 32) fecha a curva no fim do turno
        assertEquals(s + 5 * h to 32.0, c.last())
    }

    @Test fun noTimedOrdersNoCurve() {
        assertTrue(Indicators.dayCurve(DayRecord("2026-11-14", orders = mapOf("Keeta#1" to 10.0)), 0L).isEmpty())
    }
}
