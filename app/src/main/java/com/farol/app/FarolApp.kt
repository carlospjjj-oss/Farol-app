package com.farol.app

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import com.farol.app.core.LogSanitizer
import com.farol.app.data.RemoteLog
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Início do app: relatório de travamentos e tarefas de fundo. */
class FarolApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        runCatching { com.farol.app.service.FarolJobs.scheduleAll(this) }
        // Travamentos da última vez vão para a fila de logs (não trava a abertura).
        Thread { runCatching { CrashReporter.flushPending(this) } }.start()
    }
}

/**
 * Quando o app fecha por erro, grava o que aconteceu (sem dados pessoais) num arquivo.
 * Na próxima abertura vai para o servidor como evento "crash", com versão, aparelho e a pilha do erro.
 */
object CrashReporter {
    private const val DIR = "crashes"
    private const val MAX_FILES = 10
    @Volatile private var lastScreen = "?"

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { lastScreen = a.javaClass.simpleName }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun save(app: Application, thread: Thread, e: Throwable) {
        val dir = File(app.filesDir, DIR).apply { mkdirs() }
        dir.listFiles()?.sortedBy { it.name }?.dropLast(MAX_FILES - 1)?.forEach { it.delete() }
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
        val o = JSONObject()
            .put("t", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
            .put("tipo", "crash")
            .put("versao", version)
            .put("android", Build.VERSION.SDK_INT)
            .put("aparelho_modelo", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("thread", thread.name)
            .put("tela", lastScreen)
            .put("erro", LogSanitizer.mask("${e.javaClass.name}: ${e.message ?: ""}").take(500))
            .put("pilha", LogSanitizer.mask(sw.toString()).take(6000))
        File(dir, "${System.currentTimeMillis()}.json").writeText(o.toString())
    }

    fun flushPending(app: Application) {
        val files = File(app.filesDir, DIR).listFiles()?.sortedBy { it.name } ?: return
        val log = RemoteLog.get(app)
        for (f in files) {
            runCatching { log.crash(JSONObject(f.readText())) }
            f.delete()
        }
    }
}
