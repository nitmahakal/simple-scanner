package com.babunator.scanner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * Tracking rules (one saved scan at a time):
 *
 *  1. A confirmed signal is seen on a COMPLETED candle (for Weekly and
 *     Monthly the running candle is not used).
 *  2. Entry happens at the OPEN of the next candle.
 *  3. Exit signals are also carried out at the OPEN of the next candle:
 *       - stop-loss (fixed % below entry, checked on the candle close)
 *       - stop-loss condition
 *       - custom exit condition, or when none is set, the default exit:
 *         the entry conditions stop matching.
 *  4. After an exit by stop-loss or exit condition, a stock is not entered
 *     again until the entry conditions have turned false once.
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

    fun process(
        savedScanId: Long,
        config: ScanConfig,
        symbols: List<String>
    ): Result {

        if (!isTrackable(config)) {
            return Result(emptyList(), 0, 0, 0)
        }

        val rules = store.getRules(savedScanId)
        val exitConfig = parseRule(rules?.exitConfigJson)
        val stopLossConfig = parseRule(rules?.stopLossConfigJson)
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

        val matches = mutableListOf<Match>()
        var entries = 0
        var exits = 0
        var signals = 0

        for (symbol in symbols) {

            val history = db.getHistory(symbol)

            if (history.isEmpty()) continue

            if (isStale(history.last().date, cutoff)) continue

            for (timeframe in timeframes) {

                val allRows = scanner.resample(history, timeframe)

                if (allRows.isEmpty()) continue

                val doneRows = completedRows(allRows, timeframe)
                val latest = allRows.last()
                val k = key(symbol, timeframe)

                var position = positions[k]
                var pending = pendingMap[k]

                // 1. A signal from an earlier candle is carried out at the
                //    open of the candle that came after it.
                val waiting = pending

                if (waiting != null) {
                    val next = allRows.firstOrNull { it.date > waiting.signalDate }

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

                // 2. Look for a new confirmed signal on the last completed candle.
                if (doneRows.isNotEmpty()) {
                    val closes = doneRows.map { it.close }
                    val lastDone = doneRows.last()

                    val entryMatch = scanner.evaluateSeries(closes, config)

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
                            if (entryMatch && !blocked.contains(k)) {
                                store.upsertPending(
                                    savedScanId,
                                    symbol,
                                    timeframe,
                                    "ENTRY",
                                    lastDone.date
                                )
                                signals++
                            }
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
                                scanner.evaluateSeries(closes, stopLossConfig)
                            ) {
                                exitKind = "EXIT-SL"
                            } else if (exitConfig != null) {
                                if (fresh && scanner.evaluateSeries(closes, exitConfig)) {
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
