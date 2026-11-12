package com.farol.app.data

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.farol.app.core.LogSanitizer
import com.farol.app.core.OfferResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Envio de logs em tempo (quase) real para análise.
 *
 * Os eventos vão para uma fila em disco (outbox.jsonl), então nada se perde sem sinal.
 * A cada "intervalo" segundos a fila é enviada num lote:
 *   - GitHub: cria logs/AAAA-MM-DD/HHmmss-<aparelho>.jsonl no seu repositório (privado!)
 *   - Webhook: POST {"eventos":[...]} para a URL que você quiser (n8n, Make, Apps Script...)
 *
 * Tipos de evento:
 *   offer = oferta reconhecida (texto + números calculados)
 *   miss  = tela com cara de oferta que o leitor NÃO entendeu (o mais valioso para calibrar)
 *   teste = botão "Enviar teste"
 */
class RemoteLog private constructor(context: Context) {

    companion object {
        @Volatile private var inst: RemoteLog? = null
        fun get(ctx: Context): RemoteLog =
            inst ?: synchronized(this) { inst ?: RemoteLog(ctx.applicationContext).also { inst = it } }

        private const val MAX_OUTBOX_BYTES = 2_000_000L
        private const val MAX_BATCH_LINES = 400
    }

    private val ctx = context
    private val settings = Settings(context)
    private val outbox = File(context.filesDir, "outbox.jsonl")
    private val exec = Executors.newSingleThreadScheduledExecutor()
    private val lock = Any()

    private var lastFlushAt = 0L
    private var lastMissKey = ""
    private var lastMissAt = 0L

    private val appVersion: String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull() ?: "?"

    private val deviceId: String
        get() = settings.prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().take(8).also {
            settings.prefs.edit().putString("deviceId", it).apply()
        }

    init {
        exec.scheduleWithFixedDelay({ runCatching { flush(force = false) } }, 20, 20, TimeUnit.SECONDS)
    }

    val enabled: Boolean get() = settings.remoteMode != "off"

    fun pendingCount(): Int = synchronized(lock) { if (outbox.exists()) outbox.readLines().count { it.isNotBlank() } else 0 }

    // ---------- eventos ----------

    private var lastOfferSig = ""
    private var lastOfferAt = 0L

    fun offer(pkg: String, source: String, raw: String, r: OfferResult) {
        if (!enabled) return
        // A mesma oferta lida de novo (cartão fechado e reaberto) não vira evento repetido.
        val now = SystemClock.elapsedRealtime()
        if (r.offer.signature == lastOfferSig && now - lastOfferAt < 120_000) return
        lastOfferSig = r.offer.signature; lastOfferAt = now
        val o = r.offer
        enqueue(base("offer", pkg, source, raw)
            .put("preco", o.price).put("km", o.totalKm).put("min", r.minutes)
            .put("tempo_estimado", r.timeEstimated)
            .put("busca_km", o.pickupKm ?: JSONObject.NULL)
            .put("nota", o.passengerRating ?: JSONObject.NULL)
            .put("distancias", JSONArray(o.distancesKm)).put("tempos", JSONArray(o.durationsMin))
            .put("r_km", round(r.perKm)).put("r_h", round(r.perHour)).put("liquido", round(r.net))
            .put("cor", r.rating.name).put("avisos", JSONArray(r.warnings)))
    }

    fun miss(pkg: String, source: String, raw: String, extra: String? = null) {
        if (!enabled) return
        // Contadores (ex.: "15 s") mudam o texto toda hora: compara sem dígitos e segura 20 s.
        val key = pkg + raw.replace(Regex("""\d"""), "").take(500)
        val now = SystemClock.elapsedRealtime()
        if (key == lastMissKey && now - lastMissAt < 20_000) return
        lastMissKey = key; lastMissAt = now
        val e = base("miss", pkg, source, raw)
        if (extra != null) e.put("texto_acessibilidade", clean(extra))
        enqueue(e)
    }

    private val lastScreenKey = mutableMapOf<String, String>()
    private val lastScreenAt = mutableMapOf<String, Long>()

    /**
     * Tela de um app de entrega depois de uma oferta (para aprender aceite/coleta/entrega/finalização).
     * Só envia quando a tela muda de verdade (sem contar números) e no máximo 1 a cada 20 s por app.
     */
    fun screen(pkg: String, source: String, raw: String, minutesSinceOffer: Int) {
        if (!enabled) return
        val key = raw.replace(Regex("""\d"""), "").replace(Regex("""\s+"""), " ").take(600)
        val now = SystemClock.elapsedRealtime()
        if (key == lastScreenKey[pkg]) return
        if (now - (lastScreenAt[pkg] ?: 0L) < 20_000) return
        lastScreenKey[pkg] = key; lastScreenAt[pkg] = now
        enqueue(base("tela", pkg, source, raw).put("min_desde_oferta", minutesSinceOffer))
    }

    fun delivery(r: com.farol.app.core.DeliveryRecord) {
        if (!enabled) return
        enqueue(JSONObject().put("t", iso(Date())).put("tipo", "pedido").put("versao", appVersion).put("aparelho", deviceId)
            .put("app", r.app).put("restaurante", r.restaurant ?: JSONObject.NULL)
            .put("min_coleta", r.collectMin?.let { round(it) } ?: JSONObject.NULL)
            .put("min_entrega", r.deliverMin?.let { round(it) } ?: JSONObject.NULL))
    }

    /** Resumo do dia ao encerrar o turno (sem endereços). */
    fun day(summary: JSONObject) {
        if (!enabled) return
        enqueue(JSONObject().put("t", iso(Date())).put("tipo", "dia").put("versao", appVersion)
            .put("aparelho", deviceId).put("resumo", summary))
    }

    /** Inclui os pacotes vistos recentemente: ajuda a descobrir o nome de apps novos (ex.: Keeta). */
    fun test() = enqueue(base("teste", ctx.packageName, "app", "Teste de envio do Farol | R$ 10,00 | 5 km | Aceitar")
        .put("pacotes_vistos", JSONArray(synchronized(com.farol.app.service.OfferReaderService.recentPackages) {
            com.farol.app.service.OfferReaderService.recentPackages.toList()
        }))
        .put("ajustes", settingsSnapshot()))

    /** Ajustes atuais para conferência remota. Nunca inclui o token nem a URL/senha do webhook. */
    private fun settingsSnapshot(): JSONObject {
        val p = settings.profile
        return JSONObject()
            .put("combustivel", p.fuelPrice).put("consumo", p.kmPerLiter).put("outros_km", p.otherCostPerKm)
            .put("custo_km", Math.round(p.costPerKm * 1000) / 1000.0)
            .put("verde_km", p.greenPerKm).put("amarelo_km", p.yellowPerKm)
            .put("verde_h", p.greenPerHour).put("amarelo_h", p.yellowPerHour)
            .put("nota_verde", p.greenStars).put("nota_min", p.minPassengerRating)
            .put("velocidade", p.avgSpeedKmh)
            .put("busca_max", p.maxPickupKm).put("dist_max", p.maxTotalKm).put("tempo_max", p.maxMinutes)
            .put("valor_min", p.minPrice).put("lucro_min", p.minNet).put("lucro_min_pct", p.minNetPct)
            .put("meta_dia", settings.dailyGoal)
            .put("bairros_alerta", p.alertWords.size).put("bairros_favoritos", p.favoriteWords.size)
            .put("cartao_tamanho", listOf("P", "M", "G")[settings.cardSize]).put("cartao_compacto", settings.cardCompact)
            .put("esconder_s", settings.autoHideSec)
            .put("ligado", settings.enabled).put("ocr", settings.ocrFallback).put("so_com_botao", settings.requireHint)
            .put("ganhos_auto", settings.autoEarnings).put("registrar_telas", settings.logScreens)
            .put("vibrar", settings.vibrateOnGreen).put("voz", settings.speak)
            .put("notif_turno", settings.shiftNotification).put("resumo_semana", settings.weeklySummary)
            .put("mascarar", settings.maskAddresses)
            .put("apps", JSONArray(settings.targets.values.toList()))
    }

    private fun base(type: String, pkg: String, source: String, raw: String) = JSONObject()
        .put("t", iso(Date()))
        .put("tipo", type)
        .put("app", pkg)
        .put("fonte", source)
        .put("texto", clean(raw))
        .put("versao", appVersion)
        .put("aparelho", deviceId)

    private fun clean(s: String) = if (settings.maskAddresses) LogSanitizer.mask(s) else s

    private fun enqueue(o: JSONObject) = synchronized(lock) {
        outbox.appendText(o.toString() + "\n")
        if (outbox.length() > MAX_OUTBOX_BYTES) {           // sem sinal por muito tempo: descarta os mais antigos
            val lines = outbox.readLines()
            outbox.writeText(lines.takeLast(lines.size / 2).joinToString("\n", postfix = "\n"))
        }
    }

    // ---------- envio ----------

    /** Botões "Enviar agora"/"Enviar teste". O callback roda fora da thread de UI. */
    fun flushNow(cb: (String) -> Unit) = exec.execute { cb(runCatching { flush(force = true) }.getOrElse { "erro: ${it.message}" }) }

    private fun flush(force: Boolean): String {
        if (!enabled) return "envio desligado"
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastFlushAt < settings.remoteIntervalSec * 1000L) return "aguardando"

        val batch = synchronized(lock) {
            if (!outbox.exists()) emptyList() else outbox.readLines().filter { it.isNotBlank() }.take(MAX_BATCH_LINES)
        }
        if (batch.isEmpty()) return "nada para enviar"
        lastFlushAt = now

        val result = runCatching {
            when (settings.remoteMode) {
                "github" -> sendGitHub(batch)
                "webhook" -> sendWebhook(batch)
                else -> error("modo desconhecido")
            }
        }
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val status = result.fold(
            onSuccess = {
                synchronized(lock) {   // remove só o que foi enviado (pode ter chegado coisa nova)
                    val all = outbox.readLines().filter { it.isNotBlank() }
                    outbox.writeText(all.drop(batch.size).joinToString("") { it + "\n" })
                }
                "✅ $time enviados ${batch.size} eventos"
            },
            onFailure = { "❌ $time ${it.message?.take(160)}" }
        )
        settings.remoteLastStatus = status
        return status
    }

    private fun sendGitHub(lines: List<String>) {
        val repo = settings.ghRepo.trim().removePrefix("https://github.com/").trim('/')
        require(repo.count { it == '/' } == 1) { "repositório inválido (use dono/nome)" }
        require(settings.ghToken.isNotBlank()) { "falta o token do GitHub" }

        val now = Date()
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val hms = SimpleDateFormat("HHmmss", Locale.US).format(now)
        val path = "logs/$day/$hms-$deviceId.jsonl"
        val content = Base64.encodeToString(lines.joinToString("\n", postfix = "\n").toByteArray(), Base64.NO_WRAP)
        val body = JSONObject()
            .put("message", "log: ${lines.size} eventos [skip ci]")
            .put("content", content)
            .put("branch", settings.ghBranch.ifBlank { "main" })

        http("PUT", "https://api.github.com/repos/$repo/contents/$path", body.toString(), mapOf(
            "Authorization" to "Bearer ${settings.ghToken.trim()}",
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
        ))
    }

    private fun sendWebhook(lines: List<String>) {
        val url = settings.webhookUrl.trim()
        require(url.startsWith("https://") || url.startsWith("http://")) { "URL do webhook inválida" }
        val arr = JSONArray(lines.map { JSONObject(it) })
        val headers = buildMap {
            if (settings.webhookAuth.isNotBlank()) put("Authorization", settings.webhookAuth.trim())
        }
        http("POST", url, JSONObject().put("eventos", arr).toString(), headers)
    }

    private fun http(method: String, url: String, body: String, headers: Map<String, String>) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("User-Agent", "Farol/$appVersion")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = runCatching { c.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                error("HTTP $code ${err.take(120)}")
            }
        } finally {
            c.disconnect()
        }
    }

    private fun iso(d: Date) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }.format(d)

    private fun round(v: Double) = Math.round(v * 100) / 100.0
}
