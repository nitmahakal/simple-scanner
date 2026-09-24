package com.babunator.scanner

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

class DataProvider {

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
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
        val q = URLEncoder.encode(
            "${symbol}.NS",
            "UTF-8"
        )

        val url =
            "https://query1.finance.yahoo.com/v8/finance/chart/$q" +
                    "?range=max" +
                    "&interval=1d&events=div%2Csplits"

        return parseDaily(symbol, get(url))
    }

    fun fetchDailySince(symbol: String, fromDate: LocalDate): List<Candle> {
        val q = URLEncoder.encode(
            "${symbol}.NS",
            "UTF-8"
        )

        val period1 = fromDate
            .atStartOfDay(ZoneId.of("Asia/Kolkata"))
            .toEpochSecond()

        val period2 = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.of("Asia/Kolkata"))
            .toEpochSecond()

        val url =
            "https://query1.finance.yahoo.com/v8/finance/chart/$q" +
                    "?period1=$period1" +
                    "&period2=$period2" +
                    "&interval=1d&events=div%2Csplits"

        return parseDaily(symbol, get(url))
    }

    private fun parseDaily(symbol: String, text: String): List<Candle> {
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
            throw IllegalStateException("Empty chart result for $symbol")
        }

        val result = resultArray.getJSONObject(0)

        val ts = result.optJSONArray("timestamp")
            ?: throw IllegalStateException("No timestamps for $symbol")

        val quote = result
            .getJSONObject("indicators")
            .optJSONArray("quote")
            ?: throw IllegalStateException("No quote data for $symbol")

        if (quote.length() == 0 || quote.isNull(0)) {
            throw IllegalStateException("Empty quote data for $symbol")
        }

        val close = quote
            .getJSONObject(0)
            .optJSONArray("close")
            ?: throw IllegalStateException("No close data for $symbol")

        val out = mutableListOf<Candle>()

        for (i in 0 until minOf(ts.length(), close.length())) {
            if (close.isNull(i)) continue

            val d = java.time.Instant
                .ofEpochSecond(ts.getLong(i))
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDate()
                .toString()

            out += Candle(
                d,
                close.getDouble(i)
            )
        }

        if (out.isEmpty()) {
            throw IllegalStateException("No valid price data for $symbol")
        }

        return out
    }
}
