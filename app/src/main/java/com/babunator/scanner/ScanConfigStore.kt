package com.babunator.scanner

import android.content.Context

object ScanConfigStore {

    private const val P = "scan_config"
    private const val MAX_SAVED_CONDITIONS = 50

    fun save(
        context: Context,
        cfg: ScanConfig
    ) {
        val sp = context.getSharedPreferences(
            P,
            Context.MODE_PRIVATE
        )

        val selectedTimeframes =
            cfg.timeframes
                .ifEmpty { listOf(cfg.timeframe) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        val primaryTimeframe =
            selectedTimeframes.firstOrNull()
                ?: cfg.timeframe

        val e = sp.edit()
            .putString(
                "timeframe",
                primaryTimeframe
            )
            .putString(
                "timeframes",
                selectedTimeframes.joinToString(",")
            )
            .putString(
                "logic",
                cfg.logic
            )
            .putInt(
                "condition_count",
                cfg.conditions.size
            )

        for (i in 0 until MAX_SAVED_CONDITIONS) {
            if (i >= cfg.conditions.size) {
                e.remove("li$i")
                e.remove("lp$i")
                e.remove("op$i")
                e.remove("ri$i")
                e.remove("rp$i")
                e.remove("rt$i")
                e.remove("rg$i")
            }
        }

        for (i in cfg.conditions.indices) {

            val c = cfg.conditions[i]

            e.putString(
                "li$i",
                c.leftIndicator
            )

            e.putString(
                "lp$i",
                c.leftParams.joinToString(",")
            )

            e.putString(
                "op$i",
                c.comparator
            )

            e.putString(
                "ri$i",
                c.rightIndicator
            )

            e.putString(
                "rp$i",
                c.rightParams.joinToString(",")
            )

            e.putString(
                "rt$i",
                c.rightTarget.toString()
            )

            e.putString(
                "rg$i",
                c.rangePct.toString()
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
                ?.ifEmpty { listOf(oldTimeframe) }
                ?: listOf(oldTimeframe)

        val conditionCount =
            if (sp.contains("condition_count")) {
                sp.getInt(
                    "condition_count",
                    1
                ).coerceIn(
                    0,
                    MAX_SAVED_CONDITIONS
                )
            } else {
                when {
                    sp.contains("li2") -> 3
                    sp.contains("li1") -> 2
                    sp.contains("li0") -> 1
                    else -> 0
                }
            }

        val cs =
            (0 until conditionCount).mapNotNull { i ->

                val li =
                    sp.getString(
                        "li$i",
                        null
                    )
                        ?: return@mapNotNull null

                val lp =
                    sp.getString(
                        "lp$i",
                        ""
                    )
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
                    sp.getString(
                        "rp$i",
                        ""
                    )
                        .orEmpty()
                        .split(',')
                        .mapNotNull {
                            it.trim().toDoubleOrNull()
                        }

                val rightTarget =
                    sp.getString(
                        "rt$i",
                        null
                    )
                        ?.toDoubleOrNull()
                        ?: 0.0

                val rangePct =
                    sp.getString(
                        "rg$i",
                        null
                    )
                        ?.toDoubleOrNull()
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
            timeframe = savedTimeframes.first(),
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
