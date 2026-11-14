package com.farol.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.farol.app.core.DayRecord
import com.farol.app.core.DaySummary
import com.farol.app.core.Fmt
import com.farol.app.core.FuelEntry
import com.farol.app.core.JournalMath
import com.farol.app.core.HourInsights
import com.farol.app.data.History
import com.farol.app.data.JournalStore
import com.farol.app.data.RemoteLog
import com.farol.app.data.Settings
import com.farol.app.service.OfferReaderService
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* Turno: cartão, GPS, iniciar/pausar/encerrar, editar e excluir. */

// shift
internal fun DashboardActivity.shiftCard(day: DayRecord, s: DaySummary) {
    val card = k.card()
    card.addView(k.overline("Turno"))
    when {
        day.kmStart == null -> {
            card.addView(k.text("Pronto para rodar?", 18, Palette.TEXT, k.bold), k.lp(top = 8))
            card.addView(k.text("Anote o km do painel para medir o dia.", 13, Palette.MUTED), k.lp(top = 4))
            card.addView(k.pill("▶  Iniciar turno") { askStart() }, k.lp(top = 14))
            previousDayRow(card)
        }
        day.kmEnd == null -> {
            val since = day.startAt?.let { hhmm.format(Date(it)) } ?: "—"
            val elapsed = day.workedMs()?.let { Fmt.min(it / 60_000.0) } ?: ""
            val paused = day.isPaused
            card.addView(k.hbox().apply {
                addView(k.dot(if (paused) Palette.YELLOW else Palette.GREEN, 10))
                val t = if (paused) "Pausado desde ${hhmm.format(Date(day.pauseAt!!))}" else "Em turno desde $since"
                // Turno que passou da meia-noite: segue contando para o dia em que começou.
                addView(k.text(t, 18, Palette.TEXT, k.bold), k.lp(w = -2).apply { marginStart = k.dp(10) })
            }, k.lp(top = 8))
            if (day.date != store.today()) {
                card.addView(k.text("Turno de ${day.date.substring(8)}/${day.date.substring(5, 7)}: passou da meia-noite e conta para esse dia. Encerre normalmente.",
                    12, Palette.YELLOW), k.lp(top = 6))
            }
            val pauseTxt = if (day.pausedMs > 0) "  •  pausas ${Fmt.min(day.pausedMs / 60_000.0)}" else ""
            card.addView(k.text("$elapsed trabalhados$pauseTxt  •  km inicial ${fmtKm(day.kmStart)}", 13, Palette.MUTED), k.lp(top = 4))
            card.addView(k.hbox().apply {
                val pb = if (paused) k.pill("▶  Retomar") { togglePause() } else k.ghost("⏸  Pausar") { togglePause() }
                val eb = k.pill("⏹  Encerrar", Palette.RED, 0xFFFFFFFF.toInt()) { askEnd() }
                addView(pb, weighted(pb)); addView(eb, weighted(eb, 10))
            }, k.lp(top = 14))
            card.addView(shiftTools(), k.lp(top = 10))
        }
        else -> {
            val a = day.startAt?.let { hhmm.format(Date(it)) } ?: "—"
            val b = day.endAt?.let { hhmm.format(Date(it)) } ?: "—"
            card.addView(k.text("Turno encerrado  ✓", 18, Palette.TEXT, k.bold), k.lp(top = 8))
            card.addView(k.text("$a – $b  •  ${s.km?.let { Fmt.km(it) } ?: ""}  •  ${fmtKm(day.kmStart)} → ${fmtKm(day.kmEnd)}",
                13, Palette.MUTED), k.lp(top = 4))
            if (day.pausedMs > 0) card.addView(k.text("Pausas: ${Fmt.min(day.pausedMs / 60_000.0)} (fora das horas)", 12, Palette.MUTED), k.lp(top = 2))
            card.addView(shiftTools(), k.lp(top = 14))
        }
    }
    add(card, 18)
}

// GPS: só mostra e sugere; nada muda sem tocar em "Usar"
internal fun DashboardActivity.gpsCard(day: DayRecord) {
    if (!settings.gpsEnabled) return
    val g = com.farol.app.data.GpsStore.today(this)
    val on = com.farol.app.service.GpsService.running
    val card = k.card()
    card.addView(k.hbox().apply {
        val t = k.overline("📍 GPS do turno")
        addView(t, weighted(t))
        addView(k.chip(if (on) "● medindo" else "parado", if (on) Palette.GREEN else Palette.MUTED))
    })
    if (!com.farol.app.service.GpsService.hasPermission(this)) {
        card.addView(k.text("Falta a permissão de localização.", 13, Palette.MUTED), k.lp(top = 10))
        card.addView(k.pill("Permitir localização") {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 11)
        }, k.lp(top = 12))
        add(card); return
    }
    fun stat(label: String, value: String) = k.vbox().apply {
        addView(k.text(value, 18, Palette.TEXT, k.bold))
        addView(k.text(label, 12, Palette.MUTED), k.lp(top = 3))
    }
    val a = stat("km pelo GPS hoje", Fmt.km(g.km))
    val b = stat("velocidade em movimento", g.movingKmh?.let { "${Fmt.dec(it, 1)} km/h" } ?: "—")
    card.addView(k.hbox().apply { addView(a, weighted(a)); addView(b, weighted(b)) }, k.lp(top = 12))
    if (!on && day.kmStart == null) card.addView(k.text("Começa a medir quando você inicia o turno.", 12, Palette.MUTED), k.lp(top = 8))

    // Velocidade de entrega por app, medida pelo GPS: sugestão com botão "Usar"
    listOf("Keeta", "99", "iFood").forEach { app ->
        val samples = settings.gpsSpeedSamples(app)
        val med = com.farol.app.core.SpeedLearner.learned(samples) ?: return@forEach
        card.addView(k.hbox().apply {
            addView(k.dot(Palette.app(app), 10))
            val t = k.text("$app: ${Fmt.dec(med, 1)} km/h por pedido (${samples.size})", 13, Palette.TEXT)
            addView(t, weighted(t, 10))
            addView(k.chip("Usar", Palette.ACCENT).apply {
                isClickable = true
                setOnClickListener {
                    settings.setSpeedSamples(app, samples)
                    settings.autoSpeed = true
                    toast("$app passa a usar ${Fmt.dec(med, 1)} km/h")
                    render()
                }
            })
        }, k.lp(top = 12))
    }
    if (listOf("Keeta", "99", "iFood").none { com.farol.app.core.SpeedLearner.learned(settings.gpsSpeedSamples(it)) != null })
        card.addView(k.text("A velocidade de entrega por app aparece depois de 5 pedidos medidos com o GPS.", 12, Palette.MUTED), k.lp(top = 10))
    add(card)
}

internal fun DashboardActivity.askStart() {
    val f = k.field("Km do painel", lastKnownKm()?.let { fmtKm(it) } ?: "")
    val startBox = k.vbox().apply {
        addView(labeled("Km do painel agora", f))
        addView(k.text("Se o último número do painel for décimo, use vírgula: 58081,7", 12, Palette.MUTED), k.lp(top = 8))
    }
    dialog("Iniciar turno", startBox, "Iniciar") {
        val km = f.num() ?: return@dialog fail("Digite o km do painel")
        val d = store.day()
        store.putDay(d.copy(kmStart = km, startAt = d.startAt ?: System.currentTimeMillis(), dayOff = false))   // trabalhou: não é folga
        // Começou a trabalhar: o Farol não pode continuar pausado sem você perceber.
        com.farol.app.service.PauseControl.set(this, paused = false, origem = "turno")
        if (settings.gpsEnabled) com.farol.app.service.GpsService.start(this)
        toast("Turno iniciado. Boas corridas!"); true
    }
}

/** A distância só faz sentido dividida por 10 (o último dígito do hodômetro é décimo de km). */
internal fun DashboardActivity.tenthsLikely(start: Double, end: Double): Boolean {
    val d = end - start
    return d > 350 && d / 10 in 1.0..350.0 && start == Math.floor(start) && end == Math.floor(end)
}

/** Distância suspeita: mais de 350 km no dia ou média acima de 60 km/h. */
internal fun DashboardActivity.suspicious(km: Double, startAt: Long?): String? {
    val h = startAt?.let { (System.currentTimeMillis() - it) / 3_600_000.0 }
    return when {
        km > 350 -> "Isso dá ${Fmt.km(km)} rodados hoje."
        h != null && h > 0.5 && km / h > 60 -> "Isso dá ${Fmt.km(km)} em ${Fmt.min(h * 60)} (média de ${(km / h).toInt()} km/h)."
        else -> null
    }
}

internal fun DashboardActivity.askEnd() {
    val d = store.day()
    val f = k.field("Km do painel")
    val preview = k.text("", 14, Palette.ACCENT, k.bold)
    f.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            val km = f.num(); val st = d.kmStart
            preview.text = if (km != null && st != null && km >= st) "Rodou hoje: ${Fmt.km(km - st)}" else ""
        }
    })
    val box = k.vbox().apply {
        addView(labeled("Km do painel agora (início: ${fmtKm(d.kmStart)})", f))
        addView(preview, k.lp(top = 10))
        val st = d.kmStart
        val gkm = if (settings.gpsEnabled) com.farol.app.data.GpsStore.today(self).km else 0.0
        if (st != null && gkm > 1) {
            val sug = Math.round((st + gkm) * 10) / 10.0
            addView(k.text("📍 Pelo GPS você rodou ${Fmt.km(gkm)} → km final ≈ ${fmtKm(sug)}. Toque para usar.", 13, Palette.ACCENT, k.medium).apply {
                isClickable = true
                setOnClickListener { f.setText(String.format(ptBR, "%.1f", sug)) }
            }, k.lp(top = 10))
        }
    }
    dialog("Encerrar turno", box, "Encerrar") {
        val km = f.num() ?: return@dialog fail("Digite o km do painel")
        if (d.kmStart != null && km < d.kmStart) return@dialog fail("O km final é menor que o inicial")
        val finish = {
            com.farol.app.service.GpsService.stop(this)
            val now = System.currentTimeMillis()
            val up = d.resume(now).copy(kmEnd = km, endAt = now)
            store.putDay(up)
            sendDaySummary(up)
            toast("Turno encerrado")
            render()
        }
        val warn = d.kmStart?.let { suspicious(km - it, d.workedMs()?.let { w -> System.currentTimeMillis() - w }) }
        if (warn != null) {
            AlertDialog.Builder(this, com.farol.app.ui.Palette.dialogTheme)
                .setTitle("Confere o km?")
                .setMessage("$warn\n\nInício: ${fmtKm(d.kmStart)}\nFim: ${fmtKm(km)}\n\nSe estiver errado, toque em Corrigir.")
                .setPositiveButton("Está certo") { _, _ -> finish() }
                .setNegativeButton("Corrigir") { _, _ -> askEnd() }
                .apply {
                    val st = d.kmStart
                    if (st != null && tenthsLikely(st, km)) setNeutralButton("Usar ${Fmt.km((km - st) / 10)}") { _, _ ->
                        val up = d.resume(System.currentTimeMillis()).copy(kmStart = st / 10, kmEnd = km / 10, endAt = System.currentTimeMillis())
                        store.putDay(up); sendDaySummary(up); toast("Corrigido: último dígito era décimo"); render()
                    }
                }
                .setCancelable(false).show()
            return@dialog true
        }
        finish(); true
    }
}

/** Linha "Editar · Excluir" do turno. */
internal fun DashboardActivity.shiftTools() = k.hbox().apply {
    val e = k.ghost("✏️  Editar") { askEditShift() }
    val x = k.ghost("🗑  Excluir") { askDeleteShift() }
    addView(e, weighted(e)); addView(x, weighted(x, 10))
}

internal fun DashboardActivity.togglePause() {
    val d = store.day()
    val now = System.currentTimeMillis()
    if (d.isPaused) {
        store.putDay(d.resume(now))
        if (settings.gpsEnabled) com.farol.app.service.GpsService.start(this)
        toast("Turno retomado")
    } else {
        store.putDay(d.pause(now))
        com.farol.app.service.GpsService.stop(this)   // parado não mede km
        toast("Turno pausado: o tempo parado não conta nas horas")
    }
    render(); runCatching { com.farol.app.service.ShiftNotifier.refresh(this) }
}

internal fun DashboardActivity.askDeleteShift() {
    val d = store.day()
    AlertDialog.Builder(this, com.farol.app.ui.Palette.dialogTheme)
        .setTitle("Excluir o turno de hoje?")
        .setMessage("Apaga km inicial/final, horários e pausas. Os ganhos dos apps continuam salvos.\n\nDepois é só iniciar de novo.")
        .setPositiveButton("Excluir") { _, _ ->
            com.farol.app.service.GpsService.stop(this)
            store.putDay(d.clearShift())
            toast("Turno excluído"); render()
            runCatching { com.farol.app.service.ShiftNotifier.refresh(this) }
        }
        .setNegativeButton("Cancelar", null).show()
}

/**
 * Dias anteriores (até 7): mostra o resumo e deixa corrigir turno e ganhos sem esperar.
 * Ex.: turno encerrado no dia seguinte, ou ganho que faltou.
 */
internal fun DashboardActivity.previousDayRow(card: LinearLayout) {
    val past = store.days().filter { it.date < store.day().date && it.date >= store.lastDays(8).minOrNull().orEmpty() && (it.hasShift || it.totalEarnings > 0) }
        .maxByOrNull { it.date } ?: return
    val s = JournalMath.summarize(past, settings.profile.costPerKm)
    val label = "${past.date.substring(8)}/${past.date.substring(5, 7)}"
    val hrs = s.hours?.let { " · ${Fmt.min(it * 60)}" } ?: ""
    card.addView(k.text("Último dia ($label): ${Fmt.money(s.earnings)}$hrs", 13, Palette.MUTED), k.lp(top = 16))
    card.addView(k.hbox().apply {
        val a = k.ghost("✏️  Turno de $label") { askEditShift(past.date) }
        val b = k.ghost("💰  Ganhos de $label") { editEarnings(past.date) }
        addView(a, weighted(a)); addView(b, weighted(b, 10))
    }, k.lp(top = 8))
}

/** Edita km, horários e pausas do turno (de hoje, ou de [date]). */
internal fun DashboardActivity.askEditShift(date: String = store.day().date) {
    val d = store.day(date)
    val a = k.field("Km inicial", fmtKm(d.kmStart))
    val b = k.field("Km final", fmtKm(d.kmEnd))
    val ta = k.field("08:00", d.startAt?.let { hhmm.format(Date(it)) } ?: "")
    val tb = k.field("18:00", d.endAt?.let { hhmm.format(Date(it)) } ?: "")
    val pz = k.field("0", if (d.pausedMs > 0) (d.pausedMs / 60_000).toString() else "")
    listOf(ta, tb).forEach { it.inputType = android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME }
    pz.inputType = android.text.InputType.TYPE_CLASS_NUMBER
    val ended = d.kmEnd != null || d.endAt != null
    val box = k.vbox().apply {
        val av = labeled("Km inicial", a); val tav = labeled("Início (hh:mm)", ta)
        addView(k.hbox().apply { addView(av, weighted(av)); addView(tav, weighted(tav, 10)) })
        if (ended) {
            val bv = labeled("Km final", b); val tbv = labeled("Fim (hh:mm)", tb)
            addView(k.hbox().apply { addView(bv, weighted(bv)); addView(tbv, weighted(tbv, 10)) }, k.lp(top = 12))
        }
        addView(labeled("Pausas (minutos no total)", pz), k.lp(top = 12))
    }
    dialog("Editar turno", ScrollView(this).apply { addView(box) }, "Salvar") {
        val s = a.num(); val e = if (ended) b.num() else d.kmEnd
        fun time(f: EditText, base: Long?): Long? {
            val t = f.text.toString().trim().replace('h', ':').replace('.', ':')
            if (t.isEmpty()) return null
            val m = Regex("""^(\d{1,2}):?(\d{2})$""").find(t) ?: return -1L
            val hh = m.groupValues[1].toInt(); val mm = m.groupValues[2].toInt()
            if (hh > 23 || mm > 59) return -1L
            val c = Calendar.getInstance().apply {
                timeInMillis = base ?: System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, mm); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            return c.timeInMillis
        }
        val st = time(ta, d.startAt)
        var en = if (ended) time(tb, d.endAt ?: d.startAt) else d.endAt
        // Turno que passou da meia-noite: "00:25" é do dia seguinte ao início
        if (st != null && st != -1L && en != null && en != -1L) {
            while (en!! <= st && st - en < 2 * 86_400_000L) en += 86_400_000L
            while (en!! - st > 86_400_000L) en -= 86_400_000L
        }
        if (st == -1L || en == -1L) return@dialog fail("Horário inválido. Use hh:mm, ex.: 08:30")
        if (st != null && st > System.currentTimeMillis()) return@dialog fail("O início está no futuro")
        if (st != null && en != null && en <= st) return@dialog fail("O fim é antes do início")
        val pauseMin = pz.text.toString().trim().toLongOrNull() ?: 0L
        val span = if (st != null) ((en ?: System.currentTimeMillis()) - st) / 60_000 else 0L
        if (pauseMin < 0 || (st != null && pauseMin >= span)) return@dialog fail("As pausas passam do tempo do turno")
        if (s != null && e != null && e < s) return@dialog fail("O km final é menor que o inicial")
        val base = d.copy(startAt = st, endAt = en, pausedMs = pauseMin * 60_000)
        if (s != null && e != null && e - s > 350) {
            if (tenthsLikely(s, e)) {
                AlertDialog.Builder(this, com.farol.app.ui.Palette.dialogTheme)
                    .setTitle("O último dígito é décimo?")
                    .setMessage("Deu ${Fmt.km(e - s)} no dia. No painel de muitas motos o último número é a casa decimal.\n\n" +
                        "${fmtKm(s / 10)} → ${fmtKm(e / 10)} = ${Fmt.km((e - s) / 10)}")
                    .setPositiveButton("Usar ${Fmt.km((e - s) / 10)}") { _, _ ->
                        val up = base.copy(kmStart = s / 10, kmEnd = e / 10)
                        store.putDay(up); sendDaySummary(up); render()
                    }
                    .setNegativeButton("Voltar", null).show()
                return@dialog true
            }
            return@dialog fail("Deu ${Fmt.km(e - s)} no dia. Confira os dois números.")
        }
        val up = base.copy(kmStart = s, kmEnd = e)
        store.putDay(up)
        if (e != null) sendDaySummary(up)
        toast("Turno atualizado"); true
    }
}
