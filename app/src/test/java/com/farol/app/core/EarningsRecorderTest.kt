package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** O dia 13/11 do Carlos, do começo do turno ao Saldo da 99 na manhã seguinte, com as telas reais. */
class EarningsRecorderTest {

    private class Mem(var now: Long) : EarningsRecorder.DayStore {
        val days = mutableMapOf<String, DayRecord>()
        fun date(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { "%04d-%02d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
        override fun today() = date(now)
        override fun currentDay(): DayRecord {   // mesma regra do JournalStore.workDate
            val y = dayAt(date(now - 86_400_000L))
            val open = y.kmStart != null && y.kmEnd == null && y.startAt != null && now - y.startAt < 20 * 3_600_000L
            return if (!dayAt(today()).hasShift && open) y else dayAt(today())
        }
        override fun dayAt(date: String) = days[date] ?: DayRecord(date)
        override fun putDay(r: DayRecord) { days[r.date] = r }
        override fun lastDays(n: Int) = (0 until n).map { date(now - it * 86_400_000L) }.toSet()
    }

    private fun at(d: Int, h: Int, m: Int) = Calendar.getInstance().apply { set(2026, 10, d, h, m, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun diaInteiro_semContarDuasVezes() {
        val mem = Mem(at(13, 21, 29))
        mem.putDay(DayRecord("2026-11-13", kmStart = 58203.5, startAt = mem.now))
        val rec = EarningsRecorder(mem) { mem.now }
        val recorded = mutableListOf<String>()
        rec.onRecorded = { d, a -> recorded += "$d $a" }
        rec.start()

        // Keeta: duas entregas concluídas (a mesma tela aparece duas vezes)
        mem.now = at(13, 22, 46)
        assertTrue(rec.onScreen("Keeta", "Pedido concluído!  Seus ganhos | R$ 11,72 | #7040 | Taxa base | R$ 6,72"))
        assertFalse(rec.onScreen("Keeta", "Pedido concluído!  Seus ganhos | R$ 11,72 | #7040 | Taxa base | R$ 6,72"))
        mem.now = at(13, 23, 9)
        rec.onScreen("Keeta", "Pedido concluído!  Seus ganhos | R$ 13,47 | #7553 | Taxa base | R$ 9,97")
        // painel da Keeta com o total: não soma em cima
        rec.onScreen("Keeta", "Painel | R$ 25,19 | 2 | Ficar online | Mapa do Google")
        assertEquals(25.19, mem.dayAt("2026-11-13").appEarnings["Keeta"]!!, 0.001)

        // 99: oferta lida e depois "Concluir" com o número do pedido
        mem.now = at(13, 23, 10)
        rec.onOffer("99", 15.65)
        mem.now = at(13, 23, 57)
        rec.onScreen("99", "Entregar em até 5 minutos | #149031 [cliente] | Peça o código de verificação ao cliente | Concluir")
        assertEquals(15.65, mem.dayAt("2026-11-13").appEarnings["99"]!!, 0.001)

        // Passou da meia-noite com o turno aberto: continua no dia 13
        mem.now = at(14, 0, 10)
        rec.onScreen("Keeta", "Pedido concluído!  Seus ganhos | R$ 9,00 | #8001 | Taxa base | R$ 9,00")
        assertEquals(34.19, mem.dayAt("2026-11-13").appEarnings["Keeta"]!!, 0.001)
        assertEquals(0.0, mem.dayAt("2026-11-14").totalEarnings, 0.001)
        // painel da Keeta depois da meia-noite mostra só o dia novo (R$ 9,00): não diminui nada
        rec.onScreen("Keeta", "Painel | R$ 9,00 | 1 | Ficar online")
        assertEquals(34.19, mem.dayAt("2026-11-13").appEarnings["Keeta"]!!, 0.001)

        // Turno encerrado às 00:25
        mem.putDay(mem.dayAt("2026-11-13").copy(kmEnd = 58254.9, endAt = at(14, 0, 25)))

        // Manhã seguinte: Saldo da 99 (lido pela imagem) completa a 99 sem somar com a oferta
        mem.now = at(14, 8, 2)
        rec.onScreen("99", "Saldo | Configurações | Transferência R\$0,00 | Bônus | R$5,00 > | 23:57 | Entrega Food | R$10,65 > | 23:57 | " +
            "Bônus | R$5,00 > | 23:49 | Entrega Food | R$6,89 > | 23:48 | Entrega Food | -R$21,49 > | 22:09-Dinheiro")
        val d13 = mem.dayAt("2026-11-13")
        assertEquals(27.54, d13.appEarnings["99"]!!, 0.001)
        assertEquals(34.19 + 27.54, d13.totalEarnings, 0.001)

        // Keeta › Estatísticas do dia 13 mostra R$ 25,19 (sem a do pós-meia-noite, que a Keeta conta no dia 14): fica o maior
        rec.onScreen("Keeta", "Estatísticas | Diárias | Semanais | Mensais | 13 de nov. | Ganhos totais | R$ 25,19 | Taxa de entrega | R$ 25,19")
        assertEquals(34.19, mem.dayAt("2026-11-13").appEarnings["Keeta"]!!, 0.001)
        assertTrue(recorded.all { it.startsWith("2026-11-13") })
    }

    @Test fun ifoodLancamentos_vaoParaADataDoCabecalho() {
        val mem = Mem(at(14, 8, 1))
        val rec = EarningsRecorder(mem) { mem.now }
        rec.onScreen("iFood", "Financeiro | Extrato | Lançamentos | Repasses | 09 - 15 Nov | Tipo de lançamento | Ganhos da semana | R$ 38,33 | " +
            "Quinta, 12 de novembro | Rota completa | 15:32 | R$ 8,07 | Rota completa | 15:12 | R$ 10,09 | Rota completa | 14:56 | R$ 9,09 | " +
            "Quarta, 11 de novembro | Rota completa | 15:05 | R$ 11,08 | Isso é tudo")
        assertEquals(27.25, mem.dayAt("2026-11-12").totalEarnings, 0.001)
        assertEquals(11.08, mem.dayAt("2026-11-11").totalEarnings, 0.001)
        assertEquals(0.0, mem.dayAt("2026-11-14").totalEarnings, 0.001)
    }
}
