package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telas de histórico (prints de 14/11/2026). */
class HistoryReaderTest {

    private val keetaStats = "Estatísticas | Diárias | Semanais | Mensais | 13 de nov. | Ganhos totais | R$ 25,19 | Taxa de entrega | R$ 25,19 | " +
        "Recompensas e extras | R$ 0,00 | Gorjeta | R$ 0,00 | Taxa de entrega | R$ 13,47 | 13 de nov. 23:09 | Concluído | Taxa de entrega | R$ 11,72 | 13 de nov. 22:46 | Concluído"
    private val saldoOcr = "Saldo | Configurações | Transferência R$0,00 | Bônus | R$5,00 > | 23:57 | Entrega Food | R$10,65 > | 23:57 | Bônus | R$5,00 > | 23:49 | " +
        "Entrega Food | R$6,89 > | 23:48 | Entrega Food | -R$21,49 > | 22:09-Dinheiro"

    @Test fun keeta_totalDoDiaDoFiltro() {
        assertTrue(HistoryReader.isHistory("Keeta", keetaStats))
        val t = HistoryReader.keetaDay(keetaStats, "2026-11-14")!!
        assertEquals("2026-11-13", t.date)
        assertEquals(25.19, t.value, 0.001)
    }

    @Test fun keeta_abaSemanalNaoConta() {
        assertNull(HistoryReader.keetaDay("Estatísticas | Diárias | Semanais | Mensais | 9–15 de nov. | Ganhos totais | R$ 312,00", "2026-11-14"))
    }

    @Test fun keeta_dezembroVistoEmJaneiroEhDoAnoPassado() {
        val t = HistoryReader.keetaDay("Estatísticas | Diárias | 30 de dez. | Ganhos totais | R$ 80,00", "2027-01-02")!!
        assertEquals("2026-12-30", t.date)
    }

    @Test fun saldo99_lancamentosPositivos_semDinheiro() {
        assertTrue(HistoryReader.isHistory("99", saldoOcr))
        val e = HistoryReader.ninetyNineSaldo(saldoOcr)
        assertEquals(4, e.size)
        assertEquals(27.54, e.sumOf { it.value }, 0.001)
        assertEquals(setOf(23 * 60 + 57, 23 * 60 + 49, 23 * 60 + 48), e.map { it.minuteOfDay }.toSet())
        assertEquals(4, e.map { it.key }.toSet().size)
        // mesma tela de novo: mesmas chaves (não soma duas vezes)
        assertEquals(e.map { it.key }, HistoryReader.ninetyNineSaldo(saldoOcr).map { it.key })
    }

    @Test fun saldo99_entraNoTurnoDeOntem_eNaoDobraComAOferta() {
        val c = java.util.Calendar.getInstance().apply { set(2026, 10, 14, 7, 23, 0) }
        val now = c.timeInMillis
        val start = java.util.Calendar.getInstance().apply { set(2026, 10, 13, 21, 29, 0) }.timeInMillis
        val d13 = DayRecord("2026-11-13", kmStart = 1.0, kmEnd = 2.0, startAt = start, endAt = start + 3 * 3_600_000L,
            earnings = mapOf("Keeta" to 25.19), orders = mapOf("99#149031" to 15.65))
        val at = HistoryReader.instantOf(23 * 60 + 57, now)
        assertTrue(at < now)
        assertEquals("2026-11-13", HistoryReader.dayFor(at, listOf(d13), now)?.date)
        val withSaldo = d13.copy(orders = d13.orders + HistoryReader.ninetyNineSaldo(saldoOcr).associate { it.key to it.value })
        // 99 = maior família (Saldo 27,54 > oferta 15,65), nunca 43,19
        assertEquals(27.54, withSaldo.appEarnings["99"]!!, 0.001)
        assertEquals(25.19 + 27.54, withSaldo.totalEarnings, 0.001)
        // Lançamento numa hora sem turno: não chuta
        assertNull(HistoryReader.dayFor(HistoryReader.instantOf(15 * 60, now), listOf(d13), now))
    }
}

class HistoryReaderIfoodTest {
    private val lanc = "Financeiro | Lançamentos | Repasses | 09 - 15 Nov | Tipo de lançamento | Ganhos da semana | R$ 38,33 | " +
        "Quinta, 12 de novembro | Rota completa | 15:32 | R$ 8,07 | Rota completa | 15:12 | R$ 10,09 | Rota completa | 14:56 | R$ 9,09 | " +
        "Quarta, 11 de novembro | Rota completa | 15:05 | R$ 11,08 | Isso é tudo"

    @Test fun lancamentosComDataDoCabecalho() {
        assertTrue(HistoryReader.isHistory("iFood", lanc))
        val e = HistoryReader.ifoodEntries(lanc, "2026-11-14")
        assertEquals(4, e.size)
        assertEquals(27.25, e.filter { it.date == "2026-11-12" }.sumOf { it.value }, 0.001)   // = o que você lançou no dia 12
        assertEquals(11.08, e.filter { it.date == "2026-11-11" }.sumOf { it.value }, 0.001)
        assertEquals(38.33, e.sumOf { it.value }, 0.001)                                      // = "Ganhos da semana"
        assertEquals(e.map { it.key }, HistoryReader.ifoodEntries(lanc, "2026-11-14").map { it.key })
        // valor antes da hora também funciona
        val inv = HistoryReader.ifoodEntries("Quinta, 12 de novembro | Rota completa | R$ 8,07 | 15:32", "2026-11-14")
        assertEquals(1, inv.size); assertEquals(15 * 60 + 32, inv[0].minuteOfDay)
    }

    @Test fun naoDobraComOValorLancadoAMao() {
        val d = DayRecord("2026-11-12", earnings = mapOf("iFood" to 27.25),
            orders = HistoryReader.ifoodEntries(lanc, "2026-11-14").filter { it.date == "2026-11-12" }.associate { it.key to it.value })
        assertEquals(27.25, d.totalEarnings, 0.001)
    }
}
