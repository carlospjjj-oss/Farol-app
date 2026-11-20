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

/** 17/11: relançar o abastecimento de 16/11 (58.269,2) depois do turno que fechou em 58.395,8. */
class Release61011Test {
    @Test fun ultimoAbastecimentoIgnoraKmErrado() {
        val f = listOf(FuelEntry(1L, 58110.0, 5.4, 31.28, true), FuelEntry(2L, 582692.0, 2.86, 19.71, true, "gasolina"))
        assertEquals(58110.0, JournalMath.lastFuelKm(f, 58395.8)!!, 0.001)
        assertEquals(58110.0, JournalMath.lastFuelKm(f.take(1), 58395.8)!!, 0.001)
    }
    @Test fun digitoAMaisCorrigeContraOUltimoAbastecimento() =
        assertEquals(58269.2, JournalMath.odometerFix(582692.0, 58110.0)!!, 0.001)
}

/** 19/11: aviso de taxa de aceitação e calendário do histórico da 99 não são oferta perdida; URL sem parâmetros. */
class Release61012Test {
    @Test fun avisoDeTaxaNaoEOferta() = org.junit.Assert.assertFalse(OfferParser.looksLikeOffer(
        "baixa | COOPERATIVA | Recusar | Aceitar pedido | Sua taxa de aceitação está | Rejeitar muitas vezes pode reduzira TA e | afetar o envio de pedidos | X"))
    @Test fun calendarioNaoEOferta() = org.junit.Assert.assertFalse(OfferParser.looksLikeOffer(
        "< Histórico de corridas | Corridas | X | 19/11 v Taxas v | Selecionar data | nov 2026 | dom seg | Pago"))
    @Test fun ofertaDaNoventaENoveContinua() = org.junit.Assert.assertTrue(OfferParser.looksLikeOffer(
        "Entrega Food | R$10,17 | Distância total 6,3 km | Aceitar pedido"))
    @Test fun selecionarDaUberContinua() = org.junit.Assert.assertTrue(OfferParser.looksLikeOffer("UberX | R$ 74,36 | Selecionar"))
    @Test fun urlSemParametros() {
        val t = LogSanitizer.forServer("page.didiglobal.com/global/driver-wallet/x.html?driverCityID=1&lat=-23.6&lng=-46.6&a3=WoHi2OY84p7&randomKey=m0z5eb | Pago")!!
        org.junit.Assert.assertTrue(t, !t.contains("a3=") && !t.contains("randomKey") && t.contains("didiglobal.com/global/driver-wallet/x.html?[parâmetros]"))
    }
}

/** 6.11 (teste): voltando para casa com destino definido na 99. */
class HomeRoute611Test {
    @Test fun metadeDaOfertaNoMaximo() = assertEquals(3.0, HomeRoute.onWayKm(6.0, 10.0), 0.001)
    @Test fun naoPassaDaDistanciaAteCasa() = assertEquals(1.3, HomeRoute.onWayKm(6.0, 1.0), 0.001)
    @Test fun pertoDeCasaNaoConta() = assertEquals(0.0, HomeRoute.onWayKm(6.0, 0.1), 0.001)
    @Test fun distanciaEmLinhaReta() = assertEquals(11.1, HomeRoute.distanceKm(-23.60, -46.60, -23.70, -46.60), 0.1)
    @Test fun ofertaAjustadaMantemOTempo() {
        val o = ParsedOffer("99", 10.17, 6.3, null, listOf(6.3), emptyList())
        val a = HomeRoute.adjusted(o, 3.15, 25.0)
        assertEquals(3.15, a.totalKm, 0.001)
        assertEquals(6.3 / 25.0 * 60, a.totalMin!!, 0.001)
    }
    @Test fun amarelaViraVerdeNoCaminho() {
        val p = CostProfile(fuelPrice = 6.69, kmPerLiter = 35.0, otherCostPerKm = 0.10, greenPerKm = 2.0, yellowPerKm = 1.5, greenPerHour = 25.0, yellowPerHour = 18.0, avgSpeedKmh = 25.0)
        val o = ParsedOffer("99", 10.17, 6.3, null, listOf(6.3), emptyList())
        val normal = ProfitCalculator.evaluate(o, p)
        val adj = ProfitCalculator.evaluate(HomeRoute.adjusted(o, HomeRoute.onWayKm(6.3, 8.0), p.avgSpeedKmh), p)
        org.junit.Assert.assertTrue(adj.net > normal.net)
        org.junit.Assert.assertEquals(normal.minutes, adj.minutes, 0.001)
    }
}
