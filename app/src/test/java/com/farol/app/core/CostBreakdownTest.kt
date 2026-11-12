package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CostBreakdownTest {
    @Test fun motoPropria() {
        val c = MonthlyCosts(kmPerMonth = 2400.0, depreciation = 50.0, maintenance = 120.0, ipva = 25.0, other = 60.0)
        // outros = 255 / 2400 = 0,10625
        assertEquals(0.10625, CostBreakdown.otherPerKm(c), 0.0001)
        val items = CostBreakdown.items(c, fuelPrice = 5.79, kmPerLiter = 30.0)
        assertEquals("Combustível", items.first().name)          // 2400/30*5,79 = 463,20
        assertEquals(463.2, items.first().monthly, 0.01)
        assertEquals(718.2, CostBreakdown.total(items), 0.01)
        assertEquals(100.0, items.sumOf { it.pct }, 0.01)
    }

    @Test fun alugadoMudaONome() {
        val items = CostBreakdown.items(MonthlyCosts(vehicle = "alugado", financing = 1200.0), 5.79, 30.0)
        assertEquals("Aluguel", items.first().name)
    }
}
