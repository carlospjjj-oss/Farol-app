package com.farol.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.farol.app.core.Fmt
import com.farol.app.data.History
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Histórico de ofertas: todas as ofertas que o Farol leu, com filtros por período, app e cor,
 * resumo no topo e detalhes ao tocar.
 */
class HistoryActivity : Activity() {

    private lateinit var history: History
    private lateinit var k: Kit
    private lateinit var col: LinearLayout

    private var period = 0                 // 0 = hoje, 1 = 7 dias, 2 = 30 dias
    private var appFilter: String? = null  // null = todos
    private var colorFilter: String? = null // GREEN / YELLOW / RED / null

    private val ptBR = Locale("pt", "BR")
    private val hhmm = SimpleDateFormat("HH:mm", ptBR)
    private val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayTitle = SimpleDateFormat("EEEE, d 'de' MMMM", ptBR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = History(this)
        k = Kit(this)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG

        col = k.vbox().apply { setPadding(k.dp(18), k.dp(12), k.dp(18), k.dp(40)) }
        val root = k.vbox().apply {
            setBackgroundColor(Palette.BG)
            addView(header(), LinearLayout.LayoutParams(-1, -2))
            addView(ScrollView(this@HistoryActivity).apply {
                isVerticalScrollBarEnabled = false; clipToPadding = false; addView(col)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
        k.fitSystemBars(root)
        render()
    }

    private fun header() = k.vbox().apply {
        setPadding(k.dp(18), k.dp(12), k.dp(18), k.dp(6))
        val back = k.text("←", 20, Palette.TEXT, k.bold).apply {
            gravity = Gravity.CENTER
            background = k.ripple(k.rounded(Palette.SURFACE, 999, Palette.STROKE))
            isClickable = true; setOnClickListener { finish() }
        }
        addView(k.hbox().apply {
            addView(back, LinearLayout.LayoutParams(k.dp(44), k.dp(44)))
            addView(k.vbox().apply {
                addView(k.text("Histórico de ofertas", 22, Palette.TEXT, k.bold))
                addView(k.text("Tudo o que o Farol leu nos apps", 13, Palette.MUTED), k.lp(top = 2))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(14) })
        })
    }

    // ============================== dados ==============================

    private fun all(): List<JSONObject> = history.recent(5000)

    private fun inPeriod(o: JSONObject): Boolean {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (period == 1) c.add(Calendar.DAY_OF_YEAR, -6)
        if (period == 2) c.add(Calendar.DAY_OF_YEAR, -29)
        return o.optLong("t") >= c.timeInMillis
    }

    private fun colorOf(r: String) = when (r) { "GREEN" -> Palette.GREEN; "YELLOW" -> Palette.YELLOW; else -> Palette.RED }

    // ============================== render ==============================

    private fun render() {
        col.removeAllViews()
        val periodList = all().filter { inPeriod(it) }
        val list = periodList.filter { o ->
            (appFilter == null || o.optString("app") == appFilter) &&
                (colorFilter == null || o.optString("rating") == colorFilter)
        }

        // Período
        col.addView(segmented(listOf("Hoje", "7 dias", "30 dias"), period) { period = it; render() })

        // Filtro por app (só os apps que aparecem no período)
        val apps = periodList.map { it.optString("app") }.filter { it.isNotEmpty() }.distinct().sorted()
        col.addView(chipsRow(listOf<Pair<String, String?>>("Todos" to null) + apps.map { it to it }, appFilter) {
            appFilter = it; render()
        }, k.lp(top = 12))
        col.addView(chipsRow(listOf("Todas as cores" to null, "🟢 Verdes" to "GREEN", "🟡 Amarelas" to "YELLOW", "🔴 Vermelhas" to "RED"),
            colorFilter) { colorFilter = it; render() }, k.lp(top = 8))

        // Resumo
        col.addView(summary(list), k.lp(top = 14))

        if (list.isEmpty()) {
            col.addView(k.text("Nenhuma oferta com esses filtros.", 14, Palette.MUTED).apply {
                gravity = Gravity.CENTER; setPadding(0, k.dp(30), 0, 0)
            })
            return
        }

        // Lista agrupada por dia (mais recente primeiro)
        list.groupBy { dayKey.format(Date(it.optLong("t"))) }.toSortedMap(compareByDescending { it }).forEach { (_, offers) ->
            val d = Date(offers.first().optLong("t"))
            val avg = offers.map { it.optDouble("perKm") }.filter { !it.isNaN() }.average()
            col.addView(k.hbox().apply {
                val t = k.text(dayTitle.format(d).replaceFirstChar { it.uppercase() }, 14, Palette.TEXT, k.bold)
                addView(t, LinearLayout.LayoutParams(0, -2, 1f))
                addView(k.text("${offers.size} · ${Fmt.money(avg)}/km", 12, Palette.MUTED))
            }, k.lp(top = 20))
            offers.forEach { col.addView(row(it), k.lp(top = 8)) }
        }
    }

    private fun segmented(labels: List<String>, active: Int, onPick: (Int) -> Unit) = k.hbox().apply {
        background = k.rounded(Palette.SURFACE2, 12)
        setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4))
        labels.forEachIndexed { i, label ->
            val on = i == active
            addView(k.text(label, 13, if (on) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
                gravity = Gravity.CENTER
                setPadding(0, k.dp(9), 0, k.dp(9))
                if (on) background = k.rounded(Palette.ACCENT, 10)
                isClickable = true; setOnClickListener { onPick(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun chipsRow(items: List<Pair<String, String?>>, active: String?, onPick: (String?) -> Unit): View {
        val row = k.hbox()
        items.forEachIndexed { i, (label, value) ->
            val on = value == active
            val chip = k.text(label, 13, if (on) Palette.ON_ACCENT else Palette.TEXT, k.medium).apply {
                setPadding(k.dp(12), k.dp(7), k.dp(12), k.dp(7))
                background = if (on) k.rounded(Palette.ACCENT, 999) else k.rounded(Palette.SURFACE, 999, Palette.STROKE)
                isClickable = true; setOnClickListener { onPick(value) }
            }
            row.addView(chip, k.lp(w = -2).apply { if (i > 0) marginStart = k.dp(8) })
        }
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun summary(list: List<JSONObject>): View {
        val card = k.card()
        val g = list.count { it.optString("rating") == "GREEN" }
        val y = list.count { it.optString("rating") == "YELLOW" }
        val r = list.size - g - y
        val avgKm = list.map { it.optDouble("perKm") }.filter { !it.isNaN() }.average().takeIf { !it.isNaN() }
        val avgPrice = list.map { it.optDouble("price") }.filter { !it.isNaN() }.average().takeIf { !it.isNaN() }

        fun stat(label: String, value: String) = k.vbox().apply {
            addView(k.text(value, 20, Palette.TEXT, k.bold))
            addView(k.text(label, 12, Palette.MUTED), k.lp(top = 3))
        }
        val a = stat("ofertas", "${list.size}")
        val b = stat("R$/km médio", avgKm?.let { Fmt.money(it) } ?: "—")
        val c = stat("preço médio", avgPrice?.let { Fmt.money(it) } ?: "—")
        card.addView(k.hbox().apply {
            addView(a, LinearLayout.LayoutParams(0, -2, 1f))
            addView(b, LinearLayout.LayoutParams(0, -2, 1f))
            addView(c, LinearLayout.LayoutParams(0, -2, 1f))
        })
        if (list.isNotEmpty()) {
            card.addView(k.hbox().apply {
                addView(View(this@HistoryActivity).apply { background = k.rounded(Palette.GREEN, 999) },
                    LinearLayout.LayoutParams(0, k.dp(8), g.toFloat().coerceAtLeast(0.001f)))
                addView(View(this@HistoryActivity).apply { background = k.rounded(Palette.YELLOW, 999) },
                    LinearLayout.LayoutParams(0, k.dp(8), y.toFloat().coerceAtLeast(0.001f)).apply { marginStart = k.dp(3) })
                addView(View(this@HistoryActivity).apply { background = k.rounded(Palette.RED, 999) },
                    LinearLayout.LayoutParams(0, k.dp(8), r.toFloat().coerceAtLeast(0.001f)).apply { marginStart = k.dp(3) })
            }, k.lp(top = 14))
            card.addView(k.text("🟢 $g   🟡 $y   🔴 $r", 13, Palette.MUTED), k.lp(top = 8))

            // Por app
            val byApp = list.groupBy { it.optString("app") }.entries.sortedByDescending { it.value.size }
            if (byApp.size > 1 || appFilter == null) {
                card.addView(k.overline("Por app"), k.lp(top = 14))
                byApp.forEach { (app, l) ->
                    val avg = l.map { it.optDouble("perKm") }.filter { !it.isNaN() }.average()
                    card.addView(k.hbox().apply {
                        addView(k.dot(Palette.app(app), 10))
                        val n = k.text(app, 14, Palette.TEXT, k.medium)
                        addView(n, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
                        addView(k.text("${l.size} ofertas · ${Fmt.money(avg)}/km", 12, Palette.MUTED))
                    }, k.lp(top = 8))
                }
            }
        }
        return card
    }

    private fun row(o: JSONObject): View {
        val rating = o.optString("rating")
        val c = colorOf(rating)
        val card = k.hbox().apply {
            gravity = Gravity.FILL_VERTICAL
            background = k.ripple(k.rounded(Palette.SURFACE, 16, Palette.STROKE))
            isClickable = true
            setOnClickListener { details(o) }
        }
        // faixa da cor
        card.addView(View(this).apply {
            background = k.rounded(c, 999)
        }, LinearLayout.LayoutParams(k.dp(4), -1).apply { setMargins(k.dp(8), k.dp(10), 0, k.dp(10)) })

        val body = k.vbox(12, 10)
        val app = o.optString("app")
        body.addView(k.hbox().apply {
            addView(k.text(hhmm.format(Date(o.optLong("t"))), 12, Palette.MUTED, k.medium))
            addView(k.chip(app, Palette.app(app)).apply { setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f) },
                k.lp(w = -2).apply { marginStart = k.dp(8) })
            addView(View(this@HistoryActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(k.text(Fmt.money(o.optDouble("price")), 17, Palette.TEXT, k.bold))
        })
        val perKmColor = colorOf(o.optString("ratingKm", rating))
        val perHColor = colorOf(o.optString("ratingHour", rating))
        val est = if (o.optBoolean("estimated")) "~" else ""
        body.addView(k.hbox().apply {
            addView(k.text("${Fmt.km(o.optDouble("km"))} · $est${Fmt.min(o.optDouble("min"))}", 13, Palette.MUTED))
            addView(View(this@HistoryActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(k.text("${Fmt.dec(o.optDouble("perKm"), 2)}/km", 13, perKmColor, k.bold))
            addView(k.text("${Fmt.dec(o.optDouble("perHour"), 0)}/h", 13, perHColor, k.bold), k.lp(w = -2).apply { marginStart = k.dp(10) })
        }, k.lp(top = 6))
        o.optJSONArray("warnings")?.let { w ->
            if (w.length() > 0) body.addView(k.text("⚠ " + (0 until w.length()).joinToString(" • ") { w.getString(it) },
                12, Palette.ACCENT, k.medium), k.lp(top = 6))
        }
        card.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
        return card
    }

    private fun details(o: JSONObject) {
        val lines = buildList {
            add("App: ${o.optString("app")}")
            add("Horário: ${SimpleDateFormat("dd/MM HH:mm", ptBR).format(Date(o.optLong("t")))}")
            add("Preço: ${Fmt.money(o.optDouble("price"))}")
            add("Distância: ${Fmt.km(o.optDouble("km"))}" + (if (o.has("pickup")) "  (busca ${Fmt.km(o.optDouble("pickup"))})" else ""))
            add("Tempo: ${if (o.optBoolean("estimated")) "~" else ""}${Fmt.min(o.optDouble("min"))}" +
                if (o.optBoolean("estimated")) "  (estimado pela velocidade média)" else "")
            if (o.has("stars")) add("Nota do passageiro: ${Fmt.dec(o.optDouble("stars"), 2)} ★")
            add("")
            add("R$/km: ${Fmt.money(o.optDouble("perKm"))}")
            add("R$/h: ${Fmt.money(o.optDouble("perHour"))}")
            if (o.has("cost")) add("Custo estimado: ${Fmt.money(o.optDouble("cost"))}")
            add("Lucro líquido: ${Fmt.money(o.optDouble("net"))}")
            o.optJSONArray("warnings")?.let { w -> if (w.length() > 0) add("Avisos: " + (0 until w.length()).joinToString(", ") { w.getString(it) }) }
            o.optString("source").takeIf { it.isNotEmpty() }?.let { add("Leitura: $it") }
        }
        val verdict = when (o.optString("rating")) { "GREEN" -> "🟢 Verde"; "YELLOW" -> "🟡 Amarela"; else -> "🔴 Vermelha" }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("$verdict · ${Fmt.money(o.optDouble("price"))}")
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton("Fechar", null)
            .show()
    }
}
