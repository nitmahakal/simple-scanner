package com.babunator.scanner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
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
import kotlin.math.abs

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

            // true only for the scheduled daily run: after the last chunk,
            // a daily scan is saved first and then auto tracking runs.
            // A manual update runs auto tracking only.
            val chain = inputData.getBoolean("chain", false)

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

                try {
                    val rows = loadRows(
                        provider,
                        s,
                        history,
                        historyInitialized
                    )

                    db.upsertPrices(s, rows)

                    if (!historyInitialized) {
                        db.markHistoryInitialized(s)
                    }

                    updated = true

                } catch (_: Exception) {
                }

                if (!updated) {
                    retryCount++

                    try {
                        val rows = loadRows(
                            provider,
                            s,
                            history,
                            historyInitialized
                        )

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

                // Every finished update refreshes auto tracking.
                enqueuePostUpdateChain(chain)

            } else {

                val nextRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
                    .setInputData(
                        Data.Builder()
                            .putInt("offset", processed)
                            .putInt("limit", limit)
                            .putBoolean("chain", chain)
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
            Result.failure()
        }
    }

    // First sync: full history. Later syncs: only data since the last stored
    // date. The overlapping (last stored) candle is compared with what Yahoo
    // returns now; a big difference means a split/bonus re-adjusted the past,
    // so the whole history is fetched again and overwritten.
    private fun loadRows(
        provider: DataProvider,
        symbol: String,
        history: List<Candle>,
        historyInitialized: Boolean
    ): List<Candle> {

        if (!historyInitialized) {
            return provider.fetchDaily(symbol, 500)
        }

        val last = history.lastOrNull()
            ?: return provider.fetchDaily(symbol, 500)

        val rows = provider.fetchDailySince(
            symbol,
            LocalDate.parse(last.date)
        )

        val overlap = rows.firstOrNull { it.date == last.date }

        if (overlap != null && last.close > 0.0) {
            val change = abs(overlap.close - last.close) / last.close

            if (change > 0.03) {
                return provider.fetchDaily(symbol, 500)
            }
        }

        return rows
    }

    // withScan = true  (scheduled daily run): daily scan, then auto tracking.
    // withScan = false (manual update)       : auto tracking only.
    private fun enqueuePostUpdateChain(withScan: Boolean) {
        val workManager = WorkManager.getInstance(applicationContext)

        val trackingRequest =
            OneTimeWorkRequestBuilder<AutoTrackingWorker>()
                .build()

        if (withScan) {
            val scanRequest =
                OneTimeWorkRequestBuilder<ScanWorker>()
                    .setInputData(
                        Data.Builder()
                            .putBoolean("chain", true)
                            .build()
                    )
                    .build()

            workManager
                .beginUniqueWork(
                    "nse_post_update",
                    ExistingWorkPolicy.REPLACE,
                    scanRequest
                )
                .then(trackingRequest)
                .enqueue()
        } else {
            workManager.enqueueUniqueWork(
                "nse_post_update",
                ExistingWorkPolicy.REPLACE,
                trackingRequest
            )
        }
    }
}

class ScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val chained = inputData.getBoolean("chain", false)

        return try {
            val db = AppDb(applicationContext)
            val cfg = ScanConfigStore.load(applicationContext)
            val symbols = db.symbols()
            val matches = ScannerEngine(db).scan(symbols, cfg)

            val savedTimeframes =
                cfg.timeframes
                    .ifEmpty { listOf(cfg.timeframe) }
                    .distinct()
                    .joinToString(", ")

            val savedConditions = buildString {
                append("TF = ")
                append(savedTimeframes)
                append("\n")
                append("Logic = ")
                append(cfg.logic)
            
                cfg.conditions.forEachIndexed { index, c ->
                    append("\n")
            
                    append("Condition ")
                    append(index + 1)
            
                    if (index > 0) {
                        append(" [")
                        append(c.logic)
                        append("]")
                    }
            
                    append(" = ")
                    append(c.leftIndicator)
            
                    if (c.leftParams.isNotEmpty()) {
                        append(
                            c.leftParams.joinToString(
                                prefix = "(",
                                postfix = ")"
                            )
                        )
                    }
            
                    append(" ")
                    append(c.comparator)
                    append(" ")
                    append(c.rightIndicator)
            
                    if (c.rightParams.isNotEmpty()) {
                        append(
                            c.rightParams.joinToString(
                                prefix = "(",
                                postfix = ")"
                            )
                        )
                    }
            
                    if (c.rightIndicator == "Number") {
                        append(" ")
                        append(c.rightTarget)
                    }
                }
            }


            db.saveRun(
                savedTimeframes,
                matches,
                savedConditions
            )

            Result.success()
        } catch (_: Exception) {
            // In the daily chain a failed scan must not block auto tracking.
            if (chained) Result.success() else Result.failure()
        }
    }
}

class AutoTrackingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDb(applicationContext)
            val symbols = db.symbols()
            val activeSavedScans = db.getAutoTrackSavedScans()

            TrackingStore(db).deleteOrphans()

            if (activeSavedScans.isEmpty()) {
                return Result.success()
            }

            val engine = TrackingEngine(db)

            var trackedCount = 0
            var entries = 0
            var exits = 0
            var signals = 0

            for (savedScan in activeSavedScans) {

                val config = try {
                    ScanConfigStore.fromJson(
                        savedScan.configJson
                    )
                } catch (_: Exception) {
                    continue
                }

                // Scans with Near By / May Go To Cross conditions are
                // watch-only and are not tracked.
                if (!TrackingEngine.isTrackable(config)) {
                    continue
                }

                val result = engine.process(
                    savedScan.id,
                    config,
                    symbols
                )

                db.saveTrackingRun(
                    savedScanId = savedScan.id,
                    matches = result.matches,
                    status =
                    if (result.matches.isEmpty()) {
                        "VOID"
                    } else {
                        "ACTIVE"
                    }
                )

                trackedCount++
                entries += result.entries
                exits += result.exits
                signals += result.signals
            }

            showTrackingNotification(
                applicationContext,
                trackedCount,
                entries,
                exits,
                signals
            )

            Result.success(
                Data.Builder()
                    .putInt("tracked_scans", trackedCount)
                    .putInt("entries", entries)
                    .putInt("exits", exits)
                    .putInt("signals", signals)
                    .build()
            )

        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun showTrackingNotification(
        context: Context,
        trackedCount: Int,
        entries: Int,
        exits: Int,
        signals: Int
    ) {
        val channelId = "scanner_tracking"

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Scanner Tracking",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            manager.createNotificationChannel(channel)
        }

        val text =
            "$trackedCount scan(s) tracked: " +
                    "$entries entered, $exits exited, " +
                    "$signals new signal(s)"

        val notification =
            NotificationCompat.Builder(
                context,
                channelId
            )
                .setSmallIcon(
                    android.R.drawable.ic_menu_info_details
                )
                .setContentTitle(
                    "NSE Simple Scanner"
                )
                .setContentText(text)
                .setAutoCancel(true)
                .build()

        manager.notify(
            1001,
            notification
        )
    }
}
