package com.farol.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.farol.app.core.CostProfile
import com.farol.app.core.Fmt
import com.farol.app.core.Rating
import com.farol.app.data.CaptureLog
import com.farol.app.data.History
import com.farol.app.data.RemoteLog
import com.farol.app.data.Settings
import com.farol.app.service.OfferReaderService

/** Ajustes do Farol: custos, semáforo, filtros, cartão, leitura, envio de logs, histórico e calibração. */
class SettingsActivity : Activity() {

    internal val self: SettingsActivity get() = this
    internal lateinit var settings: Settings
    internal lateinit var history: History
    internal lateinit var capture: CaptureLog
    internal lateinit var k: Kit
    internal lateinit var col: LinearLayout

    // status
    internal lateinit var statusBox: LinearLayout
    // custos
    internal lateinit var etFuel: EditText
    internal lateinit var etKml: EditText
    internal lateinit var etFuelE: EditText
    internal lateinit var etKmlE: EditText
    internal lateinit var segFuel: LinearLayout
    internal lateinit var tvFuelCmp: android.widget.TextView
    internal var activeFuel = "gasolina"
    internal lateinit var etOther: EditText
    internal lateinit var tvCost: TextView
    // semáforo
    internal lateinit var etGreenKm: EditText
    internal lateinit var etYellowKm: EditText
    internal lateinit var etGreenH: EditText
    internal lateinit var etYellowH: EditText
    internal lateinit var etSpeed: EditText
    // filtros
    internal lateinit var etMaxPickup: EditText
    internal lateinit var etMinRating: EditText
    internal lateinit var etGreenStars: EditText
    internal lateinit var etMinPrice: EditText
    internal lateinit var etMinNet: EditText
    internal lateinit var etMinNetPct: EditText
    internal lateinit var etMaxKm: EditText
    internal lateinit var etMaxMin: EditText
    internal lateinit var bandsBox: LinearLayout
    internal lateinit var etGoal: EditText
    internal lateinit var etFavorites: EditText
    internal lateinit var segSize: LinearLayout
    internal var cardSize = 1
    internal lateinit var swCompact: Switch
    internal lateinit var swShiftNotif: Switch
    internal lateinit var swWeekly: Switch
    internal lateinit var swAutoSpeed: Switch
    internal lateinit var swGps: Switch
    internal lateinit var tvSpeed: TextView
    internal lateinit var etAlerts: EditText
    // cartão / leitura
    internal lateinit var etHide: EditText
    internal lateinit var swEnabled: Switch
    internal lateinit var swVibrate: Switch
    internal lateinit var swSpeak: Switch
    internal lateinit var swOcr: Switch
    internal lateinit var swHint: Switch
    internal lateinit var swCapture: Switch
    internal lateinit var swScreens: Switch
    internal lateinit var swAutoEarn: Switch
    internal lateinit var etTargets: EditText
    internal lateinit var tvRecent: TextView
    // logs
    internal var remoteMode = "off"
    internal lateinit var segRemote: LinearLayout
    internal lateinit var boxServer: LinearLayout
    internal lateinit var etServer: EditText
    internal lateinit var boxRemoteCommon: LinearLayout
    internal lateinit var etInterval: EditText
    internal lateinit var swMask: Switch
    internal lateinit var tvRemote: TextView
    // histórico
    internal lateinit var tvHistSummary: TextView
    internal lateinit var boxHist: LinearLayout

    internal val remoteModes = listOf("off" to "Desligado", "server" to "Servidor")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Themes.apply(this)
        settings = Settings(this)
        history = History(this)
        capture = CaptureLog(this)
        k = Kit(this)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        buildUi()
        loadValues()
    }

    internal var firstResume = true
    override fun onResume() {
        super.onResume()
        if (!firstResume) loadValues()   // volta da calculadora de custos com valores novos
        firstResume = false
        refreshStatus()
    }

    /** Salva sozinho ao sair, se estiver tudo válido (sem avisos). */
    override fun onPause() { super.onPause(); save(silent = true) }

    // ============================== helpers de UI ==============================

    internal fun card(title: String, subtitle: String? = null): LinearLayout {
        val c = k.card()
        c.addView(k.text(title, 17, Palette.TEXT, k.bold))
        subtitle?.let { c.addView(k.text(it, 13, Palette.MUTED), k.lp(top = 4)) }
        col.addView(c, k.lp(top = 14))
        return c
    }

    internal fun hint(parent: LinearLayout, t: String) =
        parent.addView(k.text(t, 12, Palette.MUTED).apply { setLineSpacing(0f, 1.15f) }, k.lp(top = 10))

    /** Linha "rótulo .......... [campo] unidade". */
    internal fun numRow(parent: LinearLayout, label: String, unit: String, sub: String? = null): EditText {
        val f = k.field("0").apply {
            gravity = Gravity.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(k.dp(12), k.dp(10), k.dp(12), k.dp(10))
        }
        val left = k.vbox().apply {
            addView(k.text(label, 15, Palette.TEXT, k.medium))
            sub?.let { addView(k.text(it, 12, Palette.MUTED), k.lp(top = 2)) }
        }
        parent.addView(k.hbox().apply {
            addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            addView(f, LinearLayout.LayoutParams(k.dp(104), -2).apply { marginStart = k.dp(10) })
            addView(k.text(unit, 12, Palette.MUTED), LinearLayout.LayoutParams(k.dp(44), -2).apply { marginStart = k.dp(8) })
        }, k.lp(top = 12))
        return f
    }

    internal fun textField(hint: String, secret: Boolean = false, multi: Boolean = false, mono: Boolean = false) = EditText(this).apply {
        this.hint = hint
        setTextColor(Palette.TEXT); setHintTextColor(Palette.MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (mono) 13f else 15f)
        if (mono) typeface = Typeface.MONOSPACE
        inputType = when {
            secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        if (multi) { minLines = 2; gravity = Gravity.TOP or Gravity.START } else isSingleLine = true
        setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12))
        background = k.rounded(Palette.SURFACE2, 14, Palette.STROKE)
    }

    internal fun labeledText(parent: LinearLayout, label: String, f: EditText) {
        parent.addView(k.text(label, 12, Palette.MUTED, k.medium), k.lp(top = 12))
        parent.addView(f, k.lp(top = 6))
    }

    internal fun toggle(parent: LinearLayout, label: String, sub: String? = null): Switch {
        val sw = Switch(this).apply {
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Palette.ACCENT, 0xFFB0B7C3.toInt()))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Palette.alpha(Palette.ACCENT, 0x77), 0xFF3A424E.toInt()))
        }
        val left = k.vbox().apply {
            addView(k.text(label, 15, Palette.TEXT, k.medium))
            sub?.let { addView(k.text(it, 12, Palette.MUTED), k.lp(top = 2)) }
        }
        val row = k.hbox().apply {
            addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            addView(sw)
            isClickable = true
            setOnClickListener { sw.isChecked = !sw.isChecked }
        }
        parent.addView(row, k.lp(top = 14))
        return sw
    }

    internal fun buttons(parent: LinearLayout, vararg items: Pair<String, () -> Unit>, primaryFirst: Boolean = false) {
        val row = k.hbox()
        items.forEachIndexed { i, (label, action) ->
            val b = if (primaryFirst && i == 0) k.pill(label, onClick = action) else k.ghost(label, action)
            row.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = k.dp(8) })
        }
        parent.addView(row, k.lp(top = 14))
    }

    internal fun onChange(vararg fields: EditText, block: () -> Unit) = fields.forEach {
        it.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = block()
        })
    }

    // ============================== construção ==============================

    internal fun askChangePassword() {
        fun pf(h: String) = k.field(h).apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val a = pf("Senha atual"); val b = pf("Nova senha (mín. 8)"); val c = pf("Repita a nova senha")
        val box = k.vbox(22, 8).apply { addView(a); addView(b, k.lp(top = 10)); addView(c, k.lp(top = 10)) }
        val d = android.app.AlertDialog.Builder(this, com.farol.app.ui.Palette.dialogTheme)
            .setTitle("Trocar senha").setView(box).setPositiveButton("Trocar", null).setNegativeButton("Cancelar", null).create()
        d.setOnShowListener {
            d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nova = b.text.toString()
                when {
                    nova.length < 8 -> toast("A nova senha precisa ter 8 caracteres")
                    nova != c.text.toString() -> toast("As senhas novas não são iguais")
                    else -> Thread {
                        val r = runCatching { com.farol.app.data.Account.changePassword(this, a.text.toString(), nova) }
                        runOnUiThread {
                            r.onSuccess { toast("Senha trocada. Outros aparelhos vão pedir login de novo."); d.dismiss() }
                             .onFailure { toast(it.message ?: "Não deu certo") }
                        }
                    }.start()
                }
            }
        }
        d.show()
    }

    internal fun buildUi() {
        col = k.vbox().apply { setPadding(k.dp(18), k.dp(18), k.dp(18), k.dp(110)) }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Palette.BG); isVerticalScrollBarEnabled = false; addView(col)
        }

        // Barra superior
        val back = k.text("←", 20, Palette.TEXT, k.bold).apply {
            gravity = Gravity.CENTER
            background = k.ripple(k.rounded(Palette.SURFACE, 999, Palette.STROKE))
            isClickable = true; setOnClickListener { finish() }
        }
        col.addView(k.hbox().apply {
            addView(back, LinearLayout.LayoutParams(k.dp(44), k.dp(44)))
            addView(k.vbox().apply {
                addView(k.text("Ajustes · ${settings.modeLabel}", 22, Palette.TEXT, k.bold))
                addView(k.text("Seus números e como o cartão se comporta", 13, Palette.MUTED), k.lp(top = 2))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(14) })
        })

        // Status
        statusBox = k.card().also { col.addView(it, k.lp(top = 18)) }

        buildCostSections()
        buildReadingSections()
        buildAccountSections()

        // Raiz com botão Salvar fixo embaixo
        val save = k.pill("Salvar alterações") { save() }.apply { elevation = k.dp(8).toFloat() }
        val saveLp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(k.dp(18), 0, k.dp(18), k.dp(16)) }
        // Faixa escura atrás do botão, para a lista não aparecer por baixo dele
        val saveBar = FrameLayout(this).apply {
            setBackgroundColor(Palette.alpha(Palette.BG, 0xF2))
            addView(save, saveLp)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Palette.BG)
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(saveBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        setContentView(root)
        scroll.clipToPadding = false
        k.fitSystemBars(root) { _, bottom ->
            saveBar.setPadding(0, k.dp(12), 0, bottom)
            col.setPadding(col.paddingLeft, col.paddingTop, col.paddingRight, k.dp(110) + bottom)
        }
    }

    internal fun renderSegment() {
        segRemote.removeAllViews()
        remoteModes.forEach { (mode, label) ->
            val active = mode == remoteMode
            segRemote.addView(k.text(label, 13, if (active) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
                gravity = Gravity.CENTER
                setPadding(0, k.dp(9), 0, k.dp(9))
                if (active) background = k.rounded(Palette.ACCENT, 10)
                isClickable = true
                setOnClickListener { remoteMode = mode; renderSegment() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        boxServer.visibility = if (remoteMode == "server") View.VISIBLE else View.GONE
        boxRemoteCommon.visibility = if (remoteMode == "off") View.GONE else View.VISIBLE
    }

    // ============================== dados ==============================

    internal fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString().replace('.', ',')
    internal fun EditText.num(def: Double) = text.toString().trim().replace(',', '.').toDoubleOrNull() ?: def

    internal fun loadValues() {
        val p = settings.profile
        activeFuel = settings.fuelType
        settings.fuel("gasolina").let { etFuel.setText(fmt(it.price)); etKml.setText(fmt(it.kml)) }
        settings.fuel("etanol").let { etFuelE.setText(fmt(it.price)); etKmlE.setText(fmt(it.kml)) }
        etOther.setText(fmt(p.otherCostPerKm))
        renderFuel()
        etGreenKm.setText(fmt(p.greenPerKm)); etYellowKm.setText(fmt(p.yellowPerKm))
        etGreenH.setText(fmt(p.greenPerHour)); etYellowH.setText(fmt(p.yellowPerHour))
        etSpeed.setText(fmt(p.avgSpeedKmh))
        etMaxPickup.setText(fmt(p.maxPickupKm)); etMinRating.setText(fmt(p.minPassengerRating))
        etGreenStars.setText(fmt(p.greenStars)); etMinPrice.setText(fmt(p.minPrice)); etMinNet.setText(fmt(p.minNet))
        etMinNetPct.setText(fmt(p.minNetPct)); etMaxKm.setText(fmt(p.maxTotalKm)); etMaxMin.setText(fmt(p.maxMinutes))
        etAlerts.setText(settings.alertWordsText)
        etFavorites.setText(settings.favoriteWordsText)
        etGoal.setText(fmt(settings.dailyGoal))
        cardSize = settings.cardSize; renderSize()
        swCompact.isChecked = settings.cardCompact
        swShiftNotif.isChecked = settings.shiftNotification
        swWeekly.isChecked = settings.weeklySummary
        swAutoSpeed.isChecked = settings.autoSpeed
        swGps.isChecked = settings.gpsEnabled
        tvSpeed.text = listOf("Keeta", "99", "iFood").joinToString("\n") { app ->
            val n = settings.speedSamples(app).size
            val l = settings.learnedSpeed(app)
            when {
                app == "iFood" -> "iFood: o app mostra o tempo" + (l?.let { " (média real ${Fmt.dec(it, 1)} km/h)" } ?: "")
                l != null -> "$app: ${Fmt.dec(l, 1)} km/h aprendida ($n pedidos)"
                else -> "$app: usando o valor acima ($n de ${com.farol.app.core.SpeedLearner.MIN_SAMPLES} pedidos medidos)"
            }
        }
        etHide.setText(settings.autoHideSec.toString())
        etTargets.setText(settings.targetsText)
        swEnabled.isChecked = settings.enabled
        swVibrate.isChecked = settings.vibrateOnGreen
        swSpeak.isChecked = settings.speak
        swOcr.isChecked = settings.ocrFallback
        swHint.isChecked = settings.requireHint
        swCapture.isChecked = settings.captureMode
        swScreens.isChecked = settings.logScreens
        swAutoEarn.isChecked = settings.autoEarnings
        remoteMode = settings.remoteMode
        etServer.setText(settings.serverUrl)
        etInterval.setText(settings.remoteIntervalSec.toString())
        swMask.isChecked = settings.maskAddresses
        renderSegment()
        showCost()
        renderBands()
    }

    internal fun currentProfile(): CostProfile {
        val d = CostProfile()
        return CostProfile(
            fuelPrice = if (activeFuel == "etanol") etFuelE.num(3.89) else etFuel.num(d.fuelPrice),
            kmPerLiter = if (activeFuel == "etanol") etKmlE.num(7.0) else etKml.num(d.kmPerLiter),
            otherCostPerKm = etOther.num(d.otherCostPerKm),
            greenPerKm = etGreenKm.num(d.greenPerKm),
            yellowPerKm = etYellowKm.num(d.yellowPerKm),
            greenPerHour = etGreenH.num(d.greenPerHour),
            yellowPerHour = etYellowH.num(d.yellowPerHour),
            avgSpeedKmh = etSpeed.num(d.avgSpeedKmh),
            maxPickupKm = etMaxPickup.num(0.0),
            minPassengerRating = etMinRating.num(0.0),
            greenStars = etGreenStars.num(0.0),
            minPrice = etMinPrice.num(0.0),
            minNet = etMinNet.num(0.0),
            minNetPct = etMinNetPct.num(0.0),
            maxTotalKm = etMaxKm.num(0.0),
            maxMinutes = etMaxMin.num(0.0),
        )
    }

    /** Retorna o motivo se algo estiver errado, ou null se estiver tudo certo. */
    internal fun problem(p: CostProfile): String? = when {
        p.greenPerKm > 20 || p.yellowPerKm > 20 -> "Meta por km muito alta. Use vírgula: 1,80 e 1,40"
        p.otherCostPerKm > 5 || p.kmPerLiter > 120 || p.fuelPrice > 30 -> "Confira os custos: use vírgula (ex.: 0,15)"
        p.yellowPerKm > p.greenPerKm || p.yellowPerHour > p.greenPerHour -> "O amarelo precisa ser menor que o verde"
        p.greenStars > 5 || p.minPassengerRating > 5 -> "A nota vai até 5 (ex.: 4,90)"
        p.greenStars > 0 && p.minPassengerRating > p.greenStars -> "Na nota, 'atenção' precisa ser menor que 'aceitar'"
        else -> null
    }

    internal fun save(silent: Boolean = false) {
        val p = currentProfile()
        problem(p)?.let { if (!silent) toast(it); return }
        settings.alertWordsText = etAlerts.text.toString()  // antes do profile: profile lê as palavras
        settings.favoriteWordsText = etFavorites.text.toString()
        settings.dailyGoal = etGoal.num(0.0).coerceAtLeast(0.0)
        settings.cardSize = cardSize
        settings.cardCompact = swCompact.isChecked
        settings.shiftNotification = swShiftNotif.isChecked
        settings.weeklySummary = swWeekly.isChecked
        settings.autoSpeed = swAutoSpeed.isChecked
        val gpsWas = settings.gpsEnabled
        settings.gpsEnabled = swGps.isChecked
        if (gpsWas && !swGps.isChecked) com.farol.app.service.GpsService.stop(this)
        settings.fuelType = activeFuel
        settings.setFuel("gasolina", Settings.Fuel(etFuel.num(6.0), etKml.num(10.0)))
        settings.setFuel("etanol", Settings.Fuel(etFuelE.num(3.89), etKmlE.num(7.0)))
        settings.profile = p
        settings.autoHideSec = etHide.num(15.0).toInt()
        settings.targetsText = etTargets.text.toString()
        com.farol.app.service.OfferReaderService.instance?.applyPackageFilter()
        settings.enabled = swEnabled.isChecked
        settings.vibrateOnGreen = swVibrate.isChecked
        settings.speak = swSpeak.isChecked
        settings.ocrFallback = swOcr.isChecked
        settings.requireHint = swHint.isChecked
        settings.captureMode = swCapture.isChecked
        settings.logScreens = swScreens.isChecked
        settings.autoEarnings = swAutoEarn.isChecked
        settings.remoteMode = remoteMode
        settings.serverUrl = etServer.text.toString().trim()
        settings.remoteIntervalSec = etInterval.num(60.0).toInt()
        settings.maskAddresses = swMask.isChecked
        runCatching { RemoteLog.get(this).settingsChanged(if (silent) "auto" else "salvar") }
        if (!silent) toast("Salvo ✓")
    }

    internal fun renderFuel() {
        segFuel.removeAllViews()
        listOf("gasolina" to "⛽ Gasolina", "etanol" to "🌱 Etanol").forEach { (type, label) ->
            val on = type == activeFuel
            segFuel.addView(k.text(label, 14, if (on) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
                gravity = Gravity.CENTER; setPadding(0, k.dp(9), 0, k.dp(9))
                if (on) background = k.rounded(Palette.ACCENT, 10)
                isClickable = true; setOnClickListener { activeFuel = type; renderFuel(); showCost() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    internal fun showCost() {
        val p = currentProfile()
        tvCost.text = "Seu custo: ${Fmt.money(p.costPerKm)}/km   (combustível ${Fmt.money(p.fuelCostPerKm)} + outros ${Fmt.money(p.otherCostPerKm)})"
        // Qual compensa: compara o custo por km de cada um (o consumo real de cada carro manda, não só a regra dos 70%)
        val gp = etFuel.num(0.0); val gk = etKml.num(0.0); val ep = etFuelE.num(0.0); val ek = etKmlE.num(0.0)
        if (gp > 0 && gk > 0 && ep > 0 && ek > 0) {
            val g = gp / gk; val e = ep / ek
            val best = if (e < g) "🌱 etanol" else "⛽ gasolina"
            val pct = Math.round(ep / gp * 100)
            val diff = Math.abs(g - e) * 1000   // em 1.000 km
            var txt = "Gasolina ${Fmt.money(g)}/km · Etanol ${Fmt.money(e)}/km\nCompensa $best: economia de ${Fmt.money(diff)} a cada 1.000 km (etanol a $pct% da gasolina)."
            if (ek > gk * 0.85) txt += "\n⚠️ Consumo do etanol parece alto: costuma ser uns 70% do da gasolina (${fmt(Math.round(gk * 0.7 * 10) / 10.0)} km/L)."
            if ((best.contains("etanol")) != (activeFuel == "etanol")) txt += "\n💡 Na próxima, abastecer com $best sai mais barato."
            tvFuelCmp.text = txt
        } else tvFuelCmp.text = "Preencha preço e consumo dos dois para comparar."
    }

    internal fun renderSize() {
        segSize.removeAllViews()
        listOf("P", "M", "G").forEachIndexed { i, label ->
            val on = i == cardSize
            segSize.addView(k.text(label, 14, if (on) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
                gravity = Gravity.CENTER; setPadding(0, k.dp(9), 0, k.dp(9))
                if (on) background = k.rounded(Palette.ACCENT, 10)
                isClickable = true; setOnClickListener { cardSize = i; renderSize() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    companion object { internal const val REQ_EXPORT = 31; internal const val REQ_IMPORT = 32; internal const val REQ_MYDATA = 33 }

    @Deprecated("startActivityForResult é suficiente aqui (sem AndroidX)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        runCatching {
            when (requestCode) {
                REQ_EXPORT -> {
                    contentResolver.openOutputStream(uri)?.use { it.write(com.farol.app.data.Backup.export(this).toByteArray()) }
                    toast("Backup salvo ✓")
                }
                REQ_MYDATA -> {
                    toast("Baixando seus dados…")
                    Thread {
                        val r = runCatching {
                            val json = com.farol.app.data.Account.exportData(this)
                            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        }
                        runOnUiThread { toast(if (r.isSuccess) "Seus dados foram salvos ✓" else "Não deu: ${r.exceptionOrNull()?.message}") }
                    }.start()
                }
                REQ_IMPORT -> {
                    val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
                    confirm("Restaurar este backup? Os dados atuais do Farol serão substituídos.") {
                        runCatching { com.farol.app.data.Backup.restore(this, json) }
                            .onSuccess { msg -> toast(msg); loadValues(); refreshStatus() }
                            .onFailure { toast("Não deu: ${it.message}") }
                    }
                }
            }
        }.onFailure { toast("Erro: ${it.message}") }
    }

    /** Tabela "Suas faixas": Ruim | Atenção | Aceitar para R$/km, R$/h e nota. */
    internal fun renderBands() {
        if (!::bandsBox.isInitialized) return
        val p = currentProfile()
        bandsBox.removeAllViews()
        fun n(v: Double, d: Int = 2) = Fmt.dec(v, d)
        val colW = LinearLayout.LayoutParams(0, -1, 1f)
        fun cell(text: String, bg: Int, color: Int = Palette.TEXT, size: Number = 14, bold: Boolean = true) =
            k.text(text, size, color, if (bold) k.bold else k.medium).apply {
                gravity = Gravity.CENTER
                setPadding(k.dp(4), k.dp(12), k.dp(4), k.dp(12))
                setBackgroundColor(bg)
            }
        val red = Palette.alpha(Palette.RED, 0x22); val yel = Palette.alpha(Palette.YELLOW, 0x22); val grn = Palette.alpha(Palette.GREEN, 0x22)
        val table = k.vbox().apply {
            background = k.rounded(Palette.SURFACE2, 16, Palette.STROKE)
            clipToOutline = true
        }
        table.addView(k.hbox().apply {
            addView(cell("Faixa", Palette.SURFACE2, Palette.MUTED, 12, false), LinearLayout.LayoutParams(0, -1, 1.15f))
            addView(cell("● Ruim", red, Palette.RED, 13), colW)
            addView(cell("● Atenção", yel, Palette.YELLOW, 13), LinearLayout.LayoutParams(0, -1, 1f))
            addView(cell("● Aceitar", grn, Palette.GREEN, 13), LinearLayout.LayoutParams(0, -1, 1f))
        })
        fun line(label: String, sub: String, a: String, b: String, c: String) {
            table.addView(View(this).apply { setBackgroundColor(Palette.STROKE) }, LinearLayout.LayoutParams(-1, k.dp(1)))
            table.addView(k.hbox().apply {
                addView(k.vbox().apply {
                    gravity = Gravity.CENTER; setPadding(k.dp(6), k.dp(10), k.dp(6), k.dp(10))
                    addView(k.text(label, 14, Palette.TEXT, k.bold).apply { gravity = Gravity.CENTER })
                    addView(k.text(sub, 11, Palette.MUTED).apply { gravity = Gravity.CENTER }, k.lp(top = 2))
                }, LinearLayout.LayoutParams(0, -1, 1.15f))
                addView(cell(a, red), LinearLayout.LayoutParams(0, -1, 1f))
                addView(cell(b, yel, Palette.TEXT, if (b.length > 9) 12 else 14), LinearLayout.LayoutParams(0, -1, 1f))
                addView(cell(c, grn), LinearLayout.LayoutParams(0, -1, 1f))
            })
        }
        line("R$/km", "por km", "< ${n(p.yellowPerKm)}", "${n(p.yellowPerKm)} – ${n(p.greenPerKm)}", "≥ ${n(p.greenPerKm)}")
        line("R$/hora", "por hora", "< ${n(p.yellowPerHour, 0)}", "${n(p.yellowPerHour, 0)} – ${n(p.greenPerHour, 0)}", "≥ ${n(p.greenPerHour, 0)}")
        when {
            p.greenStars > 0 && p.minPassengerRating > 0 ->
                line("Nota", "passageiro", "< ${n(p.minPassengerRating)}", "${n(p.minPassengerRating)} – ${n(p.greenStars)}", "≥ ${n(p.greenStars)}")
            p.minPassengerRating > 0 -> line("Nota", "passageiro", "< ${n(p.minPassengerRating)}", "—", "≥ ${n(p.minPassengerRating)}")
            p.greenStars > 0 -> line("Nota", "passageiro", "—", "< ${n(p.greenStars)}", "≥ ${n(p.greenStars)}")
            else -> line("Nota", "passageiro", "—", "não usa", "—")
        }
        bandsBox.addView(table)
    }

    internal fun applyPreset(kind: String) {
        val (kml, other, gKm, yKm, gH, yH, speed) = when (kind) {
            "moto" -> listOf(30.0, 0.15, 1.80, 1.40, 30.0, 20.0, 25.0)
            "carro" -> listOf(11.0, 0.30, 2.00, 1.50, 45.0, 30.0, 25.0)
            else -> listOf(0.0, 0.05, 1.50, 1.00, 20.0, 14.0, 14.0)
        }
        if (kind == "bike") { etFuel.setText("0"); etKml.setText("1"); etFuelE.setText("0"); etKmlE.setText("1") }
        else {
            val gas = if (kind == "carro") 10.0 else kml
            etKml.setText(fmt(gas)); etKmlE.setText(fmt(Math.round(gas * 0.7 * 10) / 10.0))
        }
        etOther.setText(fmt(other)); etGreenKm.setText(fmt(gKm)); etYellowKm.setText(fmt(yKm))
        etGreenH.setText(fmt(gH)); etYellowH.setText(fmt(yH)); etSpeed.setText(fmt(speed))
        showCost()
        toast("Valores típicos de ${kind}. Ajuste e salve.")
    }

    private operator fun <T> List<T>.component6() = this[5]
    private operator fun <T> List<T>.component7() = this[6]

    internal fun isServiceEnabled(): Boolean {
        val enabled = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, OfferReaderService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    internal fun refreshStatus() {
        // Status
        val on = isServiceEnabled() && OfferReaderService.instance != null
        val color = if (on) Palette.GREEN else Palette.RED
        statusBox.removeAllViews()
        statusBox.addView(k.hbox().apply {
            addView(k.bubble(if (on) "🚦" else "⚠️", color))
            addView(k.vbox().apply {
                addView(k.text(if (on) "Leitor ativo" else "Leitor desligado", 16, color, k.bold))
                addView(k.text(if (on) "O cartão aparece sozinho nas ofertas"
                    else "Ative 'Farol' em Acessibilidade → Apps instalados", 12, Palette.MUTED), k.lp(top = 2))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(12) })
        })
        if (!on) buttons(statusBox,
            "Ativar leitor" to { startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }, primaryFirst = true)

        // Pacotes vistos
        val recent = synchronized(OfferReaderService.recentPackages) { OfferReaderService.recentPackages.toList() }
        tvRecent.text = if (recent.isEmpty()) "Apps vistos recentemente: (nenhum ainda)"
        else "Apps vistos recentemente: " + recent.joinToString(", ")

        // Logs
        val pend = RemoteLog.get(this).pendingCount()
        tvRemote.text = "Na fila: $pend eventos" +
            (settings.remoteLastStatus.takeIf { it.isNotEmpty() }?.let { "\nÚltimo envio: $it" } ?: "")

        // Histórico
        tvHistSummary.text = history.todaySummary()
        boxHist.removeAllViews()
        history.recent(15).forEach { o ->
            val c = when (o.optString("rating")) { "GREEN" -> Palette.GREEN; "YELLOW" -> Palette.YELLOW; else -> Palette.RED }
            boxHist.addView(k.hbox().apply {
                addView(k.dot(c, 8))
                addView(k.text(history.format(o).drop(2).trim(), 12, Palette.MUTED),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(10) })
            }, k.lp(top = 8))
        }
    }

    internal fun test(r: Rating) {
        save(silent = true)
        val svc = OfferReaderService.instance
        if (svc == null) toast("Ative o leitor primeiro") else svc.showTest(r)
    }

    internal fun copyCapture() {
        val txt = capture.read()
        if (txt.isEmpty()) { toast("Captura vazia. Ligue o Modo captura e abra uma oferta."); return }
        copyText("farol", txt, "Copiado (${txt.length} caracteres)")
    }

    internal fun copyText(label: String, txt: String, msg: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, txt))
        toast(msg)
    }

    internal fun showCapture() {
        val txt = capture.read().takeLast(6000).ifEmpty { "(vazia)" }
        AlertDialog.Builder(this, com.farol.app.ui.Palette.dialogTheme)
            .setTitle("Últimas leituras")
            .setView(ScrollView(this).apply {
                addView(k.text(txt, 11, Palette.TEXT).apply {
                    setTextIsSelectable(true); typeface = Typeface.MONOSPACE
                    setPadding(k.dp(18), k.dp(8), k.dp(18), k.dp(8))
                })
            })
            .setPositiveButton("Fechar", null).show()
    }

    internal fun confirm(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this, com.farol.app.ui.Palette.dialogTheme)
            .setMessage(msg).setPositiveButton("Sim") { _, _ -> onYes() }.setNegativeButton("Cancelar", null).show()
    }

    internal fun flushRemote() {
        tvRemote.text = "Enviando..."
        RemoteLog.get(this).flushNow { status -> runOnUiThread { toast(status); refreshStatus() } }
    }

    internal fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
