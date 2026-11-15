package com.babunator.scanner

import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ScannerEngine(private val db: AppDb) {

    // Symbols whose last candle is older than this many days (compared to the
    // market's latest date) are skipped: delisted / suspended / dead data.
    private val staleDays = 7L

    // Liquid anchors used to find the market's latest data date.
    private val anchorSymbols = listOf(
        "RELIANCE",
        "TCS",
        "HDFCBANK",
        "INFY",
        "ICICIBANK"
    )

    // Epoch day from year / month / day with plain integer arithmetic (no
    // objects are created). Same value as LocalDate.of(...).toEpochDay().
    private fun epochDay(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yearOfEra = y - era * 400
        val dayOfYear =
            (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val dayOfEra =
            yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear

        return era * 146097L + dayOfEra - 719468L
    }

    // Number from the digits of "yyyy-MM-dd" without creating substrings.
    private fun digits(s: String, from: Int, count: Int): Int {
        var v = 0

        for (i in from until from + count) {
            v = v * 10 + (s[i] - '0')
        }

        return v
    }

    // Weekly / Monthly candles: date and close come from the last day of the
    // period, open comes from the first day of the period.
    // The period of a date is found with plain numbers (no date parsing and
    // no temporary objects), which is much faster and gives exactly the same
    // candles as grouping the rows by week / month.
    fun resample(rows: List<Candle>, timeframe: String): List<Candle> {
        if (rows.isEmpty() || timeframe == "Daily") {
            return rows
        }

        val weekly = timeframe == "Weekly"

        // The rows come from the database already sorted by date.
        var isSorted = true

        for (i in 1 until rows.size) {
            if (rows[i - 1].date > rows[i].date) {
                isSorted = false
                break
            }
        }

        val source = if (isSorted) rows else rows.sortedBy { it.date }

        val out = ArrayList<Candle>()

        var currentKey = 0L
        var started = false
        var firstOpen: Double? = null
        var last: Candle? = null

        for (r in source) {
            val year = digits(r.date, 0, 4)
            val month = digits(r.date, 5, 2)

            val key: Long =
                if (weekly) {
                    // 1970-01-01 was a Thursday: this is the Monday-based week number.
                    Math.floorDiv(
                        epochDay(year, month, digits(r.date, 8, 2)) + 3L,
                        7L
                    )
                } else {
                    year * 12L + month
                }

            if (!started || key != currentKey) {
                val previous = last

                if (previous != null) {
                    out.add(Candle(previous.date, previous.close, firstOpen))
                }

                currentKey = key
                firstOpen = r.open
                started = true
            }

            last = r
        }

        val finalRow = last

        if (finalRow != null) {
            out.add(Candle(finalRow.date, finalRow.close, firstOpen))
        }

        return out
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

    // True when the price series satisfies the whole condition rule
    // (AND is checked first, then OR). Used by tracking.
    fun evaluateSeries(close: List<Double>, cfg: ScanConfig): Boolean {
        if (close.isEmpty() || cfg.conditions.isEmpty()) return false

        val cache = mutableMapOf<String, Double?>()

        fun value(indicator: String, params: List<Double>): Double? {
            val key = indicator + "|" + params.joinToString(",")

            return cache.getOrPut(key) {
                Indicators.valueAt(close, indicator, params)
            }
        }

        val checks = cfg.conditions.map { c ->
            val left = value(c.leftIndicator, c.leftParams)

            val right =
                if (c.rightIndicator == "Number") {
                    c.rightTarget
                } else {
                    value(c.rightIndicator, c.rightParams)
                }

            ConditionEngine.evaluate(close, c, left, right)
        }

        return ConditionEngine.combine(
            checks,
            cfg.conditions.map { it.logic }
        )
    }

    // Time spent in the last scan (shown briefly after a scan to find slow parts).
    var timingText: String = ""

    fun scan(
        symbols: List<String>,
        cfg: ScanConfig
    ): List<Match> {

        val selectedTimeframes =
            cfg.timeframes
                .ifEmpty { listOf(cfg.timeframe) }
                .distinct()

        val cutoff = staleCutoff()

        val logics = cfg.conditions.map { it.logic }
        val ruleText = ConditionEngine.explain(logics)

        val historyNanos = AtomicLong(0L)
        val resampleNanos = AtomicLong(0L)
        val evalNanos = AtomicLong(0L)
        val candleCount = AtomicLong(0L)
        val symbolCount = AtomicInteger(0)
        val startAll = System.nanoTime()

        // Symbols are scanned by a few threads at the same time (SQLite in WAL
        // mode allows parallel reads). Every symbol is independent and the
        // results are joined in the original symbol order, so the matches are
        // exactly the same as with one thread.
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val pool = Executors.newFixedThreadPool(threads)

        try {
            val futures = ArrayList<Future<List<Match>>>(symbols.size)

            for (s in symbols) {
                futures.add(
                    pool.submit(
                        Callable<List<Match>> {
                            scanSymbol(
                                s,
                                cfg,
                                selectedTimeframes,
                                cutoff,
                                logics,
                                ruleText,
                                historyNanos,
                                resampleNanos,
                                evalNanos,
                                candleCount,
                                symbolCount
                            )
                        }
                    )
                )
            }

            val hits = mutableListOf<Match>()

            for (future in futures) {
                try {
                    hits += future.get()
                } catch (e: ExecutionException) {
                    throw (e.cause ?: e)
                }
            }

            timingText = String.format(
                Locale.US,
                "Scan %.1fs (%d threads) | history %.1fs | resample %.1fs | indicators %.1fs (summed over threads) | %d symbols, %d candles",
                (System.nanoTime() - startAll) / 1e9,
                threads,
                historyNanos.get() / 1e9,
                resampleNanos.get() / 1e9,
                evalNanos.get() / 1e9,
                symbolCount.get(),
                candleCount.get()
            )

            return hits
        } finally {
            pool.shutdownNow()
        }
    }

    private fun scanSymbol(
        s: String,
        cfg: ScanConfig,
        selectedTimeframes: List<String>,
        cutoff: LocalDate?,
        logics: List<String>,
        ruleText: String,
        historyNanos: AtomicLong,
        resampleNanos: AtomicLong,
        evalNanos: AtomicLong,
        candleCount: AtomicLong,
        symbolCount: AtomicInteger
    ): List<Match> {

        val hits = mutableListOf<Match>()

        val t0 = System.nanoTime()
        val history = db.getHistory(s)
        historyNanos.addAndGet(System.nanoTime() - t0)

        if (history.isEmpty()) return hits

        if (cutoff != null) {
            val lastDate = try {
                LocalDate.parse(history.last().date)
            } catch (_: Exception) {
                null
            }

            if (lastDate != null && lastDate.isBefore(cutoff)) {
                return hits
            }
        }

        symbolCount.incrementAndGet()
        candleCount.addAndGet(history.size.toLong())

        for (timeframe in selectedTimeframes) {

            val t1 = System.nanoTime()
            val rows = resample(
                history,
                timeframe
            )
            resampleNanos.addAndGet(System.nanoTime() - t1)

            if (rows.isEmpty()) continue

            val close = rows.map { it.close }

            val t2 = System.nanoTime()

            val checks = mutableListOf<Boolean>()

            val currentValueCache =
                mutableMapOf<String, Double?>()

            fun cachedValue(
                indicator: String,
                params: List<Double>
            ): Double? {

                val key =
                    indicator +
                            "|" +
                            params.joinToString(",")

                return currentValueCache.getOrPut(key) {
                    ConditionEngineValue.value(
                        close,
                        indicator,
                        params
                    )
                }
            }

            for (condition in cfg.conditions) {

                val leftValue = cachedValue(
                    condition.leftIndicator,
                    condition.leftParams
                )

                val rightValue =
                    if (condition.rightIndicator == "Number") {
                        condition.rightTarget
                    } else {
                        cachedValue(
                            condition.rightIndicator,
                            condition.rightParams
                        )
                    }

                val check = ConditionEngine.evaluate(
                    series = close,
                    c = condition,
                    currentLeft = leftValue,
                    currentRight = rightValue
                )

                checks += check
            }

            // AND is checked first, then OR (see ConditionEngine.combine).
            val ok = ConditionEngine.combine(checks, logics)

            evalNanos.addAndGet(System.nanoTime() - t2)

            if (ok) {

                // The text of a match is built only for matches
                // (indicator values come from the cache).
                val conditionDetails = mutableListOf<String>()
                val indicatorValues = linkedMapOf<String, String>()

                for ((index, condition) in cfg.conditions.withIndex()) {

                    val leftValue = cachedValue(
                        condition.leftIndicator,
                        condition.leftParams
                    )

                    val rightValue =
                        if (condition.rightIndicator == "Number") {
                            condition.rightTarget
                        } else {
                            cachedValue(
                                condition.rightIndicator,
                                condition.rightParams
                            )
                        }

                    conditionDetails +=
                        "Condition ${index + 1}: " +
                                "${condition.leftIndicator}${formatParams(condition.leftParams)} " +
                                "${condition.comparator} " +
                                "${condition.rightIndicator}${formatParams(condition.rightParams)}" +
                                if (condition.rightIndicator == "Number") {
                                    " ${formatNumber(condition.rightTarget)}"
                                } else {
                                    ""
                                }

                    indicatorValues[
                        "${condition.leftIndicator}${formatParams(condition.leftParams)}"
                    ] = formatNumber(leftValue)

                    if (condition.rightIndicator != "Number") {
                        indicatorValues[
                            "${condition.rightIndicator}${formatParams(condition.rightParams)}"
                        ] = formatNumber(rightValue)
                    }
                }

                val details = buildString {

                    append(
                        conditionDetails.joinToString("\n")
                    )

                    if (ruleText.isNotEmpty()) {
                        append("\n")
                        append(ruleText)
                    }

                    append("\n\n")

                    append(
                        "Close: ${formatNumber(close.last())}"
                    )

                    if (indicatorValues.isNotEmpty()) {
                        append("\n")

                        append(
                            indicatorValues.entries.joinToString("\n") {
                                "${it.key}: ${it.value}"
                            }
                        )
                    }
                }

                hits += Match(
                    symbol = s,
                    timeframe = timeframe,
                    close = close.last(),
                    note = details
                )
            }
        }

        return hits
    }

    private fun formatParams(params: List<Double>): String {
        if (params.isEmpty()) return ""

        return params.joinToString(
            prefix = "(",
            postfix = ")"
        ) {
            if (it % 1.0 == 0.0) {
                it.toInt().toString()
            } else {
                formatNumber(it)
            }
        }
    }

    private fun formatNumber(value: Double?): String {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "N/A"
        }

        return String.format(
            Locale.US,
            "%.2f",
            value
        )
    }
}

/*
 * Small result-value helper.
 *
 * This uses the same indicator calculation source as the scanner,
 * so the displayed value comes from the actual value used for
 * the current scan.
 */
private object ConditionEngineValue {

    fun value(
        series: List<Double>,
        indicator: String,
        params: List<Double>
    ): Double? {
        return Indicators.valueAt(
            series,
            indicator,
            params
        )
    }
}
