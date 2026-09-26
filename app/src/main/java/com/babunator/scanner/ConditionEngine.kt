package com.babunator.scanner

import kotlin.math.abs

object ConditionEngine {

    /*
     * Basic comparison.
     *
     * All calculations use full Double precision.
     * Near By / May Go To Cross use percentage gap.
     */
    fun compare(
        a: Double,
        op: String,
        b: Double,
        rangePct: Double
    ): Boolean {

        if (
            a.isNaN() ||
            a.isInfinite() ||
            b.isNaN() ||
            b.isInfinite()
        ) {
            return false
        }

        val gap =
            abs(a - b) /
                    maxOf(abs(b), 1e-12) *
                    100.0

        return when (op) {

            "Above" ->
                a > b

            "Below" ->
                a < b

            "Equal" ->
                abs(a - b) < 1e-9

            "Near By" ->
                gap <= rangePct

            "May Go To Cross Above" ->
                a < b &&
                        gap <= rangePct

            "May Go To Cross Below" ->
                a > b &&
                        gap <= rangePct

            else ->
                false
        }
    }

    /*
     * Evaluate one complete scanner condition.
     *
     * Current indicator values are supplied by ScannerEngine,
     * so normal comparisons do not calculate the same indicators
     * a second time.
     *
     * For Cross Above / Cross Below, only the previous candle
     * values need to be calculated here.
     */
    fun evaluate(
        series: List<Double>,
        c: Condition,
        currentLeft: Double?,
        currentRight: Double?
    ): Boolean {

        if (series.isEmpty()) {
            return false
        }

        if (currentLeft == null || currentRight == null) {
            return false
        }

        if (
            currentLeft.isNaN() ||
            currentLeft.isInfinite() ||
            currentRight.isNaN() ||
            currentRight.isInfinite()
        ) {
            return false
        }

        return when (c.comparator) {

            "Cross Above" -> {

                if (series.size < 2) {
                    false
                } else {

                    val previousSeries =
                        series.dropLast(1)

                    val previousLeft =
                        Indicators.valueAt(
                            previousSeries,
                            c.leftIndicator,
                            c.leftParams
                        )
                            ?: return false

                    val previousRight =
                        if (c.rightIndicator == "Number") {
                            c.rightTarget
                        } else {
                            Indicators.valueAt(
                                previousSeries,
                                c.rightIndicator,
                                c.rightParams
                            )
                                ?: return false
                        }

                    if (
                        previousLeft.isNaN() ||
                        previousLeft.isInfinite() ||
                        previousRight.isNaN() ||
                        previousRight.isInfinite()
                    ) {
                        false
                    } else {
                        previousLeft <= previousRight &&
                                currentLeft > currentRight
                    }
                }
            }

            "Cross Below" -> {

                if (series.size < 2) {
                    false
                } else {

                    val previousSeries =
                        series.dropLast(1)

                    val previousLeft =
                        Indicators.valueAt(
                            previousSeries,
                            c.leftIndicator,
                            c.leftParams
                        )
                            ?: return false

                    val previousRight =
                        if (c.rightIndicator == "Number") {
                            c.rightTarget
                        } else {
                            Indicators.valueAt(
                                previousSeries,
                                c.rightIndicator,
                                c.rightParams
                            )
                                ?: return false
                        }

                    if (
                        previousLeft.isNaN() ||
                        previousLeft.isInfinite() ||
                        previousRight.isNaN() ||
                        previousRight.isInfinite()
                    ) {
                        false
                    } else {
                        previousLeft >= previousRight &&
                                currentLeft < currentRight
                    }
                }
            }

            else -> {

                compare(
                    a = currentLeft,
                    op = c.comparator,
                    b = currentRight,
                    rangePct = c.rangePct
                )
            }
        }
    }
}
