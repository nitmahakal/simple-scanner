package com.babunator.scanner

import java.time.LocalDate
import java.util.Locale

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

    // Weekly / Monthly candles: date and close come from the last day of the
    // period, open comes from the first day of the period.
    // The period of a date is found with plain numbers (no date parsing),
    // which is much faster and gives exactly the same groups.
    fun resample(rows: List<Candle>, timeframe: String): List<Candle> {
        if (rows.isEmpty() || timeframe == "Daily") {
            return rows
        }

        val weekly = timeframe == "Weekly"

        val groups = linkedMapOf<Long, Candle>()
        val firstOpen = HashMap<Long, Double?>()

        for (r in rows.sortedBy { it.date }) {
            val year = r.date.substring(0, 4).toInt()
            val month = r.date.substring(5, 7).toInt()

            val key: Long =
                if (weekly) {
                    val day = r.date.substring(8, 10).toInt()
                    val epochDay = LocalDate.of(year, month, day).toEpochDay()

                    // 1970-01-01 was a Thursday: this is the Monday-based week number.
                    Math.floorDiv(epochDay + 3L, 7L)
                } else {
                    year * 12L + month
                }

            if (!firstOpen.containsKey(key)) {
                firstOpen[key] = r.open
            }

            groups[key] = Candle(r.date, r.close, firstOpen[key])
        }

        return groups.values.toList()
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

        val hits = mutableListOf<Match>()

        val selectedTimeframes =
            cfg.timeframes
                .ifEmpty { listOf(cfg.timeframe) }
                .distinct()

        val cutoff = staleCutoff()

        val logics = cfg.conditions.map { it.logic }
        val ruleText = ConditionEngine.explain(logics)

        var historyNanos = 0L
        var resampleNanos = 0L
        var evalNanos = 0L
        var candleCount = 0L
        var symbolCount = 0
        val startAll = System.nanoTime()

        for (s in symbols) {

            val t0 = System.nanoTime()
            val history = db.getHistory(s)
            historyNanos += System.nanoTime() - t0

            if (history.isEmpty()) continue

            if (cutoff != null) {
                val lastDate = try {
                    LocalDate.parse(history.last().date)
                } catch (_: Exception) {
                    null
                }

                if (lastDate != null && lastDate.isBefore(cutoff)) {
                    continue
                }
            }

            symbolCount++
            candleCount += history.size

            for (timeframe in selectedTimeframes) {

                val t1 = System.nanoTime()
                val rows = resample(
                    history,
                    timeframe
                )
                resampleNanos += System.nanoTime() - t1

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

                evalNanos += System.nanoTime() - t2

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
        }

        timingText = String.format(
            Locale.US,
            "Scan %.1fs | history %.1fs | resample %.1fs | indicators %.1fs | %d symbols, %d candles",
            (System.nanoTime() - startAll) / 1e9,
            historyNanos / 1e9,
            resampleNanos / 1e9,
            evalNanos / 1e9,
            symbolCount,
            candleCount
        )

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
