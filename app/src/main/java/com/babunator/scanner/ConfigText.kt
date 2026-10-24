package com.babunator.scanner

import java.util.Locale

object ConfigText {

    fun timeframesOf(cfg: ScanConfig): String {
        return cfg.timeframes
            .ifEmpty { listOf(cfg.timeframe) }
            .distinct()
            .joinToString(", ")
    }

    // Same text format that scans save with their results
    // ("TF = ...", "Logic = ...", "Condition 1 = ...").
    fun describe(cfg: ScanConfig): String {
        return buildString {
            append("TF = ")
            append(timeframesOf(cfg))
            append("\n")
            append("Logic = ")
            append(cfg.logic)

            cfg.conditions.forEachIndexed { index, c ->
                append("\n")

                append("Condition ")
                append(index + 1)

                if (index > 0) {
                    append(" [")
                    append(c.logic)
                    append("]")
                }

                append(" = ")
                append(c.leftIndicator)

                if (c.leftParams.isNotEmpty()) {
                    append(
                        c.leftParams.joinToString(
                            prefix = "(",
                            postfix = ")"
                        )
                    )
                }

                append(" ")
                append(c.comparator)
                append(" ")
                append(c.rightIndicator)

                if (c.rightParams.isNotEmpty()) {
                    append(
                        c.rightParams.joinToString(
                            prefix = "(",
                            postfix = ")"
                        )
                    )
                }

                if (c.rightIndicator == "Number") {
                    append(" ")
                    append(c.rightTarget)
                }
            }
        }
    }

    // Short, easy to read version for screens:
    //   Timeframe: Daily
    //   1. EMA(20) Cross Above EMA(50)
    //   2. [AND] RSI(14) Above 50
    //   Rule: (1 AND 2)  (AND is checked first, then OR)
    fun readable(cfg: ScanConfig): String {
        return buildString {
            append("Timeframe: ")
            append(timeframesOf(cfg))

            cfg.conditions.forEachIndexed { index, c ->
                append("\n")
                append(index + 1)
                append(". ")

                if (index > 0) {
                    append("[")
                    append(c.logic)
                    append("] ")
                }

                append(c.leftIndicator)
                append(params(c.leftParams))
                append(" ")
                append(c.comparator)
                append(" ")

                if (c.rightIndicator == "Number") {
                    append(number(c.rightTarget))
                } else {
                    append(c.rightIndicator)
                    append(params(c.rightParams))
                }
            }

            val rule = ConditionEngine.explain(cfg.conditions.map { it.logic })

            if (rule.isNotEmpty()) {
                append("\n")
                append(rule)
            }
        }
    }

    private fun params(values: List<Double>): String {
        if (values.isEmpty()) return ""

        return values.joinToString(prefix = "(", postfix = ")") { number(it) }
    }

    private fun number(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }
}
