package com.babunator.scanner

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
                    "?range=${if (days <= 20) "15d" else "2y"}" +
                    "&interval=1d&events=div%2Csplits"

        val root = JSONObject(get(url))

        val chart = root.getJSONObject("chart")

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
                .atZone(java.time.ZoneId.of("Asia/Kolkata"))
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
