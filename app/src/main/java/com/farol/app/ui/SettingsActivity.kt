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

    private lateinit var settings: Settings
    private lateinit var history: History
    private lateinit var capture: CaptureLog
    private lateinit var k: Kit
    private lateinit var col: LinearLayout

    // status
    private lateinit var statusBox: LinearLayout
    // custos
    private lateinit var etFuel: EditText
    private lateinit var etKml: EditText
    private lateinit var etOther: EditText
    private lateinit var tvCost: TextView
    // semáforo
    private lateinit var etGreenKm: EditText
    private lateinit var etYellowKm: EditText
    private lateinit var etGreenH: EditText
    private lateinit var etYellowH: EditText
    private lateinit var etSpeed: EditText
    // filtros
    private lateinit var etMaxPickup: EditText
    private lateinit var etMinRating: EditText
    private lateinit var etGreenStars: EditText
    private lateinit var etMinPrice: EditText
    private lateinit var etMinNet: EditText
    private lateinit var etMinNetPct: EditText
    private lateinit var etMaxKm: EditText
    private lateinit var etMaxMin: EditText
    private lateinit var bandsBox: LinearLayout
    private lateinit var etGoal: EditText
    private lateinit var etFavorites: EditText
    private lateinit var segSize: LinearLayout
    private var cardSize = 1
    private lateinit var swCompact: Switch
    private lateinit var swShiftNotif: Switch
    private lateinit var swWeekly: Switch
    private lateinit var swAutoSpeed: Switch
    private lateinit var swGps: Switch
    private lateinit var tvSpeed: TextView
    private lateinit var etAlerts: EditText
    // cartão / leitura
    private lateinit var etHide: EditText
    private lateinit var swEnabled: Switch
    private lateinit var swVibrate: Switch
    private lateinit var swSpeak: Switch
    private lateinit var swOcr: Switch
    private lateinit var swHint: Switch
    private lateinit var swCapture: Switch
    private lateinit var swScreens: Switch
    private lateinit var swAutoEarn: Switch
    private lateinit var etTargets: EditText
    private lateinit var tvRecent: TextView
    // logs
    private var remoteMode = "off"
    private lateinit var segRemote: LinearLayout
    private lateinit var boxGithub: LinearLayout
    private lateinit var boxWebhook: LinearLayout
    private lateinit var boxRemoteCommon: LinearLayout
    private lateinit var etGhRepo: EditText
    private lateinit var etGhToken: EditText
    private lateinit var etGhBranch: EditText
    private lateinit var etHookUrl: EditText
    private lateinit var etHookAuth: EditText
    private lateinit var etInterval: EditText
    private lateinit var swMask: Switch
    private lateinit var tvRemote: TextView
    // histórico
    private lateinit var tvHistSummary: TextView
    private lateinit var boxHist: LinearLayout

    private val remoteModes = listOf("off" to "Desligado", "github" to "GitHub", "webhook" to "Webhook")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        history = History(this)
        capture = CaptureLog(this)
        k = Kit(this)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        buildUi()
        loadValues()
    }

    private var firstResume = true
    override fun onResume() {
        super.onResume()
        if (!firstResume) loadValues()   // volta da calculadora de custos com valores novos
        firstResume = false
        refreshStatus()
    }

    /** Salva sozinho ao sair, se estiver tudo válido (sem avisos). */
    override fun onPause() { super.onPause(); save(silent = true) }

    // ============================== helpers de UI ==============================

    private fun card(title: String, subtitle: String? = null): LinearLayout {
        val c = k.card()
        c.addView(k.text(title, 17, Palette.TEXT, k.bold))
        subtitle?.let { c.addView(k.text(it, 13, Palette.MUTED), k.lp(top = 4)) }
        col.addView(c, k.lp(top = 14))
        return c
    }

    private fun hint(parent: LinearLayout, t: String) =
        parent.addView(k.text(t, 12, Palette.MUTED).apply { setLineSpacing(0f, 1.15f) }, k.lp(top = 10))

    /** Linha "rótulo .......... [campo] unidade". */
    private fun numRow(parent: LinearLayout, label: String, unit: String, sub: String? = null): EditText {
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

    private fun textField(hint: String, secret: Boolean = false, multi: Boolean = false, mono: Boolean = false) = EditText(this).apply {
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

    private fun labeledText(parent: LinearLayout, label: String, f: EditText) {
        parent.addView(k.text(label, 12, Palette.MUTED, k.medium), k.lp(top = 12))
        parent.addView(f, k.lp(top = 6))
    }

    private fun toggle(parent: LinearLayout, label: String, sub: String? = null): Switch {
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

    private fun buttons(parent: LinearLayout, vararg items: Pair<String, () -> Unit>, primaryFirst: Boolean = false) {
        val row = k.hbox()
        items.forEachIndexed { i, (label, action) ->
            val b = if (primaryFirst && i == 0) k.pill(label, onClick = action) else k.ghost(label, action)
            row.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = k.dp(8) })
        }
        parent.addView(row, k.lp(top = 14))
    }

    private fun onChange(vararg fields: EditText, block: () -> Unit) = fields.forEach {
        it.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = block()
        })
    }

    // ============================== construção ==============================

    private fun buildUi() {
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

        // Custos
        val cCost = card("Seus custos", "Quanto custa rodar cada km")
        cCost.addView(k.hbox().apply {
            listOf("🛵 Moto" to "moto", "🚗 Carro" to "carro", "🚲 Bike" to "bike").forEachIndexed { i, (label, preset) ->
                addView(k.chip(label, Palette.ACCENT).apply {
                    isClickable = true; setOnClickListener { applyPreset(preset) }
                }, k.lp(w = -2).apply { if (i > 0) marginStart = k.dp(8) })
            }
        }, k.lp(top = 12))
        etFuel = numRow(cCost, "Combustível", "R$/L")
        etKml = numRow(cCost, "Consumo", "km/L", "média real na cidade")
        etOther = numRow(cCost, "Outros custos", "R$/km", "óleo, pneu, manutenção, desgaste")
        tvCost = k.text("", 15, Palette.ACCENT, k.bold).apply {
            setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12))
            background = k.rounded(Palette.alpha(Palette.ACCENT, 0x1F), 14)
        }
        cCost.addView(tvCost, k.lp(top = 14))
        onChange(etFuel, etKml, etOther) { showCost() }
        cCost.addView(k.ghost("🧮  Calcular custos do mês") {
            save(silent = true)
            startActivity(Intent(this, CostsActivity::class.java))
        }, k.lp(top = 12))

        // Meta
        val cGoal = card("Meta do dia", "Aparece no dashboard, no cartão de cada oferta, na notificação e no widget")
        etGoal = numRow(cGoal, "Quero ganhar por dia", "R$", "0 = sem meta")

        // Semáforo
        val cLight = card("Suas faixas", "A cor final é a pior entre R$/km, R$/h e nota")
        bandsBox = k.vbox().also { cLight.addView(it, k.lp(top = 14)) }
        fun lightHeader(color: Int, label: String) = cLight.addView(k.hbox().apply {
            addView(k.dot(color, 12))
            addView(k.text(label, 14, color, k.bold), k.lp(w = -2).apply { marginStart = k.dp(8) })
        }, k.lp(top = 16))
        lightHeader(Palette.GREEN, "Verde a partir de")
        etGreenKm = numRow(cLight, "Por km", "R$/km")
        etGreenH = numRow(cLight, "Por hora", "R$/h")
        lightHeader(Palette.YELLOW, "Amarelo a partir de")
        etYellowKm = numRow(cLight, "Por km", "R$/km")
        etYellowH = numRow(cLight, "Por hora", "R$/h")
        lightHeader(Palette.RED, "Vermelho: abaixo do amarelo")
        cLight.addView(k.text("Nota do passageiro (quando o app mostra)", 13, Palette.MUTED, k.medium), k.lp(top = 16))
        etGreenStars = numRow(cLight, "Aceitar a partir de", "★", "0 = não usa")
        etMinRating = numRow(cLight, "Atenção a partir de", "★", "abaixo disso é vermelho")
        etSpeed = numRow(cLight, "Velocidade média", "km/h", "usada quando a oferta não mostra o tempo (~)")
        swAutoSpeed = toggle(cLight, "Aprender com meus pedidos", "Cada app usa a velocidade dos SEUS pedidos nele (aceite até o fim). Sem 5 pedidos medidos, usa o valor acima")
        tvSpeed = k.text("", 13, Palette.ACCENT, k.medium).also { cLight.addView(it, k.lp(top = 8)) }
        swGps = toggle(cLight, "📍 Medir pelo GPS (durante o turno)", "Mede km do dia e velocidade real de cada pedido. Só sugere no dashboard: nada muda sem você tocar em \"Usar\"")
        swGps.setOnCheckedChangeListener { _, on ->
            if (on && !com.farol.app.service.GpsService.hasPermission(this))
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 12)
        }
        onChange(etGreenKm, etYellowKm, etGreenH, etYellowH, etGreenStars, etMinRating) { renderBands() }

        // Condições mínimas
        val cMin = card("Condições mínimas", "Abaixo disso o cartão fica vermelho (0 = não usa)")
        etMinPrice = numRow(cMin, "Valor da corrida", "R$")
        etMinNet = numRow(cMin, "Lucro líquido mínimo", "R$")
        etMinNetPct = numRow(cMin, "Lucro mínimo", "%", "do valor da corrida")

        // Tempo e distância
        val cTD = card("Tempo e distância", "Acima disso o cartão fica vermelho (0 = não usa)")
        etMaxPickup = numRow(cTD, "Busca máxima", "km", "até o restaurante ou passageiro")
        etMaxKm = numRow(cTD, "Distância total máxima", "km")
        etMaxMin = numRow(cTD, "Tempo total máximo", "min")

        // Filtros
        val cFilter = card("Bairros", "Alerta deixa o cartão vermelho; favorito ganha uma ⭐")
        etAlerts = textField("ex.: Jardim Exemplo, Vila Tal", multi = true)
        labeledText(cFilter, "🔴 Bairros ou ruas de alerta (separados por vírgula)", etAlerts)
        etFavorites = textField("ex.: São Caetano, Vila Tal", multi = true)
        labeledText(cFilter, "⭐ Bairros favoritos: perto de casa ou bons (só destaca, não muda a cor)", etFavorites)

        // Cartão
        val cCard = card("Cartão flutuante")
        cCard.addView(k.text("Tamanho", 12, Palette.MUTED, k.medium), k.lp(top = 12))
        segSize = k.hbox().apply { background = k.rounded(Palette.SURFACE2, 12); setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4)) }
        cCard.addView(segSize, k.lp(top = 6))
        swCompact = toggle(cCard, "Modo compacto", "Só a cor, o R$/km bem grande e o preço: para ler de relance no guidão")
        swEnabled = toggle(cCard, "Farol ligado", "Pausa a leitura sem desligar a acessibilidade")
        swVibrate = toggle(cCard, "Vibrar na oferta verde")
        swSpeak = toggle(cCard, "Falar o resumo", "\"Verde. R$ 2,10 por quilômetro...\"")
        etHide = numRow(cCard, "Esconder após", "seg")
        cCard.addView(k.text("Testar o cartão", 12, Palette.MUTED, k.medium), k.lp(top = 16))
        cCard.addView(k.hbox().apply {
            listOf(Rating.GREEN to Palette.GREEN, Rating.YELLOW to Palette.YELLOW, Rating.RED to Palette.RED).forEachIndexed { i, (r, c) ->
                val label = when (r) { Rating.GREEN -> "Verde"; Rating.YELLOW -> "Amarelo"; Rating.RED -> "Vermelho" }
                addView(k.pill(label, Palette.alpha(c, 0x33), c) { test(r) },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = k.dp(8) })
            }
        }, k.lp(top = 8))

        // Leitura
        val cRead = card("Leitura das ofertas")
        swAutoEarn = toggle(cRead, "Ganhos automáticos", "Lê o 'ganhos de hoje' na tela inicial de cada app e preenche o dashboard")
        swHint = toggle(cRead, "Só com botão de aceitar", "Evita ler telas que não são oferta (recomendado)")
        swOcr = toggle(cRead, "OCR de reserva", "Lê a imagem quando o app esconde o texto (Android 11+)")
        swCapture = toggle(cRead, "Modo captura", "Grava o texto lido para calibrar")
        swScreens = toggle(cRead, "Registrar telas após oferta", "Envia nos logs as telas de aceite, coleta e entrega por 90 min (para somar ganhos sozinho no futuro)")
        etTargets = textField("pacote=Nome", multi = true, mono = true).apply { minLines = 4 }
        labeledText(cRead, "Apps monitorados (pacote=Nome, um por linha)", etTargets)
        tvRecent = k.text("", 12, Palette.MUTED).also { cRead.addView(it, k.lp(top = 8)) }

        // Logs
        val cLog = card("Envio de logs", "Manda o que o Farol lê para análise e calibração")
        segRemote = k.hbox().apply {
            background = k.rounded(Palette.SURFACE2, 12)
            setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4))
        }
        cLog.addView(segRemote, k.lp(top = 14))
        boxGithub = k.vbox().also { cLog.addView(it) }
        etGhRepo = textField("dono/repositório"); labeledText(boxGithub, "Repositório", etGhRepo)
        etGhToken = textField("github_pat_...", secret = true); labeledText(boxGithub, "Token do GitHub", etGhToken)
        etGhBranch = textField("main"); labeledText(boxGithub, "Branch", etGhBranch)
        hint(boxGithub, "Token: github.com → Settings → Developer settings → Fine-grained tokens → só o repositório Farol → Contents: Read and write.")
        boxWebhook = k.vbox().also { cLog.addView(it) }
        etHookUrl = textField("https://..."); labeledText(boxWebhook, "URL do webhook", etHookUrl)
        etHookAuth = textField("opcional", secret = true); labeledText(boxWebhook, "Authorization", etHookAuth)
        boxRemoteCommon = k.vbox().also { cLog.addView(it) }
        etInterval = numRow(boxRemoteCommon, "Enviar a cada", "seg")
        swMask = toggle(boxRemoteCommon, "Mascarar endereços", "Rua, número, telefone e CEP saem como [endereço]")
        tvRemote = k.text("", 13, Palette.MUTED).apply {
            setPadding(k.dp(14), k.dp(10), k.dp(14), k.dp(10))
            background = k.rounded(Palette.SURFACE2, 12)
        }.also { boxRemoteCommon.addView(it, k.lp(top = 14)) }
        buttons(boxRemoteCommon,
            "Enviar teste" to { save(); RemoteLog.get(this).test(); flushRemote() },
            "Enviar agora" to { save(); flushRemote() })

        // Notificações
        val cNotif = card("Notificações")
        swShiftNotif = toggle(cNotif, "Resumo do turno na barra", "Tempo, ganhos, meta e ofertas; com botão de pausar o Farol")
        swWeekly = toggle(cNotif, "Resumo da semana", "Domingo à noite: ganhos, km, melhor dia e melhor horário")

        // Backup
        val cBackup = card("Backup", "Turnos, ganhos, abastecimentos, histórico e ajustes num arquivo")
        buttons(cBackup,
            "Exportar" to {
                save(silent = true)
                val name = "farol-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".json"
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE, name), REQ_EXPORT)
            },
            "Restaurar" to {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*"), REQ_IMPORT)
            }, primaryFirst = true)
        hint(cBackup, "Faça antes de reinstalar ou trocar de celular. O arquivo inclui o token do GitHub: guarde num lugar só seu.")

        // Acesso
        val cAccess = card("Permissões", "Se o leitor parar de funcionar")
        buttons(cAccess, "📖 Guia de instalação" to { startActivity(Intent(this, OnboardingActivity::class.java)) }, primaryFirst = true)
        buttons(cAccess,
            "Acessibilidade" to { startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) },
            "Restritas" to {
                startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                toast("Toque nos ⋮ → 'Permitir configurações restritas'")
            })
        buttons(cAccess, "Bateria sem restrição" to {
            startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            toast("Bateria → Sem restrições")
        })

        // Histórico
        val cHist = card("Histórico de ofertas")
        tvHistSummary = k.text("", 14, Palette.TEXT, k.medium).also { cHist.addView(it, k.lp(top = 10)) }
        boxHist = k.vbox().also { cHist.addView(it, k.lp(top = 6)) }
        buttons(cHist, "Abrir histórico completo" to {
            startActivity(Intent(this, HistoryActivity::class.java))
        }, primaryFirst = true)
        buttons(cHist,
            "Copiar CSV" to { copyText("farol.csv", history.toCsv(), "Histórico copiado — cole numa planilha") },
            "Limpar" to { confirm("Apagar o histórico de ofertas?") { history.clear(); refreshStatus() } })

        // Calibração
        val cCal = card("Calibração", "Texto bruto lido nas telas (Modo captura)")
        buttons(cCal,
            "Copiar" to { copyCapture() },
            "Ver" to { showCapture() },
            "Limpar" to { capture.clear(); toast("Captura apagada") })

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

    private fun renderSegment() {
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
        boxGithub.visibility = if (remoteMode == "github") View.VISIBLE else View.GONE
        boxWebhook.visibility = if (remoteMode == "webhook") View.VISIBLE else View.GONE
        boxRemoteCommon.visibility = if (remoteMode == "off") View.GONE else View.VISIBLE
    }

    // ============================== dados ==============================

    private fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString().replace('.', ',')
    private fun EditText.num(def: Double) = text.toString().trim().replace(',', '.').toDoubleOrNull() ?: def

    private fun loadValues() {
        val p = settings.profile
        etFuel.setText(fmt(p.fuelPrice)); etKml.setText(fmt(p.kmPerLiter)); etOther.setText(fmt(p.otherCostPerKm))
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
        etGhRepo.setText(settings.ghRepo); etGhToken.setText(settings.ghToken); etGhBranch.setText(settings.ghBranch)
        etHookUrl.setText(settings.webhookUrl); etHookAuth.setText(settings.webhookAuth)
        etInterval.setText(settings.remoteIntervalSec.toString())
        swMask.isChecked = settings.maskAddresses
        renderSegment()
        showCost()
        renderBands()
    }

    private fun currentProfile(): CostProfile {
        val d = CostProfile()
        return CostProfile(
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
            greenStars = etGreenStars.num(0.0),
            minPrice = etMinPrice.num(0.0),
            minNet = etMinNet.num(0.0),
            minNetPct = etMinNetPct.num(0.0),
            maxTotalKm = etMaxKm.num(0.0),
            maxMinutes = etMaxMin.num(0.0),
        )
    }

    /** Retorna o motivo se algo estiver errado, ou null se estiver tudo certo. */
    private fun problem(p: CostProfile): String? = when {
        p.greenPerKm > 20 || p.yellowPerKm > 20 -> "Meta por km muito alta. Use vírgula: 1,80 e 1,40"
        p.otherCostPerKm > 5 || p.kmPerLiter > 120 || p.fuelPrice > 30 -> "Confira os custos: use vírgula (ex.: 0,15)"
        p.yellowPerKm > p.greenPerKm || p.yellowPerHour > p.greenPerHour -> "O amarelo precisa ser menor que o verde"
        p.greenStars > 5 || p.minPassengerRating > 5 -> "A nota vai até 5 (ex.: 4,90)"
        p.greenStars > 0 && p.minPassengerRating > p.greenStars -> "Na nota, 'atenção' precisa ser menor que 'aceitar'"
        else -> null
    }

    private fun save(silent: Boolean = false) {
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
        settings.profile = p
        settings.autoHideSec = etHide.num(15.0).toInt()
        settings.targetsText = etTargets.text.toString()
        settings.enabled = swEnabled.isChecked
        settings.vibrateOnGreen = swVibrate.isChecked
        settings.speak = swSpeak.isChecked
        settings.ocrFallback = swOcr.isChecked
        settings.requireHint = swHint.isChecked
        settings.captureMode = swCapture.isChecked
        settings.logScreens = swScreens.isChecked
        settings.autoEarnings = swAutoEarn.isChecked
        settings.remoteMode = remoteMode
        settings.ghRepo = etGhRepo.text.toString().trim()
        settings.ghToken = etGhToken.text.toString().trim()
        settings.ghBranch = etGhBranch.text.toString().trim().ifEmpty { "main" }
        settings.webhookUrl = etHookUrl.text.toString().trim()
        settings.webhookAuth = etHookAuth.text.toString().trim()
        settings.remoteIntervalSec = etInterval.num(60.0).toInt()
        settings.maskAddresses = swMask.isChecked
        runCatching { RemoteLog.get(this).settingsChanged(if (silent) "auto" else "salvar") }
        if (!silent) toast("Salvo ✓")
    }

    private fun showCost() {
        val p = currentProfile()
        tvCost.text = "Seu custo: ${Fmt.money(p.costPerKm)}/km   (combustível ${Fmt.money(p.fuelCostPerKm)} + outros ${Fmt.money(p.otherCostPerKm)})"
    }

    private fun renderSize() {
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

    companion object { private const val REQ_EXPORT = 31; private const val REQ_IMPORT = 32 }

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
    private fun renderBands() {
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

    private fun applyPreset(kind: String) {
        val (kml, other, gKm, yKm, gH, yH, speed) = when (kind) {
            "moto" -> listOf(30.0, 0.15, 1.80, 1.40, 30.0, 20.0, 25.0)
            "carro" -> listOf(11.0, 0.30, 2.00, 1.50, 45.0, 30.0, 25.0)
            else -> listOf(0.0, 0.05, 1.50, 1.00, 20.0, 14.0, 14.0)
        }
        if (kind == "bike") { etFuel.setText("0"); etKml.setText("1") } else etKml.setText(fmt(kml))
        etOther.setText(fmt(other)); etGreenKm.setText(fmt(gKm)); etYellowKm.setText(fmt(yKm))
        etGreenH.setText(fmt(gH)); etYellowH.setText(fmt(yH)); etSpeed.setText(fmt(speed))
        showCost()
        toast("Valores típicos de ${kind}. Ajuste e salve.")
    }

    private operator fun <T> List<T>.component6() = this[5]
    private operator fun <T> List<T>.component7() = this[6]

    private fun isServiceEnabled(): Boolean {
        val enabled = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, OfferReaderService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun refreshStatus() {
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

    private fun test(r: Rating) {
        save(silent = true)
        val svc = OfferReaderService.instance
        if (svc == null) toast("Ative o leitor primeiro") else svc.showTest(r)
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
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Últimas leituras")
            .setView(ScrollView(this).apply {
                addView(k.text(txt, 11, Palette.TEXT).apply {
                    setTextIsSelectable(true); typeface = Typeface.MONOSPACE
                    setPadding(k.dp(18), k.dp(8), k.dp(18), k.dp(8))
                })
            })
            .setPositiveButton("Fechar", null).show()
    }

    private fun confirm(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage(msg).setPositiveButton("Sim") { _, _ -> onYes() }.setNegativeButton("Cancelar", null).show()
    }

    private fun flushRemote() {
        tvRemote.text = "Enviando..."
        RemoteLog.get(this).flushNow { status -> runOnUiThread { toast(status); refreshStatus() } }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
