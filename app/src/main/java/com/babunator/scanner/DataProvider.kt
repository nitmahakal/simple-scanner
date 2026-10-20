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

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection

        try {
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.requestMethod = "GET"
            c.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 NSE-Simple-Scanner/1.0"
            )

            if (c.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${c.responseCode}")
            }

            return BufferedReader(c.inputStream.reader()).use {
                it.readText()
            }
        } finally {
            c.disconnect()
        }
    }

    fun fetchSymbolList(): List<String> {
        val text = get(
            "https://archives.nseindia.com/content/equities/EQUITY_L.csv"
        )

        val symbols = text.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val p = line.split(',')
                p.firstOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            .toList()

        if (symbols.isEmpty()) {
            throw IllegalStateException("NSE symbol list is empty")
        }

        return symbols
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
