package com.farol.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import com.farol.app.core.GpsMath
import com.farol.app.data.GpsStore

/**
 * Mede km e tempo em movimento pelo GPS durante o turno.
 * Serviço em primeiro plano (com a notificação do turno): por isso basta a permissão de
 * localização "durante o uso". Começa no "Iniciar turno" e para no "Encerrar turno".
 */
class GpsService : Service(), LocationListener {

    companion object {
        @Volatile var running = false
            private set

        fun hasPermission(ctx: Context) =
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(ctx: Context) {
            if (running || !hasPermission(ctx)) return
            runCatching {
                val i = Intent(ctx, GpsService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, GpsService::class.java)) }
        }
    }

    private var last: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val n = ShiftNotifier.buildForeground(this)
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ShiftNotifier.ID_SHIFT, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ShiftNotifier.ID_SHIFT, n)
        }.isSuccess
        // Religado pelo sistema em segundo plano sem permissão: desiste (volta ao abrir o Farol)
        if (!ok || !hasPermission(this)) { stopSelf(); return }
        running = true
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val provider = if (Build.VERSION.SDK_INT >= 31 && lm.allProviders.contains(LocationManager.FUSED_PROVIDER))
            LocationManager.FUSED_PROVIDER else LocationManager.GPS_PROVIDER
        try {
            lm.requestLocationUpdates(provider, 4_000L, 0f, this, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onLocationChanged(loc: Location) {
        val prev = last
        if (prev != null) {
            val secs = (loc.time - prev.time) / 1000.0
            val step = GpsMath.step(prev.distanceTo(loc).toDouble(), secs, loc.accuracy)
            GpsStore.add(this, step, secs)
            if (step > 0 || loc.accuracy <= GpsMath.MAX_ACCURACY_M) last = loc
        } else if (loc.accuracy <= GpsMath.MAX_ACCURACY_M) {
            last = loc
            GpsStore.add(this, 0.0, 0.0)
        }
    }

    @Deprecated("API antiga") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        running = false
        runCatching { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(this) }
        runCatching { GpsStore.save(this) }
        super.onDestroy()
        runCatching { ShiftNotifier.refresh(this) }
    }
}
