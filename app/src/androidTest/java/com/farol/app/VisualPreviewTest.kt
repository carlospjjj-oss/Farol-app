package com.farol.app

import android.app.Activity
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.farol.app.core.CostProfile
import com.farol.app.core.DayRecord
import com.farol.app.core.OfferParser
import com.farol.app.core.ProfitCalculator
import com.farol.app.data.Account
import com.farol.app.data.History
import com.farol.app.data.JournalStore
import com.farol.app.data.SecureStore
import com.farol.app.data.Settings
import com.farol.app.ui.ConsentActivity
import com.farol.app.ui.DashboardActivity
import com.farol.app.ui.LoginActivity
import com.farol.app.ui.ModeActivity
import com.farol.app.ui.SettingsActivity
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Tira prints das telas principais (para conferir o visual antes de publicar). Não falha por aparência. */
@RunWith(AndroidJUnit4::class)
class VisualPreviewTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext

    private fun shot(name: String) {
        Thread.sleep(1800)
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        val w = 540; val h = bmp.height * w / bmp.width
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun <T : Activity> open(cls: Class<T>, name: String) {
        runCatching { ActivityScenario.launch(cls).use { shot(name) } }
    }

    private fun demoAccount() {
        SecureStore.put(ctx, "token", "demo-token-0123456789abcdefghij")
        SecureStore.plain(ctx).edit().putString("email", "carlos@exemplo.com").putString("nome", "Carlos")
            .putString("consent_versao", Account.POLICY_VERSION).putBoolean("compartilhar", false).apply()
        val s = Settings(ctx)
        s.vehicleMode = "moto"; s.userName = "Carlos"; s.prefs.edit().putBoolean("onboarded", true).putBoolean("name_asked", true).apply()
        s.dailyGoal = 150.0
        val now = System.currentTimeMillis()
        val store = JournalStore(ctx)
        store.putDay(DayRecord(store.today(), kmStart = 58100.0, startAt = now - 4 * 3600_000L,
            earnings = mapOf("Keeta" to 72.4, "iFood" to 31.5),
            orders = mapOf("Keeta#7040" to 11.72, "Keeta#7553" to 13.47, "99#149031" to 15.65),
            expenses = listOf(com.farol.app.core.Expense(now - 3600_000L, "alimentacao", 22.0, "Marmita"),
                com.farol.app.core.Expense(now - 600_000L, "outros", 9.0, "Café"))))
        // dias anteriores da semana, para as metas
        val cal = java.util.Calendar.getInstance()
        for (i in 1..3) {
            cal.timeInMillis = now - i * 86_400_000L
            val d = store.dateOf(cal.timeInMillis)
            if (d.substring(0, 7) == store.today().substring(0, 7))
                store.putDay(DayRecord(d, kmStart = 58000.0 - i * 90, kmEnd = 58080.0 - i * 90, startAt = cal.timeInMillis - 6 * 3600_000L,
                    endAt = cal.timeInMillis, earnings = mapOf("Keeta" to 160.0 + i * 10)))
        }
        val h = History(ctx)
        listOf("R$ 14,90 | 5,2 km | 18 min | Aceitar" to "Keeta", "R$ 8,00 | 6,8 km | 25 min | Aceitar" to "iFood",
               "R$ 11,50 | 4,1 km | 14 min | Aceitar" to "99").forEach { (t, app) ->
            OfferParser.parse(t, app)?.let { h.add(ProfitCalculator.evaluate(it, CostProfile()), "demo") }
        }
    }

    @Test fun prints() {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, android.Manifest.permission.POST_NOTIFICATIONS) }
        Account.clear(ctx)
        open(LoginActivity::class.java, "1-login")
        demoAccount()
        SecureStore.plain(ctx).edit().putString("consent_versao", null).apply()
        open(ConsentActivity::class.java, "2-privacidade")
        SecureStore.plain(ctx).edit().putString("consent_versao", Account.POLICY_VERSION).apply()
        open(ModeActivity::class.java, "3-modo")
        open(DashboardActivity::class.java, "4-painel")
        runCatching {
            ActivityScenario.launch(DashboardActivity::class.java).use { sc ->
                Thread.sleep(1200)
                sc.onActivity { a ->
                    fun find(v: android.view.View): android.widget.ScrollView? = when (v) {
                        is android.widget.ScrollView -> v
                        is android.view.ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
                        else -> null
                    }
                    val sv = find(a.window.decorView)
                    // rola até o cartão de gastos
                    sv?.let { s -> val c = s.getChildAt(0) as android.view.ViewGroup
                        val target = (0 until c.childCount).map { c.getChildAt(it) }.firstOrNull { v -> (v as? android.view.ViewGroup)?.let { g -> g.childCount > 0 && g.getChildAt(0).let { h -> (h as? android.view.ViewGroup)?.getChildAt(0) as? android.widget.TextView }?.text?.toString()?.contains("GASTOS") == true } == true }
                        s.scrollTo(0, target?.top?.minus(40) ?: c.height / 2) }
                }
                shot("4b-painel-gastos-metas")
            }
        }
        open(SettingsActivity::class.java, "5-ajustes")
        // tema claro
        Settings(ctx).theme = "claro"
        open(ModeActivity::class.java, "6-claro-modo")
        open(DashboardActivity::class.java, "7-claro-painel")
        open(SettingsActivity::class.java, "8-claro-ajustes")
        Account.clear(ctx)
        open(LoginActivity::class.java, "9-claro-login")
        Settings(ctx).theme = "escuro"
    }
}
