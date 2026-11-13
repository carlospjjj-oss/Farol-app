package com.farol.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização automática pelo GitHub (repositório público Farol-app, sem token).
 *
 * - Lê a última Release; o corpo traz "versionCode=NN" (posto pelo build).
 * - Se for maior que a instalada, baixa o Farol.apk e instala com o PackageInstaller.
 * - Android 12+: depois que o próprio Farol instalou uma vez (vira o "instalador" dele),
 *   as próximas atualizações entram sem perguntar. Antes disso, o Android mostra
 *   "Atualizar?" uma vez, por notificação ou direto na tela, se o app estiver aberto.
 */
object Updater {
    private const val API = "https://api.github.com/repos/carlospjjj-oss/Farol-app/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    private const val CH = "farol_update"
    private const val NOTIF_ID = 77
    const val ACTION_DONE = "com.farol.app.UPDATE_DONE"
    @Volatile private var running = false

    fun installedCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("farol", Context.MODE_PRIVATE)

    /** Chamado sozinho (painel e serviço). Respeita o intervalo de 6 h. */
    fun maybeCheck(ctx: Context, foreground: Boolean) {
        val p = prefs(ctx)
        if (!p.getBoolean("auto_update", true)) return
        val now = System.currentTimeMillis()
        if (now - p.getLong("update_checked_at", 0) < CHECK_EVERY_MS) return
        p.edit().putLong("update_checked_at", now).apply()
        check(ctx, foreground, manual = false)
    }

    /** Botão "Procurar atualização" nos ajustes. */
    fun checkNow(ctx: Context) = check(ctx, foreground = true, manual = true)

    private fun check(ctx: Context, foreground: Boolean, manual: Boolean) {
        if (running) { if (manual) toast(ctx, "Já estou verificando…"); return }
        running = true
        val app = ctx.applicationContext
        Thread {
            try {
                val rel = fetchLatest()
                val mine = installedCode(app)
                when {
                    rel == null -> if (manual) toast(app, "Não consegui ver a versão nova (sem internet?)")
                    rel.code <= mine -> if (manual) toast(app, "Você já está na última versão (${installedName(app)})")
                    else -> {
                        if (manual) toast(app, "Baixando a versão ${rel.name}…")
                        val apk = download(app, rel.url)
                        install(app, apk, rel.name, foreground)
                        runCatching { com.farol.app.data.RemoteLog.get(app).settingsChanged("atualização ${rel.name}") }
                    }
                }
            } catch (e: Exception) {
                if (manual) toast(app, "Falha ao atualizar: ${e.message}")
            } finally {
                running = false
            }
        }.start()
    }

    private data class Release(val code: Long, val name: String, val url: String)

    private fun fetchLatest(): Release? {
        val c = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Farol-app")
        }
        try {
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val body = j.optString("body")
            val code = Regex("""versionCode=(\d+)""").find(body)?.groupValues?.get(1)?.toLongOrNull() ?: return null
            val name = Regex("""versionName=([\w.\-]+)""").find(body)?.groupValues?.get(1) ?: j.optString("tag_name")
            val assets = j.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) return Release(code, name, a.getString("browser_download_url"))
            }
            return null
        } finally { c.disconnect() }
    }

    private fun download(ctx: Context, url: String): File {
        val out = File(ctx.cacheDir, "update.apk")
        var u = URL(url)
        repeat(5) {   // segue os redirecionamentos do GitHub
            val c = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000; readTimeout = 60000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Farol-app")
            }
            val code = c.responseCode
            if (code in 300..399) { u = URL(u, c.getHeaderField("Location")); c.disconnect(); return@repeat }
            if (code != 200) { c.disconnect(); error("download HTTP $code") }
            c.inputStream.use { inp -> out.outputStream().use { inp.copyTo(it) } }
            c.disconnect()
            if (out.length() < 1_000_000) error("arquivo incompleto")
            return out
        }
        error("redirecionamentos demais")
    }

    private fun install(ctx: Context, apk: File, name: String, foreground: Boolean) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("farol.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val i = Intent(ctx, UpdateReceiver::class.java).setAction(ACTION_DONE)
                .putExtra("name", name).putExtra("fg", foreground)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(ctx, id, i, flags).intentSender)
        }
    }

    fun notifyConfirm(ctx: Context, confirm: Intent, name: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "Atualizações", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, NOTIF_ID, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Farol $name pronto")
            .setContentText("Toque para atualizar")
            .setContentIntent(pi).setAutoCancel(true).build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }

    fun toast(ctx: Context, msg: String) =
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
}

/** Resultado da instalação. Se o Android pedir confirmação, mostra na tela ou por notificação. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: ""
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)) ?: return
                if (intent.getBooleanExtra("fg", false)) {
                    runCatching { ctx.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { Updater.notifyConfirm(ctx, confirm, name) }
                } else Updater.notifyConfirm(ctx, confirm, name)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit   // o app reinicia na versão nova
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "erro"
                Updater.toast(ctx, "Atualização não instalada: $msg")
            }
        }
    }
}
