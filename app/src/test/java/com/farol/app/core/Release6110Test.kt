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
