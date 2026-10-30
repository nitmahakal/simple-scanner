package com.babunator.scanner

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/*
 * The conditions (ScanConfig) that each scan run was made with.
 *
 * AppDb keeps only a readable text of the conditions for every run. This
 * table keeps the real configuration as well, so a scan opened from the
 * history can be tracked with exactly the conditions it was made with.
 * Runs that were made before this table existed have no entry.
 */
class RunConfigStore(private val appDb: AppDb) {

    init {
        appDb.writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS run_configs(" +
                    "run_id INTEGER PRIMARY KEY NOT NULL, " +
                    "config_json TEXT NOT NULL)"
        )
    }

    fun save(runId: Long, configJson: String) {
        val cv = ContentValues()
        cv.put("run_id", runId)
        cv.put("config_json", configJson)

        appDb.writableDatabase.insertWithOnConflict(
            "run_configs",
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    // Returns null for older runs or when the stored text cannot be read.
    fun get(runId: Long): ScanConfig? {
        val c = appDb.writableDatabase.rawQuery(
            "SELECT config_json FROM run_configs WHERE run_id=?",
            arrayOf(runId.toString())
        )

        val json = c.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: return null

        return try {
            ScanConfigStore.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    // Entries of scans that were deleted from the history are not needed.
    fun deleteOrphans() {
        appDb.writableDatabase.delete(
            "run_configs",
            "run_id NOT IN (SELECT id FROM runs)",
            null
        )
    }
}
