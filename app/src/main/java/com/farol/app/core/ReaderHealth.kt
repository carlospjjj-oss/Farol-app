package com.farol.app.core

/**
 * Saúde do leitor: quantas ofertas o Farol conseguiu ler e quantas telas com cara de oferta
 * (botão "Aceitar" etc.) ele NÃO conseguiu ler, por app e por dia. Só números: funciona sem
 * enviar texto nenhum. Se a taxa cair (app mudou a tela), o painel avisa.
 * As mesmas regras estão em server/worker.js (healthStatus): mudou aqui, mude lá.
 */
object ReaderHealth {
    data class Day(val ok: Int, val miss: Int) {
        val total: Int get() = ok + miss
        val rate: Double? get() = if (total == 0) null else ok.toDouble() / total
    }

    enum class Status { OK, ATENCAO, PROBLEMA, SEM_DADOS }

    /** Poucos casos não dizem nada: abaixo disso fica "sem dados". */
    const val MIN_CASES = 3

    fun status(today: Day, previous: List<Day>): Status {
        val r = today.rate
        if (r == null || today.total < MIN_CASES) return Status.SEM_DADOS
        val base = previous.filter { it.total >= MIN_CASES }.mapNotNull { it.rate }.takeIf { it.isNotEmpty() }?.average()
        return when {
            r < 0.50 -> Status.PROBLEMA
            base != null && base - r > 0.25 -> Status.PROBLEMA
            r < 0.75 -> Status.ATENCAO
            base != null && base - r > 0.12 -> Status.ATENCAO
            else -> Status.OK
        }
    }

    /** A mesma tela não lida aparece várias vezes por segundo: conta uma vez a cada 20 s (ignorando números). */
    class MissDedup(private val windowMs: Long = 20_000) {
        private val last = mutableMapOf<String, Long>()
        fun isNew(app: String, text: String, now: Long): Boolean {
            val key = app + "|" + text.replace(Regex("""\d"""), "").replace(Regex("""\s+"""), " ").take(300)
            val prev = last[key]
            last[key] = now
            last.entries.removeAll { now - it.value > windowMs * 3 }
            return prev == null || now - prev > windowMs
        }
    }
}
