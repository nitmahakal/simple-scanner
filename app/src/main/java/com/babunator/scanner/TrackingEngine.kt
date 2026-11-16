package com.babunator.scanner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/*
 * Tracking rules (one saved scan at a time):
 *
 *  1. A tracked scan follows exactly the stocks of the scan result (all
 *     timeframes). Every one of them enters at the OPEN of the next trading
 *     day, so they all start on the same day. No other stock is added
 *     later and a closed trade is not entered again.
 *  2. Exit signals are seen on a COMPLETED candle of the stock's timeframe
 *     (for Weekly and Monthly the running candle is not used).
 *  3. Exit signals are carried out at the OPEN of the next candle:
 *       - stop-loss (fixed % below entry, checked on the candle close)
 *       - stop-loss condition
 *       - custom exit condition, or when none is set:
 *           * entry has Cross Above / Cross Below: the opposite cross
 *             (Cross Above -> Cross Below and the other way round)
 *           * otherwise the default exit: the entry conditions stop matching.
 *
 * Every trade is written to the trade log (entry, exit, last price,
 * highest and lowest close), which the tracking tables show.
 *
 * Scans (and exit / stop-loss conditions) that contain Near By /
 * May Go To Cross conditions only watch a setup, so they are not used.
 */
class TrackingEngine(private val db: AppDb) {

    data class Result(
        val matches: List<Match>,
        val entries: Int,
        val exits: Int,
        val signals: Int
    )

    // What the (parallel, read-only) first phase finds for one symbol and
    // timeframe of the tracked scan. entryCandle is the first daily candle
    // after the waiting ENTRY signal (null while it has not come yet).
    private class TfData(
        val timeframe: String,
        val rows: List<Candle>,
        val latest: Candle,
        val entryCandle: Candle?,
        val lastDone: Candle?,
        val entryMatch: Boolean,
        val exitMatch: Boolean,
        val slMatch: Boolean
    )

    private val store = TrackingStore(db)
    private val scanner = ScannerEngine(db)

    private val staleDays = 7L

    private val anchorSymbols = listOf(
        "RELIANCE",
        "TCS",
        "HDFCBANK",
        "INFY",
        "ICICIBANK"
    )

    companion object {
        private val crossFlip = mapOf(
            "Cross Above" to "Cross Below",
            "Cross Below" to "Cross Above"
        )

        // A cross is true only on the candle where it happens, so the
        // default exit (entry conditions stop matching) would close the trade
        // one candle later. For such scans the exit is the opposite cross of
        // each cross condition (any one of them ends the trade).
        fun oppositeCrossExit(config: ScanConfig): ScanConfig? {
            val crosses =
                config.conditions.filter { it.comparator in crossFlip }

            if (crosses.isEmpty()) return null

            val flipped = crosses.map {
                it.copy(
                    comparator = crossFlip.getValue(it.comparator),
                    logic = "OR"
                )
            }

            return config.copy(conditions = flipped)
        }

        private val watchOnlyComparators = setOf(
            "Near By",
            "May Go To Cross Above",
            "May Go To Cross Below"
        )

        fun isTrackable(config: ScanConfig): Boolean {
            return config.conditions.isNotEmpty() &&
                    config.conditions.none { it.comparator in watchOnlyComparators }
        }
    }

    // Exit / stop-loss condition: ignored when it cannot be read or is watch-only.
    private fun parseRule(json: String?): ScanConfig? {
        if (json == null) return null

        val config = try {
            ScanConfigStore.fromJson(json)
        } catch (_: Exception) {
            null
        }

        if (config == null || !isTrackable(config)) return null

        return config
    }

    // Starts tracking of a scan: every stock of the scan result (all
    // timeframes) gets a waiting ENTRY for the next trading day's open, so all
    // of them start on the same day. Nothing else is added to this scan later.
    fun seedBasket(savedScanId: Long, matches: List<Match>): Result {
        val lastDates = HashMap<String, String>()
        val items = mutableListOf<Pair<Match, String>>()

        for (m in matches) {
            val lastDate = lastDates.getOrPut(m.symbol) {
                db.getHistory(m.symbol).lastOrNull()?.date ?: ""
            }

            if (lastDate.isNotEmpty()) {
                items += Pair(m, lastDate)
            }
        }

        val database = db.writableDatabase

        database.beginTransaction()

        try {
            for ((m, lastDate) in items) {
                store.upsertPending(
                    savedScanId,
                    m.symbol,
                    m.timeframe,
                    "ENTRY",
                    lastDate
                )
            }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return Result(items.map { it.first }, 0, 0, items.size)
    }

    // Only the stocks that have a waiting signal or an open position are
    // looked at (a few hundred, not the whole market), so this is fast.
    // Phase 1 (history, indicators) runs on a few threads and writes nothing.
    // Phase 2 (the rules and all writes) runs one symbol after another, in
    // transactions of 20 symbols, so the write lock is held only for a short
    // time at once. The symbols argument is not needed any more.
    fun process(
        savedScanId: Long,
        config: ScanConfig,
        symbols: List<String>
    ): Result {
        return processScan(db.writableDatabase, savedScanId, config)
    }

    private fun prepare(
        symbol: String,
        timeframes: List<String>,
        config: ScanConfig,
        exitRule: ScanConfig?,
        stopLossConfig: ScanConfig?,
        keepKeys: Set<String>,
        entrySignals: Map<String, String>,
        cutoff: LocalDate?
    ): List<TfData> {

        val history = db.getHistory(symbol)

        if (history.isEmpty()) return emptyList()

        if (isStale(history.last().date, cutoff)) return emptyList()

        val out = ArrayList<TfData>()

        for (timeframe in timeframes) {

            val k = key(symbol, timeframe)

            // Only the stocks of this scan's list are tracked.
            if (!keepKeys.contains(k)) continue

            val allRows = scanner.resample(history, timeframe)

            if (allRows.isEmpty()) continue

            val doneRows = completedRows(allRows, timeframe)

            val signalDate = entrySignals[k]

            val entryCandle: Candle? =
                if (signalDate == null) {
                    null
                } else {
                    history.firstOrNull { it.date > signalDate }
                }

            var lastDone: Candle? = null
            var entryMatch = false
            var exitMatch = false
            var slMatch = false

            if (doneRows.isNotEmpty()) {
                val closes = doneRows.map { it.close }

                lastDone = doneRows.last()
                entryMatch = scanner.evaluateSeries(closes, config)

                if (exitRule != null) {
                    exitMatch = scanner.evaluateSeries(closes, exitRule)
                }

                if (stopLossConfig != null) {
                    slMatch = scanner.evaluateSeries(closes, stopLossConfig)
                }
            }

            out.add(
                TfData(
                    timeframe,
                    allRows,
                    allRows.last(),
                    entryCandle,
                    lastDone,
                    entryMatch,
                    exitMatch,
                    slMatch
                )
            )
        }

        return out
    }

    private fun processScan(
        database: android.database.sqlite.SQLiteDatabase,
        savedScanId: Long,
        config: ScanConfig
    ): Result {

        if (!isTrackable(config)) {
            return Result(emptyList(), 0, 0, 0)
        }

        val rules = store.getRules(savedScanId)
        val exitConfig = parseRule(rules?.exitConfigJson)
        val stopLossConfig = parseRule(rules?.stopLossConfigJson)
        val exitRule = exitConfig ?: oppositeCrossExit(config)
        val stopLossPercent =
            rules?.stopLossPercent?.takeIf { it > 0.0 && it < 100.0 }

        val timeframes =
            config.timeframes
                .ifEmpty { listOf(config.timeframe) }
                .distinct()

        val positions =
            db.getTrackingPositions(savedScanId)
                .associateBy { key(it.symbol, it.timeframe) }

        val pendingMap =
            store.getPending(savedScanId)
                .associateBy { key(it.symbol, it.timeframe) }

        val blocked = store.getBlocked(savedScanId).toMutableSet()
        val openTrades = store.getOpenTradeKeys(savedScanId).toMutableSet()

        val cutoff = staleCutoff()

        // The stocks of this scan: waiting for entry or in a trade.
        val keepKeys = positions.keys + pendingMap.keys

        val symbols = keepKeys
            .map { it.substringBefore('|') }
            .distinct()
            .sorted()

        val entrySignals = pendingMap
            .filter { it.value.kind == "ENTRY" }
            .mapValues { it.value.signalDate }

        // Phase 1: the heavy part, on a few threads, no writes.
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val pool = Executors.newFixedThreadPool(threads)

        val prepared: List<List<TfData>> = try {
            val futures = symbols.map { symbol ->
                pool.submit(
                    Callable<List<TfData>> {
                        prepare(
                            symbol,
                            timeframes,
                            config,
                            exitRule,
                            stopLossConfig,
                            keepKeys,
                            entrySignals,
                            cutoff
                        )
                    }
                )
            }

            futures.map {
                try {
                    it.get()
                } catch (e: ExecutionException) {
                    throw (e.cause ?: e)
                }
            }
        } finally {
            pool.shutdownNow()
        }

        // Phase 2: the rules and all writes, one symbol after another.
        val matches = mutableListOf<Match>()
        var entries = 0
        var exits = 0
        var signals = 0

        var sinceCommit = 0

        database.beginTransaction()

        try {

        for ((symbolIndex, symbol) in symbols.withIndex()) {

            // Commit the finished batch and start a new transaction.
            if (sinceCommit >= 20) {
                database.setTransactionSuccessful()
                database.endTransaction()
                database.beginTransaction()
                sinceCommit = 0
            }

            sinceCommit++

            for (d in prepared[symbolIndex]) {

                val timeframe = d.timeframe
                val allRows = d.rows
                val latest = d.latest
                val k = key(symbol, timeframe)

                var position = positions[k]
                var pending = pendingMap[k]

                // 1. A signal from an earlier candle is carried out at the
                //    open of the candle that came after it.
                val waiting = pending

                if (waiting != null) {
                    // ENTRY: the next trading day's open (daily candle, whatever
                    // the timeframe). EXIT: the next candle of the timeframe.
                    val next =
                        if (waiting.kind == "ENTRY") {
                            d.entryCandle
                        } else {
                            allRows.firstOrNull { it.date > waiting.signalDate }
                        }

                    if (next != null) {
                        val price = next.open ?: next.close

                        if (waiting.kind == "ENTRY" && position == null) {
                            position = openPosition(
                                savedScanId,
                                symbol,
                                timeframe,
                                allRows,
                                next.date,
                                price
                            )
                            openTrades.add(k)
                            entries++
                        } else if (waiting.kind.startsWith("EXIT") && position != null) {
                            val entryDate = position.opened.take(10)

                            val extremes = extremes(
                                allRows,
                                entryDate,
                                position.entryPrice,
                                next.date,
                                price
                            )

                            // Positions from before the trade log existed.
                            if (!openTrades.contains(k)) {
                                store.openTrade(
                                    savedScanId,
                                    symbol,
                                    timeframe,
                                    entryDate,
                                    position.entryPrice,
                                    position.currentPrice ?: position.entryPrice,
                                    extremes.first,
                                    extremes.second
                                )
                            }

                            val reason =
                                if (waiting.kind == "EXIT-SL") {
                                    "Stop-loss"
                                } else if (exitConfig != null) {
                                    "Exit condition"
                                } else if (exitRule != null) {
                                    "Opposite cross"
                                } else {
                                    "Entry conditions ended"
                                }

                            store.closeTrade(
                                savedScanId,
                                symbol,
                                timeframe,
                                next.date,
                                price,
                                extremes.first,
                                extremes.second,
                                reason
                            )

                            db.closeTrackingPosition(
                                savedScanId,
                                symbol,
                                timeframe,
                                price,
                                next.date
                            )

                            openTrades.remove(k)
                            position = null
                            exits++
                        }

                        store.deletePending(savedScanId, symbol, timeframe)
                        pending = null
                    }
                }

                // 2. Look for an exit signal on the last completed candle.
                val lastDone = d.lastDone

                if (lastDone != null) {
                    val entryMatch = d.entryMatch

                    if (entryMatch) {
                        matches += Match(symbol, timeframe, latest.close, "")
                    }

                    // Entry conditions turned false once: entry is allowed again.
                    if (!entryMatch && blocked.contains(k)) {
                        store.removeBlocked(savedScanId, symbol, timeframe)
                        blocked.remove(k)
                    }

                    if (pending == null) {
                        val open = position

                        if (open == null) {
                            // No new entries and no re-entry: a tracked scan
                            // follows only the stocks it started with.
                        } else {
                            // Only candles from the entry candle onward count.
                            val fresh = lastDone.date >= open.opened.take(10)

                            var exitKind: String? = null

                            if (fresh &&
                                stopLossPercent != null &&
                                lastDone.close <= open.entryPrice * (1.0 - stopLossPercent / 100.0)
                            ) {
                                exitKind = "EXIT-SL"
                            } else if (fresh &&
                                stopLossConfig != null &&
                                d.slMatch
                            ) {
                                exitKind = "EXIT-SL"
                            } else if (exitRule != null) {
                                if (fresh && d.exitMatch) {
                                    exitKind = "EXIT"
                                }
                            } else if (!entryMatch) {
                                exitKind = "EXIT"
                            }

                            if (exitKind != null) {
                                store.upsertPending(
                                    savedScanId,
                                    symbol,
                                    timeframe,
                                    exitKind,
                                    lastDone.date
                                )
                                signals++

                                // Entry conditions are still true: do not walk
                                // back in until they have turned false once.
                                if (entryMatch) {
                                    store.addBlocked(savedScanId, symbol, timeframe)
                                    blocked.add(k)
                                }
                            }
                        }
                    }
                }

                // 3. Keep the open position's numbers up to date.
                val current = position

                if (current != null) {
                    val entryDate = current.opened.take(10)
                    val price = latest.close

                    val extremes = extremes(
                        allRows,
                        entryDate,
                        current.entryPrice,
                        null,
                        null
                    )

                    val pnl =
                        if (current.entryPrice == 0.0) {
                            0.0
                        } else {
                            (price - current.entryPrice) / current.entryPrice * 100.0
                        }

                    val downside =
                        if (current.entryPrice == 0.0) {
                            0.0
                        } else {
                            (extremes.second - current.entryPrice) / current.entryPrice * 100.0
                        }

                    db.upsertTrackingPosition(
                        savedScanId = savedScanId,
                        symbol = symbol,
                        timeframe = timeframe,
                        entryPrice = current.entryPrice,
                        currentPrice = price,
                        lowestPrice = extremes.second,
                        pnl = pnl,
                        maxDownside = downside,
                        status = "OPEN",
                        opened = current.opened,
                        updated = LocalDateTime.now().toString()
                    )

                    if (openTrades.contains(k)) {
                        store.updateOpenTrade(
                            savedScanId,
                            symbol,
                            timeframe,
                            price,
                            extremes.first,
                            extremes.second
                        )
                    } else {
                        // Positions from before the trade log existed.
                        store.openTrade(
                            savedScanId,
                            symbol,
                            timeframe,
                            entryDate,
                            current.entryPrice,
                            price,
                            extremes.first,
                            extremes.second
                        )
                        openTrades.add(k)
                    }
                }
            }
        }

        database.setTransactionSuccessful()

        } finally {
            database.endTransaction()
        }

        return Result(matches, entries, exits, signals)
    }

    private fun openPosition(
        savedScanId: Long,
        symbol: String,
        timeframe: String,
        allRows: List<Candle>,
        entryDate: String,
        price: Double
    ): AppDb.TrackingPosition {

        val current = allRows.last().close
        val extremes = extremes(allRows, entryDate, price, null, null)

        val pnl =
            if (price == 0.0) 0.0 else (current - price) / price * 100.0

        val downside =
            if (price == 0.0) 0.0 else (extremes.second - price) / price * 100.0

        val now = LocalDateTime.now().toString()

        db.upsertTrackingPosition(
            savedScanId = savedScanId,
            symbol = symbol,
            timeframe = timeframe,
            entryPrice = price,
            currentPrice = current,
            lowestPrice = extremes.second,
            pnl = pnl,
            maxDownside = downside,
            status = "OPEN",
            opened = entryDate,
            updated = now
        )

        store.openTrade(
            savedScanId,
            symbol,
            timeframe,
            entryDate,
            price,
            current,
            extremes.first,
            extremes.second
        )

        return AppDb.TrackingPosition(
            savedScanId = savedScanId,
            symbol = symbol,
            timeframe = timeframe,
            entryPrice = price,
            currentPrice = current,
            lowestPrice = extremes.second,
            pnl = pnl,
            maxDownside = downside,
            status = "OPEN",
            opened = entryDate,
            updated = now
        )
    }

    // Highest and lowest close from the entry candle onward (before
    // beforeDate when given). The entry price, and the exit price when
    // given, are always part of the range. Returns (highest, lowest).
    private fun extremes(
        allRows: List<Candle>,
        fromDate: String,
        entryPrice: Double,
        beforeDate: String?,
        extraPrice: Double?
    ): Pair<Double, Double> {

        var high = entryPrice
        var low = entryPrice

        for (row in allRows) {
            if (row.date < fromDate) continue
            if (beforeDate != null && row.date >= beforeDate) break

            if (row.close > high) high = row.close
            if (row.close < low) low = row.close
        }

        if (extraPrice != null) {
            if (extraPrice > high) high = extraPrice
            if (extraPrice < low) low = extraPrice
        }

        return Pair(high, low)
    }

    // Weekly / Monthly: the running candle is left out until the period is over
    // (the next trading weekday belongs to a new week / month).
    private fun completedRows(allRows: List<Candle>, timeframe: String): List<Candle> {
        if (timeframe == "Daily" || allRows.isEmpty()) return allRows

        val latest = LocalDate.parse(allRows.last().date)
        val next = nextWeekday(latest)

        val finished =
            if (timeframe == "Weekly") {
                weekStart(next) != weekStart(latest)
            } else {
                next.month != latest.month || next.year != latest.year
            }

        return if (finished) allRows else allRows.dropLast(1)
    }

    private fun nextWeekday(date: LocalDate): LocalDate {
        var next = date.plusDays(1)

        while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
            next = next.plusDays(1)
        }

        return next
    }

    private fun weekStart(date: LocalDate): LocalDate {
        return date.minusDays((date.dayOfWeek.value - 1).toLong())
    }

    private fun staleCutoff(): LocalDate? {
        val latest = anchorSymbols
            .mapNotNull { db.getHistory(it).lastOrNull()?.date }
            .maxOrNull()
            ?: return null

        return try {
            LocalDate.parse(latest).minusDays(staleDays)
        } catch (_: Exception) {
            null
        }
    }

    private fun isStale(lastDate: String, cutoff: LocalDate?): Boolean {
        if (cutoff == null) return false

        return try {
            LocalDate.parse(lastDate).isBefore(cutoff)
        } catch (_: Exception) {
            false
        }
    }

    private fun key(symbol: String, timeframe: String): String {
        return "$symbol|$timeframe"
    }
}
