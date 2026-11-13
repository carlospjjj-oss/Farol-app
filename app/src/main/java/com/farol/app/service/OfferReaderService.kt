package com.farol.app.service

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import com.farol.app.core.Fmt
import com.farol.app.core.OfferResult
import java.util.Locale
import com.farol.app.core.CostProfile
import com.farol.app.core.EarningsReader
import com.farol.app.core.RejectionReader
import com.farol.app.ui.Palette
import com.farol.app.core.DeliveryTracker
import com.farol.app.core.PhaseDetector
import com.farol.app.data.JournalStore
import com.farol.app.core.OfferParser
import com.farol.app.core.ParsedOffer
import com.farol.app.core.ProfitCalculator
import com.farol.app.core.Rating
import com.farol.app.data.CaptureLog
import com.farol.app.data.History
import com.farol.app.data.RemoteLog
import com.farol.app.data.Settings
import com.farol.app.ui.OverlayCard

/**
 * Fluxo:
 *   evento de acessibilidade de um app monitorado
 *     -> (throttle 200 ms) lê o texto de todas as janelas desse app
 *     -> OfferParser; se não achou e parece ter oferta, tenta OCR do print
 *     -> ProfitCalculator com seus custos
 *     -> cartão flutuante verde/amarelo/vermelho + histórico
 *
 * O Farol só LÊ. Nunca clica, aceita ou recusa nada nos outros apps.
 */
class OfferReaderService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        @Volatile var instance: OfferReaderService? = null
            private set

        /** Últimos pacotes vistos em primeiro plano: ajuda a descobrir o nome de pacote de um app. */
        val recentPackages: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    }

    private lateinit var settings: Settings
    private lateinit var overlay: OverlayCard
    private lateinit var history: History
    private lateinit var capture: CaptureLog
    private lateinit var ocr: OcrFallback
    private lateinit var remote: RemoteLog
    private lateinit var journal: JournalStore
    private val tracker = DeliveryTracker { r ->
        runCatching { journal.addDelivery(r) }
        runCatching { remote.delivery(r) }
        // Pedido completo com km da oferta: amostra real de velocidade de entrega
        val km = r.km; val mins = r.totalMin
        if (km != null && mins != null) com.farol.app.core.SpeedLearner.sample(km, mins)?.let { settings.addSpeedSample(r.app, it) }
        // Com GPS ligado: km REAL rodado no pedido (vira sugestão no dashboard)
        val end = r.endAt
        if (GpsService.running && mins != null && end != null) {
            com.farol.app.data.GpsStore.kmBetween(this, r.collectAt, end)?.let { gkm ->
                com.farol.app.core.SpeedLearner.sample(gkm, mins)?.let { settings.addGpsSpeedSample(r.app, it) }
            }
        }
    }

    /** Última oferta de cada app (km e hora) para ligar ao pedido aceito. */
    private val lastOfferKm = mutableMapOf<String, Pair<Double, Long>>()

    private val handler = Handler(Looper.getMainLooper())
    private var scanScheduled = false
    private val scanRunnable = Runnable { scanScheduled = false; scan() }

    /** Apps em que a acessibilidade já leu uma oferta: neles o OCR não roda (evita leituras erradas). */
    private val accessibleApps = mutableSetOf<String>()
    private var profile = CostProfile()

    private var lastSig: String? = null
    private var lastSigAt = 0L
    private var lastOfferSeenAt = 0L
    /** Última oferta vista (para registrar as telas seguintes por até 90 min). */
    private var lastAnyOfferAt = 0L
    private var lastScreenOcrAt = 0L

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onServiceConnected() {
        settings = Settings(this)
        overlay = OverlayCard(this, settings)
        history = History(this)
        capture = CaptureLog(this)
        ocr = OcrFallback(this)
        remote = RemoteLog.get(this)
        journal = JournalStore(this)
        // Uma vez: limpa as ofertas repetidas gravadas pelas versões antigas
        if (!settings.prefs.getBoolean("dedupDone", false)) {
            runCatching { history.removeDuplicates() }
            settings.prefs.edit().putBoolean("dedupDone", true).apply()
        }
        seedSpeed()
        reload()
        settings.purgeLegacySecrets(com.farol.app.data.Account.isLoggedIn(this))
        settings.prefs.registerOnSharedPreferenceChangeListener(this)
        instance = this
        applyPackageFilter()
        handler.post(tick)
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        reload()
        if (key == "targets") applyPackageFilter()
    }

    // ---------- Quais apps a acessibilidade pode ler ----------
    private var discoverUntil = 0L

    /**
     * Só os apps de corrida/entrega mandam eventos para o Farol: WhatsApp, banco, galeria etc.
     * nem chegam aqui. No "modo descoberta" (2 min, pelo botão nos ajustes) todos passam,
     * para achar o nome do pacote de um app novo.
     */
    fun applyPackageFilter() {
        runCatching {
            val discovering = SystemClock.elapsedRealtime() < discoverUntil
            serviceInfo = serviceInfo.apply {
                packageNames = if (discovering) null else settings.allowedPackages().toTypedArray()
            }
        }
    }

    fun discover(seconds: Int = 120) {
        discoverUntil = SystemClock.elapsedRealtime() + seconds * 1000L
        applyPackageFilter()
        handler.postDelayed({ applyPackageFilter() }, seconds * 1000L + 500)
    }

    /** A cada minuto: notificação do turno, widget e resumo da semana (domingo à noite). */
    private val tick = object : Runnable {
        override fun run() {
            runCatching { ShiftNotifier.refresh(this@OfferReaderService) }
            runCatching { ShiftNotifier.maybeWeekly(this@OfferReaderService) }
            runCatching { remote.settingsChanged("diário") }
            runCatching { Updater.maybeCheck(this@OfferReaderService, foreground = false) }
            handler.postDelayed(this, 60_000)
        }
    }

    /** Primeira vez: aprende a velocidade com as ofertas do histórico que tinham tempo real (ex.: iFood). */
    private fun seedSpeed() {
        if (settings.speedSamples("iFood").isNotEmpty()) return
        val seeds = history.recent(2000).reversed().mapNotNull { o ->
            // Ofertas novas guardam se o tempo era estimado; nas antigas, o iFood sempre mostra o tempo real
            // Só iFood (as ofertas dele trazem o tempo real)
            if (o.optString("app") != "iFood") return@mapNotNull null
            if (o.has("estimated") && o.optBoolean("estimated")) return@mapNotNull null
            com.farol.app.core.SpeedLearner.sample(o.optDouble("km"), o.optDouble("min"))
        }
        if (seeds.isNotEmpty()) settings.setSpeedSamples("iFood", seeds.takeLast(com.farol.app.core.SpeedLearner.MAX_SAMPLES))
    }

    /** Guarda o aviso de recusas que o app mostrou (99: "rejeitou 7 vezes", iFood: "parada técnica"). */
    private fun readRejections(label: String, text: String) {
        val sig = RejectionReader.read(text) ?: return
        settings.setRejection(label, sig.streak, sig.paused)
    }

    /** Linhas extras do cartão: recusas seguidas e meta do dia. */
    private fun extrasFor(label: String): List<com.farol.app.ui.OverlayCard.Extra> = buildList {
        settings.rejection(label)?.let { (n, paused) ->
            when {
                paused -> add(com.farol.app.ui.OverlayCard.Extra("⛔ $label pausou sua conta por recusas", Palette.RED))
                n >= 5 -> add(com.farol.app.ui.OverlayCard.Extra("⚠ $n recusas seguidas: risco de pausa", Palette.YELLOW))
            }
        }
        val goal = settings.dailyGoal
        if (goal > 0) {
            val left = goal - journal.day().totalEarnings
            add(com.farol.app.ui.OverlayCard.Extra(if (left <= 0) "🎯 Meta do dia batida!" else "🎯 Faltam ${com.farol.app.core.Fmt.money(left)} para a meta",
                if (left <= 0) Palette.GREEN else Palette.MUTED))
        }
    }

    private fun reload() {
        profile = settings.profile
        if (settings.speak && tts == null) {
            tts = TextToSpeech(this) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) tts?.language = Locale("pt", "BR")
            }
        }
    }

    /** Resumo falado, para não precisar tirar o olho da rua. */
    private fun speak(r: OfferResult) {
        if (!settings.speak || !ttsReady) return
        val cor = when (r.rating) { Rating.GREEN -> "Verde"; Rating.YELLOW -> "Amarelo"; Rating.RED -> "Vermelho" }
        val msg = buildString {
            append("$cor. ${Fmt.money(r.perKm)} por quilômetro. ${Fmt.money(r.perHour)} por hora.")
            if (r.warnings.isNotEmpty()) append(" Atenção: ${r.warnings.joinToString(", ")}.")
        }
        tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "farol")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (pkg == packageName) return
        if (pkg != null) noteRecent(pkg)
        if (!settings.enabled) { if (overlay.isShowing) overlay.hide(); return }
        if (pkg != null && settings.labelFor(pkg) == null) {
            // Saiu do app de corrida: esconde o cartão depois de uma pequena folga.
            if (overlay.isShowing && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) maybeHide()
            return
        }
        // Throttle (não debounce): o mapa/contador da oferta gera eventos sem parar.
        if (!scanScheduled) {
            scanScheduled = true
            handler.postDelayed(scanRunnable, 120)
        }
    }

    // Login obrigatório: sem conta o Farol não lê nada. Conferido a cada 30 s (decifrar o token custa).
    private var loggedAt = 0L
    private var logged = false
    private fun loggedIn(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - loggedAt > 30_000) { logged = com.farol.app.data.Account.isLoggedIn(this); loggedAt = now }
        return logged
    }

    private fun scan() {
        if (!loggedIn()) { maybeHide(); return }
        val snap = ScreenTextCollector.collect(this) { settings.labelFor(it) != null } ?: run { maybeHide(); return }
        val label = settings.labelFor(snap.pkg) ?: snap.pkg
        if (settings.captureMode) capture.append(snap.pkg, "acessibilidade", snap.text)

        val offer = OfferParser.parse(snap.text, label, settings.requireHint)
        readRejections(label, snap.text)
        if (offer == null && snap.onTop) { logScreen(snap); autoEarnings(label, snap.text); trackPhase(label, snap.text) }
        if (offer != null) {
            accessibleApps += snap.pkg
            handleOffer(offer, snap.pkg, "acessibilidade", snap.text); return
        }

        val hasHint = OfferParser.OFFER_HINTS.containsMatchIn(snap.text)
        val looksLikeOffer = hasHint || snap.text.length < 40
        if (settings.ocrFallback && ocr.supported && looksLikeOffer && snap.pkg !in accessibleApps) {
            runOcr(snap, label, hasHint)
        } else {
            if (hasHint && snap.pkg !in accessibleApps) remote.miss(snap.pkg, "acessibilidade", snap.text)
            maybeHide()
        }
    }

    /** Registra telas de trabalho depois de uma oferta: aceite, coleta, entrega, finalização. */
    private fun logScreen(snap: ScreenTextCollector.Snapshot) {
        if (!settings.logScreens || !remote.enabled || lastAnyOfferAt == 0L) return
        val now = SystemClock.elapsedRealtime()
        val mins = ((now - lastAnyOfferAt) / 60_000).toInt()
        if (mins > 90) return
        if (snap.text.length >= 60) remote.screen(snap.pkg, "acessibilidade", snap.text, mins)
        // Texto curto (app que esconde o texto, ex.: 99): o runOcr registra a tela lida pela imagem.
    }

    /**
     * Se a tela é o painel do app, guarda o "ganhos de hoje" dele no dashboard.
     * Usa o maior valor do dia (o total só cresce; evita voltar a zero enquanto o app carrega).
     */
    private fun autoEarnings(label: String, text: String) {
        if (!settings.autoEarnings) return
        val v = EarningsReader.read(label, text) ?: return
        val day = journal.day()
        val cur = day.earnings[label]
        if (cur != null && v <= cur) return
        if (v <= 0.0 && cur == null) return
        journal.putDay(day.copy(earnings = day.earnings + (label to v)))
        settings.markAuto(day.date, label)
        runCatching { ShiftNotifier.refresh(this) }
    }

    /** Acompanha as etapas do pedido (coleta → entrega → livre) para medir o tempo de cada um. */
    private fun trackPhase(label: String, text: String) {
        val phase = PhaseDetector.detect(label, text) ?: return
        val now = System.currentTimeMillis()
        val km = lastOfferKm[label]?.takeIf { now - it.second < 3 * 60_000L }?.first
        tracker.feed(label, phase, PhaseDetector.restaurant(label, text), now, km)
    }

    private fun minutesSinceOffer(): Int? {
        if (lastAnyOfferAt == 0L) return null
        val m = ((SystemClock.elapsedRealtime() - lastAnyOfferAt) / 60_000).toInt()
        return if (m <= 90) m else null
    }

    private var ocrRetries = 0
    private val ocrRetry = Runnable { scan() }

    /**
     * Um único OCR para tudo: primeiro tenta achar oferta; se não achar, registra a tela (se for o caso).
     * Se a oferta ainda está carregando (ou o OCR estava ocupado), tenta de novo em 0,8 s, até 4 vezes,
     * mesmo sem novo evento de acessibilidade (a 99 às vezes não gera mais eventos).
     */
    private fun runOcr(snap: ScreenTextCollector.Snapshot, label: String, hasHint: Boolean) {
        val started = ocr.run(overlay.screenRect()) { text ->
            if (settings.captureMode) capture.append(snap.pkg, "ocr", text)
            val o = OfferParser.parse(text, label, settings.requireHint)
            if (o != null) {
                ocrRetries = 0
                handleOffer(o, snap.pkg, "ocr", text)
                return@run
            }
            // O print pega a tela inteira: só usa para ganhos/avisos/telas se o app estiver na frente
            if (snap.onTop) { autoEarnings(label, text); readRejections(label, text) }
            val hint = hasHint || OfferParser.OFFER_HINTS.containsMatchIn(text)
            if (hint && ocrRetries < 4) {
                ocrRetries++
                handler.removeCallbacks(ocrRetry); handler.postDelayed(ocrRetry, 800)
            } else {
                if (hint) remote.miss(snap.pkg, "ocr", text, snap.text)
                ocrRetries = 0
            }
            if (settings.logScreens && snap.onTop && text.length >= 40) {
                val now = SystemClock.elapsedRealtime()
                minutesSinceOffer()?.let { m ->
                    if (now - lastScreenOcrAt > 15_000) { lastScreenOcrAt = now; remote.screen(snap.pkg, "ocr", text, m) }
                }
            }
            maybeHide()
        }
        if (!started && ocrRetries < 6) {
            ocrRetries++
            handler.removeCallbacks(ocrRetry); handler.postDelayed(ocrRetry, 500)
        }
    }

    private fun handleOffer(offer: ParsedOffer, pkg: String, source: String, raw: String) {
        val now = SystemClock.elapsedRealtime()
        lastOfferSeenAt = now
        lastAnyOfferAt = now
        if (offer.signature == lastSig && now - lastSigAt < 60_000 && overlay.isShowing) {
            overlay.keepAlive()
            return
        }
        lastSig = offer.signature
        lastSigAt = now

        lastOfferKm[offer.app] = offer.totalKm to System.currentTimeMillis()
        offer.totalMin?.let { m -> com.farol.app.core.SpeedLearner.sample(offer.totalKm, m)?.let { settings.addSpeedSample(offer.app, it) } }
        // Cada app com a sua velocidade (só usada quando a oferta não mostra o tempo)
        val result = ProfitCalculator.evaluate(offer, settings.effectiveProfile(offer.app))
        overlay.show(result, extrasFor(offer.app))
        runCatching { ShiftNotifier.refresh(this) }
        runCatching { history.add(result, source) }
        runCatching { remote.offer(pkg, source, raw, result) }
        if (result.rating == Rating.GREEN && settings.vibrateOnGreen) vibrate()
        speak(result)
    }

    /** A oferta sumiu da tela (aceita, recusada ou expirada): esconde após 3 s de folga. */
    private fun maybeHide() {
        if (overlay.isShowing && SystemClock.elapsedRealtime() - lastOfferSeenAt > 3_000) {
            overlay.hide()
            lastSig = null
        }
    }

    private fun vibrate() {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1))
    }

    private fun noteRecent(pkg: String) {
        synchronized(recentPackages) {
            recentPackages.remove(pkg)
            recentPackages.add(0, pkg)
            while (recentPackages.size > 12) recentPackages.removeAt(recentPackages.size - 1)
        }
    }

    /** Botão "Testar cartão" da tela principal. */
    fun showTest(rating: Rating) {
        val sample = when (rating) {
            Rating.GREEN -> "UberX | R$ 28,00 | 3 min (1,0 km) | Viagem de 16 min (9,0 km) | Aceitar"
            Rating.YELLOW -> "99Pop | R$ 17,00 | 5 min (2,0 km) | Viagem de 20 min (8,5 km) | Aceitar"
            Rating.RED -> "UberX | 4,61 ★ | R$ 9,50 | 8 min (3,1 km) | Viagem de 15 min (7,5 km) | Aceitar"
        }
        val offer = OfferParser.parse(sample, "Teste") ?: return
        lastSig = null
        handler.post {
            val r = ProfitCalculator.evaluate(offer, profile)
            overlay.show(r, extrasFor("Teste"))
            speak(r)
            lastOfferSeenAt = SystemClock.elapsedRealtime() + 10_000
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        if (::settings.isInitialized) settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (::overlay.isInitialized) overlay.hide()
        if (::ocr.isInitialized) ocr.close()
        handler.removeCallbacks(tick)
        tts?.shutdown(); tts = null
        super.onDestroy()
    }
}
