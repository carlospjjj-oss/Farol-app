package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 6.8: manutenção e caixinha, consumo real por combustível, indicadores, meta bruta. */
class Release68Test {
    private val h = 3_600_000L

    // ---------- manutenção ----------
    @Test fun maintenanceStatusLevels() {
        val oil = MaintItem("oleo_motor", "Óleo do motor", 3000.0, lastKm = 58_000.0)
        assertEquals(Maintenance.Level.OK, Maintenance.status(oil, 59_000.0).level)
        val soon = Maintenance.status(oil, 60_750.0)
        assertEquals(Maintenance.Level.SOON, soon.level)        // faltam 250 km (< 300)
        assertEquals(250.0, soon.remainingKm!!, 0.01)
        assertTrue(soon.text, soon.text.startsWith("faltam 250 km"))
        val due = Maintenance.status(oil, 61_120.0)
        assertEquals(Maintenance.Level.DUE, due.level)
        assertTrue(due.text, due.text.startsWith("passou 120 km"))
        assertEquals(Maintenance.Level.UNKNOWN, Maintenance.status(oil.copy(lastKm = null), 60_000.0).level)
    }

    @Test fun maintenanceSortsMostUrgentFirstAndServiceResets() {
        val items = listOf(
            MaintItem("pneu", "Pneu", 12_000.0, lastKm = 55_000.0),
            MaintItem("oleo", "Óleo", 3000.0, lastKm = 57_000.0),
            MaintItem("freio", "Freio", 8000.0),
        )
        val s = Maintenance.sorted(items, 60_100.0)
        assertEquals(listOf("oleo", "pneu", "freio"), s.map { it.item.id })
        val after = Maintenance.service(items, "oleo", 60_100.0, 1L)
        assertEquals(Maintenance.Level.OK, Maintenance.status(after.first { it.id == "oleo" }, 60_100.0).level)
    }

    @Test fun currentKmComesFromTheDiary() {
        val days = listOf(DayRecord("2026-11-13", kmStart = 58_100.0, kmEnd = 58_203.5), DayRecord("2026-11-14", kmStart = 58_203.5))
        val fuel = listOf(FuelEntry(1, 58_150.0, 3.0, 17.0))
        assertEquals(58_203.5, Maintenance.currentKm(days, fuel)!!, 0.01)
    }

    @Test fun maintenanceLeavesTheNetAndGoesToTheReserve() {
        val d = DayRecord("2026-11-14", kmStart = 1000.0, kmEnd = 1100.0, earnings = mapOf("Keeta" to 150.0),
            expenses = listOf(Expense(1, "alimentacao", 20.0), Expense(2, "manutencao", 45.0, "óleo", item = "oleo_motor", km = 1100.0)))
        // líquido = 150 − 100 km × 0,47 − 20 (almoço). O óleo (45) não sai de novo: já está no custo por km.
        assertEquals(83.0, JournalMath.summarize(d, 0.47).netAfterExpenses, 0.001)
        assertEquals(83.0, GoalMath.dayNet(d, 0.47), 0.001)
        assertEquals(65.0, d.totalExpenses, 0.001)               // o card de gastos mostra tudo
        val r = Maintenance.reserve(listOf(d, DayRecord("2026-11-13", kmStart = 900.0, kmEnd = 1000.0)), 0.30)
        assertEquals(200.0, r.km, 0.001)
        assertEquals(60.0, r.saved, 0.001)
        assertEquals(15.0, r.balance, 0.001)                     // 60 juntados − 45 do óleo
        assertEquals(30.0 - 45.0, Maintenance.reserve(listOf(d), 0.30, since = "2026-11-14").balance, 0.001)
    }

    // ---------- consumo real por combustível ----------
    @Test fun realConsumptionDoesNotMixFuels() {
        val f = listOf(
            FuelEntry(1, 10_000.0, 4.0, 23.2, true, "gasolina"),
            FuelEntry(2, 10_140.0, 4.0, 15.6, true, "etanol"),     // 140 km com gasolina / 4 L = 35 km/L
            FuelEntry(3, 10_240.0, 4.0, 23.2, true, "gasolina"),   // 100 km com etanol / 4 L = 25 km/L
            FuelEntry(4, 10_380.0, 4.0, 23.2, true, "gasolina"),   // 140 km com gasolina / 4 L = 35
        )
        assertEquals(35.0, JournalMath.fuelStats(f, "gasolina").realKmPerLiter!!, 0.01)
        assertEquals(25.0, JournalMath.fuelStats(f, "etanol").realKmPerLiter!!, 0.01)
        assertEquals(3.9, JournalMath.fuelStats(f, "etanol").lastPricePerLiter!!, 0.01)
        assertEquals(5.8, JournalMath.fuelStats(f, "gasolina").lastPricePerLiter!!, 0.01)
    }

    @Test fun oldFillUpsGuessFuelByPriceAndMixedTankIsSkipped() {
        assertEquals("etanol", FuelEntry(1, 0.0, 10.0, 39.0).fuelType)
        assertEquals("gasolina", FuelEntry(1, 0.0, 10.0, 57.9).fuelType)
        val f = listOf(
            FuelEntry(1, 1000.0, 4.0, 23.2, true, "gasolina"),
            FuelEntry(2, 1050.0, 1.0, 3.9, false, "etanol"),        // completou com etanol no meio: tanque misturado
            FuelEntry(3, 1140.0, 3.0, 17.4, true, "gasolina"),
        )
        assertNull(JournalMath.fuelStats(f, "gasolina").realKmPerLiter)
    }

    // ---------- meta bruta ----------
    @Test fun goalCanCountGross() {
        val d = DayRecord("2026-11-14", kmStart = 1000.0, kmEnd = 1100.0, earnings = mapOf("Keeta" to 119.55),
            expenses = listOf(Expense(1, "alimentacao", 31.0)))
        assertEquals(119.55, GoalMath.dayValue(d, 0.0, gross = true), 0.001)
        assertEquals(88.55, GoalMath.dayValue(d, 0.0, gross = false), 0.001)
        val g = GoalMath.compute(listOf(d), "2026-11-14", 150.0, 6, 0.0, gross = true)
        assertEquals(119.55, g.day.done, 0.001)
    }

    // ---------- indicadores ----------
    @Test fun indicatorsTicketPerHourAndSlots() {
        val base = 1_763_100_000_000L   // um instante qualquer; a hora vem do hourOf
        val hourOf = { t: Long -> (((t - base) / h).toInt() + 11) % 24 }   // base = 11h
        val d = DayRecord(
            "2026-11-14", kmStart = 0.0, kmEnd = 80.0, startAt = base, endAt = base + 8 * h,   // 11h às 19h
            orders = mapOf("Keeta#1" to 10.0, "Keeta#2" to 14.0, "iFood#3" to 9.0, "99\$saldo:12:30:8.5" to 8.5),
            orderAt = mapOf("Keeta#1" to base + h / 2, "Keeta#2" to base + 5 * h, "iFood#3" to base + 7 * h + h / 2),
            earnings = mapOf("iFood" to 9.0),
        )
        val r = Indicators.compute(listOf(d), hourOf)
        assertEquals(3, r.deliveries)                              // o lançamento do Saldo não conta como entrega lida
        assertEquals(11.0, r.ticket!!, 0.001)                      // (10 + 14 + 9) / 3
        assertEquals(3.0 / 8, r.deliveriesPerHour!!, 0.001)
        val keeta = r.apps.first { it.app == "Keeta" }
        assertEquals(2, keeta.deliveries); assertEquals(12.0, keeta.ticket!!, 0.001)
        val almoco = r.slots.first { it.slot == Indicators.Slot.ALMOCO }
        assertEquals(4.0, almoco.hours, 0.01)                      // 11h–15h
        assertEquals(1, almoco.deliveries)
        val jantar = r.slots.first { it.slot == Indicators.Slot.JANTAR }
        assertEquals(1.0, jantar.hours, 0.01)                      // 18h–19h
        assertEquals(9.0, jantar.perHour!!, 0.01)
        assertFalse(jantar.enough)                                 // 1 entrega não basta para concluir
        assertTrue(Indicators.Slot.OUTRO.has(2) && Indicators.Slot.OUTRO.has(23) && !Indicators.Slot.OUTRO.has(11))
    }
}
