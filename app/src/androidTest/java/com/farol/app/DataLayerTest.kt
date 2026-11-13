package com.farol.app

import android.app.job.JobScheduler
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.farol.app.core.CostProfile
import com.farol.app.core.DayRecord
import com.farol.app.core.FuelEntry
import com.farol.app.core.OfferParser
import com.farol.app.core.ProfitCalculator
import com.farol.app.data.Backup
import com.farol.app.data.FarolDb
import com.farol.app.data.History
import com.farol.app.data.JournalStore
import com.farol.app.data.SecureStore
import com.farol.app.data.Settings
import com.farol.app.service.FarolJobs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Roda num Android de verdade (emulador no GitHub): banco, migração, sincronização, cofre e tarefas. */
@RunWith(AndroidJUnit4::class)
class DataLayerTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun migraArquivosAntigosParaOBanco() {
        val sfx = "_t" + System.nanoTime()
        File(ctx.filesDir, "history$sfx.jsonl").writeText(
            """{"t":1000,"app":"iFood","price":10.0,"km":5.0,"rating":"GREEN"}""" + "\n" +
            """{"t":2000,"app":"Keeta","price":12.0,"km":6.0,"rating":"RED"}""" + "\n" + "lixo sem json\n")
        File(ctx.filesDir, "journal$sfx.json").writeText(JSONObject()
            .put("days", JSONObject().put("2026-11-12", JSONObject().put("kmStart", 100.0).put("kmEnd", 150.0)
                .put("earnings", JSONObject().put("iFood", 80.0))))
            .put("fuel", org.json.JSONArray().put(JSONObject().put("at", 5L).put("odo", 100.0).put("liters", 5.0).put("total", 30.0).put("full", true)))
            .put("deliveries", org.json.JSONArray().put(JSONObject().put("app", "Keeta").put("rest", "").put("c", 10L).put("d", 20L).put("e", 30L)))
            .toString())
        val db = FarolDb.of(ctx, sfx)
        val n = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM offers", null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(2, n)
        val store = JournalStore(ctx, sfx)
        val d = store.day("2026-11-12")
        assertEquals(50.0, d.kmDriven!!, 0.001)
        assertEquals(80.0, d.totalEarnings, 0.001)
        assertEquals(1, store.fuel().size)
        assertEquals(1, store.deliveries().size)
        assertTrue(File(ctx.filesDir, "history$sfx.jsonl.antigo").exists())
        assertFalse(File(ctx.filesDir, "history$sfx.jsonl").exists())
    }

    @Test fun diarioSincronizacaoPendenteEMaisNovoVence() {
        val sfx = "_t" + System.nanoTime()
        val s = JournalStore(ctx, sfx)
        s.putDay(DayRecord("2026-11-13", kmStart = 10.0, earnings = mapOf("99" to 20.0)))
        s.addFuel(FuelEntry(77L, 10.0, 4.0, 24.0, true))
        var p = s.pendingSync()
        assertEquals(1, p.days.size); assertEquals(1, p.fuel.size)
        s.markSynced(p.maxUpdated)
        assertEquals(0, s.pendingSync().days.size)
        // remoto mais velho é ignorado; mais novo entra
        val old = JSONObject().put("kmStart", 1.0).put("earnings", JSONObject().put("99", 1.0))
        s.applyRemote(listOf(Triple("2026-11-13", old, 1L)), emptyList())
        assertEquals(20.0, s.day("2026-11-13").totalEarnings, 0.001)
        val newer = JSONObject().put("kmStart", 10.0).put("earnings", JSONObject().put("99", 55.0))
        s.applyRemote(listOf(Triple("2026-11-13", newer, System.currentTimeMillis() + 60_000)), emptyList())
        assertEquals(55.0, s.day("2026-11-13").totalEarnings, 0.001)
        assertEquals(0, s.pendingSync().days.size)          // o que veio do servidor não volta para ele
        // apagar abastecimento marca para sincronizar e some da lista
        s.removeLastFuel()
        assertEquals(0, s.fuel().size)
        p = s.pendingSync()
        assertTrue(p.fuel.single().deleted)
    }

    @Test fun historicoGravaLeEApagaRepetidas() {
        val h = History(ctx)
        h.clear()
        val offer = OfferParser.parse("R$ 15,00 | 5 km | 20 min | Aceitar", "99")!!
        val r = ProfitCalculator.evaluate(offer, CostProfile())
        assertTrue(h.add(r, "teste"))
        assertFalse(h.add(r, "teste"))          // repetida em menos de 3 min
        assertEquals(1, h.recent(10).size)
        assertEquals(15.0, h.recent(1).first().optDouble("price"), 0.001)
        assertTrue(h.toCsv().lines().size >= 2)
    }

    @Test fun backupIdaEVolta() {
        val store = JournalStore(ctx, "")
        store.putDay(DayRecord("2026-11-01", kmStart = 1.0, kmEnd = 9.0, earnings = mapOf("iFood" to 33.0)))
        val json = Backup.export(ctx)
        store.putDay(DayRecord("2026-11-01", earnings = mapOf("iFood" to 1.0)))
        Backup.restore(ctx, json)
        assertEquals(33.0, JournalStore(ctx, "").day("2026-11-01").totalEarnings, 0.001)
        assertFalse(json.contains("ghToken"))
    }

    @Test fun cofreGuardaECifra() {
        SecureStore.put(ctx, "teste", "segredo-123")
        assertEquals("segredo-123", SecureStore.get(ctx, "teste"))
        val raw = SecureStore.plain(ctx).getString("teste", "")!!
        assertFalse(raw.contains("segredo"))
        SecureStore.put(ctx, "teste", null)
        assertNull(SecureStore.get(ctx, "teste"))
    }

    @Test fun ajustesDeOutroModoSemTrocarOModo() {
        val s = Settings(ctx)
        s.vehicleMode = "moto"
        assertTrue(Settings(ctx, "carro").isCar)
        assertEquals("moto", s.vehicleMode)
        assertNotNull(Settings(ctx, "carro").profile)
    }

    @Test fun tarefasDeFundoAgendadas() {
        FarolJobs.scheduleAll(ctx)
        val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        assertNotNull(js.getPendingJob(FarolJobs.LOGS))
        assertNotNull(js.getPendingJob(FarolJobs.SYNC))
        assertNotNull(js.getPendingJob(FarolJobs.UPDATE))
    }
}
