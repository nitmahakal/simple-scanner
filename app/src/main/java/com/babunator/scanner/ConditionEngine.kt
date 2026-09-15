package com.babunator.scanner

import kotlin.math.abs

object ConditionEngine {
    fun compare(a: Double, op: String, b: Double, rangePct: Double): Boolean {
        val gap = abs(a - b) / maxOf(abs(b), 1e-9) * 100.0
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

    fun evaluate(series: List<Double>, c: Condition): Boolean {
        val a = Indicators.valueAt(series, c.leftIndicator, c.leftParams) ?: return false
        val b = if (c.rightIndicator == "Number") c.rightTarget else Indicators.valueAt(series, c.rightIndicator, c.rightParams) ?: return false
        val range = if (c.comparator == "Near By" || c.comparator.startsWith("May Go")) c.rangePct else c.rangePct
        return when (c.comparator) {
            "Cross Above" -> {
                if (series.size < 2) false else {
                    val a0 = Indicators.valueAt(series.dropLast(1), c.leftIndicator, c.leftParams) ?: return false
                    val b0 = if (c.rightIndicator == "Number") c.rightTarget else Indicators.valueAt(series.dropLast(1), c.rightIndicator, c.rightParams) ?: return false
                    a0 <= b0 && a > b
                }
            }
            "Cross Below" -> {
                if (series.size < 2) false else {
                    val a0 = Indicators.valueAt(series.dropLast(1), c.leftIndicator, c.leftParams) ?: return false
                    val b0 = if (c.rightIndicator == "Number") c.rightTarget else Indicators.valueAt(series.dropLast(1), c.rightIndicator, c.rightParams) ?: return false
                    a0 >= b0 && a < b
                }
            }
            else -> compare(a, c.comparator, b, range)
        }
    }
}
