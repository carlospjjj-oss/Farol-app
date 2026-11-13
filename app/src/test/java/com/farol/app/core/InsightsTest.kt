package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telas reais do Keeta (12/11, endereços como chegam no app, sem máscara). */
class InsightsTest {

    private val coleta = "Mapa do Google | Marcador do mapa | 1 | Tempero e Doçura | Rua padre de mororó 166 | . | 2 | Rotas | Restaurante | Pedido pronto em cerca de 5–10 min. | #7801 | Go to Delivery Details | Chegue na loja até 10:56 | 1 item"
    private val esperando = "Mapa do Google | Marcador do mapa | Tempero e Doçura | Rua padre de mororó 166 | . | Rotas | Restaurante | Você está esperando há muito tempo. | O pedido pode estar pronto | #7801"
    private val entrega = "Mapa do Google | Marcador do mapa | 1 | Débora Pereira | Rua X, São Caetano do Sul, SP | Rotas | Cliente | #7801 | Go to Delivery Details | Entregue até 11:12 | 1 item"
    private val finalizar = "Entrega | Débora Pereira | #7801 | Para finalizar a entrega | Entregue até 11:12 | Passo 1 | Falar com o cliente"
    private val procurando = "Novo | Procurando pedidos... | Encontrando os melhores pedidos pra você | Mapa do Google | R$ 42,60 | Ganhos | 5 | Pedidos hoje | Online"
    private val painelEntregando = "Novo | Painel | R$ 42,60 | 5 | Entregando (1) | Mapa do Google | R$ 42,60 | Ganhos | 5 | Pedidos hoje"
    private val coletaComAviso = "Mapa do Google | Marcador do mapa | Se um pedido estiver demorando, você pode cancelar sem penalidade. | Chat da entrega | Suporte | Coletar o pedido | O pedido pode estar pronto | #5503 | +R$ 2,00 | Chegue na loja até 12:06"

    @Test fun fases() {
        assertEquals(Phase.COLLECT, PhaseDetector.detect("Keeta", coleta))
        assertEquals(Phase.COLLECT, PhaseDetector.detect("Keeta", esperando))
        assertEquals(Phase.DELIVER, PhaseDetector.detect("Keeta", entrega))
        assertEquals(Phase.DELIVER, PhaseDetector.detect("Keeta", finalizar))
        assertEquals(Phase.IDLE, PhaseDetector.detect("Keeta", procurando))
        assertNull(PhaseDetector.detect("Keeta", painelEntregando))   // ainda entregando
        assertNull(PhaseDetector.detect("iFood", coleta))
    }

    @Test fun restaurante() {
        assertEquals("Tempero e Doçura", PhaseDetector.restaurant("Keeta", coleta))
        assertEquals("Tempero e Doçura", PhaseDetector.restaurant("Keeta", esperando))
        assertNull(PhaseDetector.restaurant("Keeta", coletaComAviso)?.takeIf { it.startsWith("Se um") })
    }

    @Test fun pedidoCompleto() {
        val done = mutableListOf<DeliveryRecord>()
        val t = DeliveryTracker { done += it }
        val m = 60_000L
        t.feed("Keeta", Phase.COLLECT, "Tempero e Doçura", 0)
        t.feed("Keeta", Phase.COLLECT, "Tempero e Doçura", 5 * m)
        t.feed("Keeta", Phase.DELIVER, null, 12 * m)
        t.feed("Keeta", Phase.DELIVER, null, 20 * m)
        t.feed("Keeta", Phase.IDLE, null, 23 * m)
        assertEquals(1, done.size)
        assertEquals(12.0, done[0].collectMin!!, 0.01)
        assertEquals(11.0, done[0].deliverMin!!, 0.01)
        assertEquals("Tempero e Doçura", done[0].restaurant)
    }

    @Test fun pedidosEmSequenciaSemTelaDeEspera() {
        val done = mutableListOf<DeliveryRecord>()
        val t = DeliveryTracker { done += it }
        val m = 60_000L
        t.feed("Keeta", Phase.COLLECT, "A", 0)
        t.feed("Keeta", Phase.DELIVER, null, 10 * m)
        t.feed("Keeta", Phase.COLLECT, "B", 25 * m)     // aceitou outro direto
        t.feed("Keeta", Phase.DELIVER, null, 30 * m)
        t.feed("Keeta", Phase.IDLE, null, 40 * m)
        assertEquals(listOf("A", "B"), done.map { it.restaurant })
        assertEquals(15.0, done[0].deliverMin!!, 0.01)
    }

    @Test fun melhoresHoras() {
        val offers = listOf(
            Triple(11, 1.5, false), Triple(11, 1.5, false), Triple(11, 1.6, false),
            Triple(12, 2.2, true), Triple(12, 2.0, true), Triple(12, 1.9, true),
            Triple(13, 3.0, true),                                  // só 1 oferta: não entra no ranking
        )
        val stats = HourInsights.byHour(offers)
        val best = HourInsights.best(stats)
        assertEquals(12, best.first().hour)
        assertTrue(best.none { it.hour == 13 })
    }

    // ---- avisos de recusa (textos reais de 12/11) ----

    @Test fun recusas99() {
        val s = RejectionReader.read("Recusar | X | rejeitou 7 vez(es) seguidas | Se rejeitar novamente, sua conta ficará restrita | de receber novos ped")!!
        assertEquals(7, s.streak); assertTrue(!s.paused)
    }

    @Test fun contaPausada99() {
        val s = RejectionReader.read("Entregas disponíveis apenas nas Solicitações de Pedidos | Conta pausada por excesso de rejeições | 18:33")!!
        assertTrue(s.paused)
    }

    @Test fun paradaTecnicaIfood() {
        assertTrue(RejectionReader.read("Aceitar(53s) | Muitas rotas rejeitadas | Parada técnica de 14 minutos")!!.paused)
    }

    @Test fun telaNormal_semAviso() = assertNull(RejectionReader.read("Painel | R$ 42,60 | Pedidos hoje"))

    @Test fun bairroFavorito_soDestaca() {
        val o = OfferParser.parse("R$ 10,00 | 4 km | 10 min | Rua X, São Caetano do Sul | Aceitar", "Keeta")!!
        val r = ProfitCalculator.evaluate(o, CostProfile(favoriteWords = listOf("sao caetano")))
        assertEquals(listOf("sao caetano"), r.favorites)
        assertTrue(r.warnings.isEmpty())
    }

    // ---- velocidade aprendida ----

    @Test fun velocidadeAmostras() {
        assertEquals(15.9, SpeedLearner.sample(6.62, 25.0)!!, 0.05)        // iFood real de 11/11
        assertNull(SpeedLearner.sample(3.3, 118.0))                        // 1,7 km/h: descarta
        assertNull(SpeedLearner.sample(0.2, 10.0))
    }

    @Test fun velocidadeMediana() {
        assertNull(SpeedLearner.learned(listOf(12.0, 13.0, 14.0)))         // poucas amostras
        assertEquals(13.0, SpeedLearner.learned(listOf(12.0, 13.0, 30.0, 10.0, 14.0))!!, 0.01)
        assertEquals(40, (1..60).fold(emptyList<Double>()) { acc, _ -> SpeedLearner.add(acc, 12.0) }.size)
    }

    @Test fun pedidoGuardaKmDaOferta() {
        val done = mutableListOf<DeliveryRecord>()
        val t = DeliveryTracker { done += it }
        val m = 60_000L
        t.feed("Keeta", Phase.COLLECT, "A", 0, offerKm = 6.4)
        t.feed("Keeta", Phase.DELIVER, null, 10 * m)
        t.feed("Keeta", Phase.IDLE, null, 23 * m)
        assertEquals(6.4, done[0].km!!, 0.001)
        assertEquals(23.0, done[0].totalMin!!, 0.01)
    }

    // ---- GPS ----

    @Test fun gpsFiltraRuido() {
        assertEquals(0.0, GpsMath.step(5.0, 4.0, 10f), 0.0)       // tremido parado
        assertEquals(0.0, GpsMath.step(50.0, 4.0, 80f), 0.0)      // ponto impreciso
        assertEquals(0.0, GpsMath.step(900.0, 4.0, 10f), 0.0)     // salto (810 km/h)
        assertEquals(40.0, GpsMath.step(40.0, 4.0, 10f), 0.0)     // 36 km/h: vale
    }

    @Test fun gpsKmEntreHorarios() {
        val m = 60_000L
        val pts = listOf(0L to 0.0, 10 * m to 3.0, 20 * m to 5.0, 30 * m to 9.0)
        assertEquals(6.0, GpsMath.kmBetween(pts, 10 * m, 30 * m)!!, 0.001)
        assertEquals(2.5, GpsMath.kmBetween(pts, 5 * m, 15 * m)!!, 0.001)   // interpolado
        assertNull(GpsMath.kmBetween(pts, 60 * m, 70 * m))                  // fora do período medido
    }

    // ---- histórico sem duplicadas ----

    @Test fun duplicadasDoHistorico() {
        val m = 60_000L
        val k1 = OfferDedup.key("99", 11.85, 9.5); val k2 = OfferDedup.key("Keeta", 7.5, 3.3)
        val flags = OfferDedup.keepFlags(listOf(0L to k1, 30_000L to k1, 60_000L to k1, 70_000L to k2, 10 * m to k1))
        assertEquals(listOf(true, false, false, true, true), flags)   // repetições em <3 min somem; 10 min depois é outra
    }
}
