package com.babunator.scanner

import java.util.Locale

class ScannerEngine(private val db: AppDb) {

    fun resample(rows: List<Candle>, timeframe: String): List<Candle> {
        if (timeframe == "Daily") return rows

        val groups = linkedMapOf<String, Candle>()

        for (r in rows) {
            val d = java.time.LocalDate.parse(r.date)

            val key =
                if (timeframe == "Weekly") {
                    d.minusDays((d.dayOfWeek.value - 1).toLong()).toString()
                } else {
                    d.withDayOfMonth(1).toString()
                }

            groups[key] = r
        }

        return groups.values.toList()
    }

    fun scan(
        symbols: List<String>,
        cfg: ScanConfig
    ): List<Match> {

        val hits = mutableListOf<Match>()

        for (s in symbols) {

            val rows = resample(
                db.getHistory(s),
                cfg.timeframe
            )

            if (rows.size < 60) continue

            val close = rows.map { it.close }

            val checks = mutableListOf<Boolean>()
            val conditionDetails = mutableListOf<String>()
            val indicatorValues = linkedMapOf<String, String>()

            for ((index, condition) in cfg.conditions.withIndex()) {

                val leftValue = ConditionEngineValue.value(
                    close,
                    condition.leftIndicator,
                    condition.leftParams
                )

                val rightValue =
                    if (condition.rightIndicator == "Number") {
                        condition.rightTarget
                    } else {
                        ConditionEngineValue.value(
                            close,
                            condition.rightIndicator,
                            condition.rightParams
                        )
                    }

                val check = ConditionEngine.evaluate(
                    close,
                    condition
                )

                checks += check

                val leftText = formatConditionValue(
                    condition.leftIndicator,
                    condition.leftParams,
                    leftValue
                )

                val rightText =
                    if (condition.rightIndicator == "Number") {
                        formatNumber(condition.rightTarget)
                    } else {
                        formatConditionValue(
                            condition.rightIndicator,
                            condition.rightParams,
                            rightValue
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

                // Keep these values available in the result note.
                // The condition itself remains evaluated by ConditionEngine.
                @Suppress("UNUSED_VARIABLE")
                val unusedDisplayValues = leftText to rightText
            }

            val ok =
                if (cfg.logic == "OR") {
                    checks.any { it }
                } else {
                    checks.all { it }
                }

            if (ok) {

                val details = buildString {

                    append(
                        conditionDetails.joinToString("\n")
                    )

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
                    timeframe = cfg.timeframe,
                    close = close.last(),
                    note = details
                )
            }
        }

        return hits
    }

    private fun formatConditionValue(
        name: String,
        params: List<Double>,
        value: Double?
    ): String {
        return "$name${formatParams(params)}=${formatNumber(value)}"
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
