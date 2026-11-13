package com.farol.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.io.File

/**
 * Banco SQLite do Farol, um por modo: farol.db (moto) e farol_carro.db (carro).
 * Substitui os arquivos history*.jsonl e journal*.json: sem limite de 5.000 ofertas,
 * buscas rápidas e gravação em transação (não corrompe se o celular desligar no meio).
 *
 * Na primeira abertura importa os arquivos antigos e os renomeia para *.antigo (não apaga).
 */
class FarolDb private constructor(private val ctx: Context, val suffix: String) :
    SQLiteOpenHelper(ctx, "farol$suffix.db", null, VERSION) {

    companion object {
        private const val VERSION = 1
        private val cache = mutableMapOf<String, FarolDb>()

        /** Banco do modo atual (moto ou carro). */
        fun get(ctx: Context): FarolDb = of(ctx, Settings(ctx).fileSuffix)

        fun of(ctx: Context, suffix: String): FarolDb = synchronized(cache) {
            cache.getOrPut(suffix) { FarolDb(ctx.applicationContext, suffix) }
        }
    }

    init { setWriteAheadLoggingEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE offers (id INTEGER PRIMARY KEY AUTOINCREMENT, t INTEGER NOT NULL,
            app TEXT, rating TEXT, data TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX offers_t ON offers(t)")
        db.execSQL("""CREATE TABLE days (date TEXT PRIMARY KEY, data TEXT NOT NULL,
            updated_at INTEGER NOT NULL, synced_at INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE fuel (at INTEGER PRIMARY KEY, data TEXT NOT NULL, updated_at INTEGER NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0, synced_at INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE deliveries (id INTEGER PRIMARY KEY AUTOINCREMENT, collect_at INTEGER NOT NULL,
            data TEXT NOT NULL)""")
        db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)")
        importOldFiles(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { /* versões futuras */ }

    // ---------- migração dos arquivos antigos ----------

    private fun importOldFiles(db: SQLiteDatabase) {
        val dir = ctx.filesDir
        val hist = File(dir, "history$suffix.jsonl")
        val journal = File(dir, "journal$suffix.json")
        if (hist.exists()) {
            hist.forEachLine { line ->
                val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                insertOffer(db, o)
            }
        }
        if (journal.exists()) runCatching { importJournal(db, JSONObject(journal.readText()), System.currentTimeMillis()) }
        // Guarda os originais renomeados, por segurança (o backup do app passa a ler do banco).
        if (hist.exists()) hist.renameTo(File(dir, "history$suffix.jsonl.antigo"))
        if (journal.exists()) journal.renameTo(File(dir, "journal$suffix.json.antigo"))
    }

    internal fun insertOffer(db: SQLiteDatabase, o: JSONObject): Long =
        db.insert("offers", null, ContentValues().apply {
            put("t", o.optLong("t")); put("app", o.optString("app")); put("rating", o.optString("rating"))
            put("data", o.toString())
        })

    /** Formato antigo {days:{data:{...}}, fuel:[...], deliveries:[...]} → tabelas. */
    internal fun importJournal(db: SQLiteDatabase, o: JSONObject, now: Long) {
        o.optJSONObject("days")?.let { d ->
            d.keys().forEach { k ->
                db.insertWithOnConflict("days", null, ContentValues().apply {
                    put("date", k); put("data", d.getJSONObject(k).toString()); put("updated_at", now)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
        o.optJSONArray("fuel")?.let { a ->
            for (i in 0 until a.length()) {
                val f = a.getJSONObject(i)
                db.insertWithOnConflict("fuel", null, ContentValues().apply {
                    put("at", f.getLong("at")); put("data", f.toString()); put("updated_at", now)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
        o.optJSONArray("deliveries")?.let { a ->
            for (i in 0 until a.length()) {
                val r = a.getJSONObject(i)
                db.insert("deliveries", null, ContentValues().apply { put("collect_at", r.optLong("c")); put("data", r.toString()) })
            }
        }
    }

    fun meta(k: String): String? = readableDatabase.rawQuery("SELECT v FROM meta WHERE k = ?", arrayOf(k)).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    fun setMeta(k: String, v: String) {
        writableDatabase.insertWithOnConflict("meta", null, ContentValues().apply { put("k", k); put("v", v) },
            SQLiteDatabase.CONFLICT_REPLACE)
    }
}
