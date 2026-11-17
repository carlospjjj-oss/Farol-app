package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 16/11: km do abastecimento "58269,2" gravado como 582692. */
class Release6110Test {
    private val days = listOf(
        DayRecord("2026-11-12", kmStart = 58081.7, kmEnd = 58176.4),
        DayRecord("2026-11-13", kmStart = 58203.5, kmEnd = 58254.9),
        DayRecord("2026-11-16", kmStart = 58269.7, kmEnd = 58395.8),
    )
    private val bad = FuelEntry(2L, 582692.0, 2.86, 19.71, true, "gasolina")
    private val first = FuelEntry(1L, 58110.0, 5.4, 31.28, true)

    @Test fun ultimoKmIgnoraDigitacaoErrada() =
        assertEquals(58395.8, JournalMath.lastKnownKm(days, listOf(first, bad))!!, 0.001)

    @Test fun kmComDigitoAMaisViraSugestao() {
        assertEquals(58269.2, JournalMath.odometerFix(582692.0, 58254.9)!!, 0.001)
        assertNull(JournalMath.odometerFix(58400.0, 58395.8))          // normal
        assertNull(JournalMath.odometerFix(5.0, 58395.8))              // sem jeito: só pede para conferir
    }

    @Test fun trechoImpossivelNaoEntraNoConsumo() {
        val s = JournalMath.fuelStats(listOf(first, bad), "gasolina")
        assertNull(s.realKmPerLiter)
        val ok = JournalMath.fuelStats(listOf(first, bad.copy(odometer = 58269.2)), "gasolina")
        assertEquals(159.2 / 2.86, ok.realKmPerLiter!!, 0.01)
    }
}
