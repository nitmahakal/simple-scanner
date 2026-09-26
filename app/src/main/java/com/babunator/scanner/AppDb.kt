
package com.babunator.scanner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDb(context: Context) : SQLiteOpenHelper(context, "scanner.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE prices(symbol TEXT NOT NULL, date TEXT NOT NULL, close REAL NOT NULL, PRIMARY KEY(symbol,date))")
        db.execSQL("CREATE TABLE runs(id INTEGER PRIMARY KEY AUTOINCREMENT, created TEXT NOT NULL, timeframe TEXT NOT NULL, matched INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE results(run_id INTEGER NOT NULL, symbol TEXT NOT NULL, timeframe TEXT NOT NULL, close REAL NOT NULL, note TEXT)")
        db.execSQL("CREATE TABLE update_status(id INTEGER PRIMARY KEY, total INTEGER NOT NULL DEFAULT 0, processed INTEGER NOT NULL DEFAULT 0, successful INTEGER NOT NULL DEFAULT 0, failed INTEGER NOT NULL DEFAULT 0, retry_count INTEGER NOT NULL DEFAULT 0, last_update_time TEXT)")
        db.execSQL("CREATE TABLE history_init(symbol TEXT PRIMARY KEY NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS update_status(id INTEGER PRIMARY KEY, total INTEGER NOT NULL DEFAULT 0, processed INTEGER NOT NULL DEFAULT 0, successful INTEGER NOT NULL DEFAULT 0, failed INTEGER NOT NULL DEFAULT 0, retry_count INTEGER NOT NULL DEFAULT 0, last_update_time TEXT)")
        }

        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS history_init(symbol TEXT PRIMARY KEY NOT NULL)")
        }
    }

    fun upsertPrices(symbol: String, rows: List<Candle>) {
        writableDatabase.beginTransaction()
        try {
            val cv = ContentValues()
            for (r in rows) {
                cv.clear()
                cv.put("symbol", symbol)
                cv.put("date", r.date)
                cv.put("close", r.close)
                writableDatabase.insertWithOnConflict(
                    "prices",
                    null,
                    cv,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun isHistoryInitialized(symbol: String): Boolean {
        val c = readableDatabase.rawQuery(
            "SELECT 1 FROM history_init WHERE symbol=? LIMIT 1",
            arrayOf(symbol)
        )

        c.use {
            return it.moveToFirst()
        }
    }

    fun markHistoryInitialized(symbol: String) {
        val cv = ContentValues()
        cv.put("symbol", symbol)

        writableDatabase.insertWithOnConflict(
            "history_init",
            null,
            cv,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun getHistory(symbol: String): List<Candle> {
        val out = mutableListOf<Candle>()
        val c = readableDatabase.rawQuery(
            "SELECT date,close FROM prices WHERE symbol=? ORDER BY date ASC",
            arrayOf(symbol)
        )

        c.use {
            while (it.moveToNext()) {
                out += Candle(it.getString(0), it.getDouble(1))
            }
        }

        return out
    }

    fun symbols(): List<String> {
        val out = mutableListOf<String>()
        val c = readableDatabase.rawQuery(
            "SELECT DISTINCT symbol FROM prices ORDER BY symbol",
            null
        )

        c.use {
            while (it.moveToNext()) {
                out += it.getString(0)
            }
        }

        return out
    }

    fun saveRun(timeframe: String, matches: List<Match>): Long {
        val cv = ContentValues()
        cv.put("created", java.time.LocalDateTime.now().toString())
        cv.put("timeframe", timeframe)
        cv.put("matched", matches.size)

        val id = writableDatabase.insert("runs", null, cv)

        for (m in matches) {
            val r = ContentValues()
            r.put("run_id", id)
            r.put("symbol", m.symbol)
            r.put("timeframe", m.timeframe)
            r.put("close", m.close)
            r.put("note", m.note)

            writableDatabase.insert("results", null, r)
        }

        return id
    }

    fun recentRuns(limit: Int = 30): List<String> {
        val out = mutableListOf<String>()
        val c = readableDatabase.rawQuery(
            "SELECT id,created,timeframe,matched FROM runs ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        )

        c.use {
            while (it.moveToNext()) {
                out += "#${it.getLong(0)}  ${it.getString(1)}  ${it.getString(2)}  matched=${it.getInt(3)}"
            }
        }

        return out
    }

    fun results(runId: Long): List<String> {
        val out = mutableListOf<String>()
        val c = readableDatabase.rawQuery(
            "SELECT symbol,timeframe,close,note FROM results WHERE run_id=? ORDER BY symbol",
            arrayOf(runId.toString())
        )

        c.use {
            while (it.moveToNext()) {
                out += "${it.getString(0)}  ${it.getString(1)}  close=${it.getDouble(2)}  ${it.getString(3) ?: ""}"
            }
        }

        return out
    }

    data class UpdateStatus(
        val total: Int,
        val processed: Int,
        val successful: Int,
        val failed: Int,
        val retryCount: Int,
        val lastUpdateTime: String?
    )

    fun saveUpdateStatus(
        total: Int,
        processed: Int,
        successful: Int,
        failed: Int,
        retryCount: Int,
        lastUpdateTime: String? = null
    ) {
        val cv = ContentValues()
        cv.put("id", 1)
        cv.put("total", total)
        cv.put("processed", processed)
        cv.put("successful", successful)
        cv.put("failed", failed)
        cv.put("retry_count", retryCount)

        if (lastUpdateTime == null) {
            cv.putNull("last_update_time")
        } else {
            cv.put("last_update_time", lastUpdateTime)
        }

        writableDatabase.insertWithOnConflict(
            "update_status",
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getUpdateStatus(): UpdateStatus? {
        val c = readableDatabase.rawQuery(
            "SELECT total,processed,successful,failed,retry_count,last_update_time FROM update_status WHERE id=1",
            null
        )

        c.use {
            if (it.moveToFirst()) {
                return UpdateStatus(
                    total = it.getInt(0),
                    processed = it.getInt(1),
                    successful = it.getInt(2),
                    failed = it.getInt(3),
                    retryCount = it.getInt(4),
                    lastUpdateTime = if (it.isNull(5)) null else it.getString(5)
                )
            }
        }

        return null
    }
}
