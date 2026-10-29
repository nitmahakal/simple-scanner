package com.babunator.scanner

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class TrackingPending(
    val savedScanId: Long,
    val symbol: String,
    val timeframe: String,
    val kind: String,
    val signalDate: String
)

/*
 * Optional rules of one saved scan.
 *
 * exitConfigJson      : null = default exit (entry conditions stop matching)
 * stopLossPercent     : null = no fixed stop-loss
 * stopLossConfigJson  : null = no condition based stop-loss
 */
data class TrackingRules(
    val savedScanId: Long,
    val exitConfigJson: String?,
    val stopLossPercent: Double?,
    val stopLossConfigJson: String?
)

/*
 * One trade of a tracked scan, from entry to exit.
 *
 * Max up / max down are measured on the candle closes between entry and
 * now (or exit), and always include the entry price itself.
 */
data class TrackedTrade(
    val id: Long,
    val savedScanId: Long,
    val symbol: String,
    val timeframe: String,
    val entryDate: String,
    val entryPrice: Double,
    val exitDate: String?,
    val exitPrice: Double?,
    val lastPrice: Double,
    val highest: Double,
    val lowest: Double,
    val status: String,
    val exitReason: String?
) {
    val resultPrice: Double
        get() = exitPrice ?: lastPrice

    val pnlPercent: Double
        get() =
            if (entryPrice == 0.0) 0.0
            else (resultPrice - entryPrice) / entryPrice * 100.0

    val maxUpPercent: Double
        get() =
            if (entryPrice == 0.0) 0.0
            else (maxOf(highest, entryPrice) - entryPrice) / entryPrice * 100.0

    val maxDownPercent: Double
        get() =
            if (entryPrice == 0.0) 0.0
            else (minOf(lowest, entryPrice) - entryPrice) / entryPrice * 100.0
}

/*
 * Extra tracking data kept outside AppDb, so the database version of
 * AppDb does not have to change. Tables are created here when needed.
 *
 *  tracking_pending: signals that wait for the next candle's open.
 *                    kind = "ENTRY", "EXIT" or "EXIT-SL".
 *  tracking_rules  : exit condition and stop-loss of a saved scan.
 *  tracking_blocked: after an exit by exit-condition or stop-loss, a stock
 *                    is not entered again until the entry conditions
 *                    have turned false once.
 *  trade_log       : every trade (open and completed) with its numbers.
 */
class TrackingStore(private val appDb: AppDb) {

    init {
        ensureTables()
    }

    private fun database(): SQLiteDatabase {
        return appDb.writableDatabase
    }

    private fun ensureTables() {
        val db = database()

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tracking_pending(" +
                    "saved_scan_id INTEGER NOT NULL, " +
                    "symbol TEXT NOT NULL, " +
                    "timeframe TEXT NOT NULL, " +
                    "kind TEXT NOT NULL, " +
                    "signal_date TEXT NOT NULL, " +
                    "PRIMARY KEY(saved_scan_id,symbol,timeframe))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tracking_rules(" +
                    "saved_scan_id INTEGER PRIMARY KEY NOT NULL, " +
                    "exit_json TEXT, " +
                    "sl_percent REAL, " +
                    "sl_json TEXT)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tracking_blocked(" +
                    "saved_scan_id INTEGER NOT NULL, " +
                    "symbol TEXT NOT NULL, " +
                    "timeframe TEXT NOT NULL, " +
                    "PRIMARY KEY(saved_scan_id,symbol,timeframe))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS trade_log(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "saved_scan_id INTEGER NOT NULL, " +
                    "symbol TEXT NOT NULL, " +
                    "timeframe TEXT NOT NULL, " +
                    "entry_date TEXT NOT NULL, " +
                    "entry_price REAL NOT NULL, " +
                    "exit_date TEXT, " +
                    "exit_price REAL, " +
                    "last_price REAL NOT NULL, " +
                    "highest REAL NOT NULL, " +
                    "lowest REAL NOT NULL, " +
                    "status TEXT NOT NULL, " +
                    "exit_reason TEXT)"
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS trade_log_scan " +
                    "ON trade_log(saved_scan_id,status)"
        )
    }

    // ---------------- pending signals ----------------

    fun getPending(savedScanId: Long): List<TrackingPending> {
        val out = mutableListOf<TrackingPending>()

        val c = database().rawQuery(
            "SELECT saved_scan_id,symbol,timeframe,kind,signal_date " +
                    "FROM tracking_pending " +
                    "WHERE saved_scan_id=? " +
                    "ORDER BY symbol",
            arrayOf(savedScanId.toString())
        )

        c.use {
            while (it.moveToNext()) {
                out += TrackingPending(
                    savedScanId = it.getLong(0),
                    symbol = it.getString(1),
                    timeframe = it.getString(2),
                    kind = it.getString(3),
                    signalDate = it.getString(4)
                )
            }
        }

        return out
    }

    fun upsertPending(
        savedScanId: Long,
        symbol: String,
        timeframe: String,
        kind: String,
        signalDate: String
    ) {
        val cv = ContentValues()
        cv.put("saved_scan_id", savedScanId)
        cv.put("symbol", symbol)
        cv.put("timeframe", timeframe)
        cv.put("kind", kind)
        cv.put("signal_date", signalDate)

        database().insertWithOnConflict(
            "tracking_pending",
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deletePending(
        savedScanId: Long,
        symbol: String,
        timeframe: String
    ) {
        database().delete(
            "tracking_pending",
            "saved_scan_id=? AND symbol=? AND timeframe=?",
            arrayOf(savedScanId.toString(), symbol, timeframe)
        )
    }

    fun clearPending(savedScanId: Long) {
        database().delete(
            "tracking_pending",
            "saved_scan_id=?",
            arrayOf(savedScanId.toString())
        )
    }

    // ---------------- rules ----------------

    fun getRules(savedScanId: Long): TrackingRules? {
        val c = database().rawQuery(
            "SELECT saved_scan_id,exit_json,sl_percent,sl_json " +
                    "FROM tracking_rules WHERE saved_scan_id=?",
            arrayOf(savedScanId.toString())
        )

        c.use {
            if (it.moveToFirst()) {
                return TrackingRules(
                    savedScanId = it.getLong(0),
                    exitConfigJson =
                        if (it.isNull(1)) null else it.getString(1),
                    stopLossPercent =
                        if (it.isNull(2)) null else it.getDouble(2),
                    stopLossConfigJson =
                        if (it.isNull(3)) null else it.getString(3)
                )
            }
        }

        return null
    }

    private fun saveRules(rules: TrackingRules) {
        val cv = ContentValues()
        cv.put("saved_scan_id", rules.savedScanId)

        if (rules.exitConfigJson == null) {
            cv.putNull("exit_json")
        } else {
            cv.put("exit_json", rules.exitConfigJson)
        }

        if (rules.stopLossPercent == null) {
            cv.putNull("sl_percent")
        } else {
            cv.put("sl_percent", rules.stopLossPercent)
        }

        if (rules.stopLossConfigJson == null) {
            cv.putNull("sl_json")
        } else {
            cv.put("sl_json", rules.stopLossConfigJson)
        }

        database().insertWithOnConflict(
            "tracking_rules",
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun rulesOrEmpty(savedScanId: Long): TrackingRules {
        return getRules(savedScanId)
            ?: TrackingRules(savedScanId, null, null, null)
    }

    fun setExitConfig(savedScanId: Long, json: String?) {
        saveRules(rulesOrEmpty(savedScanId).copy(exitConfigJson = json))
    }

    fun setStopLossPercent(savedScanId: Long, percent: Double?) {
        saveRules(rulesOrEmpty(savedScanId).copy(stopLossPercent = percent))
    }

    fun setStopLossConfig(savedScanId: Long, json: String?) {
        saveRules(rulesOrEmpty(savedScanId).copy(stopLossConfigJson = json))
    }

    // ---------------- blocked re-entry ----------------

    // Keys are "SYMBOL|Timeframe".
    fun getBlocked(savedScanId: Long): Set<String> {
        val out = mutableSetOf<String>()

        val c = database().rawQuery(
            "SELECT symbol,timeframe FROM tracking_blocked " +
                    "WHERE saved_scan_id=?",
            arrayOf(savedScanId.toString())
        )

        c.use {
            while (it.moveToNext()) {
                out += it.getString(0) + "|" + it.getString(1)
            }
        }

        return out
    }

    fun addBlocked(
        savedScanId: Long,
        symbol: String,
        timeframe: String
    ) {
        val cv = ContentValues()
        cv.put("saved_scan_id", savedScanId)
        cv.put("symbol", symbol)
        cv.put("timeframe", timeframe)

        database().insertWithOnConflict(
            "tracking_blocked",
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun removeBlocked(
        savedScanId: Long,
        symbol: String,
        timeframe: String
    ) {
        database().delete(
            "tracking_blocked",
            "saved_scan_id=? AND symbol=? AND timeframe=?",
            arrayOf(savedScanId.toString(), symbol, timeframe)
        )
    }

    // ---------------- trade log ----------------

    // Keys ("SYMBOL|Timeframe") of the trades that are open right now.
    fun getOpenTradeKeys(savedScanId: Long): Set<String> {
        val out = mutableSetOf<String>()

        val c = database().rawQuery(
            "SELECT symbol,timeframe FROM trade_log " +
                    "WHERE saved_scan_id=? AND status='OPEN'",
            arrayOf(savedScanId.toString())
        )

        c.use {
            while (it.moveToNext()) {
                out += it.getString(0) + "|" + it.getString(1)
            }
        }

        return out
    }

    fun openTrade(
        savedScanId: Long,
        symbol: String,
        timeframe: String,
        entryDate: String,
        entryPrice: Double,
        lastPrice: Double,
        highest: Double,
        lowest: Double
    ) {
        val cv = ContentValues()
        cv.put("saved_scan_id", savedScanId)
        cv.put("symbol", symbol)
        cv.put("timeframe", timeframe)
        cv.put("entry_date", entryDate)
        cv.put("entry_price", entryPrice)
        cv.putNull("exit_date")
        cv.putNull("exit_price")
        cv.put("last_price", lastPrice)
        cv.put("highest", highest)
        cv.put("lowest", lowest)
        cv.put("status", "OPEN")
        cv.putNull("exit_reason")

        database().insert("trade_log", null, cv)
    }

    fun updateOpenTrade(
        savedScanId: Long,
        symbol: String,
        timeframe: String,
        lastPrice: Double,
        highest: Double,
        lowest: Double
    ) {
        val cv = ContentValues()
        cv.put("last_price", lastPrice)
        cv.put("highest", highest)
        cv.put("lowest", lowest)

        database().update(
            "trade_log",
            cv,
            "saved_scan_id=? AND symbol=? AND timeframe=? AND status='OPEN'",
            arrayOf(savedScanId.toString(), symbol, timeframe)
        )
    }

    fun closeTrade(
        savedScanId: Long,
        symbol: String,
        timeframe: String,
        exitDate: String,
        exitPrice: Double,
        highest: Double,
        lowest: Double,
        reason: String
    ) {
        val cv = ContentValues()
        cv.put("exit_date", exitDate)
        cv.put("exit_price", exitPrice)
        cv.put("last_price", exitPrice)
        cv.put("highest", highest)
        cv.put("lowest", lowest)
        cv.put("status", "CLOSED")
        cv.put("exit_reason", reason)

        database().update(
            "trade_log",
            cv,
            "saved_scan_id=? AND symbol=? AND timeframe=? AND status='OPEN'",
            arrayOf(savedScanId.toString(), symbol, timeframe)
        )
    }

    // status = "OPEN" or "CLOSED". Newest first.
    fun getTrades(savedScanId: Long, status: String): List<TrackedTrade> {
        val out = mutableListOf<TrackedTrade>()

        val order =
            if (status == "OPEN") "entry_date DESC, symbol"
            else "exit_date DESC, symbol"

        val c = database().rawQuery(
            "SELECT id,saved_scan_id,symbol,timeframe,entry_date," +
                    "entry_price,exit_date,exit_price,last_price," +
                    "highest,lowest,status,exit_reason " +
                    "FROM trade_log " +
                    "WHERE saved_scan_id=? AND status=? " +
                    "ORDER BY $order",
            arrayOf(savedScanId.toString(), status)
        )

        c.use {
            while (it.moveToNext()) {
                out += TrackedTrade(
                    id = it.getLong(0),
                    savedScanId = it.getLong(1),
                    symbol = it.getString(2),
                    timeframe = it.getString(3),
                    entryDate = it.getString(4),
                    entryPrice = it.getDouble(5),
                    exitDate = if (it.isNull(6)) null else it.getString(6),
                    exitPrice = if (it.isNull(7)) null else it.getDouble(7),
                    lastPrice = it.getDouble(8),
                    highest = it.getDouble(9),
                    lowest = it.getDouble(10),
                    status = it.getString(11),
                    exitReason = if (it.isNull(12)) null else it.getString(12)
                )
            }
        }

        return out
    }

    // ---------------- edit the entry conditions of a saved scan ----------------

    fun updateSavedScanConfig(
        savedScanId: Long,
        configJson: String,
        timeframe: String,
        conditions: String
    ) {
        val cv = ContentValues()
        cv.put("config_json", configJson)
        cv.put("timeframe", timeframe)
        cv.put("conditions", conditions)

        database().update(
            "saved_scans",
            cv,
            "id=?",
            arrayOf(savedScanId.toString())
        )

        clearPending(savedScanId)

        database().delete(
            "tracking_blocked",
            "saved_scan_id=?",
            arrayOf(savedScanId.toString())
        )
    }

    // Rows of scans that were deleted are not needed any more.
    fun deleteOrphans() {
        val db = database()

        val gone = "saved_scan_id NOT IN (SELECT id FROM saved_scans)"

        db.delete("tracking_pending", gone, null)
        db.delete("tracking_rules", gone, null)
        db.delete("tracking_blocked", gone, null)
        db.delete("trade_log", gone, null)
    }
}
