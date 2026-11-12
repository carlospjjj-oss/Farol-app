package com.farol.app.data

import android.content.Context
import android.content.SharedPreferences
import com.farol.app.core.CostProfile

/** Configurações persistidas (SharedPreferences). Lidas pelo serviço e pela tela principal. */
class Settings(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("farol", Context.MODE_PRIVATE)

    companion object {
        /** pacote=Nome. Edite na tela do app se algum pacote for diferente no seu aparelho. */
        const val DEFAULT_TARGETS = """com.ubercab.driver=Uber
com.app99.driver=99
sinet.startup.inDriver=inDrive
br.com.ifood.driver.app=iFood
com.sankuai.sailor.courier=Keeta"""
    }

    private fun d(key: String, def: Double) = prefs.getString(key, null)?.toDoubleOrNull() ?: def

    var profile: CostProfile
        get() {
            val def = CostProfile()
            return CostProfile(
                fuelPrice = d("fuelPrice", def.fuelPrice),
                kmPerLiter = d("kmPerLiter", def.kmPerLiter),
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
            )
        }
        set(p) {
            prefs.edit()
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
        }

    var targetsText: String
        get() = prefs.getString("targets", DEFAULT_TARGETS) ?: DEFAULT_TARGETS
        set(v) = prefs.edit().putString("targets", v).apply()

    /**
     * Apps reconhecidos pelo NOME do pacote mesmo sem estar na lista
     * (ex.: Keeta, cujo pacote varia por país/versão).
     */
    private val builtInPatterns = listOf("keeta" to "Keeta", "sankuai" to "Keeta")

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
        return builtInPatterns.firstOrNull { pkg.contains(it.first, ignoreCase = true) }?.second
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
            val o = runCatching { org.json.JSONObject(prefs.getString("monthlyCosts", "{}")!!) }.getOrNull() ?: org.json.JSONObject()
            val d = com.farol.app.core.MonthlyCosts()
            fun g(k: String, def: Double) = if (o.has(k)) o.optDouble(k, def) else def
            return com.farol.app.core.MonthlyCosts(g("km", d.kmPerMonth), o.optString("vehicle", d.vehicle), g("fin", d.financing),
                g("dep", d.depreciation), g("man", d.maintenance), g("ipva", d.ipva), g("seg", d.insurance), g("out", d.other))
        }
        set(c) = prefs.edit().putString("monthlyCosts", org.json.JSONObject()
            .put("km", c.kmPerMonth).put("vehicle", c.vehicle).put("fin", c.financing).put("dep", c.depreciation)
            .put("man", c.maintenance).put("ipva", c.ipva).put("seg", c.insurance).put("out", c.other).toString()).apply()

    /** Bairros/ruas separados por vírgula ou linha. */
    var alertWordsText: String
        get() = prefs.getString("alertWords", "") ?: ""
        set(v) = prefs.edit().putString("alertWords", v).apply()

    var speak: Boolean
        get() = prefs.getBoolean("speak", false)
        set(v) = prefs.edit().putBoolean("speak", v).apply()

    // ---- envio de logs ----
    /** "off", "github" ou "webhook" */
    var remoteMode: String
        get() = prefs.getString("remoteMode", "off") ?: "off"
        set(v) = prefs.edit().putString("remoteMode", v).apply()

    var ghRepo: String
        get() = prefs.getString("ghRepo", "carlospjjj-oss/Farol") ?: ""
        set(v) = prefs.edit().putString("ghRepo", v).apply()

    var ghToken: String
        get() = prefs.getString("ghToken", "") ?: ""
        set(v) = prefs.edit().putString("ghToken", v).apply()

    var ghBranch: String
        get() = prefs.getString("ghBranch", "main") ?: "main"
        set(v) = prefs.edit().putString("ghBranch", v).apply()

    var webhookUrl: String
        get() = prefs.getString("webhookUrl", "") ?: ""
        set(v) = prefs.edit().putString("webhookUrl", v).apply()

    var webhookAuth: String
        get() = prefs.getString("webhookAuth", "") ?: ""
        set(v) = prefs.edit().putString("webhookAuth", v).apply()

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
    fun markAuto(date: String, app: String) = prefs.edit().putLong("auto_${date}_$app", System.currentTimeMillis()).apply()
    fun autoAt(date: String, app: String): Long = prefs.getLong("auto_${date}_$app", 0L)

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
