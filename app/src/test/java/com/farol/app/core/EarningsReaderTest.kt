package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Telas reais dos logs de 12/11. */
class EarningsReaderTest {

    @Test fun keeta_painel() = assertEquals(42.60, EarningsReader.read("Keeta",
        "Novo | Painel | R$ 42,60 | 5 | Entregando (1) | Mapa do Google | Marcador do mapa | +R$ 4,00 | Ganhos extras para todos os pedidos em ACLIMAÇÃO agora | Extra pelo pedido | 12:45-13:00 | +R$ 4,00 | R$ 42,60 | Ganhos | 5 | Pedidos hoje | Offline | Perfil")!!, 0.001)

    @Test fun keeta_procurando() = assertEquals(42.60, EarningsReader.read("Keeta",
        "Novo | Procurando pedidos... | Mapa do Google | +R$ 4,00 | Ganhos extras para todos os pedidos | 12:45-13:00 | +R$ 4,00 | R$ 42,60 | Ganhos | 5 | Pedidos hoje | Online | Perfil")!!, 0.001)

    @Test fun keeta_zerado() = assertEquals(0.0, EarningsReader.read("Keeta",
        "Painel | R$ 0,00 | 0 | Ficar online | R$ 0,00 | Ganhos | 0 | Pedidos hoje | Offline | Perfil")!!, 0.001)

    @Test fun noventaENove_painelOcr() = assertEquals(17.85, EarningsReader.read("99",
        "Painel Editar | R$17,85 | R$17,85 | / (solicitação) dia Solicitações > | Recompensas (5) | +R$3 por pedido| | Entrega Food | Termina em 17 min")!!, 0.001)

    @Test fun ifood_valorLetraPorLetra() = assertEquals(0.0, EarningsReader.read("iFood",
        "Mapbox Logo | Vem conferir o que mudou na tela inicial! | Rotas pra entregar agora | SOS | R | $ | 0 | , | 0")!!, 0.001)

    @Test fun ifood_comValor() = assertEquals(37.5, EarningsReader.read("iFood",
        "Rotas pra entregar agora | SOS | R | $ | 3 | 7 | , | 5 | 0")!!, 0.001)

    @Test fun telaDeOferta_naoEPainel() = assertNull(EarningsReader.read("Keeta",
        "Painel | R$ 7,50 | 3,3 km total | Aceitar(53s)"))

    @Test fun telaDeEntrega_naoEPainel() = assertNull(EarningsReader.read("Keeta",
        "Mapa do Google | Tempero e Doçura | Rotas | Restaurante | Pedido pronto em cerca de 5–10 min. | #7801 | Chegue na loja até 10:56"))
}
