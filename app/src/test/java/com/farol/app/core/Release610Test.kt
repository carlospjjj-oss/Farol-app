package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 6.10: caixinhas e regiões. */
class Release610Test {
    @Test fun boxesBalances() {
        val days = listOf(
            DayRecord("2026-11-13", kmStart = 1000.0, kmEnd = 1100.0),
            DayRecord("2026-11-14", kmStart = 1100.0, kmEnd = 1200.0, expenses = listOf(Expense(1, "manutencao", 25.0))),
        )
        val fuel = listOf(FuelEntry(1, 1150.0, 5.0, 28.95))
        val b = Boxes.build(days, fuel, 0.30, 0.17, listOf(Boxes.Move(1, 50.0), Boxes.Move(2, -20.0)),
            mapOf("emergencia" to 300.0), dateOf = { "2026-11-14" })
        val m = b.first { it.key == "manutencao" }
        assertEquals(35.0, m.balance, 0.001)                  // 200 km × 0,30 − 25
        assertEquals(34.0 - 28.95, b.first { it.key == "combustivel" }.balance, 0.001)   // 200 × 0,17 − 28,95
        val e = b.first { it.key == "emergencia" }
        assertEquals(30.0, e.balance, 0.001)
        assertEquals(0.1f, e.progress!!, 0.001f)
        assertNull(m.progress)
    }

    @Test fun regionsRankedByPerKmWithBestHours() {
        val h = 3_600_000L
        val hourOf = { t: Long -> (t / h).toInt() % 24 }
        val seen = (0 until 6).map { RegionStats.Seen(18 * h + it * 600_000L, "centro", "Centro", 2.6, true) } +
            (0 until 5).map { RegionStats.Seen(12 * h, "vila prudente", "Vila Prudente", 1.9, it < 1) } +
            (0 until 3).map { RegionStats.Seen(12 * h, "mooca", "Mooca", 3.0, true) }   // poucas: fica de fora
        val r = RegionStats.compute(seen, hourOf)
        assertEquals(listOf("centro", "vila prudente"), r.map { it.key })
        assertEquals(2.6, r[0].avgPerKm, 0.001)
        assertEquals(100, r[0].greenPct)
        assertEquals(18..21, r[0].bestHours)     // janela de 3 h que começa nas 18h, onde estão os verdes
        assertNull(r[1].bestHours)                // só 1 verde: sem melhor horário
        assertEquals("Vila Prudente", RegionStats.pretty("vila prudente"))
        assertEquals("Jardim do Mar", RegionStats.pretty("jardim do mar"))
    }
}
