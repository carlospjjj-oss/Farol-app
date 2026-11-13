package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ofertas e telas REAIS dos logs (anonimizadas) com o resultado que o leitor deu quando foram conferidas.
 * Se uma mudança no leitor alterar a leitura de alguma delas, o build falha e mostra qual.
 * Para atualizar o arquivo depois de uma correção proposital: tools/golden.py (gera de novo a partir dos logs).
 */
class OfertasReaisTest {

    private data class Case(val line: Int, val app: String, val expected: List<Double>?, val text: String)

    private fun cases(): List<Case> {
        val res = javaClass.getResourceAsStream("/golden/ofertas_reais.tsv") ?: error("golden/ofertas_reais.tsv não encontrado")
        return res.bufferedReader().readLines().mapIndexedNotNull { i, l ->
            if (l.isBlank() || l.startsWith("#")) return@mapIndexedNotNull null
            val p = l.split('\t')
            val text = p.drop(6).joinToString("\t").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
            Case(i + 1, p[0], if (p[1] == "null") null else p.subList(1, 6).map { it.toDouble() }, text)
        }
    }

    @Test fun leituraDasOfertasReaisNaoMudou() {
        val all = cases()
        assertTrue("arquivo de ofertas reais vazio", all.size >= 50)
        val wrong = mutableListOf<String>()
        for (c in all) {
            val o = OfferParser.parse(c.text, c.app)
            val got = o?.let { listOf(it.price, it.totalKm, it.totalMin ?: -1.0, it.pickupKm ?: -1.0, it.passengerRating ?: -1.0) }
            val same = if (c.expected == null || got == null) c.expected == got
                       else c.expected.zip(got).all { (a, b) -> Math.abs(a - b) < 0.011 }
            if (!same) wrong += "linha ${c.line} (${c.app}): esperado ${c.expected} → leu $got | ${c.text.take(90)}"
        }
        assertEquals("Leitura mudou em ${wrong.size} oferta(s) real(is):\n" + wrong.joinToString("\n"), 0, wrong.size)
    }
}
