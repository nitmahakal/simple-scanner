package com.babunator.scanner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters

class UpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDb(applicationContext)
            val provider = DataProvider()
            val symbols = provider.fetchSymbolList()

            val offset = inputData.getInt("offset", 0)
            val limit = inputData.getInt("limit", 25)
            val part = symbols.drop(offset).take(limit)

            var processed = offset
            var successful = 0
            var failed = 0
            var retryCount = 0

            val previous = db.getUpdateStatus()
            if (offset == 0) {
                db.saveUpdateStatus(
                    total = symbols.size,
                    processed = 0,
                    successful = 0,
                    failed = 0,
                    retryCount = 0
                )
            } else if (previous != null) {
                successful = previous.successful
                failed = previous.failed
                retryCount = previous.retryCount
            }

            for (s in part) {
                val days = if (db.getHistory(s, 60).size < 60) 500 else 15

                var updated = false

                try {
                    db.upsertPrices(s, provider.fetchDaily(s, days))
                    updated = true
                } catch (_: Exception) {
                    retryCount++

                    try {
                        db.upsertPrices(s, provider.fetchDaily(s, days))
                        updated = true
                    } catch (_: Exception) {
                    }
                }

                if (updated) {
                    successful++
                } else {
                    failed++
                }

                processed++

                db.saveUpdateStatus(
                    total = symbols.size,
                    processed = processed,
                    successful = successful,
                    failed = failed,
                    retryCount = retryCount
                )

                setProgress(
                    Data.Builder()
                        .putInt("done", processed)
                        .putInt("total", symbols.size)
                        .build()
                )
            }

            if (processed >= symbols.size) {
                val finalStatus = db.getUpdateStatus()
                db.saveUpdateStatus(
                    total = symbols.size,
                    processed = processed,
                    successful = finalStatus?.successful ?: successful,
                    failed = finalStatus?.failed ?: failed,
                    retryCount = finalStatus?.retryCount ?: retryCount,
                    lastUpdateTime = java.time.LocalDateTime.now().toString()
                )
            }


            Result.success(
                Data.Builder()
                    .putInt("total", symbols.size)
                    .putInt("done", processed)
                    .putInt("successful", successful)
                    .putInt("failed", failed)
                    .putInt("retry_count", retryCount)
                    .build()
            )
        } catch (e: Exception) {
            Result.retry()
        }
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
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
