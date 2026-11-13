package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalMathTest {

    @Test fun resumoDoDia() {
        val day = DayRecord(
            date = "2026-11-11", kmStart = 41_230.0, kmEnd = 41_350.0,
            startAt = 0L, endAt = 6 * 3_600_000L,
            earnings = mapOf("iFood" to 92.40, "99" to 58.10, "Keeta" to 31.50),
        )
        val s = JournalMath.summarize(day, costPerKm = 0.36)
        assertEquals(182.0, s.earnings, 0.001)
        assertEquals(120.0, s.km!!, 0.001)
        assertEquals(43.2, s.cost!!, 0.001)
        assertEquals(138.8, s.net!!, 0.001)
        assertEquals(1.5167, s.perKm!!, 0.001)
        assertEquals(30.333, s.perHour!!, 0.001)
    }

    @Test fun diaSemKmFinal_semCalculosDeKm() {
        val s = JournalMath.summarize(DayRecord("2026-11-11", kmStart = 100.0, earnings = mapOf("iFood" to 50.0)), 0.36)
        assertEquals(50.0, s.earnings, 0.001)
        assertNull(s.km); assertNull(s.net); assertNull(s.perKm)
    }

    @Test fun consumoReal_tanqueCheioATanqueCheio_comParcialNoMeio() {
        val f = listOf(
            FuelEntry(1, 41_000.0, 4.0, 25.20, fullTank = true),   // referência
            FuelEntry(2, 41_100.0, 1.0, 6.30, fullTank = false),   // parcial
            FuelEntry(3, 41_280.0, 7.0, 44.10, fullTank = true),   // 280 km com 8 L
        )
        val s = JournalMath.fuelStats(f)
        assertEquals(35.0, s.realKmPerLiter!!, 0.001)
        assertEquals(6.30, s.lastPricePerLiter!!, 0.001)
    }

    @Test fun umAbastecimentoSo_semConsumo() {
        val s = JournalMath.fuelStats(listOf(FuelEntry(1, 41_000.0, 4.0, 25.0)))
        assertNull(s.realKmPerLiter)
        assertEquals(6.25, s.lastPricePerLiter!!, 0.001)
    }

    @Test fun periodo() {
        val days = listOf(
            DayRecord("2026-11-10", 100.0, 200.0, earnings = mapOf("iFood" to 150.0)),
            DayRecord("2026-11-11", 200.0, 320.0, earnings = mapOf("99" to 170.0)),
            DayRecord("2026-11-01", 0.0, 50.0, earnings = mapOf("99" to 70.0)),
        )
        val fuel = listOf(FuelEntry(10, 0.0, 5.0, 31.5))
        val p = JournalMath.period(days, fuel, setOf("2026-11-10", "2026-11-11"), 0.36) { "2026-11-11" }
        assertEquals(2, p.days)
        assertEquals(320.0, p.earnings, 0.001)
        assertEquals(220.0, p.km, 0.001)
        assertEquals(320.0 - 220 * 0.36, p.net, 0.001)
        assertEquals(31.5, p.fuelSpent, 0.001)
    }

    @Test fun pausa_naoContaNasHoras_eExcluirMantemGanhos() {
        val h = 3_600_000L
        var d = DayRecord("2026-11-13", kmStart = 100.0, startAt = 0L, earnings = mapOf("iFood" to 80.0))
        d = d.pause(2 * h)
        assertEquals(2 * h, d.workedMs(3 * h))          // parado de 2h a 3h
        d = d.resume(3 * h)
        assertEquals(h, d.pausedMs)
        d = d.copy(kmEnd = 150.0, endAt = 6 * h)
        assertEquals(5.0, d.hours!!, 0.001)            // 6h de turno - 1h de pausa
        val x = d.clearShift()
        assertNull(x.kmStart); assertNull(x.startAt); assertEquals(0L, x.pausedMs)
        assertEquals(80.0, x.totalEarnings, 0.001)
    }
}
