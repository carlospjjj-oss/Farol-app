package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LogSanitizerTest {

    @Test fun ruaComNumeroEBairro_mantemBairro() =
        assertEquals("[endereço], Centro, São Paulo", LogSanitizer.mask("Rua das Flores, 123 - Centro, São Paulo"))

    @Test fun avenidaComHifen() =
        assertEquals("[endereço], Bela Vista", LogSanitizer.mask("Av. Paulista - Bela Vista"))

    @Test fun ruaSemComplemento() =
        assertEquals("[endereço]", LogSanitizer.mask("Rua Augusta"))

    @Test fun mantemPrecoDistanciaEOutrosSegmentos() =
        assertEquals("UberX | R$ 15,20 | 4 min (1,2 km) | [endereço], Moema | Aceitar",
            LogSanitizer.mask("UberX | R$ 15,20 | 4 min (1,2 km) | Alameda dos Maracatins, 500, Moema | Aceitar"))

    @Test fun telefoneECep() =
        assertEquals("Contato [num] | [cep]", LogSanitizer.mask("Contato (11) 98765-4321 | 01310-100"))

    @Test fun textoMascaradoContinuaParseavel() {
        val raw = "UberX | 4,93 ★ | R$ 15,20 | 4 min (1,2 km) de distância | Rua X, 10 - Centro | Viagem de 18 min (9,4 km) | Aceitar"
        val o = OfferParser.parse(LogSanitizer.mask(raw), "Uber")!!
        assertEquals(15.2, o.price, 0.001)
        assertEquals(10.6, o.totalKm, 0.001)
    }
}
