package com.farol.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.util.TypedValue
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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

/** Tela única: status, seus custos e metas, opções de leitura, histórico e captura. */
class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var history: History
    private lateinit var capture: CaptureLog
    private lateinit var col: LinearLayout

    private lateinit var tvStatus: TextView
    private lateinit var tvCost: TextView
    private lateinit var tvHistory: TextView
    private lateinit var tvRecent: TextView

    private lateinit var etFuel: EditText
    private lateinit var etKml: EditText
    private lateinit var etOther: EditText
    private lateinit var etGreenKm: EditText
    private lateinit var etYellowKm: EditText
    private lateinit var etGreenH: EditText
    private lateinit var etYellowH: EditText
    private lateinit var etSpeed: EditText
    private lateinit var etMaxPickup: EditText
    private lateinit var etMinRating: EditText
    private lateinit var etAlerts: EditText
    private lateinit var cbSpeak: CheckBox
    private lateinit var etHide: EditText
    private lateinit var etTargets: EditText
    private lateinit var cbEnabled: CheckBox
    private lateinit var cbVibrate: CheckBox
    private lateinit var cbOcr: CheckBox
    private lateinit var cbHint: CheckBox
    private lateinit var cbCapture: CheckBox
    private lateinit var rgRemote: android.widget.RadioGroup
    private lateinit var etGhRepo: EditText
    private lateinit var etGhToken: EditText
    private lateinit var etGhBranch: EditText
    private lateinit var etHookUrl: EditText
    private lateinit var etHookAuth: EditText
    private lateinit var etInterval: EditText
    private lateinit var cbMask: CheckBox
    private lateinit var tvRemote: TextView
    private val remoteModes = listOf("off" to "Desligado", "github" to "GitHub (recomendado)", "webhook" to "Webhook")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        history = History(this)
        capture = CaptureLog(this)
        buildUi()
        loadValues()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ---------- UI ----------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun section(title: String) = col.addView(TextView(this).apply {
        text = title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(20), 0, dp(6))
    })

    private fun note(t: String) = TextView(this).apply {
        text = t; alpha = 0.75f; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    }.also { col.addView(it) }

    private fun number(label: String): EditText {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(TextView(this).apply { text = label }, LinearLayout.LayoutParams(0, -2, 1.4f))
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSelectAllOnFocus(true)
        }
        row.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(row)
        return et
    }

    private fun text(label: String, secret: Boolean = false): EditText {
        col.addView(TextView(this).apply { this.text = label; setPadding(0, dp(6), 0, 0) })
        return EditText(this).apply {
            isSingleLine = true
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }.also { col.addView(it) }
    }

    private fun check(label: String) = CheckBox(this).apply { text = label }.also { col.addView(it) }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { onClick() }
    }.also { col.addView(it) }

    private fun buttonRow(vararg items: Pair<String, () -> Unit>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { (label, action) ->
            row.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } },
                LinearLayout.LayoutParams(0, -2, 1f))
        }
        col.addView(row)
    }

    private fun buildUi() {
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(40))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        col.addView(TextView(this).apply {
            text = "🚦 Farol"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f); typeface = Typeface.DEFAULT_BOLD
        })
        tvStatus = TextView(this).apply { setPadding(0, dp(8), 0, dp(4)) }.also { col.addView(it) }
        button("Abrir configurações de Acessibilidade") {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Liberar 'configurações restritas' (Android 13+)") {
            startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
            Toast.makeText(this, "Toque nos ⋮ no canto superior → 'Permitir configurações restritas'", Toast.LENGTH_LONG).show()
        }
        cbEnabled = check("Farol ligado")

        section("Seus custos")
        etFuel = number("Combustível (R$/litro)")
        etKml = number("Consumo (km/litro)")
        etOther = number("Outros custos (R$/km)")
        note("Outros = manutenção, pneus, óleo, depreciação, seguro, aluguel do carro dividido pelos km do mês.")
        tvCost = TextView(this).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(6), 0, 0) }
            .also { col.addView(it) }

        section("Metas do semáforo")
        etGreenKm = number("🟢 Verde a partir de (R$/km)")
        etYellowKm = number("🟡 Amarelo a partir de (R$/km)")
        etGreenH = number("🟢 Verde a partir de (R$/h)")
        etYellowH = number("🟡 Amarelo a partir de (R$/h)")
        etSpeed = number("Velocidade média (km/h)")
        note("A cor final é a pior entre R$/km e R$/h. A velocidade média só é usada quando a oferta não mostra o tempo (aparece '~' no cartão).")

        section("Filtros (deixam o cartão vermelho)")
        etMaxPickup = number("Busca máxima (km, 0 = sem limite)")
        etMinRating = number("Nota mínima do passageiro (0 = sem)")
        note("Bairros/ruas de alerta, separados por vírgula (acento e maiúscula não importam):")
        etAlerts = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2; hint = "ex.: Jardim Exemplo, Vila Tal"
        }.also { col.addView(it) }

        section("Cartão e leitura")
        etHide = number("Esconder cartão após (s)")
        cbVibrate = check("Vibrar quando a oferta for verde")
        cbSpeak = check("Falar o resumo da oferta (voz)")
        cbOcr = check("OCR de reserva (Android 11+) quando o texto não puder ser lido")
        cbHint = check("Só ler quando houver botão de aceitar na tela (recomendado)")
        cbCapture = check("Modo captura (grava o texto lido, para calibrar)")

        section("Apps monitorados")
        note("Um por linha: pacote=Nome. Se um app não for detectado, abra-o, volte aqui e veja 'Pacotes vistos' abaixo.")
        etTargets = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 4; typeface = Typeface.MONOSPACE; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }.also { col.addView(it) }
        tvRecent = note("")

        button("💾 Salvar") { save() }
        note("Testar o cartão (o serviço precisa estar ativo):")
        buttonRow(
            "🟢" to { test(Rating.GREEN) },
            "🟡" to { test(Rating.YELLOW) },
            "🔴" to { test(Rating.RED) },
        )

        section("Histórico")
        tvHistory = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }.also { col.addView(it) }
        buttonRow(
            "Atualizar" to { refreshStatus() },
            "Copiar CSV" to { copyText("farol.csv", history.toCsv(), "Histórico copiado — cole numa planilha") },
            "Limpar" to { history.clear(); refreshStatus() },
        )

        section("Envio de logs em tempo real")
        note("Envia o que o Farol lê nas ofertas (reconhecidas e não reconhecidas) para análise e calibração. Vai em lotes; sem sinal, fica guardado e sai depois.")
        rgRemote = android.widget.RadioGroup(this).apply {
            remoteModes.forEachIndexed { i, (_, label) ->
                addView(android.widget.RadioButton(this@MainActivity).apply { id = 1000 + i; text = label })
            }
        }.also { col.addView(it) }
        etGhRepo = text("Repositório (dono/nome)")
        etGhToken = text("Token do GitHub", secret = true)
        etGhBranch = text("Branch")
        note("Token: github.com → Settings → Developer settings → Fine-grained tokens → só o repositório Farol → Contents: Read and write.")
        etHookUrl = text("URL do webhook")
        etHookAuth = text("Authorization (opcional)", secret = true)
        etInterval = number("Enviar a cada (s)")
        cbMask = check("Mascarar endereços, telefones e CEP (recomendado)")
        tvRemote = TextView(this).apply { setPadding(0, dp(6), 0, 0) }.also { col.addView(it) }
        buttonRow(
            "Enviar teste" to { save(); RemoteLog.get(this).test(); flushRemote() },
            "Enviar agora" to { save(); flushRemote() },
        )

        section("Captura (calibração)")
        buttonRow(
            "Copiar captura" to { copyCapture() },
            "Ver" to { showCapture() },
            "Limpar" to { capture.clear(); toast("Captura apagada") },
        )
    }

    // ---------- dados ----------

    private fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString().replace('.', ',')
    private fun EditText.num(def: Double) = text.toString().trim().replace(',', '.').toDoubleOrNull() ?: def

    private fun loadValues() {
        val p = settings.profile
        etFuel.setText(fmt(p.fuelPrice)); etKml.setText(fmt(p.kmPerLiter)); etOther.setText(fmt(p.otherCostPerKm))
        etGreenKm.setText(fmt(p.greenPerKm)); etYellowKm.setText(fmt(p.yellowPerKm))
        etGreenH.setText(fmt(p.greenPerHour)); etYellowH.setText(fmt(p.yellowPerHour))
        etSpeed.setText(fmt(p.avgSpeedKmh))
        etMaxPickup.setText(fmt(p.maxPickupKm))
        etMinRating.setText(fmt(p.minPassengerRating))
        etAlerts.setText(settings.alertWordsText)
        cbSpeak.isChecked = settings.speak
        rgRemote.check(1000 + remoteModes.indexOfFirst { it.first == settings.remoteMode }.coerceAtLeast(0))
        etGhRepo.setText(settings.ghRepo); etGhToken.setText(settings.ghToken); etGhBranch.setText(settings.ghBranch)
        etHookUrl.setText(settings.webhookUrl); etHookAuth.setText(settings.webhookAuth)
        etInterval.setText(settings.remoteIntervalSec.toString())
        cbMask.isChecked = settings.maskAddresses
        etHide.setText(settings.autoHideSec.toString())
        etTargets.setText(settings.targetsText)
        cbEnabled.isChecked = settings.enabled
        cbVibrate.isChecked = settings.vibrateOnGreen
        cbOcr.isChecked = settings.ocrFallback
        cbHint.isChecked = settings.requireHint
        cbCapture.isChecked = settings.captureMode
        showCost(p)
    }

    private fun save() {
        val d = CostProfile()
        val p = CostProfile(
            fuelPrice = etFuel.num(d.fuelPrice),
            kmPerLiter = etKml.num(d.kmPerLiter),
            otherCostPerKm = etOther.num(d.otherCostPerKm),
            greenPerKm = etGreenKm.num(d.greenPerKm),
            yellowPerKm = etYellowKm.num(d.yellowPerKm),
            greenPerHour = etGreenH.num(d.greenPerHour),
            yellowPerHour = etYellowH.num(d.yellowPerHour),
            avgSpeedKmh = etSpeed.num(d.avgSpeedKmh),
            maxPickupKm = etMaxPickup.num(0.0),
            minPassengerRating = etMinRating.num(0.0),
        )
        if (p.yellowPerKm > p.greenPerKm || p.yellowPerHour > p.greenPerHour) {
            toast("O limite do amarelo precisa ser menor que o do verde"); return
        }
        settings.alertWordsText = etAlerts.text.toString()  // antes do profile: profile lê as palavras
        settings.speak = cbSpeak.isChecked
        settings.remoteMode = remoteModes.getOrNull(rgRemote.checkedRadioButtonId - 1000)?.first ?: "off"
        settings.ghRepo = etGhRepo.text.toString().trim()
        settings.ghToken = etGhToken.text.toString().trim()
        settings.ghBranch = etGhBranch.text.toString().trim().ifEmpty { "main" }
        settings.webhookUrl = etHookUrl.text.toString().trim()
        settings.webhookAuth = etHookAuth.text.toString().trim()
        settings.remoteIntervalSec = etInterval.num(60.0).toInt()
        settings.maskAddresses = cbMask.isChecked
        settings.profile = p
        settings.autoHideSec = etHide.num(15.0).toInt()
        settings.targetsText = etTargets.text.toString()
        settings.enabled = cbEnabled.isChecked
        settings.vibrateOnGreen = cbVibrate.isChecked
        settings.ocrFallback = cbOcr.isChecked
        settings.requireHint = cbHint.isChecked
        settings.captureMode = cbCapture.isChecked
        showCost(p)
        toast("Salvo")
    }

    private fun showCost(p: CostProfile) {
        tvCost.text = "Seu custo: ${Fmt.money(p.costPerKm)}/km " +
            "(combustível ${Fmt.money(p.fuelCostPerKm)} + outros ${Fmt.money(p.otherCostPerKm)})"
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = AndroidSettings.Secure.getString(contentResolver,
            AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, OfferReaderService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun refreshStatus() {
        val on = isServiceEnabled() && OfferReaderService.instance != null
        tvStatus.text = if (on) "✅ Serviço ativo — o cartão vai aparecer nas ofertas."
        else "⚠️ Serviço desligado. Ative 'Farol – leitor de ofertas' em Acessibilidade → Apps instalados."
        val recent = synchronized(OfferReaderService.recentPackages) { OfferReaderService.recentPackages.toList() }
        tvRecent.text = if (recent.isEmpty()) "Pacotes vistos: (nenhum ainda)"
        else "Pacotes vistos: " + recent.joinToString(", ")
        val pend = RemoteLog.get(this).pendingCount()
        tvRemote.text = "Na fila: $pend eventos" +
            (settings.remoteLastStatus.takeIf { it.isNotEmpty() }?.let { "\nÚltimo envio: $it" } ?: "")
        val list = history.recent(25)
        tvHistory.text = history.todaySummary() + "\n\n" +
            (if (list.isEmpty()) "" else list.joinToString("\n") { history.format(it) })
    }

    private fun test(r: Rating) {
        val svc = OfferReaderService.instance
        if (svc == null) toast("Ative o serviço de acessibilidade primeiro") else svc.showTest(r)
    }

    private fun copyCapture() {
        val txt = capture.read()
        if (txt.isEmpty()) { toast("Captura vazia. Ligue o Modo captura e abra uma oferta."); return }
        copyText("farol", txt, "Copiado (${txt.length} caracteres)")
    }

    private fun copyText(label: String, txt: String, msg: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, txt))
        toast(msg)
    }

    private fun showCapture() {
        val txt = capture.read().takeLast(6000).ifEmpty { "(vazia)" }
        android.app.AlertDialog.Builder(this)
            .setTitle("Últimas leituras")
            .setView(ScrollView(this).apply {
                addView(TextView(this@MainActivity).apply {
                    text = txt; setTextIsSelectable(true); typeface = Typeface.MONOSPACE
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setPadding(dp(16), dp(8), dp(16), dp(8))
                })
            })
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun flushRemote() {
        tvRemote.text = "Enviando..."
        RemoteLog.get(this).flushNow { status -> runOnUiThread { toast(status); refreshStatus() } }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

}
