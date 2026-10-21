package com.babunator.scanner

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object Indicators {

    // =========================================================
    // Moving averages
    // EMA/RMA follow Pine: recursive, first value = SMA(length)
    // =========================================================

    fun sma(x: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0 || x.size < n) return out

        var sum = 0.0
        for (i in x.indices) {
            sum += x[i]
            if (i >= n) sum -= x[i - n]
            if (i >= n - 1) out[i] = sum / n
        }
        return out
    }

    fun wma(x: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0 || x.size < n) return out

        val denominator = n * (n + 1) / 2.0
        for (i in n - 1 until x.size) {
            var sum = 0.0
            for (j in 0 until n) {
                sum += x[i - n + 1 + j] * (j + 1)
            }
            out[i] = sum / denominator
        }
        return out
    }

    fun ema(x: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0 || x.size < n) return out

        var seedSum = 0.0
        for (i in 0 until n) seedSum += x[i]

        var previous = seedSum / n
        out[n - 1] = previous

        val alpha = 2.0 / (n + 1.0)
        for (i in n until x.size) {
            previous = alpha * x[i] + (1.0 - alpha) * previous
            out[i] = previous
        }
        return out
    }

    private fun emaNullable(x: List<Double?>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0) return out

        val start = x.indexOfFirst { it != null }
        if (start < 0 || start + n > x.size) return out

        var seedSum = 0.0
        for (i in start until start + n) {
            val value = x[i] ?: return out
            seedSum += value
        }

        var previous = seedSum / n
        out[start + n - 1] = previous

        val alpha = 2.0 / (n + 1.0)
        for (i in start + n until x.size) {
            val value = x[i] ?: continue
            previous = alpha * value + (1.0 - alpha) * previous
            out[i] = previous
        }
        return out
    }

    fun rma(x: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0 || x.size < n) return out

        var seedSum = 0.0
        for (i in 0 until n) seedSum += x[i]

        var previous = seedSum / n
        out[n - 1] = previous

        for (i in n until x.size) {
            previous = (previous * (n - 1) + x[i]) / n
            out[i] = previous
        }
        return out
    }

    private fun smaNullable(x: List<Double?>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0) return out

        for (i in x.indices) {
            if (i < n - 1) continue

            var sum = 0.0
            var valid = true
            for (j in 0 until n) {
                val value = x[i - n + 1 + j]
                if (value == null) {
                    valid = false
                    break
                }
                sum += value
            }
            if (valid) out[i] = sum / n
        }
        return out
    }

    // =========================================================
    // HMA
    // =========================================================

    fun hma(x: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0 || x.isEmpty()) return out
        if (n == 1) return x.map { it }

        val half = max(1, (n / 2.0).roundToInt())
        val root = max(1, sqrt(n.toDouble()).roundToInt())

        val halfWma = wma(x, half)
        val fullWma = wma(x, n)

        val difference = MutableList<Double?>(x.size) { null }
        for (i in x.indices) {
            val a = halfWma[i]
            val b = fullWma[i]
            if (a != null && b != null) difference[i] = 2.0 * a - b
        }

        val denominator = root * (root + 1) / 2.0
        for (i in x.indices) {
            if (i < n + root - 2) continue

            var sum = 0.0
            var valid = true
            for (j in 0 until root) {
                val value = difference[i - root + 1 + j]
                if (value == null) {
                    valid = false
                    break
                }
                sum += value * (j + 1)
            }
            if (valid) out[i] = sum / denominator
        }
        return out
    }

    // =========================================================
    // RSI (Wilder), RSI based averages
    // =========================================================

    private fun calculateRsi(averageGain: Double, averageLoss: Double): Double {
        if (averageLoss == 0.0) return 100.0
        if (averageGain == 0.0) return 0.0
        val rs = averageGain / averageLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    fun rsi(x: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(x.size) { null }
        if (n <= 0 || x.size <= n) return out

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..n) {
            val change = x[i] - x[i - 1]
            if (change > 0.0) gainSum += change else if (change < 0.0) lossSum -= change
        }

        var averageGain = gainSum / n
        var averageLoss = lossSum / n
        out[n] = calculateRsi(averageGain, averageLoss)

        for (i in n + 1 until x.size) {
            val change = x[i] - x[i - 1]
            val gain = if (change > 0.0) change else 0.0
            val loss = if (change < 0.0) -change else 0.0

            averageGain = (averageGain * (n - 1) + gain) / n
            averageLoss = (averageLoss * (n - 1) + loss) / n
            out[i] = calculateRsi(averageGain, averageLoss)
        }
        return out
    }

    private fun emaOfRsi(series: List<Double>, rsiLength: Int, emaLength: Int): List<Double?> {
        return emaNullable(rsi(series, rsiLength), emaLength)
    }

    private fun smaOfRsi(series: List<Double>, rsiLength: Int, smaLength: Int): List<Double?> {
        return smaNullable(rsi(series, rsiLength), smaLength)
    }

    // =========================================================
    // Stoch RSI
    // A flat window (highest == lowest) gives no value, like
    // TradingView (division by zero = na).
    // =========================================================

    fun stochRawFromRsi(rsiValues: List<Double?>, n: Int): List<Double?> {
        val out = MutableList<Double?>(rsiValues.size) { null }
        if (n <= 0) return out

        for (i in rsiValues.indices) {
            if (i < n - 1) continue

            val window = rsiValues.subList(i - n + 1, i + 1)
            if (window.any { it == null }) continue

            val values = window.map { it!! }
            val current = values.last()
            val lowest = values.minOrNull() ?: continue
            val highest = values.maxOrNull() ?: continue
            val range = highest - lowest

            if (range == 0.0) continue
            out[i] = (current - lowest) / range * 100.0
        }
        return out
    }

    fun stochK(raw: List<Double?>, k: Int): List<Double?> = smaNullable(raw, k)

    fun stochD(k: List<Double?>, d: Int): List<Double?> = smaNullable(k, d)

    // =========================================================
    // MACD
    // =========================================================

    fun macd(
        x: List<Double>,
        fast: Int,
        slow: Int,
        signal: Int
    ): Triple<List<Double?>, List<Double?>, List<Double?>> {

        val line = MutableList<Double?>(x.size) { null }

        if (fast <= 0 || slow <= 0 || signal <= 0 || fast >= slow) {
            return Triple(
                line,
                MutableList<Double?>(x.size) { null },
                MutableList<Double?>(x.size) { null }
            )
        }

        val fastEma = ema(x, fast)
        val slowEma = ema(x, slow)

        for (i in x.indices) {
            val f = fastEma[i]
            val s = slowEma[i]
            if (f != null && s != null) line[i] = f - s
        }

        val signalLine = emaNullable(line, signal)

        val histogram = MutableList<Double?>(x.size) { null }
        for (i in x.indices) {
            val m = line[i]
            val s = signalLine[i]
            if (m != null && s != null) histogram[i] = m - s
        }

        return Triple(line, signalLine, histogram)
    }

    // =========================================================
    // Reverse helpers
    //
    // Convention for every "reverse" indicator: all bars except
    // the last are fixed; the result is the close the LAST bar
    // would need so that the indicator hits the wanted value.
    // =========================================================

    private fun previousValues(values: List<Double?>, count: Int): List<Double>? {
        if (count <= 0) return emptyList()
        val result = values.dropLast(1).takeLast(count).filterNotNull()
        return if (result.size == count) result else null
    }

    private fun rsiStateBeforeLast(series: List<Double>, n: Int): Pair<Double, Double>? {
        if (n <= 0 || series.size <= n) return null

        val previousIndex = series.lastIndex - 1
        if (previousIndex < n) return null

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..n) {
            val change = series[i] - series[i - 1]
            if (change > 0.0) gainSum += change else if (change < 0.0) lossSum -= change
        }

        var averageGain = gainSum / n
        var averageLoss = lossSum / n

        for (i in n + 1..previousIndex) {
            val change = series[i] - series[i - 1]
            val gain = if (change > 0.0) change else 0.0
            val loss = if (change < 0.0) -change else 0.0

            averageGain = (averageGain * (n - 1) + gain) / n
            averageLoss = (averageLoss * (n - 1) + loss) / n
        }
        return Pair(averageGain, averageLoss)
    }

    // Close of the last bar that makes RSI(last bar) == target.
    // The move is measured from the PREVIOUS close, because the
    // Wilder state used here ends at the previous bar.
    private fun reverseRsiRawPrice(series: List<Double>, target: Double, rsiLength: Int): Double? {
        if (target <= 0.0 || target >= 100.0 || rsiLength <= 0 || series.size <= rsiLength) {
            return null
        }

        val state = rsiStateBeforeLast(series, rsiLength) ?: return null
        val averageGain = state.first
        val averageLoss = state.second

        val targetRs = target / (100.0 - target)
        val factor = rsiLength - 1.0
        val currentRs =
            if (averageLoss == 0.0) Double.POSITIVE_INFINITY else averageGain / averageLoss

        val delta =
            if (targetRs > currentRs) {
                factor * (targetRs * averageLoss - averageGain)
            } else {
                factor * (averageLoss - averageGain / targetRs)
            }

        val previousClose = series[series.lastIndex - 1]
        return previousClose + delta
    }

    // "Reverse RSI" and "Reverse SMA of RSI": price at which the
    // smoothed RSI keeps its current value.
    private fun reverseSmoothedRsiPrice(series: List<Double>, rsiLength: Int, length: Int): Double? {
        if (rsiLength <= 0 || length <= 0) return null

        val r = rsi(series, rsiLength)
        val smoothed = smaNullable(r, length).lastOrNull() ?: return null

        if (length == 1) return reverseRsiRawPrice(series, smoothed, rsiLength)

        val previous = previousValues(r, length - 1) ?: return null
        val requiredRsi = smoothed * length - previous.sum()
        if (requiredRsi <= 0.0 || requiredRsi >= 100.0) return null

        return reverseRsiRawPrice(series, requiredRsi, rsiLength)
    }

    private fun reverseRsiLevelPrice(
        series: List<Double>,
        level: Double,
        rsiLength: Int,
        smoothingLength: Int
    ): Double? {
        if (level <= 0.0 || level >= 100.0 || rsiLength <= 0 || smoothingLength <= 0) return null
        if (series.size <= rsiLength + smoothingLength) return null

        val gains = DoubleArray(series.size)
        val losses = DoubleArray(series.size)
        for (i in 1 until series.size) {
            val change = series[i] - series[i - 1]
            if (change > 0.0) gains[i] = change else if (change < 0.0) losses[i] = -change
        }

        val avgGain = DoubleArray(series.size)
        val avgLoss = DoubleArray(series.size)

        var g = 0.0
        var l = 0.0
        for (i in 1..rsiLength) {
            g += gains[i]
            l += losses[i]
        }
        g /= rsiLength
        l /= rsiLength
        avgGain[rsiLength] = g
        avgLoss[rsiLength] = l

        for (i in rsiLength + 1 until series.size) {
            g = (g * (rsiLength - 1) + gains[i]) / rsiLength
            l = (l * (rsiLength - 1) + losses[i]) / rsiLength
            avgGain[i] = g
            avgLoss[i] = l
        }

        val targetRs = level / (100.0 - level)
        val raw = arrayOfNulls<Double>(series.size)

        for (i in rsiLength + 1 until series.size) {
            val aG = avgGain[i - 1]
            val aL = avgLoss[i - 1]
            val previousClose = series[i - 1]
            val currentRs = if (aL == 0.0) Double.POSITIVE_INFINITY else aG / aL

            raw[i] =
                if (targetRs > currentRs) {
                    previousClose + (rsiLength - 1) * (targetRs * aL - aG)
                } else {
                    previousClose - (rsiLength - 1) * (aG / targetRs - aL)
                }
        }

        val validStart = rsiLength + 1
        if (validStart + smoothingLength > raw.size) return null

        var seedSum = 0.0
        for (i in validStart until validStart + smoothingLength) {
            seedSum += raw[i] ?: return null
        }

        var smoothed = seedSum / smoothingLength
        val alpha = 2.0 / (smoothingLength + 1.0)

        for (i in validStart + smoothingLength until raw.size) {
            val value = raw[i] ?: continue
            smoothed = alpha * value + (1.0 - alpha) * smoothed
        }
        return smoothed
    }

    // =========================================================
    // Reverse Stoch RSI
    // =========================================================

    private fun reverseStochRawPrice(
        series: List<Double>,
        level: Double,
        rsiLength: Int,
        stochLength: Int
    ): Double? {
        if (level < 0.0 || level > 100.0 || rsiLength <= 0 || stochLength <= 0) return null

        val r = rsi(series, rsiLength)
        val previous = previousValues(r, stochLength - 1) ?: return null
        if (previous.isEmpty()) return null

        val lowest = previous.minOrNull() ?: return null
        val highest = previous.maxOrNull() ?: return null

        if (highest == lowest) {
            if (level != 0.0) return null
            return reverseRsiRawPrice(series, lowest, rsiLength)
        }

        val requiredRsi = lowest + (highest - lowest) * level / 100.0
        return reverseRsiRawPrice(series, requiredRsi, rsiLength)
    }

    private fun reverseCurrentStochKPrice(
        series: List<Double>,
        rsiLength: Int,
        stochLength: Int,
        kLength: Int
    ): Double? {
        if (rsiLength <= 0 || stochLength <= 0 || kLength <= 0) return null

        val raw = stochRawFromRsi(rsi(series, rsiLength), stochLength)
        val currentK = stochK(raw, kLength).lastOrNull() ?: return null

        if (kLength == 1) return reverseStochRawPrice(series, currentK, rsiLength, stochLength)

        val previous = previousValues(raw, kLength - 1) ?: return null
        val requiredRaw = currentK * kLength - previous.sum()
        if (requiredRaw < 0.0 || requiredRaw > 100.0) return null

        return reverseStochRawPrice(series, requiredRaw, rsiLength, stochLength)
    }

    private fun reverseCurrentStochDPrice(
        series: List<Double>,
        rsiLength: Int,
        stochLength: Int,
        kLength: Int,
        dLength: Int
    ): Double? {
        if (rsiLength <= 0 || stochLength <= 0 || kLength <= 0 || dLength <= 0) return null

        val raw = stochRawFromRsi(rsi(series, rsiLength), stochLength)
        val k = stochK(raw, kLength)
        val currentD = stochD(k, dLength).lastOrNull() ?: return null

        if (dLength == 1) {
            return reverseCurrentStochKPrice(series, rsiLength, stochLength, kLength)
        }

        val previousK = previousValues(k, dLength - 1) ?: return null
        val requiredK = currentD * dLength - previousK.sum()
        if (requiredK < 0.0 || requiredK > 100.0) return null

        if (kLength == 1) return reverseStochRawPrice(series, requiredK, rsiLength, stochLength)

        val previousRaw = previousValues(raw, kLength - 1) ?: return null
        val requiredRaw = requiredK * kLength - previousRaw.sum()
        if (requiredRaw < 0.0 || requiredRaw > 100.0) return null

        return reverseStochRawPrice(series, requiredRaw, rsiLength, stochLength)
    }

    private fun reverseStochLevelPrice(
        series: List<Double>,
        level: Double,
        rsiLength: Int,
        stochLength: Int,
        smoothingLength: Int
    ): Double? {
        if (level < 0.0 || level > 100.0 || rsiLength <= 0 || stochLength <= 0 || smoothingLength <= 0) {
            return null
        }

        if (smoothingLength == 1) return reverseStochRawPrice(series, level, rsiLength, stochLength)

        val raw = stochRawFromRsi(rsi(series, rsiLength), stochLength)
        val previous = previousValues(raw, smoothingLength - 1) ?: return null

        val requiredRaw = level * smoothingLength - previous.sum()
        if (requiredRaw < 0.0 || requiredRaw > 100.0) return null

        return reverseStochRawPrice(series, requiredRaw, rsiLength, stochLength)
    }

    // =========================================================
    // Reverse MACD
    // =========================================================

    private fun emaBeforeLast(series: List<Double>, length: Int): Double? {
        if (length <= 0 || series.size <= length) return null
        return ema(series.dropLast(1), length).lastOrNull()
    }

    // Close of the last bar that makes MACD line(last bar) == target.
    private fun reverseMacdLinePrice(series: List<Double>, target: Double, fast: Int, slow: Int): Double? {
        if (fast <= 0 || slow <= 0 || fast >= slow) return null

        val fastPrevious = emaBeforeLast(series, fast) ?: return null
        val slowPrevious = emaBeforeLast(series, slow) ?: return null

        val fastAlpha = 2.0 / (fast + 1.0)
        val slowAlpha = 2.0 / (slow + 1.0)

        val coefficient = fastAlpha - slowAlpha
        if (coefficient == 0.0) return null

        val constant = (1.0 - fastAlpha) * fastPrevious - (1.0 - slowAlpha) * slowPrevious
        return (target - constant) / coefficient
    }

    private fun reverseCurrentMacdPrice(
        series: List<Double>,
        fast: Int,
        slow: Int,
        signal: Int,
        smooth: Int
    ): Double? {
        if (smooth <= 0) return null

        val macdValues = macd(series, fast, slow, signal).first
        val current = macdValues.lastOrNull() ?: return null

        if (smooth == 1) return reverseMacdLinePrice(series, current, fast, slow)

        val previous = previousValues(macdValues, smooth - 1) ?: return null
        val requiredCurrent = current * smooth - previous.sum()

        return reverseMacdLinePrice(series, requiredCurrent, fast, slow)
    }

    private fun reverseCurrentMacdSignalPrice(
        series: List<Double>,
        fast: Int,
        slow: Int,
        signal: Int,
        smooth: Int
    ): Double? {
        if (smooth <= 0) return null

        val values = macd(series, fast, slow, signal).second
        val current = values.lastOrNull() ?: return null

        val requiredSignal =
            if (smooth == 1) {
                current
            } else {
                val previous = previousValues(values, smooth - 1) ?: return null
                current * smooth - previous.sum()
            }

        val previousSignal = values.dropLast(1).lastOrNull { it != null } ?: return null

        val alpha = 2.0 / (signal + 1.0)
        val requiredMacd = (requiredSignal - (1.0 - alpha) * previousSignal) / alpha

        return reverseMacdLinePrice(series, requiredMacd, fast, slow)
    }

    private fun reverseCurrentMacdZeroPrice(
        series: List<Double>,
        fast: Int,
        slow: Int,
        signal: Int,
        smooth: Int
    ): Double? {
        if (smooth <= 0) return null

        val values = macd(series, fast, slow, signal).first

        val requiredCurrent =
            if (smooth == 1) {
                0.0
            } else {
                val previous = previousValues(values, smooth - 1) ?: return null
                -previous.sum() / smooth
            }

        return reverseMacdLinePrice(series, requiredCurrent, fast, slow)
    }

    // =========================================================
    // Main dispatcher
    // =========================================================

    fun valueAt(x: List<Double>, name: String, p: List<Double>): Double? {

        fun intParam(index: Int): Int? {
            val value = p.getOrNull(index) ?: return null
            if (value.isNaN() || value.isInfinite()) return null
            return value.toInt().takeIf { it > 0 }
        }

        fun levelParam(index: Int): Double? {
            val value = p.getOrNull(index) ?: return null
            return value.takeIf { it.isFinite() && it in 0.0..100.0 }
        }

        return when (name) {

            "Close" -> x.lastOrNull()

            "SMA" -> sma(x, intParam(0) ?: return null).lastOrNull()

            "WMA" -> wma(x, intParam(0) ?: return null).lastOrNull()

            "EMA" -> ema(x, intParam(0) ?: return null).lastOrNull()

            "HMA" -> hma(x, intParam(0) ?: return null).lastOrNull()

            "RSI" -> rsi(x, intParam(0) ?: return null).lastOrNull()

            "EMA of RSI" -> {
                val rsiLength = intParam(0) ?: return null
                val smoothingLength = intParam(1) ?: return null
                emaOfRsi(x, rsiLength, smoothingLength).lastOrNull()
            }

            "SMA of RSI" -> {
                val rsiLength = intParam(0) ?: return null
                val smoothingLength = intParam(1) ?: return null
                smaOfRsi(x, rsiLength, smoothingLength).lastOrNull()
            }

            "MACD" -> {
                val fast = intParam(0) ?: return null
                val slow = intParam(1) ?: return null
                val signal = intParam(2) ?: return null
                macd(x, fast, slow, signal).first.lastOrNull()
            }

            "MACD Signal" -> {
                val fast = intParam(0) ?: return null
                val slow = intParam(1) ?: return null
                val signal = intParam(2) ?: return null
                macd(x, fast, slow, signal).second.lastOrNull()
            }

            "MACD Histogram" -> {
                val fast = intParam(0) ?: return null
                val slow = intParam(1) ?: return null
                val signal = intParam(2) ?: return null
                macd(x, fast, slow, signal).third.lastOrNull()
            }

            "Stoch RSI" -> {
                val rsiLength = intParam(0) ?: return null
                val stochLength = intParam(1) ?: return null
                stochRawFromRsi(rsi(x, rsiLength), stochLength).lastOrNull()
            }

            "Stoch RSI %K" -> {
                val rsiLength = intParam(0) ?: return null
                val stochLength = intParam(1) ?: return null
                val kLength = intParam(2) ?: return null
                val raw = stochRawFromRsi(rsi(x, rsiLength), stochLength)
                stochK(raw, kLength).lastOrNull()
            }

            "Stoch RSI %D" -> {
                val rsiLength = intParam(0) ?: return null
                val stochLength = intParam(1) ?: return null
                val kLength = intParam(2) ?: return null
                val dLength = intParam(3) ?: return null
                val raw = stochRawFromRsi(rsi(x, rsiLength), stochLength)
                stochD(stochK(raw, kLength), dLength).lastOrNull()
            }

            "Numeric Value" -> p.firstOrNull()

            "Reverse RSI" -> {
                val rsiLength = intParam(0) ?: return null
                val smoothingLength = intParam(1) ?: return null
                reverseSmoothedRsiPrice(x, rsiLength, smoothingLength)
            }

            "Reverse SMA of RSI" -> {
                val rsiLength = intParam(0) ?: return null
                val smaLength = intParam(1) ?: return null
                reverseSmoothedRsiPrice(x, rsiLength, smaLength)
            }

            "Reverse RSI Level" -> {
                val rsiLength = intParam(0) ?: return null
                val smoothingLength = intParam(1) ?: return null
                val level = levelParam(2) ?: return null
                reverseRsiLevelPrice(x, level, rsiLength, smoothingLength)
            }

            "Reverse Stoch RSI Level" -> {
                val rsiLength = intParam(0) ?: return null
                val stochLength = intParam(1) ?: return null
                val level = levelParam(2) ?: return null
                val smoothingLength = intParam(3) ?: return null
                reverseStochLevelPrice(x, level, rsiLength, stochLength, smoothingLength)
            }

            "Reverse Stoch RSI %K" -> {
                val rsiLength = intParam(0) ?: return null
                val stochLength = intParam(1) ?: return null
                val kLength = intParam(2) ?: return null
                reverseCurrentStochKPrice(x, rsiLength, stochLength, kLength)
            }

            "Reverse Stoch RSI %D" -> {
                val rsiLength = intParam(0) ?: return null
                val stochLength = intParam(1) ?: return null
                val kLength = intParam(2) ?: return null
                val dLength = intParam(3) ?: return null
                reverseCurrentStochDPrice(x, rsiLength, stochLength, kLength, dLength)
            }

            "Reverse MACD" -> {
                val fast = intParam(0) ?: return null
                val slow = intParam(1) ?: return null
                val signal = intParam(2) ?: return null
                val smooth = intParam(3) ?: return null
                reverseCurrentMacdPrice(x, fast, slow, signal, smooth)
            }

            "Reverse MACD Signal" -> {
                val fast = intParam(0) ?: return null
                val slow = intParam(1) ?: return null
                val signal = intParam(2) ?: return null
                val smooth = intParam(3) ?: return null
                reverseCurrentMacdSignalPrice(x, fast, slow, signal, smooth)
            }

            "Reverse MACD Zero Line" -> {
                val fast = intParam(0) ?: return null
                val slow = intParam(1) ?: return null
                val signal = intParam(2) ?: return null
                val smooth = intParam(3) ?: return null
                reverseCurrentMacdZeroPrice(x, fast, slow, signal, smooth)
            }

            else -> null
        }
    }
}
