package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RegionsTest {

    @Test fun keeta_coletaEEntrega() {
        val t = "Mapa do Google | Recusar | Novo pedido pra você | R$ 8,03 | 5,4 km total | Coleta | Casa de Bolos | Rua das Flores, 120, Vila Palmares, Santo André - SP, 09060-000 | Entrega até | 11:54 | Rua Itambé, 45, Santo André, SP | Aceitar"
        val (o, d) = RegionReader.parse("Keeta", t)
        assertEquals("Vila Palmares", o!!.label); assertEquals("Santo André", o.city)
        assertEquals("Santo André", d!!.label); assertTrue(d.isCityOnly)
    }

    @Test fun keeta_comoChegaNosLogs() {
        val t = "Mapa do Google | Recusar | Novo pedido pra você | R$ 13,47 | Inclui incentivo | 6,7 km total | Coleta | Pizzaria Pizza Nabuco | [endereço], Jardim Itacolomi, SAO PAULO, SP, [cep] | Entrega até | 23:07 | [endereço], São Paulo, SP | Aceitar"
        val (o, d) = RegionReader.parse("Keeta", t)
        assertEquals("Jardim Itacolomi", o!!.label)
        assertEquals("São Paulo", d!!.label)
    }

    @Test fun ifood_lojaComCidade_clienteSoBairro() {
        val t = "R$ 11,08 | Valor da rota | Distância | 6,62 km | Tempo | 25 min | A Casa da Feijoada | Av. Fábio Eduardo Ramos Esquível, 2100, Conceição,Diadema,SP,BR | Rua Joaquim Nabuco, 77, Vila Fachini | Rejeitar | Aceitar | 86"
        val (o, d) = RegionReader.parse("iFood", t)
        assertEquals("Conceição", o!!.label); assertEquals("Diadema", o.city)
        assertEquals("Vila Fachini", d!!.label)
    }

    @Test fun noventaENove_corrida_comEnderecoQuebrado() {
        val t = "< Solicitações | Negocia Nova | R$17,58 RS1,56/km | 4,86 225 corridas | O (7 min 2 km) Rua Carlos Molteni, 833, Vila | Amorim | (17 min 9,2 km) Avenida Henrique Péres, | 950, Vila Bernadotti | Aceitar por R$17,58"
        val (o, d) = RegionReader.parse("99", t)
        assertEquals("Vila Amorim", o!!.label)
        assertEquals("Vila Bernadotti", d!!.label)
    }

    @Test fun semEnderecoNaoChuta() {
        val (o, d) = RegionReader.parse("Uber", "UberX | R$ 18,40 | 4 min (1,2 km) | 12 min (5,1 km) | Aceitar")
        assertNull(o); assertNull(d)
    }

    // ---------- chance de volta

    private fun at(d: Int, h: Int) = Calendar.getInstance().apply { set(2026, 10, d, h, 10, 0) }.timeInMillis
    private fun seen(d: Int, h: Int, key: String?, city: String?, green: Boolean = false) = ReturnChance.Seen(at(d, h), key, city, green)

    private fun base(): MutableList<ReturnChance.Seen> {
        // 40 ofertas de São Caetano (a "casa"), espalhadas por horários
        val l = mutableListOf<ReturnChance.Seen>()
        for (i in 0 until 40) l += seen(1 + i % 12, 10 + i % 10, "ceramica", "São Caetano do Sul")
        return l
    }

    @Test fun boaChance_quandoMuitasOfertasSaemDeLaNesseHorario() {
        val h = base()
        for (i in 0 until 7) h += seen(2 + i, 19, "vila palmares", "Santo André", green = i % 2 == 0)
        val c = ReturnChance.estimate(h, RegionReader.Place("vila palmares", "Vila Palmares", "Santo André"), at(14, 19))!!
        assertEquals(ReturnChance.Level.BOA, c.level); assertEquals(7, c.count)
        assertTrue(c.text, c.text.startsWith("Destino Vila Palmares: boa chance"))
    }

    @Test fun poucaChance_eDestinoSoComACidadeDeCasaNaoDizNada() {
        val h = base()
        val c = ReturnChance.estimate(h, RegionReader.Place("vila fachini", "Vila Fachini", null), at(14, 19))!!
        assertEquals(ReturnChance.Level.POUCA, c.level)
        assertNull(ReturnChance.estimate(h, RegionReader.Place("sao caetano do sul", "São Caetano do Sul", "São Caetano do Sul"), at(14, 12)))
    }

    @Test fun poucoHistorico_aindaAprendendo() {
        val h = base().take(10)
        assertNull(ReturnChance.estimate(h, RegionReader.Place("x", "X", null), at(14, 12)))
    }
}
