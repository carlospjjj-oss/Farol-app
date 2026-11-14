package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 6.4: ganho por entrega, espera do restaurante, explicação do card, metas e privacidade. Telas reais de 13/11. */
class Release64Test {

    // ---------------------------------------------------------------- ganho por entrega

    private val keetaDone = "Pedido concluído!  Seus ganhos | R$ 13,47 | #7553 | Taxa base | R$ 9,97 | Outros extras | R$ 3,50 | Resumo do pedido"
    private val ninetyNineFinish = "Entregar em até 5 minutos | [endereço] | Vila Clara, São Paulo - SP | #149031 Laura | muro rosa | Peça o código de verificação ao cliente | Insira o código | Detalhes do pedido > | Central de Ajuda | Concluir"

    @Test fun keeta_pedidoConcluido_lidoComNumero() {
        val d = OrderEarnings().onScreen("Keeta", keetaDone, 0L)!!
        assertEquals("Keeta#7553", d.key)
        assertEquals(13.47, d.value, 0.001)
    }

    @Test fun keeta_painelNaoEhPedido() {
        assertNull(OrderEarnings().onScreen("Keeta", "Painel | R$ 25,19 | 2 | Ficar online | Mapa do Google", 0L))
    }

    @Test fun noventaENove_usaAOfertaAceita_eNaoRepete() {
        val oe = OrderEarnings()
        oe.onOffer("99", 9.90, 1_000L)            // recusada
        oe.onOffer("99", 15.65, 2_000L)           // aceita (a última antes do pedido)
        val d = oe.onScreen("99", ninetyNineFinish, 3_000L)!!
        assertEquals("99#149031", d.key)
        assertEquals(15.65, d.value, 0.001)
        // A tela de concluir aparece várias vezes (OCR): não consome outra oferta nem soma de novo
        assertNull(oe.onScreen("99", ninetyNineFinish, 4_000L))
    }

    @Test fun noventaENove_semOferta_naoChuta() {
        assertNull(OrderEarnings().onScreen("99", ninetyNineFinish, 0L))
    }

    @Test fun ganhoDoApp_eOMaiorEntrePainelESomaDasEntregas_nuncaSoma() {
        val d = DayRecord("2026-11-13", earnings = mapOf("Keeta" to 25.19),
            orders = mapOf("Keeta#7040" to 11.72, "Keeta#7553" to 13.47))
        assertEquals(25.19, d.totalEarnings, 0.001)                     // painel = soma: não dobra
        // Passou da meia-noite: o painel do app zerou e mostra só a entrega nova; a soma do turno continua
        val after = d.copy(orders = d.orders + ("Keeta#8001" to 13.0))
        assertEquals(38.19, after.totalEarnings, 0.001)
        // Bônus que só aparece no painel: vale o painel
        assertEquals(30.0, d.copy(earnings = mapOf("Keeta" to 30.0)).totalEarnings, 0.001)
        // 99 só com entregas
        assertEquals(15.65 + 25.19, d.copy(orders = d.orders + ("99#149031" to 15.65)).totalEarnings, 0.001)
    }

    // ---------------------------------------------------------------- espera do restaurante

    @Test fun espera_medeDaChegadaAteAEntrega() {
        val done = mutableListOf<DeliveryRecord>()
        val t = DeliveryTracker { done += it }
        val min = 60_000L
        t.feed("Keeta", Phase.COLLECT, "Odin Sushi Jabaquara", 0)                  // aceitou, indo para a loja
        t.feed("Keeta", Phase.COLLECT, null, 6 * min, atStore = true)             // "Pedido pronto em cerca de…"
        t.feed("Keeta", Phase.COLLECT, null, 9 * min, atStore = true)             // continua esperando
        t.feed("Keeta", Phase.DELIVER, null, 18 * min)                             // saiu para entregar
        t.feed("Keeta", Phase.IDLE, null, 30 * min)
        assertEquals(1, done.size)
        assertEquals(12.0, done[0].waitMin!!, 0.01)
        assertEquals("Odin Sushi Jabaquara", done[0].restaurant)
    }

    @Test fun telaDeEsperaReconhecida() {
        assertTrue(PhaseDetector.atStore("Keeta", "Rotas | Restaurante | Você está esperando há muito tempo. Pergunte ao restaurante se o pedido está pronto. | Pedido pronto em cerca de 0–5 min."))
        assertFalse(PhaseDetector.atStore("Keeta", "Rotas | Cliente | Entregue até 22:47 | Cheguei na entrega"))
    }

    @Test fun restauranteDaOferta() {
        val offer = "Recusar | Novo pedido pra você | R$ 13,47 | Inclui incentivo | 6,7 km total | Coleta | Pizzaria Pizza Nabuco | [endereço], Jardim Itacolomi | Entrega até | 23:07 | Aceitar"
        assertEquals("Pizzaria Pizza Nabuco", PhaseDetector.offerRestaurant("Keeta", offer))
    }

    @Test fun mediaDeEspera_soComTresColetas() {
        val w = WaitStats()
        w.add("Pizzaria Pizza Nabuco", 15.0); w.add("pizzaria pizza nabuco", 21.0)
        assertNull(w.estimate("Pizzaria Pizza Nabuco"))
        w.add("Pizzaria  Pizza Nabuco", 18.0)
        assertEquals(18.0, w.estimate("Pizzaria Pizza Nabuco")!!.avgMin, 0.01)
    }

    // ---------------------------------------------------------------- explicação do card

    private val p = CostProfile(fuelPrice = 6.0, kmPerLiter = 30.0, otherCostPerKm = 0.15,
        greenPerKm = 1.8, yellowPerKm = 1.4, greenPerHour = 35.0, yellowPerHour = 15.0, avgSpeedKmh = 25.0)

    @Test fun verde_explicaPeloKm_eBuscaCurta() {
        val o = ParsedOffer("Keeta", 14.50, 6.0, 23.0, listOf(0.6, 5.4), emptyList(), pickupKm = 0.6)
        val r = ProfitCalculator.evaluate(o, p)
        assertEquals(Rating.GREEN, r.rating)
        assertTrue(r.why!!.headline, r.why!!.headline.startsWith("Boa: R\$"))
        assertTrue(r.why!!.headline.contains("2,42/km"))
        assertTrue(r.why!!.plus.toString(), r.why!!.plus.any { it == "busca curta 600 m" })
    }

    @Test fun amarelo_esperaDerrubaORporHora() {
        val o = ParsedOffer("Keeta", 9.80, 5.2, 14.0, listOf(5.2), emptyList())
        val semEspera = ProfitCalculator.evaluate(o, p)
        assertEquals(Rating.GREEN, semEspera.rating)                               // R$ 42/h
        val r = ProfitCalculator.evaluate(o, p, waitMin = 18.0, waitPlace = "Pizzaria")
        assertEquals(32.0, r.minutes, 0.01)
        assertEquals(Rating.YELLOW, r.rating)                                     // R$ 18/h
        assertEquals("Média: o km paga bem, mas a espera derruba o R\$/h", r.why!!.headline)
        assertTrue(r.why!!.minus.toString(), r.why!!.minus.any { it == "espera ~18 min" })
        assertEquals(42.0, r.perHourNoWait!!, 0.01)
    }

    @Test fun vermelho_explicaPeloKmEPelaBusca() {
        val o = ParsedOffer("iFood", 6.50, 6.8, 24.0, listOf(3.1, 3.7), emptyList(), pickupKm = 3.1)
        val r = ProfitCalculator.evaluate(o, p)
        assertEquals(Rating.RED, r.rating)
        assertTrue(r.why!!.headline, r.why!!.headline.startsWith("Ruim: R\$"))
        assertTrue(r.why!!.headline.contains("abaixo do seu mínimo"))
        assertTrue(r.why!!.minus.toString(), r.why!!.minus.any { it.startsWith("busca longa 3,1 km") })
        assertTrue(r.why!!.plus.isEmpty())
    }

    @Test fun vermelho_porAvisoSeu() {
        val o = ParsedOffer("Uber", 30.0, 10.0, 20.0, listOf(4.0, 6.0), emptyList(), pickupKm = 4.0)
        val r = ProfitCalculator.evaluate(o, p.copy(maxPickupKm = 3.0))
        assertEquals(Rating.RED, r.rating)
        assertEquals("Ruim: busca 4,0 km, acima do seu máximo", r.why!!.headline)
    }

    // ---------------------------------------------------------------- metas

    @Test fun metas_semanaComFolga_dividePelosDiasQueRestam() {
        // Sexta 13/11/2026. Seg 205, Ter 182, Qua folga, Qui 200, Sex até agora 125,40 (6 dias/semana, meta R$ 200/dia)
        val days = listOf(
            DayRecord("2026-11-09", earnings = mapOf("Keeta" to 205.0), startAt = 1),
            DayRecord("2026-11-10", earnings = mapOf("Keeta" to 182.0), startAt = 1),
            DayRecord("2026-11-12", earnings = mapOf("Keeta" to 200.0), startAt = 1),
            DayRecord("2026-11-13", earnings = mapOf("Keeta" to 186.40), startAt = 1,
                expenses = listOf(Expense(0, "outros", 30.0), Expense(0, "alimentacao", 9.0), Expense(0, "alimentacao", 22.0))),
        )
        val g = GoalMath.compute(days, "2026-11-13", 200.0, 6, 0.0)
        assertEquals(125.40, g.day.done, 0.001)
        assertEquals(1000.0 + 200.0, g.week.target, 0.001)                      // 6 dias × 200
        assertEquals(712.40, g.week.done, 0.001)
        assertEquals(1, g.week.daysOff)                                            // quarta
        assertEquals(3, g.week.workDaysLeft)                                       // sex, sáb, dom (6 − 3 trabalhados)
        assertEquals((1200.0 - 712.40) / 3, g.week.perDayNeeded!!, 0.01)
        // Novembro/2026: 30 dias × 6/7 ≈ 26 dias de trabalho
        assertEquals(200.0 * 26, g.month.target, 0.001)
    }

    @Test fun gastosEntramNoLiquido() {
        val d = DayRecord("2026-11-13", kmStart = 100.0, kmEnd = 150.0, earnings = mapOf("99" to 100.0),
            expenses = listOf(Expense(0, "alimentacao", 22.0)))
        val s = JournalMath.summarize(d, 0.5)
        assertEquals(22.0, s.expenses, 0.001)
        assertEquals(100.0 - 25.0 - 22.0, s.netAfterExpenses, 0.001)
    }

    // ---------------------------------------------------------------- privacidade

    @Test fun chatComClienteNuncaSai() {
        val chat = "Laura Maria | boa noite, nao acho o número | Lida | Minha imagem de perfil | Imagem de perfil dele/dela | onde você está? | Voz | Insira sua mensagem | Mensagens rápidas"
        assertTrue(LogSanitizer.isChat(chat))
        assertNull(LogSanitizer.forServer(chat))
    }

    @Test fun nomeDoClienteMascarado_restauranteNaColetaFica() {
        val entrega = "Mapa do Google | Marcador do mapa | Emanoel | [endereço], São Paulo, SP | Bloco: Vila Imperio | Rotas | Cliente | #7040 | Entregue até 22:47"
        val out = LogSanitizer.forServer(entrega)!!
        assertFalse(out, out.contains("Emanoel"))
        assertTrue(out.contains("[cliente]"))
        val finalizar = "Entrega | Franciely Gomes | #7553 | [endereço], Rua Estevão Baião, 150, São Paulo, SP | Para finalizar a entrega"
        assertFalse(LogSanitizer.forServer(finalizar)!!.contains("Franciely"))
        assertFalse(LogSanitizer.forServer(ninetyNineFinish)!!.contains("Laura"))
        assertTrue(LogSanitizer.forServer(ninetyNineFinish)!!.contains("#149031 [cliente]"))
        val codigo = "Chegue na loja até 22:54 | 1 item | Número do pedido | Chat da entrega | Código de coleta 4469 Franciely Gomes | Confirmar coleta"
        assertFalse(LogSanitizer.maskPeople(codigo).contains("Franciely"))
        assertEquals("[endereço], São Paulo, SP", LogSanitizer.mask("Rua Alfa, 10 - Estrada das Lágrimas, 2282, São Paulo, SP"))
        // Tela de coleta (restaurante) não tem cliente: o nome do restaurante continua (é estabelecimento)
        val coleta = "Mapa do Google | Marcador do mapa | Odin Sushi Jabaquara | [endereço], Jardim Oriental | Rotas | Restaurante | Pedido pronto em cerca de 0–5 min."
        assertTrue(LogSanitizer.forServer(coleta)!!.contains("Odin Sushi Jabaquara"))
    }
}
