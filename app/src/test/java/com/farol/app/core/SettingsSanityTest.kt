package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSanityTest {
    @Test fun ajustesDoMarceloSaoPegos() {
        // etanol R$ 0,00 a 1 km/l, outros R$ 0,05/km, verde 1,50 / amarelo 1,00 (13/11)
        val p = CostProfile(fuelPrice = 0.0, kmPerLiter = 1.0, otherCostPerKm = 0.05, greenPerKm = 1.5, yellowPerKm = 1.0)
        val f = SettingsSanity.check(p, isCar = true).map { it.field }
        assertTrue(f.toString(), "km_l" in f && "preco" in f && "custo" in f)
    }

    @Test fun sanderoCertoNaoReclama() {
        val p = CostProfile(fuelPrice = 3.68, kmPerLiter = 7.0, otherCostPerKm = 0.30, greenPerKm = 1.9, yellowPerKm = 1.4)
        assertEquals(emptyList<SettingsSanity.Issue>(), SettingsSanity.check(p, isCar = true))
    }

    @Test fun motoCertaNaoReclama_eAmareloAbaixoDoCustoReclama() {
        val ok = CostProfile(fuelPrice = 6.2, kmPerLiter = 32.0, otherCostPerKm = 0.15, greenPerKm = 2.0, yellowPerKm = 1.4)
        assertEquals(0, SettingsSanity.check(ok, isCar = false).size)
        // consumo de carro no modo moto
        assertTrue(SettingsSanity.check(ok.copy(kmPerLiter = 8.0), isCar = false).any { it.field == "km_l" })
        // amarelo R$ 0,30/km com custo R$ 0,35/km
        assertTrue(SettingsSanity.check(ok.copy(yellowPerKm = 0.30, greenPerKm = 2.0), isCar = false).any { it.field == "faixas" })
    }
}
