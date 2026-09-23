package com.babunator.scanner

import android.content.Context

object ScanConfigStore {

    private const val P = "scan_config"

    fun save(
        context: Context,
        cfg: ScanConfig
    ) {
        val sp = context.getSharedPreferences(
            P,
            Context.MODE_PRIVATE
        )

        val e = sp.edit()
            .putString("timeframe", cfg.timeframe)
            .putString(
                "timeframes",
                cfg.timeframes.joinToString(",")
            )
            .putString("logic", cfg.logic)

        for (i in 0 until 3) {

            val c = cfg.conditions.getOrNull(i)

            e.putString(
                "li$i",
                c?.leftIndicator ?: "Close"
            )

            e.putString(
                "lp$i",
                c?.leftParams?.joinToString(",") ?: ""
            )

            e.putString(
                "op$i",
                c?.comparator ?: "Above"
            )

            e.putString(
                "ri$i",
                c?.rightIndicator ?: "Number"
            )

            e.putString(
                "rp$i",
                c?.rightParams?.joinToString(",") ?: ""
            )

            e.putString(
                "rt$i",
                (c?.rightTarget ?: 0.0).toString()
            )

            e.putString(
                "rg$i",
                (c?.rangePct ?: 1.0).toString()
            )
        }

        e.apply()
    }

    fun load(context: Context): ScanConfig {

        val sp = context.getSharedPreferences(
            P,
            Context.MODE_PRIVATE
        )

        val oldTimeframe =
            sp.getString(
                "timeframe",
                "Daily"
            ) ?: "Daily"

        val savedTimeframes =
            sp.getString(
                "timeframes",
                null
            )
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?: listOf(oldTimeframe)

        val cs = (0 until 3).mapNotNull { i ->

            val li =
                sp.getString("li$i", null)
                    ?: return@mapNotNull null

            val lp =
                sp.getString("lp$i", "")
                    .orEmpty()
                    .split(',')
                    .mapNotNull {
                        it.trim().toDoubleOrNull()
                    }

            val op =
                sp.getString(
                    "op$i",
                    "Above"
                ) ?: "Above"

            val ri =
                sp.getString(
                    "ri$i",
                    "Number"
                ) ?: "Number"

            val rp =
                sp.getString("rp$i", "")
                    .orEmpty()
                    .split(',')
                    .mapNotNull {
                        it.trim().toDoubleOrNull()
                    }

            val rightTarget =
                sp.getString(
                    "rt$i",
                    null
                )?.toDoubleOrNull()
                    ?: 0.0

            val rangePct =
                sp.getString(
                    "rg$i",
                    null
                )?.toDoubleOrNull()
                    ?: 1.0

            Condition(
                leftIndicator = li,
                leftParams = lp,
                comparator = op,
                rightIndicator = ri,
                rightParams = rp,
                rightTarget = rightTarget,
                rangePct = rangePct
            )
        }

        return ScanConfig(
            timeframe = oldTimeframe,
            logic =
                sp.getString(
                    "logic",
                    "AND"
                ) ?: "AND",
            conditions =
                if (cs.isEmpty()) {
                    listOf(
                        Condition(
                            "EMA",
                            listOf(50.0),
                            "Above",
                            "EMA",
                            listOf(200.0),
                            0.0,
                            1.0
                        )
                    )
                } else {
                    cs
                },
            timeframes = savedTimeframes
        )
    }
}
