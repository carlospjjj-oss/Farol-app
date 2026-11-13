package com.farol.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.farol.app.core.DayRecord
import com.farol.app.core.DaySummary
import com.farol.app.core.Fmt
import com.farol.app.core.FuelEntry
import com.farol.app.core.JournalMath
import com.farol.app.core.HourInsights
import com.farol.app.data.History
import com.farol.app.data.JournalStore
import com.farol.app.data.RemoteLog
import com.farol.app.data.Settings
import com.farol.app.service.OfferReaderService
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tela inicial (dashboard): ganhos do dia em destaque, indicadores, turno,
 * ganhos por app, ofertas lidas, combustível e últimos 7 dias.
 * As entradas de dados abrem janelas rápidas; a tela só mostra informação.
 */
class DashboardActivity : Activity() {

    internal lateinit var settings: Settings
    internal lateinit var store: JournalStore
    internal lateinit var history: History
    internal lateinit var k: Kit
    internal lateinit var col: LinearLayout
    internal lateinit var headerBox: LinearLayout

    internal val apps: List<String>
        get() = if (settings.isCar) listOf("Uber", "99", "inDrive", "Outros") else listOf("iFood", "99", "Keeta", "Uber", "inDrive", "Outros")
    internal val self: DashboardActivity get() = this
    internal val ptBR = Locale("pt", "BR")
    internal val hhmm = SimpleDateFormat("HH:mm", ptBR)
    internal val ddmm = SimpleDateFormat("dd/MM", ptBR)
    internal var periodMonth = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Themes.apply(this)
        settings = Settings(this)
        store = JournalStore(this)
        history = History(this)
        k = Kit(this)

        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        window.decorView.setBackgroundColor(Palette.BG)

        headerBox = k.vbox().apply { setPadding(k.dp(18), k.dp(12), k.dp(18), k.dp(10)); setBackgroundColor(Palette.BG) }
        col = k.vbox().apply { setPadding(k.dp(18), k.dp(4), k.dp(18), k.dp(40)) }
        val root = k.vbox().apply {
            setBackgroundColor(Palette.BG)
            addView(headerBox, LinearLayout.LayoutParams(-1, -2))
            addView(ScrollView(self).apply {
                setBackgroundColor(Palette.BG)
                isVerticalScrollBarEnabled = false
                clipToPadding = false
                addView(col)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
        k.fitSystemBars(root)
    }

    override fun onResume() {
        super.onResume()
        if (Palette.isLight != Themes.isLight(this)) { recreate(); return }   // tema trocado em Ajustes
        if (!com.farol.app.data.Account.isLoggedIn(this)) {   // sessão recusada pelo servidor
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        // Primeira abertura: guia. Quem já usa (leitor ligado) pula direto.
        if (!settings.onboarded) {
            if (serviceOn()) settings.onboarded = true
            else { startActivity(Intent(this, OnboardingActivity::class.java)); settings.onboarded = true }
        } else if (!settings.nameAsked && settings.userName.isEmpty()) {
            settings.nameAsked = true
            askName()
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && !askedNotif &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            askedNotif = true
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9)
        }
        runCatching { com.farol.app.service.Updater.maybeCheck(this, foreground = true) }
        val d = store.day()
        if (settings.gpsEnabled && d.kmStart != null && d.kmEnd == null && !d.isPaused) com.farol.app.service.GpsService.start(this)
        render()
        runCatching { com.farol.app.service.ShiftNotifier.refresh(this) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 11) {
            val d = store.day()
            if (settings.gpsEnabled && d.kmStart != null && d.kmEnd == null && !d.isPaused) com.farol.app.service.GpsService.start(this)
            render()
        }
    }

    internal var askedNotif = false

    // ============================== render ==============================

    internal fun render() {
        col.removeAllViews()
        headerBox.removeAllViews()
        val day = store.day()
        val s = JournalMath.summarize(day, settings.profile.costPerKm)
        header()
        hero(day, s)
        kpis(s)
        shiftCard(day, s)
        gpsCard(day)
        earningsCard(day)
        offersCard()
        deliveriesCard()
        hoursCard()
        fuelCard()
        periodCard()
        footer()
    }

    internal fun add(v: View, top: Int = 14) = col.addView(v, k.lp(top = top))

    internal fun weighted(v: View, startMargin: Int = 0) =
        LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(startMargin) }

    // ============================== diálogos ==============================

    // ============================== utilidades ==============================

    internal fun sendDaySummary(d: DayRecord) {
        runCatching { com.farol.app.service.FarolJobs.syncSoon(this) }   // turno mudou: sincroniza com a conta
        val s = JournalMath.summarize(d, settings.profile.costPerKm)
        runCatching {
            RemoteLog.get(this).day(JSONObject()
                .put("data", d.date).put("km", s.km).put("km_inicial", d.kmStart).put("km_final", d.kmEnd)
                .put("custo_km", settings.profile.costPerKm).put("horas", s.hours)
                .put("ganhos", JSONObject(d.earnings)).put("ganho_total", s.earnings)
                .put("custo", s.cost).put("liquido", s.net).put("r_km", s.perKm).put("r_h", s.perHour)
                .put("ofertas", history.recent(500).count { it.optLong("t") >= (d.startAt ?: 0L) }))
        }
    }

    internal fun serviceOn(): Boolean {
        val enabled = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, OfferReaderService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me } && OfferReaderService.instance != null
    }

    /** "41.230" -> 41230 (milhar) ; "4,5" ou "4.5" -> 4.5 ; "1.234,56" -> 1234.56 */
    internal fun EditText.num(): Double? {
        val t = text.toString().trim().removePrefix("R$").trim()
        return when {
            t.contains(',') -> t.replace(".", "").replace(',', '.').toDoubleOrNull()
            Regex("""\d{1,3}(\.\d{3})+""").matches(t) -> t.replace(".", "").toDoubleOrNull()
            else -> t.toDoubleOrNull()
        }
    }

    internal fun fmtKm(v: Double?): String = v?.let {
        if (it == Math.floor(it)) String.format(ptBR, "%,d", it.toLong()) else String.format(ptBR, "%,.1f", it)
    } ?: "—"

    internal fun fmtMoney(v: Double) = String.format(ptBR, "%.2f", v)

    internal fun copy(txt: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("farol", txt))
        toast("Copiado — cole numa planilha")
    }

    internal fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
