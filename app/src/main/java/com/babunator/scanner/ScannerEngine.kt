package com.babunator.scanner

class ScannerEngine(private val db: AppDb) {
    fun resample(rows: List<Candle>, timeframe: String): List<Candle> {
        if (timeframe == "Daily") return rows
        val groups = linkedMapOf<String, Candle>()
        for (r in rows) {
            val d = java.time.LocalDate.parse(r.date)
            val key = if (timeframe == "Weekly") d.minusDays((d.dayOfWeek.value - 1).toLong()).toString() else d.withDayOfMonth(1).toString()
            groups[key] = r
        }
        return groups.values.toList()
    }

    fun scan(symbols: List<String>, cfg: ScanConfig): List<Match> {
        val hits = mutableListOf<Match>()
        for (s in symbols) {
            val rows = resample(db.getHistory(s), cfg.timeframe)
            if (rows.size < 60) continue
            val close = rows.map { it.close }
            val checks = cfg.conditions.map { ConditionEngine.evaluate(close, it) }
            val ok = if (cfg.logic == "OR") checks.any { it } else checks.all { it }
            if (ok) hits += Match(s, cfg.timeframe, close.last(), "conditions=${checks.joinToString(",")}")
        }
        return hits
    }
}
