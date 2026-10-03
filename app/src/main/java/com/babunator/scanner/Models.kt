package com.babunator.scanner

data class Candle(
    val date: String,
    val close: Double
)

data class Condition(
    val leftIndicator: String,
    val leftParams: List<Double>,
    val comparator: String,
    val rightIndicator: String = "Number",
    val rightParams: List<Double> = emptyList(),
    val rightTarget: Double = 0.0,
    val rangePct: Double = 1.0,
    val logic: String = "AND"
)

data class ScanConfig(
    val timeframe: String,
    val logic: String,
    val conditions: List<Condition>,
    val timeframes: List<String> = listOf(timeframe)
)

data class Match(
    val symbol: String,
    val timeframe: String,
    val close: Double,
    val note: String
)

data class SavedScan(
    val id: Long,
    val created: String,
    val configJson: String,
    val timeframe: String,
    val conditions: String,
    val autoTrack: Boolean,
    val status: String
)

data class TrackingRun(
    val id: Long,
    val savedScanId: Long,
    val created: String,
    val matched: Int,
    val status: String
)

data class TrackingState(
    val savedScanId: Long,
    val entryPrice: Double?,
    val currentPrice: Double?,
    val pnl: Double?,
    val status: String,
    val updated: String
)
