package com.babunator.scanner

import kotlin.math.*

object Indicators {
    fun sma(x: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0) return out
        var sum = 0.0
        for (i in x.indices) {
            sum += x[i]
            if (i >= n) sum -= x[i - n]
            if (i >= n - 1) out[i] = sum / n
        }
        return out
    }

    fun ema(x: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0 || x.size < n) return out
        var prev = x.take(n).average()
        out[n - 1] = prev
        val a = 2.0 / (n + 1.0)
        for (i in n until x.size) {
            prev = a * x[i] + (1 - a) * prev
            out[i] = prev
        }
        return out
    }

    fun rsi(x: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0 || x.size <= n) return out
        var gain = 0.0; var loss = 0.0
        for (i in 1..n) {
            val d = x[i] - x[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        var ag = gain / n; var al = loss / n
        out[n] = if (al == 0.0) 100.0 else 100.0 - 100.0 / (1 + ag / al)
        for (i in n + 1 until x.size) {
            val d = x[i] - x[i - 1]
            val g = max(d, 0.0); val l = max(-d, 0.0)
            ag = (ag * (n - 1) + g) / n
            al = (al * (n - 1) + l) / n
            out[i] = if (al == 0.0) 100.0 else 100.0 - 100.0 / (1 + ag / al)
        }
        return out
    }

    fun wma(x: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0) return out
        val den = n * (n + 1) / 2.0
        for (i in n - 1 until x.size) {
            var s = 0.0
            for (j in 0 until n) s += x[i - n + 1 + j] * (j + 1)
            out[i] = s / den
        }
        return out
    }

    fun hma(x: List<Double>, n: Int): List<Double?> {
        if (n < 2) return x.map { it }
        val half = max(1, n / 2); val root = max(1, sqrt(n.toDouble()).roundToInt())
        val w1 = wma(x, half); val w2 = wma(x, n)
        val diff = x.indices.map { i ->
            val a = w1[i]; val b = w2[i]
            if (a != null && b != null) 2 * a - b else Double.NaN
        }
        val valid = diff.map { if (it.isNaN()) 0.0 else it }
        val h = wma(valid, root)
        return h.mapIndexed { i, v -> if (diff[i].isNaN()) null else v }
    }

    fun stochRawFromRsi(rsi: List<Double?>, n: Int): List<Double?> {
        val out = MutableList<Double?>(rsi.size) { null }
        for (i in rsi.indices) {
            if (i < n - 1) continue
            val win = rsi.subList(i - n + 1, i + 1).filterNotNull()
            if (win.size == n) {
                val lo = win.min(); val hi = win.max()
                out[i] = if (hi == lo) 0.0 else 100.0 * (rsi[i]!! - lo) / (hi - lo)
            }
        }
        return out
    }

    fun stochK(raw: List<Double?>, k: Int) = sma(raw.map { it ?: Double.NaN }.map { if (it.isNaN()) 0.0 else it }, k)
    fun stochD(k: List<Double?>, d: Int) = sma(k.map { it ?: 0.0 }, d)

    fun macd(x: List<Double>, fast: Int = 12, slow: Int = 26, signal: Int = 9): Triple<List<Double?>, List<Double?>, List<Double?>> {
        val ef = ema(x, fast); val es = ema(x, slow)
        val line = x.indices.map { i -> if (ef[i] != null && es[i] != null) ef[i]!! - es[i]!! else null }
        val clean = line.map { it ?: 0.0 }
        val sig = ema(clean, signal)
        val hist = line.indices.map { i -> if (line[i] != null && sig[i] != null) line[i]!! - sig[i]!! else null }
        return Triple(line, sig, hist)
    }

    fun reverseRsiPrice(series: List<Double>, target: Double, n: Int): Double? {
        if (n <= 1 || series.size < n + 1 || target !in 0.0..100.0) return null
        val closes = series.takeLast(n + 1)
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..n) {
            val d = closes[i] - closes[i - 1]
            if (d >= 0) avgGain += d else avgLoss -= d
        }
        avgGain /= n; avgLoss /= n
        if (target <= 0.0) return closes.last() - max(1.0, closes.last() * 0.5)
        if (target >= 100.0) return closes.last() + max(1.0, closes.last() * 0.5)
        val rs = target / (100.0 - target)
        val k = n - 1.0
        val upDelta = k * (rs * avgLoss - avgGain)
        val downAbs = k * (avgGain / rs - avgLoss)
        val delta = when {
            upDelta >= 0.0 -> upDelta
            downAbs > 0.0 -> -downAbs
            else -> 0.0
        }
        return closes.last() + delta
    }

    private fun stochRawAtCurrentRsi(series: List<Double>, rsiN: Int, stochN: Int, targetRaw: Double): Double? {
        if (series.size < rsiN + stochN + 2) return null
        val r = rsi(series, rsiN)
        val previous = r.dropLast(1).takeLast(stochN - 1).filterNotNull()
        if (previous.size != stochN - 1) return null
        val lo = previous.minOrNull() ?: return null
        val hi = previous.maxOrNull() ?: return null
        return lo + (hi - lo) * (targetRaw / 100.0)
    }

    private fun reverseStoch(series: List<Double>, target: Double, rsiN: Int, stochN: Int): Double? {
        val r = rsi(series, rsiN)
        val prev = r.dropLast(1).takeLast(stochN - 1).filterNotNull()
        if (prev.size != stochN - 1) return null
        val lo = prev.minOrNull() ?: return null; val hi = prev.maxOrNull() ?: return null
        val targetRsi = lo + (hi - lo) * (target / 100.0)
        return reverseRsiPrice(series, targetRsi, rsiN)
    }

    private fun reverseStochK(series: List<Double>, targetK: Double, rsiN: Int, stochN: Int, kN: Int): Double? {
        val r = rsi(series, rsiN); val raw = stochRawFromRsi(r, stochN)
        val prev = raw.dropLast(1).takeLast(kN - 1).filterNotNull()
        if (prev.size != kN - 1) return null
        val targetRaw = (targetK * kN - prev.sum())
        return reverseStoch(series, targetRaw, rsiN, stochN)
    }

    private fun reverseStochD(series: List<Double>, targetD: Double, rsiN: Int, stochN: Int, kN: Int, dN: Int): Double? {
        val r = rsi(series, rsiN); val raw = stochRawFromRsi(r, stochN); val k = stochK(raw, kN)
        val prevK = k.dropLast(1).takeLast(dN - 1).filterNotNull()
        if (prevK.size != dN - 1) return null
        val targetK = targetD * dN - prevK.sum()
        return reverseStochK(series, targetK, rsiN, stochN, kN)
    }

    fun valueAt(x: List<Double>, name: String, p: List<Double>): Double? {
        val n1 = p.getOrElse(0) { 14.0 }.toInt().coerceAtLeast(1)
        return when (name) {
            "Close" -> x.lastOrNull()
            "EMA" -> ema(x, n1).lastOrNull { it != null }
            "HMA" -> hma(x, n1).lastOrNull { it != null }
            "RSI" -> rsi(x, n1).lastOrNull { it != null }
            "EMA of RSI" -> {
                val r = rsi(x, n1).map { it ?: 0.0 }
                ema(r, p.getOrElse(1) { 9.0 }.toInt().coerceAtLeast(1)).lastOrNull { it != null }
            }
            "MACD" -> macd(x, p.getOrElse(0) {12.0}.toInt(), p.getOrElse(1){26.0}.toInt(), p.getOrElse(2){9.0}.toInt()).first.lastOrNull { it != null }
            "MACD Signal" -> macd(x, p.getOrElse(0){12.0}.toInt(), p.getOrElse(1){26.0}.toInt(), p.getOrElse(2){9.0}.toInt()).second.lastOrNull { it != null }
            "MACD Histogram" -> macd(x, p.getOrElse(0){12.0}.toInt(), p.getOrElse(1){26.0}.toInt(), p.getOrElse(2){9.0}.toInt()).third.lastOrNull { it != null }
            "Stoch RSI %K" -> {
                val r = rsi(x, n1); val raw = stochRawFromRsi(r, p.getOrElse(1){14.0}.toInt()); stochK(raw, p.getOrElse(2){3.0}.toInt()).lastOrNull { it != null }
            }
            "Stoch RSI %D" -> {
                val r = rsi(x, n1); val raw = stochRawFromRsi(r, p.getOrElse(1){14.0}.toInt()); val k = stochK(raw, p.getOrElse(2){3.0}.toInt()); stochD(k, p.getOrElse(3){3.0}.toInt()).lastOrNull { it != null }
            }
            "Numeric Value" -> p.firstOrNull()
            "Reverse RSI" -> reverseRsiPrice(x, p.getOrElse(1){50.0}, n1)
            "Reverse Stoch RSI" -> reverseStoch(x, p.getOrElse(2){50.0}, n1, p.getOrElse(1){14.0}.toInt().coerceAtLeast(1))
            "Reverse Stoch RSI %K" -> reverseStochK(x, p.getOrElse(3){50.0}, n1, p.getOrElse(1){14.0}.toInt().coerceAtLeast(1), p.getOrElse(2){3.0}.toInt().coerceAtLeast(1))
            "Reverse Stoch RSI %D" -> reverseStochD(x, p.getOrElse(4){50.0}, n1, p.getOrElse(1){14.0}.toInt().coerceAtLeast(1), p.getOrElse(2){3.0}.toInt().coerceAtLeast(1), p.getOrElse(3){3.0}.toInt().coerceAtLeast(1))
            else -> null
        }
    }
}
