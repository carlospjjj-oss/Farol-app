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
import java.util.Date

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
                val col = k.vbox().apply {
                    addView(k.hbox().apply {
                        val n = k.text(a.app, 14, Palette.TEXT, k.medium)
                        addView(n, LinearLayout.LayoutParams(0, -2, 1f))
                        addView(k.text(Fmt.money(a.earnings), 14, Palette.TEXT, k.bold))
                    })
                    if (a.deliveries > 0) addView(k.text("${a.deliveries} ${if (a.deliveries == 1) "entrega lida" else "entregas lidas"} · ${Fmt.money(a.ticket!!)} por entrega", 12, Palette.MUTED), k.lp(top = 2))
                }
                addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
            }, k.lp(top = 10))
        }
    }
    val slots = r.slots.filter { it.hours >= 0.5 || it.deliveries > 0 }
    if (slots.isNotEmpty()) {
        card.addView(k.text("Por horário (pelas entregas que o Farol leu)", 13, Palette.MUTED, k.medium), k.lp(top = 14))
        val best = slots.filter { it.enough && it.perHour != null }.maxByOrNull { it.perHour!! }
        slots.forEach { s ->
            val txt = if (!s.enough) "${s.deliveries} entregas · juntando dados (mín. ${Indicators.MIN_SAMPLE})"
                else "${s.perHour?.let { Fmt.money(it) + "/h" } ?: "—"} · ${s.deliveries} entregas em ${Fmt.dec(s.hours, 1)} h"
            card.addView(k.vbox().apply {
                addView(k.text(s.slot.label + if (s == best) "  ⭐ melhor" else "", 13, Palette.TEXT, k.medium))
                addView(k.text(txt, 12, Palette.MUTED), k.lp(top = 2))
            }, k.lp(top = 8))
        }
    }
    add(card)
}

@Suppress("unused") private val keepDay: Class<DayRecord> = DayRecord::class.java
@Suppress("unused") private val keepItem: Class<MaintItem> = MaintItem::class.java

// caixinhas
internal fun DashboardActivity.boxesCard() {
    val p = settings.profile
    val boxes = com.farol.app.core.Boxes.build(store.days(), store.fuel(), p.otherCostPerKm, p.fuelCostPerKm,
        com.farol.app.data.BoxesStore.moves(settings), com.farol.app.data.BoxesStore.goals(settings), store::dateOf)
    val card = k.card()
    card.addView(k.hbox().apply {
        val t = k.overline("Caixinhas")
        addView(t, weighted(t))
        addView(k.text(Fmt.money(boxes.sumOf { it.balance }), 15, Palette.TEXT, k.bold))
    })
    card.addView(k.text("Dinheiro que o líquido já separou para não ser pego de surpresa. Toque numa caixinha para pôr uma meta.", 12, Palette.MUTED), k.lp(top = 6))
    boxes.forEach { b ->
        card.addView(k.vbox().apply {
            setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12))
            background = k.rounded(Palette.SURFACE2, 14)
            isClickable = true
            setOnClickListener { askBox(b) }
            addView(k.hbox().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(k.text(b.emoji, 18), k.lp(w = -2))
                addView(k.text(b.name, 15, Palette.TEXT, k.bold).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
                addView(k.text("›", 20, Palette.MUTED))
            })
            addView(k.hbox().apply {
                gravity = Gravity.BOTTOM
                addView(k.text(Fmt.money(b.balance), 20, if (b.balance < 0) Palette.RED else Palette.TEXT, k.bold))
                b.goal?.let { addView(k.text("  de ${Fmt.money(it)}", 13, Palette.MUTED)) }
            }, k.lp(top = 6))
            b.progress?.let { pr ->
                addView(k.bar(pr, Palette.ACCENT), k.lp(h = k.dp(6), top = 10))
                addView(k.text("${(pr * 100).toInt()}% da meta", 11, Palette.MUTED), k.lp(top = 4))
            }
            addView(k.text(b.detail, 12, Palette.MUTED), k.lp(top = 6))
        }, k.lp(top = 10))
    }
    add(card)
}

/** Meta da caixinha; na de emergência, também guardar/tirar dinheiro. */
internal fun DashboardActivity.askBox(b: com.farol.app.core.Boxes.Box) {
    val goal = k.field("0 = sem meta", b.goal?.let { fmtMoney(it) } ?: "")
    val value = k.field("R$ 0,00")
    val box = k.vbox().apply {
        addView(k.text("Saldo: ${Fmt.money(b.balance)}\n${b.detail}", 13, Palette.MUTED))
        addView(labeled("Meta da caixinha (R$)", goal), k.lp(top = 14))
        if (b.key == "emergencia") addView(labeled("Guardar agora (use − para tirar, ex.: -50)", value.apply {
            keyListener = android.text.method.DigitsKeyListener.getInstance("-0123456789.,")
        }), k.lp(top = 12))
        else addView(k.text(if (b.key == "manutencao") "Ela enche sozinha com os \"outros custos\" por km e esvazia com as trocas registradas na Manutenção."
            else "Ela enche sozinha com o combustível por km e esvazia com os abastecimentos.", 12, Palette.MUTED), k.lp(top = 10))
    }
    dialog(b.emoji + " " + b.name, box, "Salvar") {
        com.farol.app.data.BoxesStore.setGoal(settings, b.key, goal.num())
        if (b.key == "emergencia") value.text.toString().trim().replace(',', '.').toDoubleOrNull()?.takeIf { it != 0.0 && Math.abs(it) < 100_000 }?.let {
            com.farol.app.data.BoxesStore.addMove(settings, com.farol.app.core.Boxes.Move(System.currentTimeMillis(), it))
            toast(if (it > 0) "Guardou ${Fmt.money(it)}" else "Tirou ${Fmt.money(-it)}")
        }
        render(); true
    }
}

// análises: gráficos
internal var DashboardActivity.chartDays: Int
    get() = settings.prefs.getInt("chartDays", 7)
    set(v) = settings.prefs.edit().putInt("chartDays", v).apply()

internal fun DashboardActivity.chartsCard() {
    val n = chartDays
    val cal = Calendar.getInstance()
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val keys = (n - 1 downTo 0).map { i -> (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) } }
    val byDate = store.days().associateBy { it.date }
    val sel = keys.mapNotNull { byDate[fmt.format(it.time)] }
    val total = sel.sumOf { it.totalEarnings }
    // mesmo período anterior, para a seta de tendência
    val prevKeys = (2 * n - 1 downTo n).map { i -> fmt.format((cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }.time) }
    val prev = prevKeys.sumOf { byDate[it]?.totalEarnings ?: 0.0 }

    val card = k.card()
    card.addView(k.hbox().apply {
        background = k.rounded(Palette.SURFACE2, 12)
        setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4))
        listOf(7 to "7 dias", 30 to "30 dias").forEach { (d, label) ->
            val on = d == n
            val t = k.text(label, 13, if (on) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
                gravity = Gravity.CENTER; setPadding(0, k.dp(8), 0, k.dp(8))
                if (on) background = k.rounded(Palette.ACCENT, 10)
                isClickable = true; setOnClickListener { chartDays = d; render() }
            }
            addView(t, weighted(t))
        }
    })
    card.addView(k.text("Faturamento", 13, Palette.MUTED, k.medium), k.lp(top = 14))
    card.addView(k.hbox().apply {
        gravity = Gravity.BOTTOM
        addView(k.text(Fmt.money(total), 26, Palette.TEXT, k.bold))
        if (prev >= 100 && total > 0) {   // com período anterior quase vazio a porcentagem não diz nada
            val pct = Math.round((total - prev) / prev * 100)
            addView(k.text((if (pct >= 0) "  ▲ " else "  ▼ ") + "${Math.abs(pct)}%", 14, if (pct >= 0) Palette.GREEN else Palette.RED, k.bold))
        }
    })
    if (prev > 0) card.addView(k.text("${Fmt.money(prev)} nos $n dias anteriores", 12, Palette.MUTED), k.lp(top = 2))
    val wd = java.text.SimpleDateFormat("EEE", ptBR)
    card.addView(BarChartView(this).apply {
        values = keys.map { (byDate[fmt.format(it.time)]?.totalEarnings ?: 0.0).toFloat() }
        labels = keys.mapIndexed { i, c -> if (n <= 7) wd.format(c.time).take(3).replace(".", "") else if (i % 5 == 4 || i == keys.size - 1) c.get(Calendar.DAY_OF_MONTH).toString() else "" }
        valueFormatter = { "R$ ${it.toInt()}" }
    }, k.lp(top = 10))

    // fatia de cada app
    val byApp = sel.flatMap { it.appEarnings.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        .filterValues { it > 0 }.entries.sortedByDescending { it.value }
    if (byApp.isNotEmpty()) {
        card.addView(k.text("Por aplicativo", 13, Palette.MUTED, k.medium), k.lp(top = 16))
        val sum = byApp.sumOf { it.value }
        card.addView(k.hbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(DonutView(self).apply {
                slices = byApp.map { Palette.app(it.key) to it.value.toFloat() }
                center = "${byApp.size} ${if (byApp.size == 1) "app" else "apps"}"
            })
            val legend = k.vbox()
            byApp.forEach { (app, v) ->
                legend.addView(k.hbox().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(k.dot(Palette.app(app), 10))
                    val name = k.text(app, 14, Palette.TEXT, k.medium)
                    addView(name, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(8) })
                    addView(k.text("${Math.round(v / sum * 100)}%", 14, Palette.TEXT, k.bold))
                }, k.lp(top = 6))
            }
            addView(legend, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(16) })
        }, k.lp(top = 8))
    }
    add(card)
}

// análises: regiões (lista, pelo histórico de ofertas do celular)
internal fun DashboardActivity.regionsCard() {
    val since = System.currentTimeMillis() - 30L * 86_400_000
    val seen = history.since(since).mapNotNull { o ->
        val key = o.optString("origem", "").ifEmpty { return@mapNotNull null }
        val pk = o.optDouble("perKm").takeIf { !it.isNaN() } ?: return@mapNotNull null
        com.farol.app.core.RegionStats.Seen(o.optLong("t"), key, o.optString("origemNome", "").ifEmpty { com.farol.app.core.RegionStats.pretty(key) },
            pk, o.optString("rating") == "GREEN")
    }
    val cal = Calendar.getInstance()
    val lines = com.farol.app.core.RegionStats.compute(seen) { t -> cal.timeInMillis = t; cal.get(Calendar.HOUR_OF_DAY) }
    val card = k.card()
    card.addView(k.hbox().apply {
        val t = k.overline("Regiões de coleta")
        addView(t, weighted(t))
        addView(k.text("30 dias", 12, Palette.MUTED))
    })
    if (lines.isEmpty()) {
        card.addView(k.text("Ainda poucas ofertas com região (${seen.size}). Cada região aparece com ${com.farol.app.core.RegionStats.MIN_OFFERS} ofertas ou mais. Fica tudo só no celular.",
            13, Palette.MUTED), k.lp(top = 10))
        add(card); return
    }
    card.addView(k.text("Quanto pagam as ofertas que saem de cada lugar (melhores primeiro). Só no celular.", 12, Palette.MUTED), k.lp(top = 6))
    lines.take(8).forEach { l ->
        val c = when { l.greenPct >= 50 -> Palette.GREEN; l.greenPct >= 25 -> Palette.YELLOW; else -> Palette.RED }
        card.addView(k.hbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(k.dp(12), k.dp(10), k.dp(12), k.dp(10))
            background = k.rounded(Palette.SURFACE2, 14)
            addView(k.dot(c, 10))
            val col = k.vbox().apply {
                addView(k.text(l.label, 14, Palette.TEXT, k.medium))
                addView(k.text("${l.offers} ofertas · ${l.greenPct}% verdes" + (l.bestHours?.let { " · melhor ${it.first}h às ${it.last}h" } ?: ""), 12, Palette.MUTED), k.lp(top = 2))
            }
            addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
            addView(k.text("${Fmt.money(l.avgPerKm)}/km", 14, Palette.TEXT, k.bold))
        }, k.lp(top = 8))
    }
    add(card)
}

// corridas: lançar à mão
internal fun DashboardActivity.askManualRide() {
    var app = apps.first()
    val chips = k.hbox()
    val wrap = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
    fun renderChips() {
        chips.removeAllViews()
        apps.filter { it != "Outros" }.forEachIndexed { i, a ->
            val on = a == app
            chips.addView(k.text(a, 13, if (on) Palette.ON_ACCENT else Palette.TEXT, k.bold).apply {
                setPadding(k.dp(14), k.dp(9), k.dp(14), k.dp(9))
                background = k.rounded(if (on) Palette.app(a) else Palette.SURFACE2, 999)
                isClickable = true; setOnClickListener { app = a; renderChips() }
            }, k.lp(w = -2).apply { if (i > 0) marginStart = k.dp(8) })
        }
    }
    renderChips()
    val value = k.field("R$ 0,00")
    val time = k.field("hh:mm", hhmm.format(Date()))
    time.inputType = android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME
    val box = k.vbox().apply {
        addView(k.text("Para quando o Farol não leu a entrega. Ela soma no app escolhido (sem contar duas vezes com o painel dele).", 12, Palette.MUTED))
        addView(wrap, k.lp(top = 12))
        addView(k.hbox().apply {
            val a = labeled("Valor", value); val b = labeled("Hora", time)
            addView(a, LinearLayout.LayoutParams(0, -2, 1f)); addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
        }, k.lp(top = 14))
    }
    dialog("Lançar corrida", box, "Lançar") {
        val v = value.num()?.takeIf { it > 0 && it < 2000 } ?: return@dialog fail("Digite o valor")
        val at = runCatching {
            val (hh, mm) = time.text.toString().trim().split(":").map { it.toInt() }
            Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, mm); set(Calendar.SECOND, 0) }.timeInMillis
        }.getOrElse { System.currentTimeMillis() }
        val d = store.day()
        val key = "$app#m${at / 1000 % 100000}"
        store.putDay(d.copy(orders = d.orders + (key to v), orderAt = d.orderAt + (key to at)))
        runCatching { com.farol.app.service.FarolJobs.syncSoon(this) }
        toast("Corrida de ${Fmt.money(v)} lançada na $app"); render(); true
    }
}

/** Corrida lançada à mão: dá para apagar. */
internal fun DashboardActivity.askDeleteRide(date: String, key: String, v: Double) {
    android.app.AlertDialog.Builder(this, Palette.dialogTheme)
        .setMessage("Apagar a corrida de ${Fmt.money(v)} (${DayRecord.appOf(key)}) lançada à mão?")
        .setPositiveButton("Apagar") { _, _ ->
            val d = store.day(date)
            store.putDay(d.copy(orders = d.orders - key, orderAt = d.orderAt - key))
            runCatching { com.farol.app.service.FarolJobs.syncSoon(this) }; render()
        }
        .setNegativeButton("Cancelar", null).show()
}
