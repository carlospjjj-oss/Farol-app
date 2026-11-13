package com.farol.app.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.farol.app.data.Account
import com.farol.app.data.RemoteLog
import com.farol.app.data.SyncClient

/**
 * Tarefas de fundo pelo agendador do próprio Android (JobScheduler):
 * continuam depois de reiniciar o celular, esperam ter internet e o sistema escolhe a hora
 * de menor gasto de bateria. Não dependem do serviço de acessibilidade estar vivo.
 *
 *  • LOGS   — a cada 15 min: envia a fila de logs
 *  • SYNC   — a cada 15 min: sincroniza turnos e abastecimentos com a conta
 *  • UPDATE — a cada 6 h:    procura versão nova
 */
object FarolJobs {
    const val LOGS = 4101
    const val SYNC = 4102
    const val UPDATE = 4103

    fun scheduleAll(ctx: Context) {
        val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        fun periodic(id: Int, minutes: Long) {
            if (js.getPendingJob(id) != null) return          // já agendada: não reinicia o relógio
            js.schedule(JobInfo.Builder(id, ComponentName(ctx, FarolJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)                            // sobrevive ao reinício (RECEIVE_BOOT_COMPLETED)
                .setPeriodic(minutes * 60_000L)
                .build())
        }
        periodic(LOGS, 15)
        periodic(SYNC, 15)
        periodic(UPDATE, 6 * 60)
    }

    /** Sincroniza logo (ex.: depois do login ou ao encerrar o turno), quando houver internet. */
    fun syncSoon(ctx: Context) {
        val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        js.schedule(JobInfo.Builder(SYNC + 100, ComponentName(ctx, FarolJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setOverrideDeadline(60_000)
            .build())
    }
}

class FarolJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        Thread {
            var retry = false
            runCatching {
                when (params.jobId) {
                    FarolJobs.LOGS -> RemoteLog.get(app).flushBlocking()
                    FarolJobs.SYNC, FarolJobs.SYNC + 100 -> if (Account.isLoggedIn(app)) retry = !SyncClient.syncAll(app)
                    FarolJobs.UPDATE -> Updater.maybeCheck(app, foreground = false)
                }
            }.onFailure { retry = true }
            jobFinished(params, retry && params.jobId == FarolJobs.SYNC + 100)
        }.start()
        return true   // trabalho continua na thread
    }

    override fun onStopJob(params: JobParameters): Boolean = true   // interrompida: tenta de novo depois
}
