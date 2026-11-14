package com.farol.app.ui

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.farol.app.core.DayRecord
import com.farol.app.core.Expense
import com.farol.app.core.Fmt
import com.farol.app.core.Indicators
import com.farol.app.core.MaintItem
import com.farol.app.core.Maintenance
import com.farol.app.data.MaintenanceStore
import java.util.Calendar

/* Manutenção por km + caixinha da reserva, e indicadores (manual de gestão, módulos 9 a 11). */

internal fun DashboardActivity.currentKm(): Double? = Maintenance.currentKm(store.days(), store.fuel())

private fun levelColor(l: Maintenance.Level) = when (l) {
    Maintenance.Level.DUE -> Palette.RED
    Maintenance.Level.SOON -> Palette.YELLOW
    Maintenance.Level.OK -> Palette.GREEN
    Maintenance.Level.UNKNOWN -> Palette.MUTED
}

// manutenção
internal fun DashboardActivity.maintenanceCard() {
    val items = MaintenanceStore.load(settings)
    val km = currentKm()
    val list = Maintenance.sorted(items, km)
    val card = k.card()
    card.addView(k.hbox().apply {
        val t = k.overline("Manutenção")
        addView(t, weighted(t))
        km?.let { addView(k.text("painel: ${Fmt.dec(it, 0)} km", 12, Palette.MUTED)) }
    })

    // Caixinha: "outros custos" por km já saem do líquido; o gasto de manutenção sai daqui
    val rate = settings.profile.otherCostPerKm
    val r = Maintenance.reserve(store.days(), rate)
    if (rate > 0 && (r.km > 0 || r.spent > 0)) {
        val bal = r.balance
        card.addView(k.vbox().apply {
            setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12))
            background = k.rounded(Palette.SURFACE2, 14)
            addView(k.text("🐷 Caixinha: ${Fmt.money(bal)}", 18, if (bal >= 0) Palette.TEXT else Palette.RED, k.bold))
            addView(k.text("${Fmt.money(rate)}/km × ${Fmt.dec(r.km, 0)} km = ${Fmt.money(r.saved)} juntados − ${Fmt.money(r.spent)} gastos em manutenção." +
                if (bal < 0) " Faltou: talvez os \"outros custos\" por km estejam baixos." else "", 12, Palette.MUTED), k.lp(top = 4))
        }, k.lp(top = 12))
    }

    val shown = list.filter { it.level != Maintenance.Level.UNKNOWN || it.item.intervalKm != null }
    if (shown.all { it.level == Maintenance.Level.UNKNOWN }) {
        card.addView(k.text("Registre a última troca de cada item e o Farol avisa quando estiver perto, pelo km dos seus turnos. Os intervalos são sugestões: confira no manual do veículo.",
            13, Palette.MUTED), k.lp(top = 10))
    }
    shown.take(4).forEach { st ->
        card.addView(k.hbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(k.dot(levelColor(st.level), 10))
            val col = k.vbox().apply {
                addView(k.text(st.item.name, 14, Palette.TEXT, k.medium))
                addView(k.text(st.text, 12, if (st.level == Maintenance.Level.DUE) Palette.RED else Palette.MUTED), k.lp(top = 2))
            }
            addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
            isClickable = true
            setOnClickListener { askService(st.item.id) }
        }, k.lp(top = 12))
    }
    if (shown.size > 4) card.addView(k.text("+ ${shown.size - 4} itens em Editar", 12, Palette.MUTED), k.lp(top = 8))
    val row = k.hbox()
    val reg = k.pill("🔧  Registrar troca") { askService(null) }
    row.addView(reg, weighted(reg))
    val edit = k.ghost("Editar itens") { editMaintenance() }
    row.addView(edit, weighted(edit, 10))
    card.addView(row, k.lp(top = 14))
    add(card)
}

/** Registrar uma troca/serviço: atualiza o item e lança o gasto como manutenção (sai da caixinha). */
internal fun DashboardActivity.askService(preselect: String?) {
    val items = MaintenanceStore.load(settings)
    if (items.isEmpty()) { editMaintenance(); return }
    var sel = preselect ?: items.first().id
    val chips = k.vbox()
    fun renderChips() {
        chips.removeAllViews()
        items.chunked(2).forEach { pair ->
            chips.addView(k.hbox().apply {
                pair.forEachIndexed { i, m ->
                    val on = m.id == sel
                    addView(k.text(m.name, 13, Palette.TEXT, k.bold).apply {
                        gravity = Gravity.CENTER; setPadding(k.dp(8), k.dp(10), k.dp(8), k.dp(10))
                        background = k.rounded(if (on) Palette.alpha(Palette.ACCENT, 0x30) else Palette.SURFACE2, 12, if (on) Palette.ACCENT else Palette.STROKE)
                        isClickable = true; setOnClickListener { sel = m.id; renderChips() }
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = k.dp(8) })
                }
                if (pair.size == 1) addView(android.view.View(self), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = k.dp(8) })
            }, k.lp(top = 8))
        }
    }
    renderChips()
    val odo = k.field("Km do painel", currentKm()?.let { fmtKm(it) } ?: "")
    val value = k.field("R$ 0,00")
    val note = k.field("Ex.: óleo 10W30, oficina do bairro")
    note.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    val box = k.vbox().apply {
        addView(k.text("O que foi trocado", 12, Palette.MUTED, k.medium))
        addView(chips)
        addView(labeled("Km do painel na troca", odo), k.lp(top = 14))
        addView(labeled("Valor pago (0 se ainda não sabe)", value), k.lp(top = 12))
        addView(labeled("Observação (opcional)", note), k.lp(top = 12))
        addView(k.text("O valor sai da caixinha de manutenção, não do líquido do dia (ele já desconta o custo por km).", 12, Palette.MUTED), k.lp(top = 10))
    }
    dialog("Registrar troca", ScrollView(this).apply { addView(box) }, "Registrar") {
        val km = odo.num() ?: return@dialog fail("Digite o km do painel")
        val v = value.num() ?: 0.0
        if (v < 0 || v > 50_000) return@dialog fail("Valor inválido")
        val now = System.currentTimeMillis()
        MaintenanceStore.save(settings, Maintenance.service(items, sel, km, now))
        val item = items.first { it.id == sel }
        if (v > 0) {
            val d = store.day()
            store.putDay(d.copy(expenses = d.expenses + Expense(now, "manutencao", v,
                (item.name + note.text.toString().trim().let { if (it.isNotEmpty()) " · $it" else "" }).take(60), item = sel, km = km)))
            runCatching { com.farol.app.service.FarolJobs.syncSoon(this) }
        }
        toast("${item.name}: troca registrada com ${Fmt.dec(km, 0)} km"); render(); true
    }
}

/** Editar intervalos (e o km da última troca) de cada item; dá para voltar às sugestões. */
internal fun DashboardActivity.editMaintenance() {
    val items = MaintenanceStore.load(settings)
    val box = k.vbox()
    box.addView(k.text("Intervalo em km de cada item (confira no manual do veículo) e o km da última troca, se souber. Deixe o intervalo vazio para não avisar.",
        13, Palette.MUTED))
    val fields = items.map { it ->
        val iv = k.field("intervalo", it.intervalKm?.let { v -> Fmt.dec(v, 0).replace(".", "") } ?: "")
        val last = k.field("última troca", it.lastKm?.let { v -> fmtKm(v) } ?: "")
        box.addView(k.text(it.name, 14, Palette.TEXT, k.bold), k.lp(top = 14))
        box.addView(k.hbox().apply {
            addView(labeled("A cada (km)", iv), LinearLayout.LayoutParams(0, -2, 1f))
            addView(labeled("Última troca (km)", last), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
        }, k.lp(top = 6))
        Triple(it, iv, last)
    }
    box.addView(k.text("Voltar às sugestões", 13, Palette.MUTED).apply {
        gravity = Gravity.CENTER; setPadding(0, k.dp(16), 0, 0)
        isClickable = true
        setOnClickListener { settings.maintenanceJson = ""; toast("Itens voltaram às sugestões"); render() }
    })
    dialog("Itens de manutenção", ScrollView(this).apply { addView(box) }, "Salvar") {
        val up = fields.map { (it, iv, last) ->
            val i = iv.num()?.takeIf { v -> v > 0 }
            val l = last.num()?.takeIf { v -> v > 0 }
            it.copy(intervalKm = i, lastKm = l ?: it.lastKm.takeIf { last.text.isNotBlank() }, lastAt = if (l != null && l != it.lastKm) System.currentTimeMillis() else it.lastAt)
        }
        MaintenanceStore.save(settings, up)
        toast("Manutenção salva"); render(); true
    }
}

/** Linha curta para o fim do turno: o item mais urgente, se estiver perto ou vencido. */
internal fun DashboardActivity.maintenanceHint(): String? {
    val top = Maintenance.sorted(MaintenanceStore.load(settings), currentKm()).firstOrNull() ?: return null
    return if (top.level == Maintenance.Level.DUE || top.level == Maintenance.Level.SOON) "🔧 ${top.item.name}: ${top.text}" else null
}

// indicadores
internal fun DashboardActivity.indicatorsCard() {
    val cut = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(System.currentTimeMillis() - 30L * 86_400_000))
    val days = store.days().filter { it.date >= cut }
    val cal = Calendar.getInstance()
    val r = Indicators.compute(days) { t -> cal.timeInMillis = t; cal.get(Calendar.HOUR_OF_DAY) }
    if (r.days == 0) return
    val card = k.card()
    card.addView(k.hbox().apply {
        val t = k.overline("Indicadores")
        addView(t, weighted(t))
        addView(k.text("30 dias · ${r.days} ${if (r.days == 1) "dia" else "dias"}", 12, Palette.MUTED))
    })
    fun tile(label: String, value: String) = k.vbox().apply {
        setPadding(k.dp(12), k.dp(10), k.dp(12), k.dp(10))
        background = k.rounded(Palette.SURFACE2, 12)
        addView(k.text(value, 17, Palette.TEXT, k.bold))
        addView(k.text(label, 11, Palette.MUTED), k.lp(top = 2))
    }
    card.addView(k.hbox().apply {
        val a = tile("ticket médio", r.ticket?.let { Fmt.money(it) } ?: "—")
        val b = tile("entregas/hora", r.deliveriesPerHour?.let { Fmt.dec(it, 1) } ?: "—")
        val c = tile("R$/hora", r.perHour?.let { Fmt.money(it) } ?: "—")
        addView(a, LinearLayout.LayoutParams(0, -2, 1f)); addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(8) })
        addView(c, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(8) })
    }, k.lp(top = 12))

    if (r.apps.isNotEmpty()) {
        card.addView(k.text("Por app", 13, Palette.MUTED, k.medium), k.lp(top = 14))
        r.apps.forEach { a ->
            card.addView(k.hbox().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(k.dot(Palette.app(a.app), 10))
                val n = k.text(a.app, 14, Palette.TEXT, k.medium)
                addView(n, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
                addView(k.text(Fmt.money(a.earnings) + (if (a.deliveries > 0) " · ${a.deliveries} entregas · ${Fmt.money(a.ticket!!)}/entrega" else ""), 12, Palette.MUTED))
            }, k.lp(top = 8))
        }
    }
    val slots = r.slots.filter { it.hours >= 0.5 || it.deliveries > 0 }
    if (slots.isNotEmpty()) {
        card.addView(k.text("Por horário (pelas entregas que o Farol leu)", 13, Palette.MUTED, k.medium), k.lp(top = 14))
        val best = slots.filter { it.enough && it.perHour != null }.maxByOrNull { it.perHour!! }
        slots.forEach { s ->
            val txt = if (!s.enough) "${s.deliveries} entregas · juntando dados (mín. ${Indicators.MIN_SAMPLE})"
                else "${s.perHour?.let { Fmt.money(it) + "/h" } ?: "—"} · ${s.deliveries} entregas em ${Fmt.dec(s.hours, 1)} h"
            card.addView(k.hbox().apply {
                val n = k.text(s.slot.label + if (s == best) "  ⭐" else "", 13, Palette.TEXT, k.medium)
                addView(n, LinearLayout.LayoutParams(0, -2, 1f))
                addView(k.text(txt, 12, Palette.MUTED))
            }, k.lp(top = 6))
        }
    }
    add(card)
}

@Suppress("unused") private val keepDay: Class<DayRecord> = DayRecord::class.java
@Suppress("unused") private val keepItem: Class<MaintItem> = MaintItem::class.java
