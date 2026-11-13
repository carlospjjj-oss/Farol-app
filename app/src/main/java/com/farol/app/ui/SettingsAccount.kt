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

/** Seções de logs, notificações, conta, atualizações, backup, permissões, histórico e calibração. */
internal fun SettingsActivity.buildAccountSections() {
    // Logs
    val cLog = card("Envio de logs", "Manda o que o Farol lê para análise e calibração")
    segRemote = k.hbox().apply {
        background = k.rounded(Palette.SURFACE2, 12)
        setPadding(k.dp(4), k.dp(4), k.dp(4), k.dp(4))
    }
    cLog.addView(segRemote, k.lp(top = 14))
    boxServer = k.vbox().also { cLog.addView(it) }
    etServer = textField("https://farol-logs.SEU-NOME.workers.dev"); labeledText(boxServer, "Endereço do servidor", etServer)
    hint(boxServer, "Sem token: o servidor grava no banco e guarda a cópia no GitHub. Toque em \"Enviar teste\" para conferir.")
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

    // Conta
    val user = com.farol.app.data.Account.user(this)
    val cAcc = card("Conta", user?.let { "${it.name.ifBlank { "—" }} · ${it.email}" } ?: "Sem conta")
    buttons(cAcc,
        "🔑 Trocar senha" to { askChangePassword() },
        "Sair da conta" to {
            android.app.AlertDialog.Builder(this, com.farol.app.ui.Palette.dialogTheme)
                .setTitle("Sair da conta?")
                .setMessage("Seus dados continuam no celular. Para usar o Farol de novo, entre com e-mail e senha.")
                .setPositiveButton("Sair") { _, _ ->
                    Thread {
                        com.farol.app.data.Account.logout(this)
                        runOnUiThread {
                            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        }
                    }.start()
                }.setNegativeButton("Cancelar", null).show()
        })
    val lastSync = settings.prefs.getLong("lastSyncAt", 0L)
    hint(cAcc, "Turnos, ganhos e abastecimentos ficam guardados na sua conta: trocou de celular, é só entrar. " +
        if (lastSync > 0) "Última sincronização: " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(lastSync)) + "." else "Ainda não sincronizou.")
    buttons(cAcc, "🔄 Sincronizar agora" to {
        toast("Sincronizando…")
        Thread {
            val ok = runCatching { com.farol.app.data.SyncClient.syncAll(applicationContext) }.getOrElse { false }
            runOnUiThread { toast(if (ok) "Sincronizado com a sua conta" else "Não deu para sincronizar agora (sem internet?)") }
        }.start()
    })

    // Privacidade (LGPD)
    val cPriv = card("Privacidade (LGPD)", "Seus dados, suas escolhas")
    val swShare = toggle(cPriv, "Compartilhar textos das telas (opcional)",
        "Ajuda a melhorar o leitor quando um app muda a tela. Endereços e telefones vão mascarados. Ao desligar, os textos já enviados são apagados.")
    swShare.isChecked = com.farol.app.data.Account.shareScreens(this)
    swShare.setOnCheckedChangeListener { _, on ->
        Thread {
            val r = runCatching { com.farol.app.data.Account.giveConsent(this, on) }
            runOnUiThread {
                if (r.isSuccess) toast(if (on) "Obrigado! Textos serão compartilhados." else "Desligado. Textos já enviados foram apagados.")
                else { toast("Sem internet: tente de novo"); swShare.isChecked = !on }
            }
        }.start()
    }
    buttons(cPriv,
        "📄 Aviso de Privacidade" to { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(com.farol.app.data.Account.policyUrl(this)))) },
        "⬇️ Baixar meus dados" to {
            val name = "farol-meus-dados-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".json"
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, name), SettingsActivity.REQ_MYDATA)
        })
    buttons(cPriv, "🗑 Excluir minha conta" to { askDeleteAccount() })
    hint(cPriv, "Baixar: arquivo com tudo que está na sua conta (LGPD, art. 18). Excluir: apaga a conta e todos os dados do servidor na hora.")

    // Atualizações
    val cUpd = card("Atualizações", "Versão instalada: " + com.farol.app.service.Updater.installedName(this))
    buttons(cUpd, "🔄 Procurar atualização" to { com.farol.app.service.Updater.checkNow(this) }, primaryFirst = true)
    hint(cUpd, "O Farol procura sozinho a cada 6 horas e instala a versão nova. Na primeira vez o Android pede para permitir \"Instalar apps desconhecidos\" e confirmar; depois disso atualiza sem perguntar (Android 12 ou mais novo).")

    // Backup
    val cBackup = card("Backup", "Turnos, ganhos, abastecimentos, histórico e ajustes num arquivo")
    buttons(cBackup,
        "Exportar" to {
            save(silent = true)
            val name = "farol-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".json"
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, name), SettingsActivity.REQ_EXPORT)
        },
        "Restaurar" to {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*"), SettingsActivity.REQ_IMPORT)
        }, primaryFirst = true)
    hint(cBackup, "Faça antes de reinstalar ou trocar de celular. Nenhuma senha ou token vai no arquivo.")

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
}

/** Excluir conta (LGPD art. 18, VI): confirma com a senha; opcionalmente apaga também os dados deste celular. */
internal fun SettingsActivity.askDeleteAccount() {
    val pass = k.field("Sua senha").apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
    val local = android.widget.CheckBox(this).apply { text = "Apagar também os dados deste celular"; setTextColor(Palette.TEXT); isChecked = true }
    val box = k.vbox(22, 8).apply {
        addView(k.text("Apaga sua conta, seu diário, ofertas e sessões do servidor. Não dá para desfazer. Se quiser guardar uma cópia, use antes \"Baixar meus dados\".", 13, Palette.MUTED))
        addView(pass, k.lp(top = 12)); addView(local, k.lp(top = 8))
    }
    val d = android.app.AlertDialog.Builder(this, com.farol.app.ui.Palette.dialogTheme)
        .setTitle("Excluir minha conta").setView(box).setPositiveButton("Excluir", null).setNegativeButton("Cancelar", null).create()
    d.setOnShowListener {
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val p = pass.text.toString()
            if (p.isEmpty()) { toast("Digite sua senha"); return@setOnClickListener }
            Thread {
                val r = runCatching { com.farol.app.data.Account.deleteAccount(this, p) }
                runOnUiThread {
                    r.onSuccess {
                        if (local.isChecked) {
                            runCatching { com.farol.app.service.GpsService.stop(this) }
                            listOf("farol.db", "farol_carro.db").forEach { deleteDatabase(it) }
                            filesDir.listFiles()?.forEach { f -> runCatching { f.deleteRecursively() } }
                        }
                        d.dismiss(); toast("Conta excluída")
                        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    }.onFailure { toast(it.message ?: "Não deu para excluir") }
                }
            }.start()
        }
    }
    d.show()
}

