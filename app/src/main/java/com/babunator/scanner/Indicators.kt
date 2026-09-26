package com.babunator.scanner

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

        val out =
            MutableList<Double?>(x.size) { null }

        if (n <= 0) {
            return out
        }

        var sum = 0.0

        for (i in x.indices) {

            sum += x[i]

            if (i >= n) {
                sum -= x[i - n]
            }

            if (i >= n - 1) {
                out[i] =
                    sum / n.toDouble()
            }
        }

        return out
    }

    /*
     * EMA
     *
     * Seed = SMA of first n values.
     */
    fun ema(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(x.size) { null }

        if (
            n <= 0 ||
            x.size < n
        ) {
            return out
        }

        var previous =
            x.take(n).average()

        out[n - 1] =
            previous

        val alpha =
            2.0 / (n + 1.0)

        for (i in n until x.size) {

            previous =
                alpha * x[i] +
                        (1.0 - alpha) *
                        previous

            out[i] =
                previous
        }

        return out
    }

    /*
     * EMA for nullable series.
     *
     * Warm-up nulls are preserved.
     */
    private fun emaNullable(
        x: List<Double?>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(x.size) { null }

        if (n <= 0) {
            return out
        }

        val start =
            x.indexOfFirst {
                it != null
            }

        if (start < 0) {
            return out
        }

        val validCount =
            x.size - start

        if (validCount < n) {
            return out
        }

        var sum = 0.0

        for (i in start until start + n) {

            val value =
                x[i]
                    ?: return out

            sum += value
        }

        var previous =
            sum / n.toDouble()

        out[start + n - 1] =
            previous

        val alpha =
            2.0 / (n + 1.0)

        for (i in start + n until x.size) {

            val value =
                x[i]
                    ?: continue

            previous =
                alpha * value +
                        (1.0 - alpha) *
                        previous

            out[i] =
                previous
        }

        return out
    }

    /*
     * Wilder RMA / SMMA.
     */
    fun rma(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(x.size) { null }

        if (
            n <= 0 ||
            x.size < n
        ) {
            return out
        }

        var previous =
            x.take(n).average()

        out[n - 1] =
            previous

        val alpha =
            1.0 / n.toDouble()

        for (i in n until x.size) {

            previous =
                alpha * x[i] +
                        (1.0 - alpha) *
                        previous

            out[i] =
                previous
        }

        return out
    }

    /*
     * Wilder RSI.
     *
     * First RSI uses the first n actual
     * price changes.
     */
    fun rsi(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(x.size) { null }

        if (
            n <= 0 ||
            x.size <= n
        ) {
            return out
        }

        var gainSum = 0.0
        var lossSum = 0.0

        for (i in 1..n) {

            val change =
                x[i] - x[i - 1]

            if (change >= 0.0) {
                gainSum += change
            } else {
                lossSum -= change
            }
        }

        var averageGain =
            gainSum / n.toDouble()

        var averageLoss =
            lossSum / n.toDouble()

        out[n] =
            calculateRsi(
                averageGain,
                averageLoss
            )

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

            averageGain =
                (
                    averageGain * (n - 1) +
                            gain
                    ) / n.toDouble()

            averageLoss =
                (
                    averageLoss * (n - 1) +
                            loss
                    ) / n.toDouble()

            out[i] =
                calculateRsi(
                    averageGain,
                    averageLoss
                )
        }

        return out
    }

    private fun calculateRsi(
        averageGain: Double,
        averageLoss: Double
    ): Double {

        return when {

            averageLoss == 0.0 &&
                    averageGain == 0.0 ->
                0.0

            averageLoss == 0.0 ->
                100.0

            else -> {

                val rs =
                    averageGain /
                            averageLoss

                100.0 -
                        100.0 /
                        (1.0 + rs)
            }
        }
    }

    /*
     * Weighted Moving Average.
     */
    fun wma(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(x.size) { null }

        if (n <= 0) {
            return out
        }

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
     * Hull Moving Average.
     */
    fun hma(
        x: List<Double>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(x.size) { null }

        if (
            n <= 0 ||
            x.isEmpty()
        ) {
            return out
        }

        if (n == 1) {
            return x.map {
                it
            }
        }

        val half =
            max(
                1,
                n / 2
            )

        val root =
            max(
                1,
                sqrt(n.toDouble())
                    .roundToInt()
            )

        val halfWma =
            wma(
                x,
                half
            )

        val fullWma =
            wma(
                x,
                n
            )

        val difference =
            MutableList<Double?>(x.size) {
                null
            }

        for (i in x.indices) {

            val a =
                halfWma[i]

            val b =
                fullWma[i]

            if (
                a != null &&
                b != null
            ) {
                difference[i] =
                    2.0 * a - b
            }
        }

        for (i in x.indices) {

            if (
                i <
                n + root - 2
            ) {
                continue
            }

            var sum = 0.0
            var valid = true

            for (j in 0 until root) {

                val value =
                    difference[
                        i - root + 1 + j
                    ]

                if (value == null) {
                    valid = false
                    break
                }

                sum +=
                    value * (j + 1)
            }

            if (valid) {

                val denominator =
                    root *
                            (root + 1) /
                            2.0

                out[i] =
                    sum / denominator
            }
        }

        return out
    }

    /*
     * Raw Stoch RSI.
     */
    fun stochRawFromRsi(
        rsiValues: List<Double?>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(
                rsiValues.size
            ) {
                null
            }

        if (n <= 0) {
            return out
        }

        for (i in rsiValues.indices) {

            if (i < n - 1) {
                continue
            }

            val window =
                rsiValues.subList(
                    i - n + 1,
                    i + 1
                )

            if (
                window.any {
                    it == null
                }
            ) {
                continue
            }

            val values =
                window.map {
                    it!!
                }

            val current =
                values.last()

            val lowest =
                values.minOrNull()
                    ?: continue

            val highest =
                values.maxOrNull()
                    ?: continue

            out[i] =
                if (
                    highest == lowest
                ) {
                    0.0
                } else {
                    100.0 *
                            (
                                current -
                                        lowest
                                ) /
                            (
                                highest -
                                        lowest
                                )
                }
        }

        return out
    }

    /*
     * SMA smoothing of nullable values.
     */
    private fun smaNullable(
        x: List<Double?>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(
                x.size
            ) {
                null
            }

        if (n <= 0) {
            return out
        }

        for (i in x.indices) {

            if (i < n - 1) {
                continue
            }

            var sum = 0.0
            var valid = true

            for (j in 0 until n) {

                val value =
                    x[
                        i - n + 1 + j
                    ]

                if (value == null) {
                    valid = false
                    break
                }

                sum += value
            }

            if (valid) {
                out[i] =
                    sum / n.toDouble()
            }
        }

        return out
    }

    /*
     * Stoch RSI %K.
     */
    fun stochK(
        raw: List<Double?>,
        k: Int
    ): List<Double?> {

        return smaNullable(
            raw,
            k
        )
    }

    /*
     * Stoch RSI %D.
     */
    fun stochD(
        k: List<Double?>,
        d: Int
    ): List<Double?> {

        return smaNullable(
            k,
            d
        )
    }

    /*
     * MACD.
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
            MutableList<Double?>(
                x.size
            ) {
                null
            }

        val fastEma =
            ema(
                x,
                fast
            )

        val slowEma =
            ema(
                x,
                slow
            )

        for (i in x.indices) {

            val f =
                fastEma[i]

            val s =
                slowEma[i]

            if (
                f != null &&
                s != null
            ) {
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
            ) {
                null
            }

        for (i in x.indices) {

            val m =
                line[i]

            val s =
                signalLine[i]

            if (
                m != null &&
                s != null
            ) {
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
     * =========================================================
     * Reverse RSI helpers
     * =========================================================
     */

    /*
     * Returns Wilder average gain/loss immediately
     * BEFORE the final candle.
     *
     * This is the correct state needed to solve
     * the final price mathematically.
     */
    private fun rsiStateBeforeLast(
        series: List<Double>,
        n: Int
    ): Pair<Double, Double>? {

        if (
            n <= 0 ||
            series.size <= n + 0
        ) {
            return null
        }

        val lastIndex =
            series.lastIndex

        val previousIndex =
            lastIndex - 1

        if (previousIndex < n) {
            return null
        }

        var gainSum = 0.0
        var lossSum = 0.0

        for (i in 1..n) {

            val change =
                series[i] -
                        series[i - 1]

            if (change >= 0.0) {
                gainSum += change
            } else {
                lossSum -= change
            }
        }

        var averageGain =
            gainSum / n.toDouble()

        var averageLoss =
            lossSum / n.toDouble()

        for (
            i in n + 1..previousIndex
        ) {

            val change =
                series[i] -
                        series[i - 1]

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

            averageGain =
                (
                    averageGain * (n - 1) +
                            gain
                    ) / n.toDouble()

            averageLoss =
                (
                    averageLoss * (n - 1) +
                            loss
                    ) / n.toDouble()
        }

        return Pair(
            averageGain,
            averageLoss
        )
    }

    /*
     * Exact inverse of the final Wilder RSI update.
     *
     * Returns the price required for the final RSI
     * to equal the supplied target.
     *
     * 0 and 100 are asymptotic RSI boundaries,
     * so there is no finite exact price for those
     * levels in the normal case.
     */
    private fun reverseRsiRawPrice(
        series: List<Double>,
        target: Double,
        rsiLength: Int
    ): Double? {

        if (
            target <= 0.0 ||
            target >= 100.0 ||
            rsiLength <= 0 ||
            series.size <= rsiLength
        ) {
            return null
        }

        val state =
            rsiStateBeforeLast(
                series,
                rsiLength
            )
                ?: return null

        val averageGain =
            state.first

        val averageLoss =
            state.second

        val rs =
            target /
                    (100.0 - target)

        val factor =
            rsiLength - 1.0

        /*
         * Positive final price change solution.
         */
        val upwardDelta =
            factor *
                    (
                        rs * averageLoss -
                                averageGain
                        )

        /*
         * Negative final price change solution.
         */
        val downwardDelta =
            factor *
                    (
                        averageLoss -
                                averageGain / rs
                        )

        val delta =
            when {

                upwardDelta >= 0.0 ->
                    upwardDelta

                downwardDelta <= 0.0 ->
                    downwardDelta

                else ->
                    0.0
            }

        return series.last() +
                delta
    }

    /*
     * Reverse RSI with optional SMA smoothing.
     *
     * This version targets the CURRENT value of
     * the smoothed RSI, not a user supplied level.
     *
     * p[0] = RSI Length
     * p[1] = Smoothing Length
     */
    private fun reverseCurrentRsiPrice(
        series: List<Double>,
        rsiLength: Int,
        smoothingLength: Int
    ): Double? {
    
        if (
            rsiLength <= 0 ||
            smoothingLength <= 0
        ) {
            return null
        }
    
        val r =
            rsi(
                series,
                rsiLength
            )
    
        val currentSmoothed =
            smaNullable(
                r,
                smoothingLength
            ).lastOrNull {
                it != null
            }
                ?: return null
    
        if (smoothingLength == 1) {
            return reverseRsiRawPrice(
                series,
                currentSmoothed,
                rsiLength
            )
        }
    
        val previous =
            r.dropLast(1)
                .takeLast(
                    smoothingLength - 1
                )
                .filterNotNull()
    
        if (
            previous.size !=
            smoothingLength - 1
        ) {
            return null
        }
    
        /*
         * Current SMA target:
         *
         * (previous RSI values + current RSI) / smoothing
         * = current smoothed RSI
         *
         * Therefore:
         *
         * current RSI =
         * current smoothed RSI * smoothing
         * - previous RSI sum
         */
        val requiredCurrentRsi =
            currentSmoothed *
                    smoothingLength -
                    previous.sum()
    
        if (
            requiredCurrentRsi <= 0.0 ||
            requiredCurrentRsi >= 100.0
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
     * Reverse SMA of RSI.
     *
     * Finds the price corresponding to the
     * CURRENT SMA of RSI.
     *
     * p[0] = RSI Length
     * p[1] = SMA Length
     */
    private fun reverseCurrentSmaRsiPrice(
        series: List<Double>,
        rsiLength: Int,
        smaLength: Int
    ): Double? {

        if (
            rsiLength <= 0 ||
            smaLength <= 0
        ) {
            return null
        }

        val r =
            rsi(
                series,
                rsiLength
            )

        val currentSma =
            smaNullable(
                r,
                smaLength
            ).lastOrNull {
                it != null
            }
                ?: return null

        if (smaLength == 1) {
            return reverseRsiRawPrice(
                series,
                currentSma,
                rsiLength
            )
        }

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
            currentSma *
                    smaLength -
                    previous.sum()

        if (
            requiredCurrentRsi <= 0.0 ||
            requiredCurrentRsi >= 100.0
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
     * Reverse RSI level.
     *
     * p[0] = RSI Length
     * p[1] = Smoothing Length
     * p[2] = Level
     */
    private fun reverseRsiLevelPrice(
        series: List<Double>,
        level: Double,
        rsiLength: Int,
        smoothingLength: Int
    ): Double? {

        if (
            level <= 0.0 ||
            level >= 100.0 ||
            rsiLength <= 0 ||
            smoothingLength <= 0
        ) {
            return null
        }

        val r =
            rsi(
                series,
                rsiLength
            )

        if (smoothingLength == 1) {

            return reverseRsiRawPrice(
                series,
                level,
                rsiLength
            )
        }

        val previous =
            r.dropLast(1)
                .takeLast(
                    smoothingLength - 1
                )
                .filterNotNull()

        if (
            previous.size !=
            smoothingLength - 1
        ) {
            return null
        }

        val requiredCurrentRsi =
            level *
                    smoothingLength -
                    previous.sum()

        if (
            requiredCurrentRsi <= 0.0 ||
            requiredCurrentRsi >= 100.0
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
     * =========================================================
     * Reverse Stoch RSI
     * =========================================================
     */

    /*
     * Reverse raw Stoch RSI level.
     *
     * The previous RSI window is held fixed while
     * solving the final RSI required for the target.
     */
    private fun reverseStochRawPrice(
        series: List<Double>,
        level: Double,
        rsiLength: Int,
        stochLength: Int
    ): Double? {
    
        if (
            level < 0.0 ||
            level > 100.0 ||
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

        if (
            highest == lowest
        ) {
            if (level != 0.0) {
                return null
            }

            return reverseRsiRawPrice(
                series,
                lowest,
                rsiLength
            )
        }

        val requiredRsi =
            lowest +
                    (
                        highest - lowest
                        ) *
                    level /
                    100.0

        return reverseRsiRawPrice(
            series,
            requiredRsi,
            rsiLength
        )
    }

    /*
     * Reverse current Stoch RSI %K.
     *
     * p[0] = RSI Length
     * p[1] = Stoch Length
     * p[2] = K Length
     */
    private fun reverseCurrentStochKPrice(
        series: List<Double>,
        rsiLength: Int,
        stochLength: Int,
        kLength: Int
    ): Double? {

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

        val currentK =
            stochK(
                raw,
                kLength
            ).lastOrNull {
                it != null
            }
                ?: return null

        if (kLength == 1) {

            return reverseStochRawPrice(
                series,
                currentK,
                rsiLength,
                stochLength
            )
        }

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
            currentK *
                    kLength -
                    previous.sum()

        if (
            requiredRaw < 0.0 ||
            requiredRaw > 100.0
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
     * Reverse current Stoch RSI %D.
     *
     * p[0] = RSI Length
     * p[1] = Stoch Length
     * p[2] = K Length
     * p[3] = D Length
     */
    private fun reverseCurrentStochDPrice(
        series: List<Double>,
        rsiLength: Int,
        stochLength: Int,
        kLength: Int,
        dLength: Int
    ): Double? {

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

        val currentD =
            stochD(
                k,
                dLength
            ).lastOrNull {
                it != null
            }
                ?: return null

        if (dLength == 1) {
            return reverseCurrentStochKPrice(
                series,
                rsiLength,
                stochLength,
                kLength
            )
        }

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
            currentD *
                    dLength -
                    previous.sum()

        if (
            requiredK < 0.0 ||
            requiredK > 100.0
        ) {
            return null
        }

        val rawPrevious =
            raw.dropLast(1)
                .takeLast(
                    kLength - 1
                )
                .filterNotNull()

        if (
            kLength > 1 &&
            rawPrevious.size !=
            kLength - 1
        ) {
            return null
        }

        val requiredRaw =
            if (kLength == 1) {
                requiredK
            } else {
                requiredK *
                        kLength -
                        rawPrevious.sum()
            }

        if (
            requiredRaw <= 0.0 ||
            requiredRaw >= 100.0
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
     * Reverse current smoothed Stoch RSI level.
     *
     * p[0] = RSI Length
     * p[1] = Stoch Length
     * p[2] = Level
     * p[3] = Smooth Length
     */
    private fun reverseStochLevelPrice(
        series: List<Double>,
        level: Double,
        rsiLength: Int,
        stochLength: Int,
        smoothingLength: Int
    ): Double? {

        if (
            level < 0.0 ||
            level > 100.0 ||
            rsiLength <= 0 ||
            stochLength <= 0 ||
            smoothingLength <= 0
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

        if (smoothingLength == 1) {

            return reverseStochRawPrice(
                series,
                level,
                rsiLength,
                stochLength
            )
        }

        val previous =
            raw.dropLast(1)
                .takeLast(
                    smoothingLength - 1
                )
                .filterNotNull()

        if (
            previous.size !=
            smoothingLength - 1
        ) {
            return null
        }

        val requiredRaw =
            level *
                    smoothingLength -
                    previous.sum()

        if (
            requiredRaw < 0.0 ||
            requiredRaw > 100.0
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
     * =========================================================
     * Reverse MACD
     * =========================================================
     */

    /*
     * Calculate EMA value immediately BEFORE
     * the final candle.
     */
    private fun emaBeforeLast(
        series: List<Double>,
        length: Int
    ): Double? {

        if (
            length <= 0 ||
            series.size <= length
        ) {
            return null
        }

        return ema(
            series.dropLast(1),
            length
        ).lastOrNull {
            it != null
        }
    }

    /*
     * Solve the final price required to make
     * current MACD line equal target.
     */
    private fun reverseMacdLinePrice(
        series: List<Double>,
        target: Double,
        fast: Int,
        slow: Int
    ): Double? {

        if (
            fast <= 0 ||
            slow <= 0 ||
            fast == slow
        ) {
            return null
        }

        val fastPrevious =
            emaBeforeLast(
                series,
                fast
            )
                ?: return null

        val slowPrevious =
            emaBeforeLast(
                series,
                slow
            )
                ?: return null

        val fastAlpha =
            2.0 /
                    (fast + 1.0)

        val slowAlpha =
            2.0 /
                    (slow + 1.0)

        val coefficient =
            fastAlpha -
                    slowAlpha

        if (
            coefficient == 0.0
        ) {
            return null
        }

        val constant =
            (
                (1.0 - fastAlpha) *
                        fastPrevious
                ) -
                (
                    (1.0 - slowAlpha) *
                            slowPrevious
                    )

        return (
            target - constant
            ) /
            coefficient
    }

    /*
     * Return previous values needed to reverse
     * a smoothed series.
     */
    private fun previousValues(
        values: List<Double?>,
        count: Int
    ): List<Double>? {

        if (count <= 0) {
            return emptyList()
        }

        val result =
            values.dropLast(1)
                .takeLast(count)
                .filterNotNull()

        return if (
            result.size == count
        ) {
            result
        } else {
            null
        }
    }

    /*
     * Reverse current MACD line.
     *
     * p[0] = Fast
     * p[1] = Slow
     * p[2] = Signal
     * p[3] = Smooth
     */
    private fun reverseCurrentMacdPrice(
        series: List<Double>,
        fast: Int,
        slow: Int,
        signal: Int,
        smooth: Int
    ): Double? {

        val macdValues =
            macd(
                series,
                fast,
                slow,
                signal
            ).first

        val current =
            macdValues.lastOrNull {
                it != null
            }
                ?: return null

        if (smooth == 1) {

            return reverseMacdLinePrice(
                series,
                current,
                fast,
                slow
            )
        }

        val previous =
            previousValues(
                macdValues,
                smooth - 1
            )
                ?: return null

        val requiredCurrent =
            current * smooth -
                    previous.sum()

        return reverseMacdLinePrice(
            series,
            requiredCurrent,
            fast,
            slow
        )
    }

    /*
     * Reverse current MACD signal.
     */
    private fun reverseCurrentMacdSignalPrice(
        series: List<Double>,
        fast: Int,
        slow: Int,
        signal: Int,
        smooth: Int
    ): Double? {

        val values =
            macd(
                series,
                fast,
                slow,
                signal
            ).second

        val current =
            values.lastOrNull {
                it != null
            }
                ?: return null

        val requiredSignal =
            if (smooth == 1) {

                current

            } else {

                val previous =
                    previousValues(
                        values,
                        smooth - 1
                    )
                        ?: return null

                current * smooth -
                        previous.sum()
            }

        val line =
            macd(
                series,
                fast,
                slow,
                signal
            ).first

        val previousSignal =
            values.dropLast(1)
                .lastOrNull {
                    it != null
                }
                ?: return null

        val alpha =
            2.0 /
                    (signal + 1.0)

        val requiredMacd =
            (
                requiredSignal -
                        (
                            1.0 - alpha
                            ) *
                        previousSignal
                ) /
                alpha

        return reverseMacdLinePrice(
            series,
            requiredMacd,
            fast,
            slow
        )
    }

    /*
     * Reverse MACD zero line.
     */
    private fun reverseCurrentMacdZeroPrice(
        series: List<Double>,
        fast: Int,
        slow: Int,
        signal: Int,
        smooth: Int
    ): Double? {

        val values =
            macd(
                series,
                fast,
                slow,
                signal
            ).first

        val requiredCurrent =
            if (smooth == 1) {

                0.0

            } else {

                val previous =
                    previousValues(
                        values,
                        smooth - 1
                    )
                        ?: return null

                -previous.sum() /
                        smooth.toDouble()
            }

        return reverseMacdLinePrice(
            series,
            requiredCurrent,
            fast,
            slow
        )
    }

    /*
     * =========================================================
     * Main dispatcher
     * =========================================================
     */
    fun valueAt(
        x: List<Double>,
        name: String,
        p: List<Double>
    ): Double? {

        fun intParam(
            index: Int
        ): Int? {

            val value =
                p.getOrNull(index)
                    ?: return null

            if (
                value.isNaN() ||
                value.isInfinite()
            ) {
                return null
            }

            val result =
                value.toInt()

            return result.takeIf {
                it > 0
            }
        }

        fun levelParam(
            index: Int
        ): Double? {

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
                ).lastOrNull {
                    it != null
                }
            }

            "HMA" -> {

                val n =
                    intParam(0)
                        ?: return null

                hma(
                    x,
                    n
                ).lastOrNull {
                    it != null
                }
            }

            "RSI" -> {

                val n =
                    intParam(0)
                        ?: return null

                rsi(
                    x,
                    n
                ).lastOrNull {
                    it != null
                }
            }

            "EMA of RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                val values =
                    rsi(
                        x,
                        rsiLength
                    )

                emaNullable(
                    values,
                    smoothingLength
                ).lastOrNull {
                    it != null
                }
            }

            "SMA of RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                val values =
                    rsi(
                        x,
                        rsiLength
                    )

                smaNullable(
                    values,
                    smoothingLength
                ).lastOrNull {
                    it != null
                }
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
                ).first.lastOrNull {
                    it != null
                }
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
                ).second.lastOrNull {
                    it != null
                }
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
                ).third.lastOrNull {
                    it != null
                }
            }

            "Stoch RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val stochLength =
                    intParam(1)
                        ?: return null

                val values =
                    rsi(
                        x,
                        rsiLength
                    )

                stochRawFromRsi(
                    values,
                    stochLength
                ).lastOrNull {
                    it != null
                }
            }

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

                val values =
                    rsi(
                        x,
                        rsiLength
                    )

                val raw =
                    stochRawFromRsi(
                        values,
                        stochLength
                    )

                stochK(
                    raw,
                    kLength
                ).lastOrNull {
                    it != null
                }
            }

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

                val values =
                    rsi(
                        x,
                        rsiLength
                    )

                val raw =
                    stochRawFromRsi(
                        values,
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
                ).lastOrNull {
                    it != null
                }
            }

            "Numeric Value" ->
                p.firstOrNull()

            /*
             * Reverse RSI
             *
             * p[0] = RSI Length
             * p[1] = Smoothing Length
             */
            "Reverse RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                reverseCurrentRsiPrice(
                    x,
                    rsiLength,
                    smoothingLength
                )
            }

            /*
             * Reverse SMA of RSI
             *
             * p[0] = RSI Length
             * p[1] = SMA Length
             */
            "Reverse SMA of RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smaLength =
                    intParam(1)
                        ?: return null

                reverseCurrentSmaRsiPrice(
                    x,
                    rsiLength,
                    smaLength
                )
            }

            /*
             * Reverse RSI Level
             *
             * p[0] = RSI Length
             * p[1] = Smoothing Length
             * p[2] = Level
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

                reverseRsiLevelPrice(
                    x,
                    level,
                    rsiLength,
                    smoothingLength
                )
            }

            /*
             * Reverse Stoch RSI Level
             *
             * p[0] = RSI Length
             * p[1] = Stoch Length
             * p[2] = Level
             * p[3] = Smooth Length
             */
            "Reverse Stoch RSI Level" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val stochLength =
                    intParam(1)
                        ?: return null

                val level =
                    levelParam(2)
                        ?: return null

                val smoothingLength =
                    intParam(3)
                        ?: return null

                reverseStochLevelPrice(
                    x,
                    level,
                    rsiLength,
                    stochLength,
                    smoothingLength
                )
            }

            /*
             * Reverse Stoch RSI %K
             *
             * p[0] = RSI Length
             * p[1] = Stoch Length
             * p[2] = K Length
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

                reverseCurrentStochKPrice(
                    x,
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

                reverseCurrentStochDPrice(
                    x,
                    rsiLength,
                    stochLength,
                    kLength,
                    dLength
                )
            }

            /*
             * Reverse MACD
             *
             * p[0] = Fast
             * p[1] = Slow
             * p[2] = Signal
             * p[3] = Smooth
             */
            "Reverse MACD" -> {

                val fast =
                    intParam(0)
                        ?: return null

                val slow =
                    intParam(1)
                        ?: return null

                val signal =
                    intParam(2)
                        ?: return null

                val smooth =
                    intParam(3)
                        ?: return null

                reverseCurrentMacdPrice(
                    x,
                    fast,
                    slow,
                    signal,
                    smooth
                )
            }

            /*
             * Reverse MACD Signal
             *
             * p[0] = Fast
             * p[1] = Slow
             * p[2] = Signal
             * p[3] = Smooth
             */
            "Reverse MACD Signal" -> {

                val fast =
                    intParam(0)
                        ?: return null

                val slow =
                    intParam(1)
                        ?: return null

                val signal =
                    intParam(2)
                        ?: return null

                val smooth =
                    intParam(3)
                        ?: return null

                reverseCurrentMacdSignalPrice(
                    x,
                    fast,
                    slow,
                    signal,
                    smooth
                )
            }

            /*
             * Reverse MACD Zero Line
             *
             * p[0] = Fast
             * p[1] = Slow
             * p[2] = Signal
             * p[3] = Smooth
             */
            "Reverse MACD Zero Line" -> {

                val fast =
                    intParam(0)
                        ?: return null

                val slow =
                    intParam(1)
                        ?: return null

                val signal =
                    intParam(2)
                        ?: return null

                val smooth =
                    intParam(3)
                        ?: return null

                reverseCurrentMacdZeroPrice(
                    x,
                    fast,
                    slow,
                    signal,
                    smooth
                )
            }

            else ->
                null
        }
    }
}
