package com.babunator.scanner

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object Indicators {

    /*
     * =========================================================
     * Basic Moving Averages
     * =========================================================
     */

    /*
     * Simple Moving Average.
     *
     * Normal rolling SMA.
     * First valid value appears after n candles.
     */
    fun sma(
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
     * Weighted Moving Average.
     *
     * Weight 1 is oldest.
     * Weight n is newest.
     */
    fun wma(
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
     * EMA.
     *
     * IMPORTANT:
     * The caller must provide the complete historical series
     * including required warm-up candles.
     *
     * Seed = SMA of first n contiguous values.
     * After seed, EMA is fully recursive.
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

        var seedSum = 0.0

        for (i in 0 until n) {
            seedSum += x[i]
        }

        var previous =
            seedSum / n.toDouble()

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
     * Used when an indicator itself has warm-up nulls.
     *
     * The EMA starts at the first valid contiguous block,
     * seeds from SMA(n), then continues recursively.
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

        if (
            start + n > x.size
        ) {
            return out
        }

        var seedSum = 0.0

        for (i in start until start + n) {

            val value =
                x[i]
                    ?: return out

            seedSum += value
        }

        var previous =
            seedSum / n.toDouble()

        out[start + n - 1] =
            previous

        val alpha =
            2.0 / (n + 1.0)

        for (
            i in start + n until x.size
        ) {

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
     *
     * Seed = SMA of first n values.
     * Then recursive Wilder update.
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

        var seedSum = 0.0

        for (i in 0 until n) {
            seedSum += x[i]
        }

        var previous =
            seedSum / n.toDouble()

        out[n - 1] =
            previous

        for (i in n until x.size) {

            previous =
                (
                    previous * (n - 1) +
                            x[i]
                    ) / n.toDouble()

            out[i] =
                previous
        }

        return out
    }

    /*
     * =========================================================
     * RSI
     * =========================================================
     */

    /*
     * Wilder RSI.
     *
     * The first RSI is based on the first n actual
     * price changes, therefore it appears at index n.
     *
     * Subsequent values are fully recursive Wilder values.
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

            if (change > 0.0) {

                gainSum += change

            } else if (change < 0.0) {

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

        for (
            i in n + 1 until x.size
        ) {

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

    /*
     * RSI boundary handling.
     *
     * gain = 0, loss = 0 => RSI 50
     * gain > 0, loss = 0 => RSI 100
     */
    private fun calculateRsi(
        averageGain: Double,
        averageLoss: Double
    ): Double {

        return when {

            averageLoss == 0.0 &&
                    averageGain == 0.0 ->
                50.0

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
     * EMA of RSI.
     */
    private fun emaOfRsi(
        series: List<Double>,
        rsiLength: Int,
        emaLength: Int
    ): List<Double?> {

        val rsiValues =
            rsi(
                series,
                rsiLength
            )

        return emaNullable(
            rsiValues,
            emaLength
        )
    }

    /*
     * SMA of RSI.
     */
    private fun smaOfRsi(
        series: List<Double>,
        rsiLength: Int,
        smaLength: Int
    ): List<Double?> {

        val rsiValues =
            rsi(
                series,
                rsiLength
            )

        return smaNullable(
            rsiValues,
            smaLength
        )
    }

    /*
     * =========================================================
     * HMA
     * =========================================================
     */

    /*
     * TradingView-style HMA construction:
     *
     * WMA(2 * WMA(src, round(n / 2)) - WMA(src, n),
     *     round(sqrt(n)))
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
            return x.map { it }
        }

        val half =
            max(
                1,
                (n / 2.0).roundToInt()
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
                i < n + root - 2
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
     * =========================================================
     * Nullable SMA helper
     * =========================================================
     */

    private fun smaNullable(
        x: List<Double?>,
        n: Int
    ): List<Double?> {

        val out =
            MutableList<Double?>(x.size) {
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
     * =========================================================
     * Stoch RSI
     * =========================================================
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

            val range =
                highest - lowest

            out[i] =
                if (range == 0.0) {
                    0.0
                } else {
                    (
                        current - lowest
                        ) /
                            range *
                            100.0
                }
        }

        return out
    }

    fun stochK(
        raw: List<Double?>,
        k: Int
    ): List<Double?> {

        return smaNullable(
            raw,
            k
        )
    }

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
     * =========================================================
     * MACD
     * =========================================================
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

        if (
            fast <= 0 ||
            slow <= 0 ||
            signal <= 0 ||
            fast >= slow
        ) {
            return Triple(
                line,
                MutableList(x.size) { null },
                MutableList(x.size) { null }
            )
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

        /*
         * Signal EMA starts from the first valid MACD
         * value and uses the same SMA-seed + recursive EMA
         * method.
         */
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
     * Wilder state immediately BEFORE final candle.
     */
    private fun rsiStateBeforeLast(
        series: List<Double>,
        n: Int
    ): Pair<Double, Double>? {

        if (
            n <= 0 ||
            series.size <= n
        ) {
            return null
        }

        val previousIndex =
            series.lastIndex - 1

        if (previousIndex < n) {
            return null
        }

        var gainSum = 0.0
        var lossSum = 0.0

        for (i in 1..n) {

            val change =
                series[i] -
                        series[i - 1]

            if (change > 0.0) {
                gainSum += change
            } else if (change < 0.0) {
                lossSum -= change
            }
        }

        var averageGain =
            gainSum / n.toDouble()

        var averageLoss =
            lossSum / n.toDouble()

        if (previousIndex >= n + 1) {

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
                        averageGain *
                                (n - 1) +
                                gain
                        ) / n.toDouble()

                averageLoss =
                    (
                        averageLoss *
                                (n - 1) +
                                loss
                        ) / n.toDouble()
            }
        }

        return Pair(
            averageGain,
            averageLoss
        )
    }

    /*
     * Exact inverse of the final Wilder RSI update.
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

        val targetRs =
            target /
                    (100.0 - target)

        val factor =
            rsiLength - 1.0

        val currentRs =
            if (averageLoss == 0.0) {
                Double.POSITIVE_INFINITY
            } else {
                averageGain /
                        averageLoss
            }

        val delta =
            if (targetRs > currentRs) {

                factor *
                        (
                            targetRs *
                                    averageLoss -
                                    averageGain
                            )

            } else {

                factor *
                        (
                            averageLoss -
                                    averageGain /
                                    targetRs
                            )
            }

        return series.last() +
                delta
    }

    /*
     * =========================================================
     * Reverse current RSI
     * =========================================================
     *
     * Android indicator definition:
     * current RSI smoothed with SMA.
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

        val smoothed =
            smaNullable(
                r,
                smoothingLength
            ).lastOrNull()
                ?: return null

        if (smoothingLength == 1) {

            return reverseRsiRawPrice(
                series,
                smoothed,
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

        val requiredRsi =
            smoothed *
                    smoothingLength -
                    previous.sum()

        if (
            requiredRsi <= 0.0 ||
            requiredRsi >= 100.0
        ) {
            return null
        }

        return reverseRsiRawPrice(
            series,
            requiredRsi,
            rsiLength
        )
    }

    /*
     * =========================================================
     * Reverse SMA of RSI
     * =========================================================
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
            ).lastOrNull()
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

        val requiredRsi =
            currentSma *
                    smaLength -
                    previous.sum()

        if (
            requiredRsi <= 0.0 ||
            requiredRsi >= 100.0
        ) {
            return null
        }

        return reverseRsiRawPrice(
            series,
            requiredRsi,
            rsiLength
        )
    }

    /*
     * =========================================================
     * Reverse RSI Level
     * =========================================================
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
    
        if (series.size <= rsiLength + smoothingLength) {
            return null
        }
    
        /*
         * Build Wilder RSI state for every bar and calculate
         * the theoretical price required to make that bar's
         * RSI equal to the requested level.
         *
         * This follows the Colab reference:
         *
         *   1. Calculate Wilder RMA average gain/loss.
         *   2. Use the state BEFORE each candle.
         *   3. Reverse-engineer the required closing price.
         *   4. EMA-smooth the resulting reverse-price series.
         */
    
        val delta = MutableList<Double>(series.size) { 0.0 }
        val gains = MutableList<Double>(series.size) { 0.0 }
        val losses = MutableList<Double>(series.size) { 0.0 }
    
        for (i in 1 until series.size) {
    
            val change =
                series[i] -
                        series[i - 1]
    
            delta[i] = change
    
            gains[i] =
                if (change > 0.0) {
                    change
                } else {
                    0.0
                }
    
            losses[i] =
                if (change < 0.0) {
                    -change
                } else {
                    0.0
                }
        }
    
        val avgGain =
            MutableList<Double?>(series.size) {
                null
            }
    
        val avgLoss =
            MutableList<Double?>(series.size) {
                null
            }
    
        if (series.size <= rsiLength) {
            return null
        }
    
        var gainSum = 0.0
        var lossSum = 0.0
    
        for (i in 1..rsiLength) {
    
            gainSum += gains[i]
            lossSum += losses[i]
        }
    
        var previousGain =
            gainSum /
                    rsiLength.toDouble()
    
        var previousLoss =
            lossSum /
                    rsiLength.toDouble()
    
        avgGain[rsiLength] =
            previousGain
    
        avgLoss[rsiLength] =
            previousLoss
    
        for (i in rsiLength + 1 until series.size) {
    
            previousGain =
                (
                    previousGain *
                            (rsiLength - 1) +
                            gains[i]
                    ) /
                        rsiLength.toDouble()
    
            previousLoss =
                (
                    previousLoss *
                            (rsiLength - 1) +
                            losses[i]
                    ) /
                        rsiLength.toDouble()
    
            avgGain[i] =
                previousGain
    
            avgLoss[i] =
                previousLoss
        }
    
        val targetRs =
            level /
                    (100.0 - level)
    
        val raw =
            MutableList<Double?>(series.size) {
                null
            }
    
        /*
         * Reverse price for candle i uses the Wilder
         * state from candle i-1 and previous close.
         */
        for (i in 1 until series.size) {
    
            val stateIndex =
                i - 1
    
            if (stateIndex < rsiLength) {
                continue
            }
    
            val averageGain =
                avgGain[stateIndex]
                    ?: continue
    
            val averageLoss =
                avgLoss[stateIndex]
                    ?: continue
    
            val previousClose =
                series[i - 1]
    
            val currentRs =
                if (averageLoss == 0.0) {
                    Double.POSITIVE_INFINITY
                } else {
                    averageGain /
                            averageLoss
                }
    
            val requiredPrice =
                if (targetRs > currentRs) {
    
                    previousClose +
                            (
                                rsiLength - 1
                                ).toDouble() *
                            (
                                targetRs *
                                        averageLoss -
                                        averageGain
                                )
    
                } else {
    
                    previousClose -
                            (
                                rsiLength - 1
                                ).toDouble() *
                            (
                                averageGain /
                                        targetRs -
                                        averageLoss
                                )
                }
    
            raw[i] =
                requiredPrice
        }
    
        /*
         * EMA smoothing exactly like ema_tv() in the
         * Colab reference: SMA seed followed by
         * recursive EMA.
         */
        val validStart =
            raw.indexOfFirst {
                it != null
            }
    
        if (validStart < 0) {
            return null
        }
    
        if (
            validStart +
            smoothingLength >
            raw.size
        ) {
            return null
        }
    
        var seedSum = 0.0
    
        for (
            i in validStart until
                    validStart + smoothingLength
        ) {
    
            val value =
                raw[i]
                    ?: return null
    
            seedSum += value
        }
    
        var previous =
            seedSum /
                    smoothingLength.toDouble()
    
        var smoothed: Double? = null
    
        val seedIndex =
            validStart +
                    smoothingLength -
                    1
    
        for (
            i in seedIndex until
                    raw.size
        ) {
    
            if (i == seedIndex) {
    
                smoothed =
                    previous
    
            } else {
    
                val value =
                    raw[i]
                        ?: continue
    
                previous =
                    (
                        2.0 /
                                (
                                    smoothingLength +
                                            1.0
                                    )
                        ) *
                            value +
                            (
                                1.0 -
                                        2.0 /
                                        (
                                            smoothingLength +
                                                    1.0
                                            )
                                ) *
                            previous
    
                smoothed =
                    previous
            }
        }
    
        return smoothed
    }

    /*
     * =========================================================
     * Reverse Stoch RSI
     * =========================================================
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

        if (previous.isEmpty()) {
            return null
        }

        val lowest =
            previous.minOrNull()
                ?: return null

        val highest =
            previous.maxOrNull()
                ?: return null

        if (highest == lowest) {

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
     */
    private fun reverseCurrentStochKPrice(
        series: List<Double>,
        rsiLength: Int,
        stochLength: Int,
        kLength: Int
    ): Double? {

        if (
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

        val currentK =
            stochK(
                raw,
                kLength
            ).lastOrNull()
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
     */
    private fun reverseCurrentStochDPrice(
        series: List<Double>,
        rsiLength: Int,
        stochLength: Int,
        kLength: Int,
        dLength: Int
    ): Double? {

        if (
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

        val currentD =
            stochD(
                k,
                dLength
            ).lastOrNull()
                ?: return null

        if (dLength == 1) {

            return reverseCurrentStochKPrice(
                series,
                rsiLength,
                stochLength,
                kLength
            )
        }

        val previousK =
            k.dropLast(1)
                .takeLast(
                    dLength - 1
                )
                .filterNotNull()

        if (
            previousK.size !=
            dLength - 1
        ) {
            return null
        }

        val requiredK =
            currentD *
                    dLength -
                    previousK.sum()

        if (
            requiredK < 0.0 ||
            requiredK > 100.0
        ) {
            return null
        }

        if (kLength == 1) {

            return reverseStochRawPrice(
                series,
                requiredK,
                rsiLength,
                stochLength
            )
        }

        val previousRaw =
            raw.dropLast(1)
                .takeLast(
                    kLength - 1
                )
                .filterNotNull()

        if (
            previousRaw.size !=
            kLength - 1
        ) {
            return null
        }

        val requiredRaw =
            requiredK *
                    kLength -
                    previousRaw.sum()

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
     * Reverse current smoothed Stoch RSI level.
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
        ).lastOrNull()
    }

    /*
     * Solve final price for target MACD line.
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
            fast >= slow
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

        if (coefficient == 0.0) {
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
     * Reverse current MACD.
     */
    private fun reverseCurrentMacdPrice(
        series: List<Double>,
        fast: Int,
        slow: Int,
        signal: Int,
        smooth: Int
    ): Double? {

        if (
            smooth <= 0
        ) {
            return null
        }

        val macdValues =
            macd(
                series,
                fast,
                slow,
                signal
            ).first

        val current =
            macdValues.lastOrNull()
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
            current *
                    smooth -
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

        if (
            smooth <= 0
        ) {
            return null
        }

        val values =
            macd(
                series,
                fast,
                slow,
                signal
            ).second

        val current =
            values.lastOrNull()
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

                current *
                        smooth -
                        previous.sum()
            }

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

        if (
            smooth <= 0
        ) {
            return null
        }

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

            "SMA" -> {

                val n =
                    intParam(0)
                        ?: return null

                sma(
                    x,
                    n
                ).lastOrNull()
            }

            "WMA" -> {

                val n =
                    intParam(0)
                        ?: return null

                wma(
                    x,
                    n
                ).lastOrNull()
            }

            "EMA" -> {

                val n =
                    intParam(0)
                        ?: return null

                ema(
                    x,
                    n
                ).lastOrNull()
            }

            "HMA" -> {

                val n =
                    intParam(0)
                        ?: return null

                hma(
                    x,
                    n
                ).lastOrNull()
            }

            "RSI" -> {

                val n =
                    intParam(0)
                        ?: return null

                rsi(
                    x,
                    n
                ).lastOrNull()
            }

            "EMA of RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                emaOfRsi(
                    x,
                    rsiLength,
                    smoothingLength
                ).lastOrNull()
            }

            "SMA of RSI" -> {

                val rsiLength =
                    intParam(0)
                        ?: return null

                val smoothingLength =
                    intParam(1)
                        ?: return null

                smaOfRsi(
                    x,
                    rsiLength,
                    smoothingLength
                ).lastOrNull()
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
                ).first.lastOrNull()
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
                ).second.lastOrNull()
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
                ).third.lastOrNull()
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
                ).lastOrNull()
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
                ).lastOrNull()
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
                ).lastOrNull()
            }

            "Numeric Value" ->
                p.firstOrNull()

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
