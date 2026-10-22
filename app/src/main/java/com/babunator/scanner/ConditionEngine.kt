package com.babunator.scanner

import kotlin.math.abs

object ConditionEngine {

    /*
     * Basic comparison.
     *
     * All calculations use full Double precision.
     * Near By / May Go To Cross use percentage gap
     * (measured against the right side value).
     */
    fun compare(
        a: Double,
        op: String,
        b: Double,
        rangePct: Double
    ): Boolean {

        if (!isUsable(a) || !isUsable(b)) {
            return false
        }

        val gap = abs(a - b) / maxOf(abs(b), 1e-12) * 100.0

        return when (op) {
            "Above" -> a > b
            "Below" -> a < b
            "Equal" -> abs(a - b) < 1e-9
            "Near By" -> gap <= rangePct
            "May Go To Cross Above" -> a < b && gap <= rangePct
            "May Go To Cross Below" -> a > b && gap <= rangePct
            else -> false
        }
    }

    private fun isUsable(value: Double): Boolean {
        return !value.isNaN() && !value.isInfinite()
    }

    /*
     * Cross Above / Cross Below: the previous candle must be on one
     * side and the current candle on the other side.
     */
    private fun crossed(
        series: List<Double>,
        c: Condition,
        currentLeft: Double,
        currentRight: Double,
        above: Boolean
    ): Boolean {

        if (series.size < 2) return false

        val previousSeries = series.dropLast(1)

        val previousLeft =
            Indicators.valueAt(
                previousSeries,
                c.leftIndicator,
                c.leftParams
            ) ?: return false

        val previousRight =
            if (c.rightIndicator == "Number") {
                c.rightTarget
            } else {
                Indicators.valueAt(
                    previousSeries,
                    c.rightIndicator,
                    c.rightParams
                ) ?: return false
            }

        if (!isUsable(previousLeft) || !isUsable(previousRight)) return false

        return if (above) {
            previousLeft <= previousRight && currentLeft > currentRight
        } else {
            previousLeft >= previousRight && currentLeft < currentRight
        }
    }

    /*
     * Evaluate one complete scanner condition.
     *
     * Current indicator values are supplied by ScannerEngine,
     * so normal comparisons do not calculate the same indicators
     * a second time.
     */
    fun evaluate(
        series: List<Double>,
        c: Condition,
        currentLeft: Double?,
        currentRight: Double?
    ): Boolean {

        if (series.isEmpty()) return false
        if (currentLeft == null || currentRight == null) return false
        if (!isUsable(currentLeft) || !isUsable(currentRight)) return false

        return when (c.comparator) {
            "Cross Above" -> crossed(series, c, currentLeft, currentRight, true)
            "Cross Below" -> crossed(series, c, currentLeft, currentRight, false)
            else -> compare(
                a = currentLeft,
                op = c.comparator,
                b = currentRight,
                rangePct = c.rangePct
            )
        }
    }

    /*
     * Combine the results of all conditions.
     *
     * logics[i] is how condition i is joined to the one before it
     * (logics[0] is ignored). AND is checked first, then OR:
     *
     *   1 AND 2 OR 3      means  (1 AND 2) OR 3
     *   1 OR 2 AND 3      means  1 OR (2 AND 3)
     */
    fun combine(
        checks: List<Boolean>,
        logics: List<String>
    ): Boolean {

        if (checks.isEmpty()) return false

        var anyGroupTrue = false
        var currentGroup = checks[0]

        for (i in 1 until checks.size) {
            val logic = logics.getOrNull(i) ?: "AND"

            if (logic == "OR") {
                anyGroupTrue = anyGroupTrue || currentGroup
                currentGroup = checks[i]
            } else {
                currentGroup = currentGroup && checks[i]
            }
        }

        return anyGroupTrue || currentGroup
    }

    /*
     * One line that tells the user how the conditions were joined,
     * for example:  Rule: (1 AND 2) OR 3  (AND is checked first, then OR)
     * Empty when there is only one condition.
     */
    fun explain(logics: List<String>): String {

        if (logics.size < 2) return ""

        val groups = mutableListOf<MutableList<Int>>()
        groups.add(mutableListOf(1))

        for (i in 1 until logics.size) {
            if (logics[i] == "OR") {
                groups.add(mutableListOf(i + 1))
            } else {
                groups.last().add(i + 1)
            }
        }

        val text = groups.joinToString(" OR ") { group ->
            if (group.size == 1) {
                group[0].toString()
            } else {
                group.joinToString(" AND ", "(", ")")
            }
        }

        return "Rule: $text  (AND is checked first, then OR)"
    }
}
