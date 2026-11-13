package com.farol.app.data

import android.content.Context
import android.content.SharedPreferences
import com.farol.app.core.CostProfile

/** Configurações persistidas (SharedPreferences). Lidas pelo serviço e pela tela principal. */
class Settings(context: Context, private val modeOverride: String? = null) {

    private val appCtx: Context = context.applicationContext

    /** Ajustes gerais (logs, cartão, voz...) e também os da MOTO (compatível com as versões antigas). */
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("farol", Context.MODE_PRIVATE)

    /** "moto" ou "carro". Cada modo tem custos, faixas, apps, diário e histórico próprios. */
    var vehicleMode: String
        get() = prefs.getString("vehicleMode", "moto") ?: "moto"
        set(v) = prefs.edit().putString("vehicleMode", v).apply()
    /** modeOverride: ler os ajustes de um modo específico sem trocar o modo do app (sincronização). */
    val isCar: Boolean get() = (modeOverride ?: vehicleMode) == "carro"

    /** Ajustes do veículo atual: moto usa o arquivo de sempre; carro usa um separado. */
    val vprefs: SharedPreferences
        get() = if (isCar) appCtx.getSharedPreferences("farol_carro", Context.MODE_PRIVATE) else prefs

    /** Sufixo dos arquivos do modo (diário, histórico, GPS). Moto = sem sufixo. */
    val fileSuffix: String get() = if (isCar) "_carro" else ""

    val modeLabel: String get() = if (isCar) "🚗 Carro" else "🛵 Moto"

    companion object {
        /** pacote=Nome. Vêm do catálogo único (core/AppCatalog); dá para editar nos ajustes. */
        val DEFAULT_TARGETS_CAR = com.farol.app.core.AppCatalog.defaultTargets(com.farol.app.core.AppCatalog.Mode.CARRO)
        val DEFAULT_TARGETS = com.farol.app.core.AppCatalog.defaultTargets(com.farol.app.core.AppCatalog.Mode.MOTO)
    }

    private fun d(key: String, def: Double) = vprefs.getString(key, null)?.toDoubleOrNull() ?: def

    // ---------- Combustível: gasolina e etanol guardados, um ativo ----------
    data class Fuel(val price: Double, val kml: Double)

    /** Combustível no tanque agora ("gasolina" ou "etanol"). Os cálculos usam este. */
    var fuelType: String
        get() = vprefs.getString("fuelType", null) ?: legacyFuelType()
        set(v) = vprefs.edit().putString("fuelType", if (v == "etanol") "etanol" else "gasolina").apply()

    /** Antes da 5.1 havia um só combustível: pelo preço dá para saber qual era. */
    private fun legacyFuelType(): String {
        val p = vprefs.getString("fuelPrice", null)?.toDoubleOrNull()
        return when {
            p != null && p > 0 -> if (p <= 4.8) "etanol" else "gasolina"
            isCar -> "etanol"
            else -> "gasolina"
        }
    }

    fun fuel(type: String): Fuel {
        val p = vprefs.getString("fuelPrice_$type", null)?.toDoubleOrNull()
        val k = vprefs.getString("kmPerLiter_$type", null)?.toDoubleOrNull()
        if (p != null && k != null) return Fuel(p, k)
        // migração: os valores antigos passam para o combustível que eles eram
        val lp = d("fuelPrice", -1.0); val lk = d("kmPerLiter", -1.0)
        if (lp > 0 && lk > 0 && type == legacyFuelType()) return Fuel(lp, lk)
        return if (type == "etanol") {
            if (isCar) Fuel(3.89, 7.0) else Fuel(3.89, Math.round(fuel("gasolina").kml * 0.7 * 10) / 10.0)
        } else {
            if (isCar) Fuel(6.00, 10.0) else Fuel(CostProfile().fuelPrice, CostProfile().kmPerLiter)
        }
    }

    fun setFuel(type: String, f: Fuel) {
        vprefs.edit().putString("fuelPrice_$type", f.price.toString()).putString("kmPerLiter_$type", f.kml.toString()).apply()
    }

    var profile: CostProfile
        get() {
            val def = CostProfile()
            return CostProfile(
                fuelPrice = fuel(fuelType).price,
                kmPerLiter = fuel(fuelType).kml,
                otherCostPerKm = d("otherCostPerKm", def.otherCostPerKm),
                greenPerKm = d("greenPerKm", def.greenPerKm),
                yellowPerKm = d("yellowPerKm", def.yellowPerKm),
                greenPerHour = d("greenPerHour", def.greenPerHour),
                yellowPerHour = d("yellowPerHour", def.yellowPerHour),
                avgSpeedKmh = d("avgSpeedKmh", def.avgSpeedKmh),
                maxPickupKm = d("maxPickupKm", def.maxPickupKm),
                minPassengerRating = d("minPassengerRating", def.minPassengerRating),
                greenStars = d("greenStars", def.greenStars),
                minPrice = d("minPrice", def.minPrice),
                minNet = d("minNet", def.minNet),
                minNetPct = d("minNetPct", def.minNetPct),
                maxTotalKm = d("maxTotalKm", def.maxTotalKm),
                maxMinutes = d("maxMinutes", def.maxMinutes),
                alertWords = alertWordsText.split(',', '\n').map { it.trim() }.filter { it.length >= 3 },
                favoriteWords = favoriteWordsText.split(',', '\n').map { it.trim() }.filter { it.length >= 3 },
            )
        }
        set(p) {
            vprefs.edit()
                .putString("fuelPrice", p.fuelPrice.toString())
                .putString("kmPerLiter", p.kmPerLiter.toString())
                .putString("otherCostPerKm", p.otherCostPerKm.toString())
                .putString("greenPerKm", p.greenPerKm.toString())
                .putString("yellowPerKm", p.yellowPerKm.toString())
                .putString("greenPerHour", p.greenPerHour.toString())
                .putString("yellowPerHour", p.yellowPerHour.toString())
                .putString("avgSpeedKmh", p.avgSpeedKmh.toString())
                .putString("maxPickupKm", p.maxPickupKm.toString())
                .putString("minPassengerRating", p.minPassengerRating.toString())
                .putString("greenStars", p.greenStars.toString())
                .putString("minPrice", p.minPrice.toString())
                .putString("minNet", p.minNet.toString())
                .putString("minNetPct", p.minNetPct.toString())
                .putString("maxTotalKm", p.maxTotalKm.toString())
                .putString("maxMinutes", p.maxMinutes.toString())
                .apply()
            setFuel(fuelType, Fuel(p.fuelPrice, p.kmPerLiter))   // o que vale é o combustível ativo
        }

    var targetsText: String
        get() = (if (isCar) DEFAULT_TARGETS_CAR else DEFAULT_TARGETS).let { def -> vprefs.getString("targets", def) ?: def }
        set(v) = vprefs.edit().putString("targets", v).apply()

    /**
     * Apps reconhecidos pelo NOME do pacote mesmo sem estar na lista
     * (ex.: Keeta, cujo pacote varia por país/versão).
     */
    private val builtInPatterns = com.farol.app.core.AppCatalog.namePatterns

    /** Nome amigável do app, ou null se o pacote não for monitorado. Aceita "*parte*=Nome" na lista. */
    fun labelFor(pkg: String): String? {
        val t = targets
        t[pkg]?.let { return it }
        for ((pattern, label) in t) {
            if (pattern.contains('*')) {
                val rx = Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
                if (rx.matches(pkg)) return label
            }
        }
        return if (isCar) null else builtInPatterns.firstOrNull { pkg.contains(it.first, ignoreCase = true) }?.second
    }

    /** Mapa pacote -> nome amigável. */
    val targets: Map<String, String>
        get() = targetsText.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
            val pkg = t.substringBefore('=').trim()
            val label = t.substringAfter('=', pkg).trim().ifEmpty { pkg }
            pkg to label
        }.toMap()

    /** Custos mensais da calculadora (Ajustes → Calcular custos do mês). */
    var monthlyCosts: com.farol.app.core.MonthlyCosts
        get() {
            val o = runCatching { org.json.JSONObject(vprefs.getString("monthlyCosts", "{}")!!) }.getOrNull() ?: org.json.JSONObject()
            val d = if (isCar) com.farol.app.core.MonthlyCosts(kmPerMonth = 4000.0, depreciation = 400.0, maintenance = 300.0,
                ipva = 150.0, insurance = 200.0, other = 100.0) else com.farol.app.core.MonthlyCosts()
            fun g(k: String, def: Double) = if (o.has(k)) o.optDouble(k, def) else def
            return com.farol.app.core.MonthlyCosts(g("km", d.kmPerMonth), o.optString("vehicle", d.vehicle), g("fin", d.financing),
                g("dep", d.depreciation), g("man", d.maintenance), g("ipva", d.ipva), g("seg", d.insurance), g("out", d.other))
        }
        set(c) = vprefs.edit().putString("monthlyCosts", org.json.JSONObject()
            .put("km", c.kmPerMonth).put("vehicle", c.vehicle).put("fin", c.financing).put("dep", c.depreciation)
            .put("man", c.maintenance).put("ipva", c.ipva).put("seg", c.insurance).put("out", c.other).toString()).apply()

    /** Bairros/ruas separados por vírgula ou linha. */
    var alertWordsText: String
        get() = vprefs.getString("alertWords", "") ?: ""
        set(v) = vprefs.edit().putString("alertWords", v).apply()

    var favoriteWordsText: String
        get() = vprefs.getString("favoriteWords", "") ?: ""
        set(v) = vprefs.edit().putString("favoriteWords", v).apply()

    /** Meta de ganhos do dia em R$ (0 = sem meta). */
    var dailyGoal: Double
        get() = vprefs.getString("dailyGoal", null)?.toDoubleOrNull() ?: 0.0
        set(v) = vprefs.edit().putString("dailyGoal", v.toString()).apply()

    /** Tamanho do cartão: 0 = P, 1 = M, 2 = G. */
    var cardSize: Int
        get() = prefs.getInt("cardSize", 1)
        set(v) = prefs.edit().putInt("cardSize", v.coerceIn(0, 2)).apply()
    val cardScale: Float get() = when (cardSize) { 0 -> 0.85f; 2 -> 1.2f; else -> 1f }

    /** Modo compacto: só a cor, o R$/km grande e o preço. */
    var cardCompact: Boolean
        get() = prefs.getBoolean("cardCompact", false)
        set(v) = prefs.edit().putBoolean("cardCompact", v).apply()

    /** Notificação fixa com o resumo do turno. */
    var shiftNotification: Boolean
        get() = prefs.getBoolean("shiftNotification", true)
        set(v) = prefs.edit().putBoolean("shiftNotification", v).apply()

    var weeklySummary: Boolean
        get() = prefs.getBoolean("weeklySummary", true)
        set(v) = prefs.edit().putBoolean("weeklySummary", v).apply()

    var lastWeeklySent: String
        get() = prefs.getString("lastWeeklySent", "") ?: ""
        set(v) = prefs.edit().putString("lastWeeklySent", v).apply()

    /** Usa a velocidade aprendida com seus pedidos em vez do valor fixo. */
    var autoSpeed: Boolean
        get() = prefs.getBoolean("autoSpeed", true)
        set(v) = prefs.edit().putBoolean("autoSpeed", v).apply()

    /** Amostras de velocidade de entrega de cada app (aceite → fim, ou tempo informado pelo app). */
    fun speedSamples(app: String): List<Double> {
        // Migração da 3.1: as amostras antigas eram todas do iFood
        if (app == "iFood" && !vprefs.contains("speedSamples_iFood") && vprefs.contains("speedSamples"))
            vprefs.edit().putString("speedSamples_iFood", vprefs.getString("speedSamples", "")).remove("speedSamples").apply()
        return (vprefs.getString("speedSamples_$app", "") ?: "").split(';').mapNotNull { it.toDoubleOrNull() }
    }

    fun setSpeedSamples(app: String, v: List<Double>) =
        vprefs.edit().putString("speedSamples_$app", v.joinToString(";") { String.format(java.util.Locale.US, "%.2f", it) }).apply()

    fun addSpeedSample(app: String, v: Double) = setSpeedSamples(app, com.farol.app.core.SpeedLearner.add(speedSamples(app), v))

    /** Velocidade aprendida do app (null se ainda tiver menos de 5 amostras dele). */
    fun learnedSpeed(app: String): Double? = com.farol.app.core.SpeedLearner.learned(speedSamples(app))

    /** Perfil para as ofertas de [app]: com a velocidade aprendida DESSE app, se houver; senão o valor fixo. */
    fun effectiveProfile(app: String): CostProfile {
        val p = profile
        val l = learnedSpeed(app)
        return if (autoSpeed && l != null) p.copy(avgSpeedKmh = l) else p
    }

    /** Mede km e velocidade pelo GPS durante o turno (só sugere; nada muda sem confirmar). */
    var gpsEnabled: Boolean
        get() = prefs.getBoolean("gpsEnabled", false)
        set(v) = prefs.edit().putBoolean("gpsEnabled", v).apply()

    /** Velocidades de entrega medidas pelo GPS por app (aguardando você tocar em "Usar"). */
    fun gpsSpeedSamples(app: String): List<Double> =
        (vprefs.getString("gpsSpeed_$app", "") ?: "").split(';').mapNotNull { it.toDoubleOrNull() }

    fun addGpsSpeedSample(app: String, v: Double) = vprefs.edit().putString("gpsSpeed_$app",
        com.farol.app.core.SpeedLearner.add(gpsSpeedSamples(app), v).joinToString(";") { String.format(java.util.Locale.US, "%.2f", it) }).apply()

    /** Nome do usuário (aparece na saudação do painel). Global, vale para Moto e Carro. */
    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(v) = prefs.edit().putString("user_name", v.trim().take(24)).apply()

    var nameAsked: Boolean
        get() = prefs.getBoolean("name_asked", false)
        set(v) = prefs.edit().putBoolean("name_asked", v).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    /** Último aviso de recusas lido de cada app (para mostrar no cartão). */
    fun setRejection(app: String, streak: Int?, paused: Boolean) {
        val e = prefs.edit().putLong("rej_at_$app", System.currentTimeMillis()).putBoolean("rej_paused_$app", paused)
        if (streak != null) e.putInt("rej_n_$app", streak)
        e.apply()
    }
    /** (recusas seguidas, pausada?) se o aviso for das últimas 3 horas. */
    fun rejection(app: String): Pair<Int, Boolean>? {
        val at = prefs.getLong("rej_at_$app", 0L)
        if (System.currentTimeMillis() - at > 3 * 3_600_000L) return null
        return prefs.getInt("rej_n_$app", 0) to prefs.getBoolean("rej_paused_$app", false)
    }

    var speak: Boolean
        get() = prefs.getBoolean("speak", false)
        set(v) = prefs.edit().putBoolean("speak", v).apply()

    // ---- envio de logs ----
    /** "off" ou "server" (GitHub/Webhook foram removidos na 5.4) */
    var remoteMode: String
        get() = prefs.getString("remoteMode", "off") ?: "off"
        set(v) = prefs.edit().putString("remoteMode", v).apply()

    /** Servidor próprio de logs (Cloudflare Worker). Sem chave no app. */
    var serverUrl: String
        get() = prefs.getString("serverUrl", "") ?: ""
        set(v) = prefs.edit().putString("serverUrl", v).apply()

    /**
     * Remove o que as versões antigas guardavam sem criptografia (token do GitHub, senha do webhook)
     * e passa quem usava GitHub/Webhook para o servidor. Roda toda abertura; é barato.
     */
    /**
     * Pacotes que a acessibilidade pode ler: os apps de corrida/entrega dos DOIS modos
     * (Moto e Carro), mais os nomes conhecidos do Keeta. Qualquer outro app fica de fora.
     */
    fun allowedPackages(): Set<String> {
        fun parse(text: String) = text.lines().mapNotNull { l -> l.substringBefore('=').trim().takeIf { it.contains('.') && !it.contains('*') } }
        val moto = appCtx.getSharedPreferences("farol", Context.MODE_PRIVATE).getString("targets", DEFAULT_TARGETS) ?: DEFAULT_TARGETS
        val car = appCtx.getSharedPreferences("farol_carro", Context.MODE_PRIVATE).getString("targets", DEFAULT_TARGETS_CAR) ?: DEFAULT_TARGETS_CAR
        return (parse(moto) + parse(car) + com.farol.app.core.AppCatalog.allPackages).toSet()
    }

    fun purgeLegacySecrets(loggedIn: Boolean) {
        val e = prefs.edit()
        listOf("ghToken", "ghRepo", "ghBranch", "webhookUrl", "webhookAuth").forEach { if (prefs.contains(it)) e.remove(it) }
        val m = prefs.getString("remoteMode", "off")
        if (m == "github" || m == "webhook") e.putString("remoteMode", if (loggedIn) "server" else "off")
        e.apply()
    }

    var remoteIntervalSec: Int
        get() = prefs.getInt("remoteIntervalSec", 60)
        set(v) = prefs.edit().putInt("remoteIntervalSec", v.coerceIn(20, 3600)).apply()

    var maskAddresses: Boolean
        get() = prefs.getBoolean("maskAddresses", true)
        set(v) = prefs.edit().putBoolean("maskAddresses", v).apply()

    var remoteLastStatus: String
        get() = prefs.getString("remoteLastStatus", "") ?: ""
        set(v) = prefs.edit().putString("remoteLastStatus", v).apply()

    /** Registra as telas dos apps depois de uma oferta (aceite, coleta, entrega, finalização). */
    var logScreens: Boolean
        get() = prefs.getBoolean("logScreens", true)
        set(v) = prefs.edit().putBoolean("logScreens", v).apply()

    /** Lê o "ganhos de hoje" do painel de cada app e preenche o dashboard sozinho. */
    var autoEarnings: Boolean
        get() = prefs.getBoolean("autoEarnings", true)
        set(v) = prefs.edit().putBoolean("autoEarnings", v).apply()

    /** Marca que o ganho de [app] em [date] veio do painel do app (para mostrar "auto" no dashboard). */
    fun markAuto(date: String, app: String) = vprefs.edit().putLong("auto_${date}_$app", System.currentTimeMillis()).apply()
    fun autoAt(date: String, app: String): Long = vprefs.getLong("auto_${date}_$app", 0L)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var autoHideSec: Int
        get() = prefs.getInt("autoHideSec", 15)
        set(v) = prefs.edit().putInt("autoHideSec", v.coerceIn(3, 120)).apply()

    var vibrateOnGreen: Boolean
        get() = prefs.getBoolean("vibrateOnGreen", true)
        set(v) = prefs.edit().putBoolean("vibrateOnGreen", v).apply()

    var ocrFallback: Boolean
        get() = prefs.getBoolean("ocrFallback", true)
        set(v) = prefs.edit().putBoolean("ocrFallback", v).apply()

    var requireHint: Boolean
        get() = prefs.getBoolean("requireHint", true)
        set(v) = prefs.edit().putBoolean("requireHint", v).apply()

    var captureMode: Boolean
        get() = prefs.getBoolean("captureMode", false)
        set(v) = prefs.edit().putBoolean("captureMode", v).apply()

    var cardX: Int
        get() = prefs.getInt("cardX", 24)
        set(v) = prefs.edit().putInt("cardX", v).apply()

    var cardY: Int
        get() = prefs.getInt("cardY", 260)
        set(v) = prefs.edit().putInt("cardY", v).apply()
}
