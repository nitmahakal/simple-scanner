package com.babunator.scanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    fun toJson(cfg: ScanConfig): String {
        val root = JSONObject()

        root.put("timeframe", cfg.timeframe)
        root.put("logic", cfg.logic)

        val timeframes = JSONArray()
        cfg.timeframes
            .ifEmpty { listOf(cfg.timeframe) }
            .distinct()
            .forEach {
                timeframes.put(it)
            }

        root.put("timeframes", timeframes)

        val conditions = JSONArray()

        cfg.conditions.forEach { c ->

            val condition = JSONObject()

            condition.put(
                "leftIndicator",
                c.leftIndicator
            )

            condition.put(
                "leftParams",
                JSONArray(c.leftParams)
            )

            condition.put(
                "comparator",
                c.comparator
            )

            condition.put(
                "rightIndicator",
                c.rightIndicator
            )

            condition.put(
                "rightParams",
                JSONArray(c.rightParams)
            )

            condition.put(
                "rightTarget",
                c.rightTarget
            )

            condition.put(
                "rangePct",
                c.rangePct
            )

            conditions.put(condition)
        }

        root.put("conditions", conditions)

        return root.toString()
    }

    fun fromJson(json: String): ScanConfig {
        val root = JSONObject(json)

        val timeframes = mutableListOf<String>()

        val tfArray = root.optJSONArray("timeframes")

        if (tfArray != null) {
            for (i in 0 until tfArray.length()) {
                val value = tfArray.optString(i).trim()

                if (value.isNotEmpty() &&
                    !timeframes.contains(value)
                ) {
                    timeframes += value
                }
            }
        }

        val fallbackTimeframe =
            root.optString(
                "timeframe",
                "Daily"
            )

        if (timeframes.isEmpty()) {
            timeframes += fallbackTimeframe
        }

        val conditions = mutableListOf<Condition>()

        val conditionArray =
            root.optJSONArray("conditions")

        if (conditionArray != null) {

            for (i in 0 until conditionArray.length()) {

                val item =
                    conditionArray.optJSONObject(i)
                        ?: continue

                val leftParams =
                    jsonNumbers(
                        item.optJSONArray("leftParams")
                    )

                val rightParams =
                    jsonNumbers(
                        item.optJSONArray("rightParams")
                    )

                conditions += Condition(
                    leftIndicator =
                        item.optString(
                            "leftIndicator",
                            "EMA"
                        ),
                    leftParams = leftParams,
                    comparator =
                        item.optString(
                            "comparator",
                            "Above"
                        ),
                    rightIndicator =
                        item.optString(
                            "rightIndicator",
                            "Number"
                        ),
                    rightParams = rightParams,
                    rightTarget =
                        item.optDouble(
                            "rightTarget",
                            0.0
                        ),
                    rangePct =
                        item.optDouble(
                            "rangePct",
                            1.0
                        )
                )
            }
        }

        return ScanConfig(
            timeframe = timeframes.first(),
            logic =
                root.optString(
                    "logic",
                    "AND"
                ),
            conditions = conditions,
            timeframes = timeframes
        )
    }

    private fun jsonNumbers(
        array: JSONArray?
    ): List<Double> {

        if (array == null) {
            return emptyList()
        }

        val out = mutableListOf<Double>()

        for (i in 0 until array.length()) {
            val value = array.optDouble(
                i,
                Double.NaN
            )

            if (!value.isNaN()) {
                out += value
            }
        }

        return out
    }
}
