package com.babunator.scanner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters

class UpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDb(applicationContext); val provider = DataProvider()
            val symbols = provider.fetchSymbolList()
            val offset = inputData.getInt("offset", 0); val limit = inputData.getInt("limit", 25)
            val part = symbols.drop(offset).take(limit)
            for (s in part) {
                val existing = db.getHistory(s, 3)
                val days = if (db.getHistory(s, 60).size < 60) 500 else 15
                try { db.upsertPrices(s, provider.fetchDaily(s, days)) } catch (_: Exception) { }
                setProgress(Data.Builder().putInt("done", offset + 1).putInt("total", symbols.size).build())
            }
            Result.success(Data.Builder().putInt("total", symbols.size).build())
        } catch (e: Exception) { Result.retry() }
    }
}

class ScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDb(applicationContext)
            val cfg = ScanConfigStore.load(applicationContext)
            val symbols = db.symbols()
            val matches = ScannerEngine(db).scan(symbols, cfg)
            db.saveRun(cfg.timeframe, matches)
            Result.success()
        } catch (e: Exception) { Result.failure() }
    }
}
