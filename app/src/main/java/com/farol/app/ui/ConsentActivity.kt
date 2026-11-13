package com.farol.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ScrollView
import com.farol.app.data.Account

/**
 * Aviso de privacidade (LGPD). Aparece no primeiro uso de quem já tinha conta e sempre que
 * a versão do aviso mudar. O aceite é obrigatório para usar a conta; compartilhar textos é opcional
 * e começa DESLIGADO (consentimento livre, art. 8º).
 */
class ConsentActivity : Activity() {

    private lateinit var k: Kit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        k = Kit(this)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG

        fun item(emoji: String, title: String, text: String) = k.hbox().apply {
            addView(k.text(emoji, 20), k.lp(w = -2))
            addView(k.vbox().apply {
                addView(k.text(title, 15, Palette.TEXT, k.bold))
                addView(k.text(text, 13, Palette.MUTED), k.lp(top = 2))
            }, android.widget.LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(12) })
        }

        val accept = CheckBox(this).apply {
            text = "Li e aceito o Aviso de Privacidade"
            setTextColor(Palette.TEXT); textSize = 15f
        }
        val share = CheckBox(this).apply {
            text = "Opcional: compartilhar os textos das telas dos apps para melhorar o leitor de ofertas"
            setTextColor(Palette.TEXT); textSize = 14f
            isChecked = Account.shareScreens(this@ConsentActivity)
        }
        val msg = k.text("", 13, Palette.RED).apply { gravity = Gravity.CENTER }
        var busy = false

        val card = k.card().apply {
            addView(item("📱", "Leitura só no seu celular",
                "A acessibilidade lê apenas as ofertas dos apps de corrida e entrega da sua lista. Mensagens, bancos e outros apps não chegam ao Farol."))
            addView(item("☁️", "Na sua conta, só números",
                "Turnos, ganhos, abastecimentos e os números das ofertas, para guardar seu diário e montar seu painel. Sua localização não sai do celular."), k.lp(top = 14))
            addView(item("📝", "Textos das telas: você escolhe",
                "Só são enviados se você marcar a opção abaixo. Endereços e telefones são mascarados antes. Dá para desligar quando quiser, e os textos são apagados."), k.lp(top = 14))
            addView(item("🔐", "Seus direitos",
                "Baixe ou apague todos os seus dados em Ajustes → Privacidade, a qualquer momento."), k.lp(top = 14))
            addView(k.ghost("📄  Ler o Aviso de Privacidade completo") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Account.policyUrl(this@ConsentActivity))))
            }, k.lp(top = 16))
            addView(accept, k.lp(top = 14))
            addView(share, k.lp(top = 6))
            addView(msg, k.lp(top = 8))
            addView(k.pill("Concordo e continuar") {
                if (busy) return@pill
                if (!accept.isChecked) { msg.text = "Para usar a conta é preciso aceitar o aviso"; return@pill }
                busy = true; msg.setTextColor(Palette.MUTED); msg.text = "Registrando…"
                Thread {
                    val r = runCatching { Account.giveConsent(this@ConsentActivity, share.isChecked) }
                    runOnUiThread {
                        busy = false
                        r.onSuccess { startActivity(Intent(this@ConsentActivity, ModeActivity::class.java)); finish() }
                         .onFailure { msg.setTextColor(Palette.RED); msg.text = "Não deu para registrar (${it.message}). Confira a internet e tente de novo." }
                    }
                }.start()
            }, k.lp(top = 6))
            addView(k.ghost("Não concordo (sair da conta)") {
                Thread {
                    Account.logout(this@ConsentActivity)
                    runOnUiThread { startActivity(Intent(this@ConsentActivity, LoginActivity::class.java)); finish() }
                }.start()
            }, k.lp(top = 8))
        }

        val col = k.vbox(22, 24).apply {
            addView(k.text("🔒", 46).apply { gravity = Gravity.CENTER }, k.lp(top = 12))
            addView(k.text("Sua privacidade", 26, Palette.TEXT, k.black).apply { gravity = Gravity.CENTER }, k.lp(top = 4))
            addView(k.text("Como o Farol trata seus dados, segundo a LGPD", 14, Palette.MUTED).apply { gravity = Gravity.CENTER }, k.lp(top = 4))
            addView(card, k.lp(top = 20))
        }
        val root = ScrollView(this).apply { setBackgroundColor(Palette.BG); addView(col) }
        setContentView(root)
        k.fitSystemBars(root)
    }

    override fun onBackPressed() { finishAffinity() }
}
