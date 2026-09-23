package com.babunator.scanner

data class Candle(val date: String, val close: Double)
data class Condition(
    val leftIndicator: String,
    val leftParams: List<Double>,
    val comparator: String,
    val rightIndicator: String = "Number",
    val rightParams: List<Double> = emptyList(),
    val rightTarget: Double = 0.0,
    val rangePct: Double = 1.0
)
data class ScanConfig(
    val timeframe: String,
    val logic: String,
    val conditions: List<Condition>,
    val timeframes: List<String> = listOf(timeframe)
)
data class Match(val symbol: String, val timeframe: String, val close: Double, val note: String)
