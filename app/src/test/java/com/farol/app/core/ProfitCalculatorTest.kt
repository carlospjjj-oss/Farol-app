package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfitCalculatorTest {

    private val profile = CostProfile(
        fuelPrice = 6.60, kmPerLiter = 11.0, otherCostPerKm = 0.30, // custo = 0,60 + 0,30 = 0,90/km
        greenPerKm = 2.0, yellowPerKm = 1.5, greenPerHour = 45.0, yellowPerHour = 30.0,
        avgSpeedKmh = 30.0,
    )

    private fun offer(price: Double, km: Double, min: Double?) =
        ParsedOffer("Uber", price, km, min, listOf(km), listOfNotNull(min))

    @Test fun custoPorKm() = assertEquals(0.90, profile.costPerKm, 0.0001)

    @Test fun corridaBoa_verde() {
        val r = ProfitCalculator.evaluate(offer(25.0, 10.0, 20.0), profile)
        assertEquals(2.5, r.perKm, 0.001)
        assertEquals(75.0, r.perHour, 0.001)
        assertEquals(16.0, r.net, 0.001)
        assertEquals(Rating.GREEN, r.rating)
    }

    @Test fun bomPorKmMasDemorada_ficaComOPior() {
        // R$ 2,00/km, mas 40 min -> R$ 30/h -> amarelo por hora
        val r = ProfitCalculator.evaluate(offer(20.0, 10.0, 40.0), profile)
        assertEquals(Rating.GREEN, r.ratingKm)
        assertEquals(Rating.YELLOW, r.ratingHour)
        assertEquals(Rating.YELLOW, r.rating)
    }

    @Test fun ruim_vermelho() {
        val r = ProfitCalculator.evaluate(offer(9.0, 10.6, 22.0), profile)
        assertEquals(Rating.RED, r.rating)
        assertTrue(r.net < 0.5)
    }

    @Test fun semTempo_estimaPelaVelocidade() {
        val r = ProfitCalculator.evaluate(offer(15.0, 5.0, null), profile)
        assertTrue(r.timeEstimated)
        assertEquals(10.0, r.minutes, 0.001) // 5 km a 30 km/h
    }

    private fun full(text: String) = OfferParser.parse(text, "Uber")!!

    @Test fun buscaLonge_viraVermelhoComMotivo() {
        val pr = profile.copy(maxPickupKm = 4.0)
        val r = ProfitCalculator.evaluate(full("R$ 40,00 | 10 min (5,5 km) | Viagem 15 min (10 km) | Aceitar"), pr)
        assertEquals(Rating.GREEN, r.ratingKm)
        assertEquals(Rating.RED, r.rating)
        assertTrue(r.warnings.first().startsWith("busca"))
    }

    @Test fun notaBaixa_viraVermelho() {
        val pr = profile.copy(minPassengerRating = 4.7)
        val r = ProfitCalculator.evaluate(full("4,52 ★ | R$ 40,00 | 3 min (1 km) | Viagem 15 min (10 km) | Aceitar"), pr)
        assertEquals(Rating.RED, r.rating)
    }

    @Test fun bairroDeAlerta_ignoraAcentoEMaiuscula() {
        val pr = profile.copy(alertWords = listOf("sao bento"))
        val r = ProfitCalculator.evaluate(full("R$ 40,00 | 3 min (1 km) | Viagem 15 min (10 km) | Rua X, São Bento | Aceitar"), pr)
        assertEquals(Rating.RED, r.rating)
        assertEquals(listOf("sao bento"), r.warnings)
    }

    @Test fun regrasDesligadas_naoAfetam() {
        val r = ProfitCalculator.evaluate(full("4,10 ★ | R$ 40,00 | 10 min (6 km) | Viagem 15 min (10 km) | Aceitar"), profile)
        assertTrue(r.warnings.isEmpty())
    }
}
