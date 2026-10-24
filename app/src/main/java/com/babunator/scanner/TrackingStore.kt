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
 * Signals that wait for the next candle's open price.
 *
 * kind = "ENTRY": confirmed signal, entry happens at the next open.
 * kind = "EXIT" : exit signal, the position is closed at the next open.
 *
 * The table is created here when needed, so the database version of
 * AppDb does not have to change.
 */
class TrackingStore(private val appDb: AppDb) {

    init {
        ensureTable()
    }

    private fun database(): SQLiteDatabase {
        return appDb.writableDatabase
    }

    private fun ensureTable() {
        database().execSQL(
            "CREATE TABLE IF NOT EXISTS tracking_pending(" +
                    "saved_scan_id INTEGER NOT NULL, " +
                    "symbol TEXT NOT NULL, " +
                    "timeframe TEXT NOT NULL, " +
                    "kind TEXT NOT NULL, " +
                    "signal_date TEXT NOT NULL, " +
                    "PRIMARY KEY(saved_scan_id,symbol,timeframe))"
        )
    }

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

    // Pending rows of scans that were deleted are not needed any more.
    fun deleteOrphans() {
        database().delete(
            "tracking_pending",
            "saved_scan_id NOT IN (SELECT id FROM saved_scans)",
            null
        )
    }
}
