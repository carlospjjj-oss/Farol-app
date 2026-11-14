package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Banco de telas reais (golden/telas_reais.tsv): cada tela que já apareceu nos logs tem de continuar sendo
 * entendida do mesmo jeito. Se um app mudar o layout, a tela nova entra aqui e o teste mostra o que quebrou.
 */
class TelasReaisTest {

    private data class Row(val line: Int, val app: String, val check: String, val expected: String, val text: String)

    private fun rows(): List<Row> {
        val f = listOf("src/test/resources/golden/telas_reais.tsv", "app/src/test/resources/golden/telas_reais.tsv").map(::File).first { it.exists() }
        return f.readLines().withIndex().filter { it.value.isNotBlank() && !it.value.startsWith("#") }.map { (i, l) ->
            val p = l.split('\t', limit = 4)
            Row(i + 1, p[0], p[1], p[2], p[3])
        }
    }

    @Test fun todasAsTelasContinuamSendoEntendidas() {
        val errors = mutableListOf<String>()
        for (r in rows()) {
            val ok = runCatching { check(r) }.getOrElse { errors += "linha ${r.line} (${r.app} ${r.check}): ${it.message}"; true }
            if (!ok) errors += "linha ${r.line} (${r.app} ${r.check}): esperado ${r.expected}"
        }
        assertTrue("Telas que mudaram de leitura:\n" + errors.joinToString("\n"), errors.isEmpty())
        assertTrue(rows().size >= 10)
    }

    @Test fun nenhumaTelaGuardadaTemDadoDeCliente() {
        for (r in rows()) {
            assertFalse("linha ${r.line}: conversa com cliente não pode ficar no banco de testes", LogSanitizer.isChat(r.text))
            assertEquals("linha ${r.line}: nome de cliente sem máscara", r.text, LogSanitizer.maskPeople(r.text))
        }
    }

    private fun check(r: Row): Boolean = when (r.check) {
        "pedido" -> {
            val (key, v) = r.expected.split('=')
            val d = OrderEarnings().onScreen(r.app, r.text, 0L)
            d != null && d.key == key && Math.abs(d.value - v.toDouble()) < 0.005
        }
        "pedido_oferta" -> {
            val oe = OrderEarnings(); oe.onOffer(r.app, r.expected.toDouble(), 0L)
            val d = oe.onScreen(r.app, r.text, 1_000L)
            d != null && Math.abs(d.value - r.expected.toDouble()) < 0.005 && oe.onScreen(r.app, r.text, 2_000L) == null
        }
        "painel" -> Math.abs((EarningsReader.read(r.app, r.text) ?: -1.0) - r.expected.toDouble()) < 0.005
        "historico_dia" -> {
            val (date, v) = r.expected.split('=')
            val t = HistoryReader.keetaDay(r.text, "2026-11-14")
            t != null && t.date == date && Math.abs(t.value - v.toDouble()) < 0.005
        }
        "saldo" -> Math.abs(HistoryReader.ninetyNineSaldo(r.text).sumOf { it.value } - r.expected.toDouble()) < 0.005
        "lancamentos" -> Math.abs(HistoryReader.ifoodEntries(r.text, "2026-11-14").sumOf { it.value } - r.expected.toDouble()) < 0.005
        "espera" -> PhaseDetector.atStore(r.app, r.text) == (r.expected == "sim")
        else -> error("verificação desconhecida: ${r.check}")
    }

    @Suppress("unused") private fun keep() { assertNotNull(1); assertNull(null) }
}
