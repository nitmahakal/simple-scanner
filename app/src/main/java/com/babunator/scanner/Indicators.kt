package com.babunator.scanner

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object Indicators {

    /*
     * Simple Moving Average
     */
    fun sma(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out = MutableList<Double?>(x.size) { null }

        if (n <= 0) return out

        var sum = 0.0

        for (i in x.indices) {

            sum += x[i]

            if (i >= n) {
                sum -= x[i - n]
            }

            if (i >= n - 1) {
                out[i] = sum / n
            }
        }

        return out
    }

    /*
     * EMA
     *
     * Standard EMA seed:
     * SMA of the first n values.
     */
    fun ema(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out = MutableList<Double?>(x.size) { null }

        if (n <= 0 || x.size < n) {
            return out
        }

        var prev = x.take(n).average()

        out[n - 1] = prev

        val alpha = 2.0 / (n + 1.0)

        for (i in n until x.size) {

            prev =
                alpha * x[i] +
                        (1.0 - alpha) * prev

            out[i] = prev
        }

        return out
    }

    /*
     * EMA for a nullable series.
     *
     * This is important for indicators such as
     * EMA of RSI. Invalid warm-up values are NOT
     * converted to zero.
     */
    private fun emaNullable(
        x: List<Double?>,
        n: Int
    ): List<Double?> {

        val out = MutableList<Double?>(x.size) { null }

        if (n <= 0) return out

        var start = -1

        for (i in x.indices) {
            if (x[i] != null) {
                start = i
                break
            }
        }

        if (start < 0) return out

        val validCount =
            x.size - start

        if (validCount < n) {
            return out
        }

        var seedSum = 0.0

        for (i in start until start + n) {
            val v = x[i] ?: return out
            seedSum += v
        }

        var prev = seedSum / n

        out[start + n - 1] = prev

        val alpha = 2.0 / (n + 1.0)

        for (i in start + n until x.size) {

            val v = x[i] ?: continue

            prev =
                alpha * v +
                        (1.0 - alpha) * prev

            out[i] = prev
        }

        return out
    }

    /*
     * Wilder RMA / SMMA
     *
     * TradingView RSI uses RMA for average gain/loss.
     */
    fun rma(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out = MutableList<Double?>(x.size) { null }

        if (n <= 0 || x.size < n) {
            return out
        }

        var prev = x.take(n).average()

        out[n - 1] = prev

        val alpha = 1.0 / n.toDouble()

        for (i in n until x.size) {

            prev =
                alpha * x[i] +
                        (1.0 - alpha) * prev

            out[i] = prev
        }

        return out
    }

    /*
     * TradingView-compatible RSI structure:
     *
     * change
     * gain / loss
     * RMA(gain)
     * RMA(loss)
     * RSI = 100 - 100 / (1 + RS)
     */
    fun rsi(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out = MutableList<Double?>(x.size) { null }

        if (n <= 0 || x.size <= n) {
            return out
        }

        var gainSum = 0.0
        var lossSum = 0.0

        /*
         * Wilder RSI initialization:
         *
         * The first average gain/loss uses the
         * first n actual price changes:
         *
         * change[1] ... change[n]
         *
         * The first RSI is therefore available
         * at index n.
         */
        for (i in 1..n) {

            val change =
                x[i] - x[i - 1]

            if (change >= 0.0) {
                gainSum += change
            } else {
                lossSum -= change
            }
        }

        var avgGain =
            gainSum / n.toDouble()

        var avgLoss =
            lossSum / n.toDouble()

        out[n] =
            when {
                avgLoss == 0.0 && avgGain == 0.0 ->
                    0.0

                avgLoss == 0.0 ->
                    100.0

                else -> {
                    val rs =
                        avgGain / avgLoss

                    100.0 -
                            100.0 / (1.0 + rs)
                }
            }

        /*
         * Wilder recursive update.
         */
        for (i in n + 1 until x.size) {

            val change =
                x[i] - x[i - 1]

            val gain =
                if (change > 0.0) {
                    change
                } else {
                    0.0
                }

            val loss =
                if (change < 0.0) {
                    -change
                } else {
                    0.0
                }

            avgGain =
                (
                    avgGain * (n - 1) +
                            gain
                    ) / n.toDouble()

            avgLoss =
                (
                    avgLoss * (n - 1) +
                            loss
                    ) / n.toDouble()

            out[i] =
                when {
                    avgLoss == 0.0 && avgGain == 0.0 ->
                        0.0

                    avgLoss == 0.0 ->
                        100.0

                    else -> {
                        val rs =
                            avgGain / avgLoss

                        100.0 -
                                100.0 / (1.0 + rs)
                    }
                }
        }

        return out
    }    

    /*
     * Weighted Moving Average
     */
    fun wma(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out = MutableList<Double?>(x.size) { null }

        if (n <= 0) return out

        val denominator =
            n * (n + 1) / 2.0

        for (i in n - 1 until x.size) {

            var sum = 0.0

            for (j in 0 until n) {

                sum +=
                    x[i - n + 1 + j] *
                            (j + 1)
            }

            out[i] =
                sum / denominator
        }

        return out
    }

    /*
     * Hull Moving Average
     *
     * HMA(n) =
     * WMA(
     *     2 * WMA(price, n/2) - WMA(price, n),
     *     sqrt(n)
     * )
     */
    fun hma(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(x.size) { null }

        if (n <= 0 || x.isEmpty()) {
            return out
        }

        if (n == 1) {
            return x.map { it }
        }

        val half =
            max(1, n / 2)

        val root =
            max(
                1,
                sqrt(n.toDouble()).roundToInt()
            )

        val halfWma =
            wma(x, half)

        val fullWma =
            wma(x, n)

        val diff =
            MutableList<Double?>(x.size) { null }

        for (i in x.indices) {

            val a = halfWma[i]
            val b = fullWma[i]

            if (a != null && b != null) {
                diff[i] =
                    2.0 * a - b
            }
        }

        /*
         * WMA over the valid HMA intermediate series.
         * No zero-padding of invalid warm-up values.
         */
        for (i in x.indices) {

            if (i < n + root - 2) {
                continue
            }

            var sum = 0.0
            var valid = true

            for (j in 0 until root) {

                val value =
                    diff[i - root + 1 + j]

                if (value == null) {
                    valid = false
                    break
                }

                sum +=
                    value * (j + 1)
            }

            if (valid) {

                val denominator =
                    root * (root + 1) / 2.0

                out[i] =
                    sum / denominator
            }
        }

        return out
    }

    /*
     * Stochastic value of an RSI series.
     *
     * Raw Stoch RSI:
     *
     * 100 * (RSI - lowest RSI) /
     *      (highest RSI - lowest RSI)
     */
    fun stochRawFromRsi(
        rsiValues: List<Double?>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(
                rsiValues.size
            ) { null }

        if (n <= 0) return out

        for (i in rsiValues.indices) {

            if (i < n - 1) {
                continue
            }

            val window =
                rsiValues.subList(
                    i - n + 1,
                    i + 1
                )

            if (window.any { it == null }) {
                continue
            }

            val values =
                window.map { it!! }

            val current =
                values.last()

            val lowest =
                values.minOrNull()
                    ?: continue

            val highest =
                values.maxOrNull()
                    ?: continue

            out[i] =
                if (highest == lowest) {
                    0.0
                } else {
                    100.0 *
                            (current - lowest) /
                            (highest - lowest)
                }
        }

        return out
    }

    /*
     * SMA smoothing of raw Stoch RSI.
     *
     * Invalid warm-up values are NOT replaced
     * by zero.
     */
    fun stochK(
        raw: List<Double?>,
        k: Int
    ): List<Double?> {
        return smaNullable(raw, k)
    }

    fun stochD(
        k: List<Double?>,
        d: Int
    ): List<Double?> {
        return smaNullable(k, d)
    }

    private fun smaNullable(
        x: List<Double?>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(x.size) { null }

        if (n <= 0) return out

        for (i in x.indices) {

            if (i < n - 1) {
                continue
            }

            var sum = 0.0
            var valid = true

            for (j in 0 until n) {

                val value =
                    x[i - n + 1 + j]

                if (value == null) {
                    valid = false
                    break
                }

                sum += value
            }

            if (valid) {
                out[i] =
                    sum / n
            }
        }

        return out
    }

    /*
     * MACD
     *
     * MACD       = Fast EMA - Slow EMA
     * Signal     = EMA(MACD, Signal Length)
     * Histogram  = MACD - Signal
     */
    fun macd(
        x: List<Double>,
        fast: Int,
        slow: Int,
        signal: Int
    ): Triple<
            List<Double?>,
            List<Double?>,
            List<Double?>
            > {

        val line =
            MutableList<Double?>(x.size) { null }

        val fastEma =
            ema(x, fast)

        val slowEma =
            ema(x, slow)

        for (i in x.indices) {

            val f = fastEma[i]
            val s = slowEma[i]

            if (f != null && s != null) {
                line[i] =
                    f - s
            }
        }

        val signalLine =
            emaNullable(
                line,
                signal
            )

        val histogram =
            MutableList<Double?>(
                x.size
            ) { null }

        for (i in x.indices) {

            val m = line[i]
            val s = signalLine[i]

            if (m != null && s != null) {
                histogram[i] =
                    m - s
            }
        }

        return Triple(
            line,
            signalLine,
            histogram
        )
    }

    /*
     * Reverse RSI
     *
     * p[0] = RSI Length
     * p[1] = Smoothing Length
     *
     * No target level.
     * Returns the price corresponding to
     * the current smoothed RSI value.
     */
    "Reverse RSI" -> {

        val rsiLength =
            intParam(0)
                ?: return null

        val smoothingLength =
            intParam(1)
                ?: return null

        val r =
            rsi(
                x,
                rsiLength
            )

        val currentRsi =
            smaNullable(
                r,
                smoothingLength
            ).lastOrNull { it != null }
                ?: return null

        reverseRsiRawPrice(
            x,
            currentRsi,
            rsiLength
        )
    }

    /*
     * Reverse RSI Level
     *
     * p[0] = RSI Length
     * p[1] = Smoothing Length
     * p[2] = Target Level
     */
    "Reverse RSI Level" -> {

        val rsiLength =
            intParam(0)
                ?: return null

        val smoothingLength =
            intParam(1)
                ?: return null

        val level =
            levelParam(2)
                ?: return null

        reverseRsiPrice(
            x,
            level,
            rsiLength,
            smoothingLength
        )
    }
        /*
     * Reverse SMA of RSI.
     *
     * Finds the price required for the current
     * SMA of RSI to equal the supplied target.
     *
     * p[0] = RSI Length
     * p[1] = SMA Length
     *
     * The target is the current SMA of RSI
     * value supplied by the caller.
     */
    fun reverseSmaRsiPrice(
        series: List<Double>,
        target: Double,
        rsiLength: Int,
        smaLength: Int
    ): Double? {

        if (
            rsiLength <= 0 ||
            smaLength <= 0 ||
            target !in 0.0..100.0
        ) {
            return null
        }

        if (
            series.size <
            rsiLength +
            smaLength +
            2
        ) {
            return null
        }

        val r =
            rsi(
                series,
                rsiLength
            )

        val previous =
            r.dropLast(1)
                .takeLast(
                    smaLength - 1
                )
                .filterNotNull()

        if (
            previous.size !=
            smaLength - 1
        ) {
            return null
        }

        val requiredCurrentRsi =
            target * smaLength -
                    previous.sum()

        if (
            requiredCurrentRsi !in 0.0..100.0
        ) {
            return null
        }

        return reverseRsiRawPrice(
            series,
            requiredCurrentRsi,
            rsiLength
        )
    }

    /*
     * Price required to make the current RSI
     * equal to a specified level.
     */
    private fun reverseRsiRawPrice(
        series: List<Double>,
        level: Double,
        rsiLength: Int
    ): Double? {

        if (
            rsiLength <= 0 ||
            series.size < rsiLength + 1 ||
            level !in 0.0..100.0
        ) {
            return null
        }

        val closes =
            series.takeLast(
                rsiLength + 1
            )

        var gainSum = 0.0
        var lossSum = 0.0

        for (i in 1..rsiLength) {

            val change =
                closes[i] -
                        closes[i - 1]

            if (change >= 0.0) {
                gainSum += change
            } else {
                lossSum -= change
            }
        }

        /*
         * Wilder RMA update:
         *
         * newAvg =
         * (oldAvg * (n - 1) + current) / n
         */
        val oldGain =
            gainSum / rsiLength

        val oldLoss =
            lossSum / rsiLength

        when {
            level <= 0.0 ->
                return closes.last() -
                        max(
                            1.0,
                            abs(closes.last()) * 0.5
                        )

            level >= 100.0 ->
                return closes.last() +
                        max(
                            1.0,
                            abs(closes.last()) * 0.5
                        )
        }

        val rs =
            level /
                    (100.0 - level)

        val k =
            rsiLength - 1.0

        val requiredUpMove =
            k *
                    (
                        rs * oldLoss -
                                oldGain
                        )

        val requiredDownMove =
            k *
                    (
                        oldGain / rs -
                                oldLoss
                        )

        val delta =
            when {
                requiredUpMove >= 0.0 ->
                    requiredUpMove

                requiredDownMove > 0.0 ->
                    -requiredDownMove

                else ->
                    0.0
            }

        return closes.last() + delta
    }

    /*
     * Reverse Stoch RSI raw level.
     */
    private fun reverseStochRawPrice(
        series: List<Double>,
        level: Double,
        rsiLength: Int,
        stochLength: Int
    ): Double? {

        if (
            level !in 0.0..100.0 ||
            rsiLength <= 0 ||
            stochLength <= 0
        ) {
            return null
        }

        val r =
            rsi(
                series,
                rsiLength
            )

        val previous =
            r.dropLast(1)
                .takeLast(
                    stochLength - 1
                )
                .filterNotNull()

        if (
            previous.size !=
            stochLength - 1
        ) {
            return null
        }

        val lowest =
            previous.minOrNull()
                ?: return null

        val highest =
            previous.maxOrNull()
                ?: return null

        val requiredRsi =
            lowest +
                    (
                        highest - lowest
                        ) *
                    level / 100.0

        return reverseRsiRawPrice(
            series,
            requiredRsi,
            rsiLength
        )
    }

    /*
     * Reverse Stoch RSI %K.
     *
     * p[0] = RSI Length
     * p[1] = Stoch Length
     * p[2] = K Length
     * p[3] = Level
     */
    private fun reverseStochKPrice(
        series: List<Double>,
        level: Double,
        rsiLength: Int,
        stochLength: Int,
        kLength: Int
    ): Double? {

        if (
            level !in 0.0..100.0 ||
            rsiLength <= 0 ||
            stochLength <= 0 ||
            kLength <= 0
        ) {
            return null
        }

        val r =
            rsi(
                series,
                rsiLength
            )

        val raw =
            stochRawFromRsi(
                r,
                stochLength
            )

        val previous =
            raw.dropLast(1)
                .takeLast(
                    kLength - 1
                )
                .filterNotNull()

        if (
            previous.size !=
            kLength - 1
        ) {
            return null
        }

        val requiredRaw =
            level * kLength -
                    previous.sum()

        if (
            requiredRaw !in 0.0..100.0
        ) {
            return null
        }

        return reverseStochRawPrice(
            series,
            requiredRaw,
            rsiLength,
            stochLength
        )
    }

    /*
     * Reverse Stoch RSI %D.
     *
     * p[0] = RSI Length
     * p[1] = Stoch Length
     * p[2] = K Length
     * p[3] = D Length
     * p[4] = Level
     */
    private fun reverseStochDPrice(
        series: List<Double>,
        level: Double,
        rsiLength: Int,
        stochLength: Int,
        kLength: Int,
        dLength: Int
    ): Double? {

        if (
            level !in 0.0..100.0 ||
            rsiLength <= 0 ||
            stochLength <= 0 ||
            kLength <= 0 ||
            dLength <= 0
        ) {
            return null
        }

        val r =
            rsi(
                series,
                rsiLength
            )

        val raw =
            stochRawFromRsi(
                r,
                stochLength
            )

        val k =
            stochK(
                raw,
                kLength
            )

        val previous =
            k.dropLast(1)
                .takeLast(
                    dLength - 1
                )
                .filterNotNull()

        if (
            previous.size !=
            dLength - 1
        ) {
            return null
        }

        val requiredK =
            level * dLength -
                    previous.sum()

        if (
            requiredK !in 0.0..100.0
        ) {
            return null
        }

        return reverseStochKPrice(
            series,
            requiredK,
            rsiLength,
            stochLength,
            kLength
        )
    }

    /*
     * Main indicator dispatcher.
     *
     * IMPORTANT:
     * There are NO hidden indicator lengths here.
     *
     * The UI/configuration must supply the required
     * parameters.
     */
    fun valueAt(
        x: List<Double>,
        name: String,
        p: List<Double>
    ): Double? {

        fun intParam(index: Int): Int? {

            val value =
                p.getOrNull(index)
                    ?: return null

            if (
                value.isNaN() ||
                value.isInfinite()
            ) {
                return null
            }

            val n =
                value.toInt()

            return n.takeIf {
                it > 0
            }
        }

        fun levelParam(index: Int): Double? {

            val value =
                p.getOrNull(index)
                    ?: return null

            return value.takeIf {
                it.isFinite() &&
                        it in 0.0..100.0
            }
        }

        return when (name) {

            "Close" ->
                x.lastOrNull()

            "EMA" -> {

                val n =
                    intParam(0)
                        ?: return null

                ema(
                    x,
                    n
                ).lastOrNull { it != null }
            }

            "HMA" -> {

                val n =
                    intParam(0)
                        ?: return null

                hma(
                    x,
                    n
                ).lastOrNull { it != null }
            }

            "RSI" -> {

                val n =
                    intParam(0)
                        ?: return null

                rsi(
                    x,
                    n
                ).lastOrNull { it != null }
            }

                        "EMA of RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                val r =
                    rsi(
                        x,
                        rsiLength
                    )

                emaNullable(
                    r,
                    smoothingLength
                ).lastOrNull { it != null }
            }

            "SMA of RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                val r =
                    rsi(
                        x,
                        rsiLength
                    )

                smaNullable(
                    r,
                    smoothingLength
                ).lastOrNull { it != null }
            }
             /*
             * Reverse SMA of RSI
             *
             * p[0] = RSI Length
             * p[1] = SMA Length
             * p[2] = Target SMA level
             */
            "Reverse SMA of RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smaLength =
                    intParam(1)
                        ?: return null

                val target =
                    levelParam(2)
                        ?: return null

                reverseSmaRsiPrice(
                    x,
                    target,
                    rsiLength,
                    smaLength
                )
            }

            
            "MACD" -> {
                val fast =
                    intParam(0)
                        ?: return null

                val slow =
                    intParam(1)
                        ?: return null

                val signal =
                    intParam(2)
                        ?: return null

                macd(
                    x,
                    fast,
                    slow,
                    signal
                ).first.lastOrNull { it != null }
            }

            "MACD Signal" -> {

                val fast =
                    intParam(0)
                        ?: return null

                val slow =
                    intParam(1)
                        ?: return null

                val signal =
                    intParam(2)
                        ?: return null

                macd(
                    x,
                    fast,
                    slow,
                    signal
                ).second.lastOrNull { it != null }
            }

            "MACD Histogram" -> {

                val fast =
                    intParam(0)
                        ?: return null

                val slow =
                    intParam(1)
                        ?: return null

                val signal =
                    intParam(2)
                        ?: return null

                macd(
                    x,
                    fast,
                    slow,
                    signal
                ).third.lastOrNull { it != null }
            }

            /*
             * Stoch RSI raw value
             *
             * p[0] = RSI Length
             * p[1] = Stoch Length
             */
            "Stoch RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val stochLength =
                    intParam(1)
                        ?: return null

                val r =
                    rsi(
                        x,
                        rsiLength
                    )

                stochRawFromRsi(
                    r,
                    stochLength
                ).lastOrNull { it != null }
            }

            /*
             * Stoch RSI %K
             *
             * p[0] = RSI Length
             * p[1] = Stoch Length
             * p[2] = K Length
             */
            "Stoch RSI %K" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val stochLength =
                    intParam(1)
                        ?: return null

                val kLength =
                    intParam(2)
                        ?: return null

                val r =
                    rsi(
                        x,
                        rsiLength
                    )

                val raw =
                    stochRawFromRsi(
                        r,
                        stochLength
                    )

                stochK(
                    raw,
                    kLength
                ).lastOrNull { it != null }
            }

            /*
             * Stoch RSI %D
             *
             * p[0] = RSI Length
             * p[1] = Stoch Length
             * p[2] = K Length
             * p[3] = D Length
             */
            "Stoch RSI %D" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val stochLength =
                    intParam(1)
                        ?: return null

                val kLength =
                    intParam(2)
                        ?: return null

                val dLength =
                    intParam(3)
                        ?: return null

                val r =
                    rsi(
                        x,
                        rsiLength
                    )

                val raw =
                    stochRawFromRsi(
                        r,
                        stochLength
                    )

                val k =
                    stochK(
                        raw,
                        kLength
                    )

                stochD(
                    k,
                    dLength
                ).lastOrNull { it != null }
            }

            "Numeric Value" ->
                p.firstOrNull()

            /*
             * Reverse RSI
             *
             * p[0] = RSI Length
             * p[1] = Smoothing Length
             * p[2] = Level
             *
             * Price output.
             */
            "Reverse RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                val level =
                    levelParam(2)
                        ?: return null

                reverseRsiPrice(
                    x,
                    level,
                    rsiLength,
                    smoothingLength
                )
            }

            /*
             * Fixed Reverse RSI levels.
             *
             * p[0] = RSI Length
             * p[1] = Smoothing Length
             */
            "Reverse RSI Level 40" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                reverseRsiPrice(
                    x,
                    40.0,
                    rsiLength,
                    smoothingLength
                )
            }

            "Reverse RSI Level 50" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                reverseRsiPrice(
                    x,
                    50.0,
                    rsiLength,
                    smoothingLength
                )
            }

            "Reverse RSI Level 60" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                reverseRsiPrice(
                    x,
                    60.0,
                    rsiLength,
                    smoothingLength
                )
            }

            /*
             * Reverse Stoch RSI fixed levels.
             *
             * p[0] = RSI Length
             * p[1] = Stoch Length
             */
            "Reverse Stoch RSI Level 20" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val stochLength =
                    intParam(1)
                        ?: return null

                reverseStochRawPrice(
                    x,
                    20.0,
                    rsiLength,
                    stochLength
                )
            }

            "Reverse Stoch RSI Level 50" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val stochLength =
                    intParam(1)
                        ?: return null

                reverseStochRawPrice(
                    x,
                    50.0,
                    rsiLength,
                    stochLength
                )
            }

            "Reverse Stoch RSI Level 80" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val stochLength =
                    intParam(1)
                        ?: return null

                reverseStochRawPrice(
                    x,
                    80.0,
                    rsiLength,
                    stochLength
                )
            }

            /*
             * Reverse Stoch RSI %K
             *
             * p[0] = RSI Length
             * p[1] = Stoch Length
             * p[2] = K Length
             * p[3] = Level
             */
            "Reverse Stoch RSI %K" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val stochLength =
                    intParam(1)
                        ?: return null

                val kLength =
                    intParam(2)
                        ?: return null

                val level =
                    levelParam(3)
                        ?: return null

                reverseStochKPrice(
                    x,
                    level,
                    rsiLength,
                    stochLength,
                    kLength
                )
            }

            /*
             * Reverse Stoch RSI %D
             *
             * p[0] = RSI Length
             * p[1] = Stoch Length
             * p[2] = K Length
             * p[3] = D Length
             * p[4] = Level
             */
            "Reverse Stoch RSI %D" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val stochLength =
                    intParam(1)
                        ?: return null

                val kLength =
                    intParam(2)
                        ?: return null

                val dLength =
                    intParam(3)
                        ?: return null

                val level =
                    levelParam(4)
                        ?: return null

                reverseStochDPrice(
                    x,
                    level,
                    rsiLength,
                    stochLength,
                    kLength,
                    dLength
                )
            }

            else ->
                null
        }
    }
}
