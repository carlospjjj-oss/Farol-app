package com.farol.app.core

import com.farol.app.core.ReaderHealth.Day
import com.farol.app.core.ReaderHealth.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHealthTest {
    @Test fun poucosCasosSemDados() = assertEquals(Status.SEM_DADOS, ReaderHealth.status(Day(1, 1), emptyList()))
    @Test fun leituraBoa() = assertEquals(Status.OK, ReaderHealth.status(Day(19, 1), listOf(Day(20, 1))))
    @Test fun metadeNaoLidaEProblema() = assertEquals(Status.PROBLEMA, ReaderHealth.status(Day(4, 6), emptyList()))
    @Test fun quedaForteContraASemanaEProblema() =
        assertEquals(Status.PROBLEMA, ReaderHealth.status(Day(6, 4), listOf(Day(30, 0), Day(25, 1))))   // 60% vs ~98%
    @Test fun quedaModeradaEAtencao() =
        assertEquals(Status.ATENCAO, ReaderHealth.status(Day(8, 2), listOf(Day(30, 0), Day(30, 1))))    // 80% vs ~98%
    @Test fun repeticaoDaMesmaTelaContaUmaVez() {
        val d = ReaderHealth.MissDedup()
        assertTrue(d.isNew("99", "Aceitar R$ 10", 0))
        assertFalse(d.isNew("99", "Aceitar R$ 11", 5_000))      // mesma tela, só o número mudou
        assertTrue(d.isNew("99", "Aceitar R$ 11", 30_000))
        assertTrue(d.isNew("iFood", "Aceitar R$ 11", 31_000))
    }
}
