package com.babunator.scanner

import android.content.Context

object ScanConfigStore {
    private const val P = "scan_config"
    fun save(context: Context, cfg: ScanConfig) {
        val sp = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        val e = sp.edit().putString("timeframe", cfg.timeframe).putString("logic", cfg.logic)
        for (i in 0 until 3) {
            val c = cfg.conditions.getOrNull(i)
            e.putString("li$i", c?.leftIndicator ?: "Close")
                .putString("lp$i", c?.leftParams?.joinToString(",") ?: "")
                .putString("op$i", c?.comparator ?: "Above")
                .putString("ri$i", c?.rightIndicator ?: "Number")
                .putString("rp$i", c?.rightParams?.joinToString(",") ?: "")
                .putFloat("rt$i", (c?.rightTarget ?: 0.0).toFloat())
                .putFloat("rg$i", (c?.rangePct ?: 1.0).toFloat())
        }
        e.apply()
    }
    fun load(context: Context): ScanConfig {
        val sp = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        val cs = (0 until 3).mapNotNull { i ->
            val li = sp.getString("li$i", null) ?: return@mapNotNull null
            val lp = sp.getString("lp$i", "")!!.split(',').mapNotNull { it.toDoubleOrNull() }
            val op = sp.getString("op$i", "Above") ?: "Above"
            val ri = sp.getString("ri$i", "Number") ?: "Number"
            val rp = sp.getString("rp$i", "")!!.split(',').mapNotNull { it.toDoubleOrNull() }
            Condition(li, lp, op, ri, rp, sp.getFloat("rt$i", 0f).toDouble(), sp.getFloat("rg$i", 1f).toDouble())
        }
        return ScanConfig(sp.getString("timeframe", "Daily")!!, sp.getString("logic", "AND")!!,
            if (cs.isEmpty()) listOf(Condition("EMA", listOf(50.0), "Above", "EMA", listOf(200.0), 0.0, 1.0)) else cs)
    }
}
