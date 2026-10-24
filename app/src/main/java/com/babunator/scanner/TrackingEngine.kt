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
 *  3. Default exit: when the scan conditions stop matching, the position
 *     is closed at the OPEN of the next candle.
 *
 * Scans that contain Near By / May Go To Cross conditions only watch a
 * setup, so they are not tracked.
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

    fun process(
        savedScanId: Long,
        config: ScanConfig,
        symbols: List<String>
    ): Result {

        if (!isTrackable(config)) {
            return Result(emptyList(), 0, 0, 0)
        }

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
                            entries++
                        } else if (waiting.kind == "EXIT" && position != null) {
                            db.closeTrackingPosition(
                                savedScanId,
                                symbol,
                                timeframe,
                                price,
                                next.date
                            )
                            position = null
                            exits++
                        }

                        store.deletePending(savedScanId, symbol, timeframe)
                        pending = null
                    }
                }

                // 2. Look for a new confirmed signal on the last completed candle.
                if (doneRows.isNotEmpty()) {
                    val matchNow = scanner.evaluateSeries(
                        doneRows.map { it.close },
                        config
                    )

                    if (matchNow) {
                        matches += Match(symbol, timeframe, latest.close, "")
                    }

                    if (pending == null) {
                        val signalDate = doneRows.last().date

                        if (position == null && matchNow) {
                            store.upsertPending(
                                savedScanId,
                                symbol,
                                timeframe,
                                "ENTRY",
                                signalDate
                            )
                            signals++
                        } else if (position != null && !matchNow) {
                            store.upsertPending(
                                savedScanId,
                                symbol,
                                timeframe,
                                "EXIT",
                                signalDate
                            )
                            signals++
                        }
                    }
                }

                // 3. Keep the open position's numbers up to date.
                val open = position

                if (open != null) {
                    val current = latest.close
                    val lowest = minOf(open.lowestPrice, current)

                    val pnl =
                        if (open.entryPrice == 0.0) {
                            0.0
                        } else {
                            (current - open.entryPrice) / open.entryPrice * 100.0
                        }

                    val downside =
                        if (open.entryPrice == 0.0) {
                            0.0
                        } else {
                            (lowest - open.entryPrice) / open.entryPrice * 100.0
                        }

                    db.upsertTrackingPosition(
                        savedScanId = savedScanId,
                        symbol = symbol,
                        timeframe = timeframe,
                        entryPrice = open.entryPrice,
                        currentPrice = current,
                        lowestPrice = lowest,
                        pnl = pnl,
                        maxDownside = minOf(open.maxDownside, downside),
                        status = "OPEN",
                        opened = open.opened,
                        updated = LocalDateTime.now().toString()
                    )
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

        val sinceEntry = allRows.filter { it.date >= entryDate }
        val current = allRows.last().close
        val lowest = minOf(price, sinceEntry.minOfOrNull { it.close } ?: price)

        val pnl =
            if (price == 0.0) 0.0 else (current - price) / price * 100.0

        val downside =
            if (price == 0.0) 0.0 else (lowest - price) / price * 100.0

        val now = LocalDateTime.now().toString()

        db.upsertTrackingPosition(
            savedScanId = savedScanId,
            symbol = symbol,
            timeframe = timeframe,
            entryPrice = price,
            currentPrice = current,
            lowestPrice = lowest,
            pnl = pnl,
            maxDownside = downside,
            status = "OPEN",
            opened = entryDate,
            updated = now
        )

        return AppDb.TrackingPosition(
            savedScanId = savedScanId,
            symbol = symbol,
            timeframe = timeframe,
            entryPrice = price,
            currentPrice = current,
            lowestPrice = lowest,
            pnl = pnl,
            maxDownside = downside,
            status = "OPEN",
            opened = entryDate,
            updated = now
        )
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
