
package com.babunator.scanner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class UpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDb(applicationContext)

        return try {
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

                val history = db.getHistory(s)
                val historyInitialized = db.isHistoryInitialized(s)

                var updated = false

                // First attempt.
                try {
                    val rows = if (!historyInitialized) {
                        // First complete initialization:
                        // fetch maximum available daily history.
                        provider.fetchDaily(s, 500)
                    } else {
                        // Already initialized:
                        // fetch only from the last stored date onward.
                        // Including the last date allows today's value
                        // to be refreshed and safely replaced by upsert.
                        val lastDate = history.lastOrNull()
                            ?.date
                            ?.let { LocalDate.parse(it) }

                        if (lastDate != null) {
                            provider.fetchDailySince(s, lastDate)
                        } else {
                            provider.fetchDaily(s, 500)
                        }
                    }

                    db.upsertPrices(s, rows)

                    if (!historyInitialized) {
                        db.markHistoryInitialized(s)
                    }

                    updated = true

                } catch (_: Exception) {
                }

                // Exactly one retry for this stock if the first attempt failed.
                if (!updated) {
                    retryCount++

                    try {
                        val rows = if (!historyInitialized) {
                            provider.fetchDaily(s, 500)
                        } else {
                            val lastDate = history.lastOrNull()
                                ?.date
                                ?.let { LocalDate.parse(it) }

                            if (lastDate != null) {
                                provider.fetchDailySince(s, lastDate)
                            } else {
                                provider.fetchDaily(s, 500)
                            }
                        }

                        db.upsertPrices(s, rows)

                        if (!historyInitialized) {
                            db.markHistoryInitialized(s)
                        }

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
                        .putInt("successful", successful)
                        .putInt("failed", failed)
                        .putInt("retry_count", retryCount)
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
                    lastUpdateTime =
                    LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern(
                            "hh:mm a, dd/MM/yy",
                            Locale.ENGLISH
                        )
                    )
                )

            } else {

                val nextRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
                    .setInputData(
                        Data.Builder()
                            .putInt("offset", processed)
                            .putInt("limit", limit)
                            .build()
                    )
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(
                                androidx.work.NetworkType.CONNECTED
                            )
                            .build()
                    )
                    .build()               
                WorkManager.getInstance(applicationContext)
                    .enqueueUniqueWork(
                        "nse_data_update",
                        ExistingWorkPolicy.APPEND,
                        nextRequest
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

        } catch (_: Exception) {
            // Per-stock retry is already controlled above.
            // No additional automatic Worker retry.
            Result.failure()
        }
    }
}

class ScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDb(applicationContext)
            val cfg = ScanConfigStore.load(applicationContext)
            val symbols = db.symbols()
            val matches = ScannerEngine(db).scan(symbols, cfg)
            db.saveRun(cfg.timeframe, matches)
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
