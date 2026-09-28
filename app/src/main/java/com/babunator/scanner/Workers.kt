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
            Result.failure()
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

            if (activeSavedScans.isEmpty()) {
                return Result.success()
            }

            var trackedCount = 0
            var matchedCount = 0

            for (savedScan in activeSavedScans) {

                val config = try {
                    ScanConfigStore.fromJson(
                        savedScan.configJson
                    )
                } catch (_: Exception) {
                    continue
                }

                val matches =
                    ScannerEngine(db).scan(
                        symbols,
                        config
                    )

                val status =
                    if (matches.isEmpty()) {
                        "VOID"
                    } else {
                        "ACTIVE"
                    }

                db.saveTrackingRun(
                    savedScanId = savedScan.id,
                    matches = matches,
                    status = status
                )
                
                updateOpenTrackingPositions(
                    db = db,
                    savedScanId = savedScan.id,
                    matches = matches
                )

                closeMissingTrackingPositions(
                    db = db,
                    savedScanId = savedScan.id,
                    matches = matches
                )

                trackedCount++
                matchedCount += matches.size
            }

            showTrackingNotification(
                applicationContext,
                trackedCount,
                matchedCount
            )
            Result.success(
                Data.Builder()
                    .putInt("tracked_scans", trackedCount)
                    .putInt("matched_results", matchedCount)
                    .build()
            )

        } catch (_: Exception) {
            Result.failure()
        }
    }
    
    private fun updateOpenTrackingPositions(
        db: AppDb,
        savedScanId: Long,
        matches: List<Match>
    ) {
        val now = LocalDateTime.now().toString()

        val existing =
            db.getTrackingPositions(savedScanId)
                .associateBy {
                    "${it.symbol}|${it.timeframe}"
                }

        matches.forEach { match ->

            val key =
                "${match.symbol}|${match.timeframe}"

            val old = existing[key]

            if (old == null) {

                db.upsertTrackingPosition(
                    savedScanId = savedScanId,
                    symbol = match.symbol,
                    timeframe = match.timeframe,
                    entryPrice = match.close,
                    currentPrice = match.close,
                    lowestPrice = match.close,
                    pnl = 0.0,
                    maxDownside = 0.0,
                    status = "OPEN",
                    opened = now,
                    updated = now
                )

            } else {

                val currentPrice = match.close

                val lowestPrice =
                    minOf(
                        old.lowestPrice,
                        currentPrice
                    )

                val pnl =
                    if (old.entryPrice == 0.0) {
                        0.0
                    } else {
                        (
                            (currentPrice - old.entryPrice) /
                                old.entryPrice
                            ) * 100.0
                    }

                val maxDownside =
                    if (old.entryPrice == 0.0) {
                        0.0
                    } else {
                        (
                            (lowestPrice - old.entryPrice) /
                                old.entryPrice
                            ) * 100.0
                    }

                db.upsertTrackingPosition(
                    savedScanId = savedScanId,
                    symbol = match.symbol,
                    timeframe = match.timeframe,
                    entryPrice = old.entryPrice,
                    currentPrice = currentPrice,
                    lowestPrice = lowestPrice,
                    pnl = pnl,
                    maxDownside = minOf(
                        old.maxDownside,
                        maxDownside
                    ),
                    status = "OPEN",
                    opened = old.opened,
                    updated = now
                )
            }
        }
    }
    private fun closeMissingTrackingPositions(
        db: AppDb,
        savedScanId: Long,
        matches: List<Match>
    ) {
        val now = LocalDateTime.now().toString()

        val currentKeys =
            matches
                .map {
                    "${it.symbol}|${it.timeframe}"
                }
                .toSet()

        val existing =
            db.getTrackingPositions(savedScanId)

        existing.forEach { position ->

            val key =
                "${position.symbol}|${position.timeframe}"

            if (key in currentKeys) {
                return@forEach
            }

            val history =
                db.getHistory(position.symbol)

            val exitPrice =
                history.lastOrNull()?.close
                    ?: position.currentPrice
                    ?: position.entryPrice

            db.closeTrackingPosition(
                savedScanId = savedScanId,
                symbol = position.symbol,
                timeframe = position.timeframe,
                exitPrice = exitPrice,
                closed = now
            )
        }
    }

    private fun showTrackingNotification(
        context: Context,
        trackedCount: Int,
        matchedCount: Int
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
            "$trackedCount saved scan(s) checked, " +
                    "$matchedCount match(es)"

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
