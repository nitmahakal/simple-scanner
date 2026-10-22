package com.babunator.scanner

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

class DataProvider {

    private val ist: ZoneId = ZoneId.of("Asia/Kolkata")

    // NSE equity history is far older than this start date, so this means "full history".
    // Do NOT use range=max: Yahoo silently returns monthly bars for long histories.
    private val fullHistoryStart: LocalDate = LocalDate.of(1990, 1, 1)

    private class HttpException(val code: Int) : Exception("HTTP $code")

    companion object {
        // The symbol list is shared by all update chunks of a run, so every
        // chunk sees the same list (offsets stay valid) and NSE is not hit
        // again for each chunk.
        private const val SYMBOL_CACHE_MS = 6L * 60L * 60L * 1000L

        // Small gap between requests, so Yahoo is not hit in a burst.
        private const val MIN_GAP_MS = 120L

        // One retry only: the update worker keeps its own second attempt.
        private const val MAX_ATTEMPTS = 2

        @Volatile
        private var cachedSymbols: List<String>? = null

        @Volatile
        private var cachedAt: Long = 0L

        private val requestLock = Any()
        private var lastRequestAt: Long = 0L
    }

    private fun waitTurn() {
        synchronized(requestLock) {
            val now = System.currentTimeMillis()
            val wait = lastRequestAt + MIN_GAP_MS - now

            if (wait > 0) {
                Thread.sleep(wait)
            }

            lastRequestAt = System.currentTimeMillis()
        }
    }

    private fun request(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection

        try {
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.requestMethod = "GET"
            c.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 NSE-Simple-Scanner/1.0"
            )

            val code = c.responseCode

            if (code !in 200..299) {
                throw HttpException(code)
            }

            return BufferedReader(c.inputStream.reader()).use {
                it.readText()
            }
        } finally {
            c.disconnect()
        }
    }

    // Retries once for rate limit (429), server errors (5xx) and network
    // errors. Not-found and other client errors fail immediately.
    private fun get(url: String): String {
        var attempt = 1

        while (true) {
            try {
                waitTurn()
                return request(url)
            } catch (e: HttpException) {
                val retryable = e.code == 429 || e.code >= 500

                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    throw e
                }

                Thread.sleep(if (e.code == 429) 4000L else 2000L)
            } catch (e: java.io.IOException) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e
                }

                Thread.sleep(2000L)
            }

            attempt++
        }
    }

    // Only normal equity series (EQ). SME, BE and other illiquid series
    // are left out.
    fun fetchSymbolList(): List<String> {
        val now = System.currentTimeMillis()
        val cached = cachedSymbols

        if (cached != null && now - cachedAt < SYMBOL_CACHE_MS) {
            return cached
        }

        return try {
            val text = get(
                "https://archives.nseindia.com/content/equities/EQUITY_L.csv"
            )

            val list = parseEquitySymbols(text)

            cachedSymbols = list
            cachedAt = now

            list
        } catch (e: Exception) {
            // Network problem: an older list is better than no update at all.
            if (cached != null) cached else throw e
        }
    }

    private fun parseEquitySymbols(text: String): List<String> {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.size < 2) {
            throw IllegalStateException("NSE symbol list is empty")
        }

        val header = splitCsv(lines.first()).map { it.trim().uppercase() }

        val symbolIndex = header.indexOf("SYMBOL").let { if (it >= 0) it else 0 }
        val seriesIndex = header.indexOf("SERIES")

        val symbols = lines.drop(1)
            .mapNotNull { line ->
                val columns = splitCsv(line)

                val symbol = columns.getOrNull(symbolIndex)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null

                // If the file layout ever changes and no SERIES column is
                // found, keep every symbol instead of returning nothing.
                val series = if (seriesIndex >= 0) {
                    columns.getOrNull(seriesIndex)?.trim()?.uppercase()
                } else {
                    "EQ"
                }

                if (series == "EQ") symbol else null
            }
            .distinct()

        if (symbols.isEmpty()) {
            throw IllegalStateException("NSE symbol list has no EQ symbols")
        }

        return symbols
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            if (ch == '"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                out.add(current.toString())
                current.setLength(0)
            } else {
                current.append(ch)
            }
        }

        out.add(current.toString())

        return out
    }

    fun fetchDaily(symbol: String, days: Int): List<Candle> {
        return fetchRange(
            symbol = symbol,
            fromDate = fullHistoryStart,
            allowEmpty = false
        )
    }

    fun fetchDailySince(symbol: String, fromDate: LocalDate): List<Candle> {
        return fetchRange(
            symbol = symbol,
            fromDate = fromDate,
            allowEmpty = true
        )
    }

    private fun fetchRange(
        symbol: String,
        fromDate: LocalDate,
        allowEmpty: Boolean
    ): List<Candle> {
        val q = URLEncoder.encode(
            "${symbol}.NS",
            "UTF-8"
        )

        val period1 = fromDate
            .atStartOfDay(ist)
            .toEpochSecond()

        val period2 = LocalDate.now(ist)
            .plusDays(1)
            .atStartOfDay(ist)
            .toEpochSecond()

        val url =
            "https://query1.finance.yahoo.com/v8/finance/chart/$q" +
                    "?period1=$period1" +
                    "&period2=$period2" +
                    "&interval=1d&events=div%2Csplits"

        return parseDaily(symbol, get(url), allowEmpty)
    }

    private fun parseDaily(
        symbol: String,
        text: String,
        allowEmpty: Boolean
    ): List<Candle> {

        val root = JSONObject(text)

        val chart = root.getJSONObject("chart")

        val error = chart.optJSONObject("error")
        if (error != null && !error.isNull("description")) {
            throw IllegalStateException(
                error.optString("description", "Yahoo chart error for $symbol")
            )
        }

        val resultArray = chart.optJSONArray("result")
            ?: throw IllegalStateException("No chart result for $symbol")

        if (resultArray.length() == 0 || resultArray.isNull(0)) {
            if (allowEmpty) return emptyList()
            throw IllegalStateException("Empty chart result for $symbol")
        }

        val result = resultArray.getJSONObject(0)

        // Safety check: never store anything that is not true daily data.
        val granularity = result
            .optJSONObject("meta")
            ?.optString("dataGranularity", "")
            .orEmpty()

        if (granularity.isNotEmpty() && granularity != "1d") {
            throw IllegalStateException(
                "Unexpected data granularity '$granularity' for $symbol"
            )
        }

        val ts = result.optJSONArray("timestamp")
            ?: if (allowEmpty) {
                return emptyList()
            } else {
                throw IllegalStateException("No timestamps for $symbol")
            }

        val quote = result
            .getJSONObject("indicators")
            .optJSONArray("quote")
            ?: if (allowEmpty) {
                return emptyList()
            } else {
                throw IllegalStateException("No quote data for $symbol")
            }

        if (quote.length() == 0 || quote.isNull(0)) {
            if (allowEmpty) return emptyList()
            throw IllegalStateException("Empty quote data for $symbol")
        }

        val close = quote
            .getJSONObject(0)
            .optJSONArray("close")
            ?: if (allowEmpty) {
                return emptyList()
            } else {
                throw IllegalStateException("No close data for $symbol")
            }

        val out = mutableListOf<Candle>()

        for (i in 0 until minOf(ts.length(), close.length())) {
            if (close.isNull(i)) continue

            val d = java.time.Instant
                .ofEpochSecond(ts.getLong(i))
                .atZone(ist)
                .toLocalDate()
                .toString()

            out += Candle(
                d,
                close.getDouble(i)
            )
        }

        if (out.isEmpty() && !allowEmpty) {
            throw IllegalStateException("No valid price data for $symbol")
        }

        return out
    }
}
