package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 16/11: telas reais da 99 (Histórico de corridas), com a data e hora como aparecem no celular. */
class Release6106Test {

    private val hist = "Histórico de corridas | Corridas | 16/11 v Taxas v | 16/11/2026 13:34 | Ganhos pagos no app | Entrega Food | Entregas | Pago | [endereço] | Cidade Jardim, São Paulo - SP, 05502.. | O Av. Magalhães de Castro, 4800 | 16/11/2026 15:00 | Ganhos pagos no app | Bônus | OPago | 16/11/2026 15:57 | A Ganhos pagos no app | Bônus | Pago | R$7,50 > | keeta | entregador | R$5,00 > | R$5,00 >"

    @Test fun historicoDaEntregaEBonus() {
        val e = HistoryReader.ninetyNineHistorico(hist, "2026-11-16")
        assertEquals(3, e.size)
        assertEquals(17.50, e.sumOf { it.value }, 0.001)
        assertEquals("Entrega Food", e[0].label)
        assertEquals(13 * 60 + 34, e[0].minuteOfDay)
        assertTrue(e.all { it.key.startsWith("99\$hist:2026-11-16:") && it.date == "2026-11-16" })
        assertTrue(HistoryReader.isHistory("99", hist))
    }

    @Test fun historicoComUmaEntrega() {
        val e = HistoryReader.ninetyNineHistorico("Cor | 16/11/2026 13:34 | Pago | Ganhos pagos no app | Entrega Food | R$7,50 > | [endereço] | keeta | entregador", "2026-11-16")
        assertEquals(1, e.size); assertEquals(7.50, e[0].value, 0.001)
    }

    @Test fun ofertaPorCimaNaoEHistorico() {
        val t = "Histórico de corridas | Corridas | 16/11 v Taxas v | R$ 7,50 | Entregas | t 3,4 km total | Pago | 16/11/2026 15:57 | Bônus | A Ganhos pagos no app | Pago | Aceitar(54s) | keeta | entregador | R$5,00 >"
        assertTrue(HistoryReader.ninetyNineHistorico(t, "2026-11-16").isEmpty())
    }

    @Test fun quantidadeMuitoDiferenteNaoChuta() =
        assertTrue(HistoryReader.ninetyNineHistorico("Histórico de corridas | 16/11/2026 13:34 | Entrega Food | 16/11/2026 15:00 | Bônus | 16/11/2026 15:57 | Bônus | R$7,50 >", "2026-11-16").isEmpty())

    // 17h48: a última linha (13:34) ficou cortada embaixo, sem o valor
    @Test fun listaCortadaEmbaixo() {
        val e = HistoryReader.ninetyNineHistorico("Histórico de corridas | Corridas | 916/11 v | Entregas | 16/11/2026 17:33 | Taxas | Ganhos pagos no app | Entrega Food | Pago | O Rua Arroio Grande, 517 | O Avenida Jabaquara, 842 - Mirandópolis, | São Paulo - SP, O4046-400, Brasil | 16/11/2026 17:22 | Ganhos pagos no app | Entrega Food | Pago | OR.Itamarati Brasileiro, 223 | O Avenida Jabaquara, 305 - Vila Mariana, | São Paulo - SP, 04014-000, Brasil | 16/11/2026 13:34 | R$11,00 > | Ganhos pagos no app | R$10,70 > | <", "2026-11-16")
        assertEquals(2, e.size)
        assertEquals(17 * 60 + 33, e[0].minuteOfDay); assertEquals(11.0, e[0].value, 0.001)
        assertEquals(10.70, e[1].value, 0.001)
    }

    @Test fun diaInteiroDaNoventaENove() {
        // as duas telas do dia: entregas novas + entrega e bônus da tarde; cada lançamento uma vez
        val a = HistoryReader.ninetyNineHistorico(hist, "2026-11-16")
        val b = HistoryReader.ninetyNineHistorico("Histórico de corridas | 16/11/2026 17:33 | Entrega Food | 16/11/2026 17:22 | Entrega Food | 16/11/2026 13:34 | R$11,00 > | R$10,70 >", "2026-11-16")
        assertEquals(39.20, (a + b).associateBy { it.key }.values.sumOf { it.value }, 0.001)
    }

    @Test fun pagoEmDinheiroFica() {
        val e = HistoryReader.ninetyNineHistorico("Histórico de corridas | 16/11/2026 13:34 | Ganhos pagos em dinheiro | Entrega Food | 16/11/2026 15:00 | Ganhos pagos no app | Bônus | R$54,69 > | R$5,00 >", "2026-11-16")
        assertEquals(1, e.size); assertEquals(5.0, e[0].value, 0.001)
    }

    @Test fun diarioRecebeAEntregaSemContarDuasVezes() {
        val now = Calendar.getInstance().apply { set(2026, 10, 16, 16, 40, 0) }.timeInMillis
        val days = mutableMapOf<String, DayRecord>("2026-11-16" to DayRecord("2026-11-16", kmStart = 58269.7, startAt = now - 6 * 3_600_000L,
            earnings = mapOf("Keeta" to 78.39)))
        val mem = object : EarningsRecorder.DayStore {
            override fun today() = "2026-11-16"
            override fun currentDay() = dayAt("2026-11-16")
            override fun dayAt(date: String) = days[date] ?: DayRecord(date)
            override fun putDay(r: DayRecord) { days[r.date] = r }
            override fun lastDays(n: Int) = setOf("2026-11-16", "2026-11-15")
        }
        val rec = EarningsRecorder(mem) { now }
        rec.onScreen("99", hist)
        rec.onScreen("99", hist)   // a mesma tela de novo não soma
        assertEquals(17.50, mem.dayAt("2026-11-16").appEarnings["99"]!!, 0.001)
        assertEquals(78.39, mem.dayAt("2026-11-16").appEarnings["Keeta"]!!, 0.001)
        assertEquals(0, mem.dayAt("2026-11-16").deliveries)   // lançamento do histórico não conta como entrega lida
    }
}

/** 16/11: textos da 99 pela imagem que saíram com endereço e nome do cliente. */
class SanitizerOcr6106Test {
    @Test fun enderecoComIconeDoMapa() {
        val t = LogSanitizer.forServer("Histórico de corridas | O Rua Arroio Grande, 517 | O Avenida Jabaquara, 842 - Mirandópolis, | São Paulo - SP, O4046-400, Brasil | OR.Itamarati Brasileiro, 223 | O Av. Magalhães de Castro, 4800 | Cidade Jardim, São Paulo - SP, 05502... | O Vantagens >")!!
        assertTrue(t, !t.contains("Arroio") && !t.contains("Jabaquara") && !t.contains("Itamarati") && !t.contains("Magalhães"))
        assertTrue(t, !t.contains("4046") && !t.contains("05502") && !t.contains("517") && !t.contains("842"))
        assertTrue(t, t.contains("Mirandópolis") && t.contains("O Vantagens >"))
    }

    @Test fun nomeECodigoNaRetirada() {
        val t = LogSanitizer.forServer("Eldorado | Retirada concluída | #866031 | Informeo nome do cliente ou código solicitado | por Eldorado Restaurante & Marmitas | Nome do cliente | Restaurante | & Burger's | Código de verificação | Iniciar entrega | Isadora | < | 9308")!!
        assertTrue(t, !t.contains("Isadora") && !t.contains("9308"))
        assertTrue(t, t.contains("Retirada concluída") && t.contains("Iniciar entrega"))
    }

    @Test fun textoComumNaoMuda() {
        val s = "Pedidos na rota | R$21,70 | Distância total 9 km | Aceitar (2) | O Coletar R$54,69 em dinheiro do"
        assertEquals(s, LogSanitizer.forServer(s))
    }
}
