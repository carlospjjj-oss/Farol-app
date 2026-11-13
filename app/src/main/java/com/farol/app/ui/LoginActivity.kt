package com.farol.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.farol.app.data.Account
import com.farol.app.data.RemoteLog
import com.farol.app.data.Settings
import java.util.UUID

/** Entrar / Criar conta. Obrigatória na abertura; depois fica logado (funciona sem internet). */
class LoginActivity : Activity() {

    private lateinit var k: Kit
    private var register = false
    private lateinit var tabs: LinearLayout
    private lateinit var nameBox: View
    private lateinit var confirmBox: View
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPass: EditText
    private lateinit var etPass2: EditText
    private lateinit var cbAccept: android.widget.CheckBox
    private lateinit var cbShare: android.widget.CheckBox
    private lateinit var consentBox: View
    private lateinit var btn: View
    private lateinit var btnLabel: android.widget.TextView
    private lateinit var msg: android.widget.TextView
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Themes.apply(this)
        if (Account.isLoggedIn(this)) { next(); return }
        k = Kit(this)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        register = intent.getBooleanExtra("register", false)

        etName = k.field("Seu nome").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS }
        etEmail = k.field("voce@email.com", Account.user(this@LoginActivity)?.email ?: "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        etPass = k.field("Senha").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        etPass2 = k.field("Repita a senha").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }

        tabs = k.hbox().apply {
            background = k.rounded(Palette.SURFACE2, 14)
            setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4))
        }
        nameBox = labeled("Nome", etName)
        confirmBox = labeled("Confirmar senha", etPass2)
        cbAccept = android.widget.CheckBox(this).apply { text = "Li e aceito o Aviso de Privacidade"; setTextColor(Palette.TEXT) }
        cbShare = android.widget.CheckBox(this).apply {
            text = "Opcional: compartilhar os textos das telas para melhorar o leitor"; setTextColor(Palette.MUTED); textSize = 13f
        }
        consentBox = k.vbox().apply {
            addView(k.text("📄 Ler o Aviso de Privacidade", 13, Palette.ACCENT, k.bold).apply {
                isClickable = true
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Account.policyUrl(this@LoginActivity)))) }
            })
            addView(cbAccept, k.lp(top = 6))
            addView(cbShare)
        }
        msg = k.text("", 13, Palette.RED).apply { gravity = Gravity.CENTER }
        btn = k.pill("Entrar") { submit() }
        btnLabel = (btn as? android.widget.TextView) ?: k.text()

        val card = k.card().apply {
            addView(tabs)
            addView(nameBox, k.lp(top = 16))
            addView(labeled("E-mail", etEmail), k.lp(top = 14))
            addView(labeled("Senha", etPass), k.lp(top = 14))
            addView(confirmBox, k.lp(top = 14))
            addView(consentBox, k.lp(top = 12))
            addView(msg, k.lp(top = 12))
            addView(btn, k.lp(top = 6))
        }

        val col = k.vbox(22, 24).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(k.text("🚦", 54).apply { gravity = Gravity.CENTER }, k.lp(top = 20))
            addView(k.text("Farol", 30, Palette.TEXT, k.black).apply { gravity = Gravity.CENTER }, k.lp(top = 4))
            addView(k.text("Entre para guardar seus dados com segurança e ver tudo no painel.", 14, Palette.MUTED)
                .apply { gravity = Gravity.CENTER }, k.lp(top = 6))
            addView(card, k.lp(top = 24))
            addView(k.text("Sua senha é guardada só como código embaralhado no servidor. Ninguém consegue lê-la, nem o administrador.",
                12, Palette.MUTED).apply { gravity = Gravity.CENTER }, k.lp(top = 16))
        }
        val root = ScrollView(this).apply {
            setBackgroundColor(Palette.BG)
            isFillViewport = true
            addView(col)
        }
        setContentView(root)
        k.fitSystemBars(root)
        renderTabs()
    }

    private fun labeled(label: String, f: EditText) = k.vbox().apply {
        addView(k.text(label, 12, Palette.MUTED, k.medium))
        addView(f, k.lp(top = 6))
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        listOf(false to "Entrar", true to "Criar conta").forEach { (reg, label) ->
            val on = reg == register
            val t = k.text(label, 14, if (on) Palette.ON_ACCENT else Palette.MUTED, k.bold).apply {
                gravity = Gravity.CENTER
                setPadding(0, k.dp(10), 0, k.dp(10))
                if (on) background = k.rounded(Palette.ACCENT, 11)
                isClickable = true
                setOnClickListener { register = reg; msg.text = ""; renderTabs() }
            }
            tabs.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        nameBox.visibility = if (register) View.VISIBLE else View.GONE
        confirmBox.visibility = if (register) View.VISIBLE else View.GONE
        consentBox.visibility = if (register) View.VISIBLE else View.GONE
        btnLabel.text = if (register) "Criar conta" else "Entrar"
    }

    private fun deviceId(): String {
        val p = Settings(this).prefs
        return p.getString("deviceId", null) ?: UUID.randomUUID().toString().take(8).also { p.edit().putString("deviceId", it).apply() }
    }

    private fun submit() {
        if (busy) return
        val email = etEmail.text.toString().trim()
        val pass = etPass.text.toString()
        val name = etName.text.toString().trim()
        when {
            !Regex("""^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""").matches(email) -> return fail("Digite um e-mail válido")
            pass.length < (if (register) 8 else 1) -> return fail(if (register) "A senha precisa ter pelo menos 8 caracteres" else "Digite a senha")
            register && name.isEmpty() -> return fail("Digite seu nome")
            register && pass != etPass2.text.toString() -> return fail("As senhas não são iguais")
            register && !cbAccept.isChecked -> return fail("Para criar a conta, aceite o Aviso de Privacidade")
        }
        busy = true
        msg.setTextColor(Palette.MUTED); msg.text = if (register) "Criando conta…" else "Entrando…"
        btn.alpha = 0.6f
        val dev = deviceId()
        Thread {
            val r = runCatching { Account.authenticate(this, register, email, pass, name, dev, cbAccept.isChecked, cbShare.isChecked) }
            runOnUiThread {
                busy = false; btn.alpha = 1f
                r.onSuccess {
                    runCatching { RemoteLog.get(this).settingsChanged(if (register) "conta criada" else "login") }
                    // Celular novo: traz os turnos e abastecimentos guardados na conta.
                    Thread { runCatching { com.farol.app.data.SyncClient.syncAll(applicationContext) } }.start()
                    runCatching { com.farol.app.service.FarolJobs.scheduleAll(this) }
                    next()
                }.onFailure { fail(it.message ?: "Não deu certo, tente de novo") }
            }
        }.start()
    }

    private fun fail(m: String) { msg.setTextColor(Palette.RED); msg.text = m }

    private fun next() {
        startActivity(Intent(this, ModeActivity::class.java))
        finish()
    }

    override fun onBackPressed() { finishAffinity() }
}
