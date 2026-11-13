package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Textos no formato em que chegam do leitor de acessibilidade (nós juntados com " | ").
 * Quando você capturar telas reais no "Modo captura", cole aqui como novos casos de teste.
 */
class OfferParserTest {

    private fun p(t: String, app: String = "Uber") = OfferParser.parse(t, app)

    @Test fun uber_buscaMaisViagem_somaTudo_ignoraGanhosDoDia() {
        val o = p("R$ 123,45 | UberX | 4,93 ★ | R\$ 15,20 | 4 min (1,2 km) de distância | Rua das Flores, Centro | Viagem de 18 min (9,4 km) | Av. Paulista | Toque para aceitar")
        assertNotNull(o); o!!
        assertEquals(15.20, o.price, 0.001)
        assertEquals(10.6, o.totalKm, 0.001)
        assertEquals(22.0, o.totalMin!!, 0.001)
    }

    @Test fun uber_dinamicaComMais_eIgnorada() {
        val o = p("UberX | R$ 18,40 | +R$ 3,00 incluído | 6 min (2,0 km) | Viagem de 1 h 5 min (32,0 km) | Aceitar")!!
        assertEquals(18.40, o.price, 0.001)
        assertEquals(34.0, o.totalKm, 0.001)
        assertEquals(71.0, o.totalMin!!, 0.001)
    }

    @Test fun noventaENove_camposSeparados() {
        val o = p("99Pop | R$ 12,50 | 2,1 km | 5 min | 8,3 km | 17 min | Aceitar", "99")!!
        assertEquals(12.50, o.price, 0.001)
        assertEquals(10.4, o.totalKm, 0.001)
        assertEquals(22.0, o.totalMin!!, 0.001)
    }

    @Test fun ifood_usaRotaTotal_naoSomaColeta() {
        val o = p("Nova entrega | R$ 9,00 | Coleta a 800 m | Rota total de 4,5 km | Aceitar", "iFood")!!
        assertEquals(9.0, o.price, 0.001)
        assertEquals(4.5, o.totalKm, 0.001)
        assertNull(o.totalMin)
    }

    @Test fun indrive_precoSemCentavos() {
        val o = p("Maria | R$ 20 | 3 km | 12 min | Aceitar por R$ 20", "inDrive")!!
        assertEquals(20.0, o.price, 0.001)
        assertEquals(3.0, o.totalKm, 0.001)
        assertEquals(12.0, o.totalMin!!, 0.001)
    }

    @Test fun ignoraPrecoPorKmExibidoPeloProprioApp() {
        val o = p("R$ 14,00 | R$ 1,75/km | 8 km | 20 min | Aceitar")!!
        assertEquals(14.0, o.price, 0.001)
    }

    @Test fun telaInicialSemOferta_retornaNull() {
        assertNull(p("Você está online | Ganhos de hoje R$ 230,00 | 12 viagens | 3,2 km"))
    }

    @Test fun semDistancia_retornaNull() {
        assertNull(p("R$ 15,00 | Aceitar"))
    }

    @Test fun milharEDecimalComPonto() {
        assertEquals(1234.56, OfferParser.parseMoney("1.234,56")!!, 0.001)
        assertEquals(1200.0, OfferParser.parseMoney("1.200")!!, 0.001)
        assertEquals(15.2, OfferParser.parseMoney("15.20")!!, 0.001)
        val o = p("UberX | R$ 15.20 | 3 min (0.9 km) | Trip 14 min (6.1 km) | Accept")!!
        assertEquals(7.0, o.totalKm, 0.001)
    }

    @Test fun assinaturaEstavel() {
        val a = p("R$ 10,00 | 5 km | 10 min | Aceitar")!!
        val b = p("R$ 10,00 | 5 km | 10 min | Aceitar")!!
        assertEquals(a.signature, b.signature)
    }

    @Test fun extraiBuscaENota() {
        val o = p("UberX | 4,93 ★ | R$ 15,20 | 4 min (1,2 km) de distância | Viagem de 18 min (9,4 km) | Aceitar")!!
        assertEquals(1.2, o.pickupKm!!, 0.001)
        assertEquals(4.93, o.passengerRating!!, 0.001)
    }

    @Test fun ifood_buscaPorContextoColeta() {
        val o = p("Nova entrega | R$ 9,00 | Coleta a 800 m | Rota total de 4,5 km | Aceitar", "iFood")!!
        assertEquals(0.8, o.pickupKm!!, 0.001)
    }

    @Test fun estrelaAntesDaNota() {
        val o = p("★ 4,7 | R$ 12,00 | 2 km | 6 km | Aceitar")!!
        assertEquals(4.7, o.passengerRating!!, 0.001)
    }

    @Test fun semBuscaSeparada_pickupNull() {
        val o = p("R$ 14,00 | 8 km | 20 min | Aceitar")!!
        assertNull(o.pickupKm)
        assertNull(o.passengerRating)
    }

    // ---- casos reais dos logs de 11/11 ----

    @Test fun real99_telaInicialComAceiteRotas_naoEOferta() {
        assertNull(p("Atualizando sua localização... | Mapbox Logo | Vem conferir o que mudou na tela inicial! | Seus ganhos estão próximos! | Transforme suas entregas em ganhos. Aceite rotas pra alcançar seus objetivos! | Rotas pra entregar agora | SOS | R | $ | 0 | , | 0", "99"))
    }

    @Test fun real99_configuracoes_naoEOferta() {
        assertNull(OfferParser.parse("99 | Configurar solicitações | Ferramentas de aceitação | Status da solicitação | Teste de status | 5 km", "99"))
    }

    @Test fun noventaENove_valorLetraPorLetra() {
        val o = p("Entrega Moto | R | $ | 1 | 2 | , | 5 | 0 | 2,1 km | 4,3 km | Aceitar", "99")!!
        assertEquals(12.5, o.price, 0.001)
        assertEquals(6.4, o.totalKm, 0.001)
    }

    @Test fun juntaSoPedacosDeUmCaractere() {
        assertEquals("R$12,50 | 4 min | 2", OfferParser.mergeSplitChars("R | $ | 1 | 2 | , | 5 | 0 | 4 min | 2"))
    }

    // ---- ofertas reais (11/11, tarde) ----

    @Test fun realIfood_acessibilidade() {
        val o = p("R$ 11,08 | Valor da rota | Distância | 6,62 km | Tempo | 25 min | A Casa da Feijoada | [endereço], Conceição,Diadema,SP,BR | [endereço], Vila Fachini | Rejeitar | Aceitar | 86", "iFood")!!
        assertEquals(11.08, o.price, 0.001); assertEquals(6.62, o.totalKm, 0.001); assertEquals(25.0, o.totalMin!!, 0.001)
    }

    @Test fun real99Food_ocr_distanciaTotalIgnoraMetrosDaBarra() {
        val o = p("99 O | 9 11 m | A Entrega Food | R$10,35 | A Ir | S Distância total 7,8 km | La Vivi Patisserie | [endereço], Vila | Macedopolis | Aceitar pedido | < | X", "99")!!
        assertEquals(10.35, o.price, 0.001); assertEquals(7.8, o.totalKm, 0.001)
    }

    @Test fun real99_telaInicialComValorDoDia_semBotao_naoEOferta() {
        assertNull(p("400 m | R$ 11,08 | Taboão da Serra | mapbox | Seus ganhos estão próximos! | Rotas pra entregar agora | 99) | keeta | entregador | Transforme suas entregas em ganhos. Aceite | rotas pra alcançar seus objetivos!", "99"))
    }

    @Test fun realKeeta() {
        val o = p("8 de olho | R$ 13,98 | Inclui incentivo | 8 km total | Coleta | Paráçaí - Açaiteria e lanchonete | [endereço], Vila Liviero, São Paulo | Pegar (44s) | Recusar", "Keeta")!!
        assertEquals(13.98, o.price, 0.001); assertEquals(8.0, o.totalKm, 0.001)
    }

    // ---- 12/11: 99 com bônus incluído (o bônus era lido como preço) ----

    @Test fun real99_bonusIncluidoNaoEPreco() {
        val o = p("der | IPIRANGA | IPANARIO | São Caetano do | Sul | 050 | Entrega Food | R$14,43 | Inclui R$3,00 Bônus | ANCHIETA | cliente | S Distância total 9 km | Tempero de | Mãe | Pegar pedido", "99")!!
        assertEquals(14.43, o.price, 0.001); assertEquals(9.0, o.totalKm, 0.001)
    }

    @Test fun real99_bonusComRSdigitadoErradoPeloOcr() {
        val o = p("Entrega Food | R$14,77 | Inclui RS3,00 Bônus | S Distância total 9,4 km | S Pegar pedido", "99")!!
        assertEquals(14.77, o.price, 0.001)
    }

    @Test fun real99_telaInicialComPromocoes_naoEOferta() {
        assertNull(p("Painel | R$17,85 | Recompensas (5) | +R$3 por pedido | Entrega Food | Termina em 17 min | R$3,97 | Posto Fita Azul | Ganhe até R$84 > | Aceitar corridas", "99"))
    }

    @Test fun realKeeta_aceitarComContador() {
        val o = p("R$ 7,50 | 3,3 km total | [endereço] | [endereço], São Caetano do Sul, SP | Aceitar(53s)", "Keeta")!!
        assertEquals(7.5, o.price, 0.001); assertEquals(3.3, o.totalKm, 0.001)
    }

    // ---- 12/11 tarde: Keeta ----

    @Test fun realKeeta_kmTotalNaoViraBusca() {
        val o = p("Recusar | 6 de olho | R$ 7,50 | Ganhos nessa entrega | 3,5 km total | Coleta | Espetinhos Visconde Grill | [endereço], SAO CAETANO DO SUL, SP | Entrega até | 11:46 | [endereço], São Caetano do Sul, SP | Pegar | (54s)", "Keeta")!!
        assertEquals(7.5, o.price, 0.001); assertEquals(3.5, o.totalKm, 0.001); assertNull(o.pickupKm)
    }

    @Test fun realKeeta_extraIncluidoNaoEPreco() {
        val o = p("Recusar | 7 de olho | R$ 10,10 | R$ 3,00 Inclui extra de entrega caprichada | Ganhos nessa entrega | 3,8 km total | Coleta | Yakan Sushi | +R$ 3,00 | Entrega até 11:45 | Pegar | (38s)", "Keeta")!!
        assertEquals(10.10, o.price, 0.001); assertNull(o.pickupKm)
    }

    @Test fun realKeeta_extraEIncentivo() {
        val o = p("Recusar | 9 de olho | R$ 12,60 | R$ 2,00 Inclui extra de entrega caprichada | Inclui incentivo | 1,6 km total | Coleta | Espeto Paulista | +R$ 2,00 | Pegar | (54s)", "Keeta")!!
        assertEquals(12.60, o.price, 0.001); assertEquals(1.6, o.totalKm, 0.001)
    }

    // ---- 12/11 tarde: iFood com promoção (a promo era lida como preço) ----

    @Test fun realIfood_promoNaoEPreco() {
        val o = p("R$ 12,96 | Valor da rota + Extras | Promo R$ 5,00 | Distância | 3,15 km | Tempo | 17 min | Padaria Kennedy | [endereço] | 1 | [endereço], Centro | Rejeitar | Aceitar | 88", "iFood")!!
        assertEquals(12.96, o.price, 0.001); assertEquals(3.15, o.totalKm, 0.001); assertEquals(17.0, o.totalMin!!, 0.001)
    }

    @Test fun realIfood_promo10() {
        val o = p("R$ 22,58 | Valor da rota + Extras | Promo R$ 10,00 | Distância | 4,47 km | Tempo | 35 min | Popeyes | Rejeitar | Aceitar | 88", "iFood")!!
        assertEquals(22.58, o.price, 0.001)
    }

    // ---- CARRO: prints reais enviados em 13/11 ----

    @Test fun car99_telaNova_naoAfetaTa_dinamicaNaoEPreco() {
        val o = p("Não afeta a TA | Prioritário | Dinheiro | R$6,70 | R$1,96/km | Preço x1,2 | R$1,12 | 4,91 | +999 corridas | Perfil Essencial | 6min (1,1km) | IGEPPS - Instituto de Gestão | 9min (2,3km) | Shopping Pátio Belém", "99")!!
        assertEquals(6.70, o.price, 0.001); assertEquals(3.4, o.totalKm, 0.001); assertEquals(15.0, o.totalMin!!, 0.001)
        assertEquals(1.1, o.pickupKm!!, 0.001); assertEquals(4.91, o.passengerRating!!, 0.001)
    }

    @Test fun car99_corridaLonga_porKmEIncluidos() {
        val o = p("NÃO AFETA A TA | Corrida longa | R$54,89 | R$2,20 por km | x2,0 | R$15.50 incluídos | 4,8 | 12 corridas | Cartão Verif. | 5Min (2,5km) | Shopping Penha | 32Min (6,3km) | Área de risco | Estação Prefeito Saladino", "99")!!
        assertEquals(54.89, o.price, 0.001); assertEquals(8.8, o.totalKm, 0.001); assertEquals(37.0, o.totalMin!!, 0.001)
        assertEquals(4.8, o.passengerRating!!, 0.001)
    }

    @Test fun carUber_viagemLonga_naoSomaMaisDe45() {
        val o = p("UberX | R$ 90,13 | ★ 4,85 | 3 minutos (0.9 km) de distância | Rua Américo Salvador Novelli, Itaquera | Viagem de 1 h e 55 min (64.3 km) | R. Cássio M Boy, 47 | Viagem longa (mais de 45 min) | Aceitar", "Uber")!!
        assertEquals(90.13, o.price, 0.001); assertEquals(65.2, o.totalKm, 0.001); assertEquals(118.0, o.totalMin!!, 0.001)
        assertEquals(0.9, o.pickupKm!!, 0.001); assertEquals(4.85, o.passengerRating!!, 0.001)
    }

    @Test fun carUber_blackBag_incluidoNaoEPreco() {
        val o = p("Black Bag | Exclusivo | R$ 188,36 | ★ 4,92 (924) | Verificado | +R$ 3,00 incluído | 1 min (0.1 km) de distância | Terminal 3, Aeroporto Internacional | Viagem de 1 h e 48 min (43.6 km) | Rua Pires De Oliveira | Viagem longa (mais de 90 min) | Aceitar", "Uber")!!
        assertEquals(188.36, o.price, 0.001); assertEquals(43.7, o.totalKm, 0.001); assertEquals(109.0, o.totalMin!!, 0.001)
        assertEquals(4.92, o.passengerRating!!, 0.001)
    }

    @Test fun carInDrive_telaDoPassageiro_naoEOferta() {
        assertNull(p("Viagem | Moto | Entregas | Cidade a Cidade | Rua Ministro Otávio Kelly 519 (Icaraí) | Origem | Alameda São Boaventura 847 (Fonseca) | R$ 13 — Você pode alterar a tarifa recomendada | Aceitar automaticamente oferta de R$ 13 | Tarifa Recomendada Para Esta Rota R$ 13 ajustável | Encontre um motorista", "inDrive"))
        assertNull(p("Ofereça sua tarifa | R$8 | A tarifa mínima é R$ 10 | Dinheiro | Aceitar automaticamente o motorista mais próximo por R$ 8 | Concluído", "inDrive"))
    }
}
