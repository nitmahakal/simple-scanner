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
    fun resample(rows: List<Candle>, timeframe: String): List<Candle> {
        if (rows.isEmpty() || timeframe == "Daily") {
            return rows
        }

        val groups = linkedMapOf<String, Candle>()
        val firstOpen = HashMap<String, Double?>()

        for (r in rows.sortedBy { it.date }) {
            val d = LocalDate.parse(r.date)

            val key =
                if (timeframe == "Weekly") {
                    d.minusDays(
                        (d.dayOfWeek.value - 1).toLong()
                    ).toString()
                } else {
                    d.withDayOfMonth(1).toString()
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

        for (s in symbols) {

            val history = db.getHistory(s)

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

            for (timeframe in selectedTimeframes) {

                val rows = resample(
                    history,
                    timeframe
                )

                if (rows.isEmpty()) continue

                val close = rows.map { it.close }

                val checks = mutableListOf<Boolean>()
                val conditionDetails = mutableListOf<String>()
                val indicatorValues = linkedMapOf<String, String>()

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

                    val check = ConditionEngine.evaluate(
                        series = close,
                        c = condition,
                        currentLeft = leftValue,
                        currentRight = rightValue
                    )

                    checks += check

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

                // AND is checked first, then OR (see ConditionEngine.combine).
                val ok = ConditionEngine.combine(checks, logics)

                if (ok) {

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
