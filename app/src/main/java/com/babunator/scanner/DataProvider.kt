package com.babunator.scanner

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DataProvider {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000; c.requestMethod = "GET"
        c.setRequestProperty("User-Agent", "Mozilla/5.0 NSE-Simple-Scanner/1.0")
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        return BufferedReader(c.inputStream.reader()).use { it.readText() }
    }

    fun fetchSymbolList(): List<String> {
        val text = get("https://archives.nseindia.com/content/equities/EQUITY_L.csv")
        return text.lineSequence().drop(1).mapNotNull { line ->
            val p = line.split(','); p.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }.toList()
    }

    fun fetchDaily(symbol: String, days: Int): List<Candle> {
        val q = URLEncoder.encode("${symbol}.NS", "UTF-8")
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$q?range=${if (days <= 20) "15d" else "2y"}&interval=1d&events=div%2Csplits"
        val root = JSONObject(get(url))
        val result = root.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val ts = result.getJSONArray("timestamp")
        val close = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
        val out = mutableListOf<Candle>()
        for (i in 0 until minOf(ts.length(), close.length())) {
            if (close.isNull(i)) continue
            val d = java.time.Instant.ofEpochSecond(ts.getLong(i)).atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate().toString()
            out += Candle(d, close.getDouble(i))
        }
        return out
    }
}
