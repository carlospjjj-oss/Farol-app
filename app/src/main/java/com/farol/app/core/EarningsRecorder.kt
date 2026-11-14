package com.farol.app.core

/**
 * Tudo o que transforma uma TELA em ganho no diário, num lugar só e testável sem Android:
 *  - total "de hoje" na tela inicial do app (só aumenta);
 *  - entrega concluída (Keeta "Pedido concluído", 99 "Concluir" com a oferta aceita);
 *  - telas de histórico (Keeta Estatísticas, 99 Saldo, iFood Lançamentos).
 * Regras do dia: ver DayRecord.appEarnings (maior fonte, nunca soma) e JournalStore.workDate (turno da meia-noite).
 */
class EarningsRecorder(private val store: DayStore, private val clock: () -> Long = System::currentTimeMillis) {

    /** O que o diário precisa: o Android implementa com o SQLite (JournalStore); os testes, em memória. */
    interface DayStore {
        fun today(): String
        /** Dia do turno em andamento (turno que passou da meia-noite continua no dia em que começou). */
        fun currentDay(): DayRecord
        fun dayAt(date: String): DayRecord
        fun putDay(r: DayRecord)
        fun lastDays(n: Int): Set<String>
    }

    /** Avisado a cada ganho gravado (marca "lido automático" e atualiza a notificação do turno). */
    var onRecorded: (date: String, app: String) -> Unit = { _, _ -> }

    private val orders = OrderEarnings()

    fun start() { runCatching { orders.markUsed(store.currentDay().orders.keys) } }

    fun onOffer(app: String, price: Double) = orders.onOffer(app, price, clock())

    /** Uma tela do app na frente (texto da acessibilidade ou da imagem). Retorna true se gravou algo. */
    fun onScreen(app: String, text: String): Boolean {
        var any = false
        if (homeTotal(app, text)) any = true
        if (completedOrder(app, text)) any = true
        if (history(app, text)) any = true
        return any
    }

    private fun homeTotal(app: String, text: String): Boolean {
        if (HistoryReader.isHistory(app, text)) return false   // histórico não é o "hoje"
        val v = EarningsReader.read(app, text) ?: return false
        val day = store.currentDay()
        val cur = day.earnings[app]
        if (cur != null && v <= cur) return false
        if (v <= 0.0 && cur == null) return false
        store.putDay(day.copy(earnings = day.earnings + (app to v)))
        onRecorded(day.date, app)
        return true
    }

    private fun completedOrder(app: String, text: String): Boolean {
        val done = orders.onScreen(app, text, clock()) ?: return false
        val day = store.currentDay()
        if (done.key in day.orders) return false
        store.putDay(day.copy(orders = day.orders + (done.key to done.value)))
        onRecorded(day.date, app)
        return true
    }

    private fun history(app: String, text: String): Boolean {
        var any = false
        val now = clock()
        HistoryReader.keetaDay(text, store.today())?.let { t ->
            val d = store.dayAt(t.date)
            if (t.value > (d.earnings[t.app] ?: 0.0) + 0.004) {
                store.putDay(d.copy(earnings = d.earnings + (t.app to t.value)))
                onRecorded(t.date, t.app); any = true
            }
        }
        val entries = when (app) {
            "99" -> HistoryReader.ninetyNineSaldo(text)
            "iFood" -> HistoryReader.ifoodEntries(text, store.today())
            else -> emptyList()
        }
        if (entries.isEmpty()) return any
        val recent = store.lastDays(9).map { store.dayAt(it) }
        val add = mutableMapOf<String, MutableMap<String, Double>>()
        for (e in entries) {
            val dated = e.date
            val at = if (dated != null) HistoryReader.instantOf(dated, e.minuteOfDay) else HistoryReader.instantOf(e.minuteOfDay, now)
            // no turno que cobre a hora; com data na tela (iFood) e sem turno, vai para a data mostrada
            val day = HistoryReader.dayFor(at, recent, now) ?: dated?.let { store.dayAt(it) } ?: continue
            if (e.key in day.orders) continue
            add.getOrPut(day.date) { mutableMapOf() }[e.key] = e.value
        }
        add.forEach { (date, m) -> val d = store.dayAt(date); store.putDay(d.copy(orders = d.orders + m)); onRecorded(date, app) }
        return any || add.isNotEmpty()
    }
}
