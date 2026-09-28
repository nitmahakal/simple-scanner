package com.babunator.scanner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDb(context: Context) : SQLiteOpenHelper(context, "scanner.db", null, 5) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE prices(" +
                    "symbol TEXT NOT NULL, " +
                    "date TEXT NOT NULL, " +
                    "close REAL NOT NULL, " +
                    "PRIMARY KEY(symbol,date))"
        )

        db.execSQL(
            "CREATE TABLE runs(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "created TEXT NOT NULL, " +
                    "timeframe TEXT NOT NULL, " +
                    "matched INTEGER NOT NULL, " +
                    "conditions TEXT NOT NULL DEFAULT '')"
        )

        db.execSQL(
            "CREATE TABLE results(" +
                    "run_id INTEGER NOT NULL, " +
                    "symbol TEXT NOT NULL, " +
                    "timeframe TEXT NOT NULL, " +
                    "close REAL NOT NULL, " +
                    "note TEXT)"
        )

        db.execSQL(
            "CREATE TABLE update_status(" +
                    "id INTEGER PRIMARY KEY, " +
                    "total INTEGER NOT NULL DEFAULT 0, " +
                    "processed INTEGER NOT NULL DEFAULT 0, " +
                    "successful INTEGER NOT NULL DEFAULT 0, " +
                    "failed INTEGER NOT NULL DEFAULT 0, " +
                    "retry_count INTEGER NOT NULL DEFAULT 0, " +
                    "last_update_time TEXT)"
        )

        db.execSQL(
            "CREATE TABLE history_init(" +
                    "symbol TEXT PRIMARY KEY NOT NULL)"
        )

        createSavedScanTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS update_status(" +
                        "id INTEGER PRIMARY KEY, " +
                        "total INTEGER NOT NULL DEFAULT 0, " +
                        "processed INTEGER NOT NULL DEFAULT 0, " +
                        "successful INTEGER NOT NULL DEFAULT 0, " +
                        "failed INTEGER NOT NULL DEFAULT 0, " +
                        "retry_count INTEGER NOT NULL DEFAULT 0, " +
                        "last_update_time TEXT)"
            )
        }

        if (oldVersion < 3) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS history_init(" +
                        "symbol TEXT PRIMARY KEY NOT NULL)"
            )
        }

        if (oldVersion < 4) {
            db.execSQL(
                "ALTER TABLE runs ADD COLUMN conditions TEXT NOT NULL DEFAULT ''"
            )
        }

        if (oldVersion < 5) {
            createSavedScanTables(db)
        }
    }

    private fun createSavedScanTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS saved_scans(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "created TEXT NOT NULL, " +
                    "config_json TEXT NOT NULL, " +
                    "timeframe TEXT NOT NULL, " +
                    "conditions TEXT NOT NULL DEFAULT '', " +
                    "auto_track INTEGER NOT NULL DEFAULT 0, " +
                    "status TEXT NOT NULL DEFAULT 'ACTIVE')"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS saved_scan_results(" +
                    "saved_scan_id INTEGER NOT NULL, " +
                    "symbol TEXT NOT NULL, " +
                    "timeframe TEXT NOT NULL, " +
                    "close REAL NOT NULL, " +
                    "note TEXT)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tracking_runs(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "saved_scan_id INTEGER NOT NULL, " +
                    "created TEXT NOT NULL, " +
                    "matched INTEGER NOT NULL DEFAULT 0, " +
                    "status TEXT NOT NULL DEFAULT 'ACTIVE')"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tracking_results(" +
                    "tracking_run_id INTEGER NOT NULL, " +
                    "symbol TEXT NOT NULL, " +
                    "timeframe TEXT NOT NULL, " +
                    "close REAL NOT NULL, " +
                    "note TEXT)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tracking_state(" +
                    "saved_scan_id INTEGER PRIMARY KEY NOT NULL, " +
                    "entry_price REAL, " +
                    "current_price REAL, " +
                    "pnl REAL, " +
                    "status TEXT NOT NULL DEFAULT 'ACTIVE', " +
                    "updated TEXT NOT NULL)"
        )
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
                out += Candle(
                    it.getString(0),
                    it.getDouble(1)
                )
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

    fun saveRun(
        timeframe: String,
        matches: List<Match>,
        conditions: String = ""
    ): Long {
        val cv = ContentValues()
        cv.put("created", java.time.LocalDateTime.now().toString())
        cv.put("timeframe", timeframe)
        cv.put("matched", matches.size)
        cv.put("conditions", conditions)

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
            "SELECT id,created,timeframe,matched,conditions " +
                    "FROM runs ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        )

        c.use {
            while (it.moveToNext()) {
                out += "#${it.getLong(0)}  ${it.getString(1)}  " +
                        "${it.getString(2)}  matched=${it.getInt(3)}"
            }
        }

        return out
    }

    fun runConditions(runId: Long): String {
        val c = readableDatabase.rawQuery(
            "SELECT conditions FROM runs WHERE id=?",
            arrayOf(runId.toString())
        )

        c.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }

        return ""
    }

    fun results(runId: Long): List<String> {
        val out = mutableListOf<String>()

        val c = readableDatabase.rawQuery(
            "SELECT symbol,timeframe,close,note " +
                    "FROM results WHERE run_id=? ORDER BY symbol",
            arrayOf(runId.toString())
        )

        c.use {
            while (it.moveToNext()) {
                out += "${it.getString(0)}  ${it.getString(1)}  " +
                        "close=${it.getDouble(2)}  ${it.getString(3) ?: ""}"
            }
        }

        return out
    }

    fun resultMatches(runId: Long): List<Match> {
        val out = mutableListOf<Match>()
    
        val c = readableDatabase.rawQuery(
            "SELECT symbol,timeframe,close,note " +
                    "FROM results WHERE run_id=? ORDER BY symbol",
            arrayOf(runId.toString())
        )
    
        c.use {
            while (it.moveToNext()) {
                out += Match(
                    symbol = it.getString(0),
                    timeframe = it.getString(1),
                    close = it.getDouble(2),
                    note = it.getString(3) ?: ""
                )
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
            "SELECT total,processed,successful,failed,retry_count,last_update_time " +
                    "FROM update_status WHERE id=1",
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
                    lastUpdateTime = if (it.isNull(5)) {
                        null
                    } else {
                        it.getString(5)
                    }
                )
            }
        }

        return null
    }

    data class SavedScanRecord(
        val id: Long,
        val created: String,
        val configJson: String,
        val timeframe: String,
        val conditions: String,
        val autoTrack: Boolean,
        val status: String
    )

    data class TrackingRunRecord(
        val id: Long,
        val savedScanId: Long,
        val created: String,
        val matched: Int,
        val status: String
    )

    data class TrackingState(
        val savedScanId: Long,
        val entryPrice: Double?,
        val currentPrice: Double?,
        val pnl: Double?,
        val status: String,
        val updated: String
    )

    fun saveSavedScan(
        configJson: String,
        timeframe: String,
        conditions: String,
        matches: List<Match>,
        autoTrack: Boolean = false
    ): Long {
        val db = writableDatabase

        db.beginTransaction()

        try {
            val cv = ContentValues()
            cv.put("created", java.time.LocalDateTime.now().toString())
            cv.put("config_json", configJson)
            cv.put("timeframe", timeframe)
            cv.put("conditions", conditions)
            cv.put("auto_track", if (autoTrack) 1 else 0)
            cv.put("status", "ACTIVE")

            val savedScanId = db.insert("saved_scans", null, cv)

            for (m in matches) {
                val r = ContentValues()
                r.put("saved_scan_id", savedScanId)
                r.put("symbol", m.symbol)
                r.put("timeframe", m.timeframe)
                r.put("close", m.close)
                r.put("note", m.note)

                db.insert("saved_scan_results", null, r)
            }

            db.setTransactionSuccessful()
            return savedScanId
        } finally {
            db.endTransaction()
        }
    }

    fun getSavedScans(): List<SavedScanRecord> {
        val out = mutableListOf<SavedScanRecord>()

        val c = readableDatabase.rawQuery(
            "SELECT id,created,config_json,timeframe,conditions,auto_track,status " +
                    "FROM saved_scans ORDER BY id DESC",
            null
        )

        c.use {
            while (it.moveToNext()) {
                out += SavedScanRecord(
                    id = it.getLong(0),
                    created = it.getString(1),
                    configJson = it.getString(2),
                    timeframe = it.getString(3),
                    conditions = it.getString(4),
                    autoTrack = it.getInt(5) != 0,
                    status = it.getString(6)
                )
            }
        }

        return out
    }

    fun getSavedScan(savedScanId: Long): SavedScanRecord? {
        val c = readableDatabase.rawQuery(
            "SELECT id,created,config_json,timeframe,conditions,auto_track,status " +
                    "FROM saved_scans WHERE id=?",
            arrayOf(savedScanId.toString())
        )

        c.use {
            if (it.moveToFirst()) {
                return SavedScanRecord(
                    id = it.getLong(0),
                    created = it.getString(1),
                    configJson = it.getString(2),
                    timeframe = it.getString(3),
                    conditions = it.getString(4),
                    autoTrack = it.getInt(5) != 0,
                    status = it.getString(6)
                )
            }
        }

        return null
    }

    fun setSavedScanAutoTrack(
        savedScanId: Long,
        enabled: Boolean
    ) {
        val cv = ContentValues()
        cv.put("auto_track", if (enabled) 1 else 0)

        writableDatabase.update(
            "saved_scans",
            cv,
            "id=?",
            arrayOf(savedScanId.toString())
        )
    }

    fun getAutoTrackSavedScans(): List<SavedScanRecord> {
        val out = mutableListOf<SavedScanRecord>()

        val c = readableDatabase.rawQuery(
            "SELECT id,created,config_json,timeframe,conditions,auto_track,status " +
                    "FROM saved_scans " +
                    "WHERE auto_track=1 AND status='ACTIVE' " +
                    "ORDER BY id",
            null
        )

        c.use {
            while (it.moveToNext()) {
                out += SavedScanRecord(
                    id = it.getLong(0),
                    created = it.getString(1),
                    configJson = it.getString(2),
                    timeframe = it.getString(3),
                    conditions = it.getString(4),
                    autoTrack = it.getInt(5) != 0,
                    status = it.getString(6)
                )
            }
        }

        return out
    }

    fun savedScanResults(savedScanId: Long): List<String> {
        val out = mutableListOf<String>()

        val c = readableDatabase.rawQuery(
            "SELECT symbol,timeframe,close,note " +
                    "FROM saved_scan_results " +
                    "WHERE saved_scan_id=? ORDER BY symbol",
            arrayOf(savedScanId.toString())
        )

        c.use {
            while (it.moveToNext()) {
                out += "${it.getString(0)}  ${it.getString(1)}  " +
                        "close=${it.getDouble(2)}  ${it.getString(3) ?: ""}"
            }
        }

        return out
    }

    fun saveTrackingRun(
        savedScanId: Long,
        matches: List<Match>,
        status: String = "ACTIVE"
    ): Long {
        val db = writableDatabase

        db.beginTransaction()

        try {
            val cv = ContentValues()
            cv.put("saved_scan_id", savedScanId)
            cv.put("created", java.time.LocalDateTime.now().toString())
            cv.put("matched", matches.size)
            cv.put("status", status)

            val trackingRunId = db.insert("tracking_runs", null, cv)

            for (m in matches) {
                val r = ContentValues()
                r.put("tracking_run_id", trackingRunId)
                r.put("symbol", m.symbol)
                r.put("timeframe", m.timeframe)
                r.put("close", m.close)
                r.put("note", m.note)

                db.insert("tracking_results", null, r)
            }

            db.setTransactionSuccessful()
            return trackingRunId
        } finally {
            db.endTransaction()
        }
    }

    fun recentTrackingRuns(
        savedScanId: Long,
        limit: Int = 30
    ): List<TrackingRunRecord> {
        val out = mutableListOf<TrackingRunRecord>()

        val c = readableDatabase.rawQuery(
            "SELECT id,saved_scan_id,created,matched,status " +
                    "FROM tracking_runs " +
                    "WHERE saved_scan_id=? " +
                    "ORDER BY id DESC LIMIT ?",
            arrayOf(
                savedScanId.toString(),
                limit.toString()
            )
        )

        c.use {
            while (it.moveToNext()) {
                out += TrackingRunRecord(
                    id = it.getLong(0),
                    savedScanId = it.getLong(1),
                    created = it.getString(2),
                    matched = it.getInt(3),
                    status = it.getString(4)
                )
            }
        }

        return out
    }

    fun updateTrackingState(
        savedScanId: Long,
        entryPrice: Double?,
        currentPrice: Double?,
        pnl: Double?,
        status: String
    ) {
        val cv = ContentValues()
        cv.put("saved_scan_id", savedScanId)

        if (entryPrice == null) {
            cv.putNull("entry_price")
        } else {
            cv.put("entry_price", entryPrice)
        }

        if (currentPrice == null) {
            cv.putNull("current_price")
        } else {
            cv.put("current_price", currentPrice)
        }

        if (pnl == null) {
            cv.putNull("pnl")
        } else {
            cv.put("pnl", pnl)
        }

        cv.put("status", status)
        cv.put("updated", java.time.LocalDateTime.now().toString())

        writableDatabase.insertWithOnConflict(
            "tracking_state",
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getTrackingState(savedScanId: Long): TrackingState? {
        val c = readableDatabase.rawQuery(
            "SELECT saved_scan_id,entry_price,current_price,pnl,status,updated " +
                    "FROM tracking_state WHERE saved_scan_id=?",
            arrayOf(savedScanId.toString())
        )

        c.use {
            if (it.moveToFirst()) {
                return TrackingState(
                    savedScanId = it.getLong(0),
                    entryPrice = if (it.isNull(1)) null else it.getDouble(1),
                    currentPrice = if (it.isNull(2)) null else it.getDouble(2),
                    pnl = if (it.isNull(3)) null else it.getDouble(3),
                    status = it.getString(4),
                    updated = it.getString(5)
                )
            }
        }

        return null
    }

    fun setSavedScanStatus(
        savedScanId: Long,
        status: String
    ) {
        val cv = ContentValues()
        cv.put("status", status)

        writableDatabase.update(
            "saved_scans",
            cv,
            "id=?",
            arrayOf(savedScanId.toString())
        )
    }

    fun deleteSavedScan(savedScanId: Long) {
        val db = writableDatabase

        db.beginTransaction()

        try {
            db.delete(
                "tracking_results",
                "tracking_run_id IN " +
                        "(SELECT id FROM tracking_runs WHERE saved_scan_id=?)",
                arrayOf(savedScanId.toString())
            )

            db.delete(
                "tracking_runs",
                "saved_scan_id=?",
                arrayOf(savedScanId.toString())
            )

            db.delete(
                "tracking_state",
                "saved_scan_id=?",
                arrayOf(savedScanId.toString())
            )

            db.delete(
                "saved_scan_results",
                "saved_scan_id=?",
                arrayOf(savedScanId.toString())
            )

            db.delete(
                "saved_scans",
                "id=?",
                arrayOf(savedScanId.toString())
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
