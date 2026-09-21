package com.babunator.scanner

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val indicators = listOf(
        "Close",
        "EMA",
        "HMA",
        "RSI",
        "EMA of RSI",
        "MACD",
        "MACD Signal",
        "MACD Histogram",
        "Stoch RSI %K",
        "Stoch RSI %D",
        "Numeric Value",
        "Reverse RSI",
        "Reverse Stoch RSI",
        "Reverse Stoch RSI %K",
        "Reverse Stoch RSI %D"
    )

    private val comparators = listOf(
        "Above",
        "Below",
        "Equal",
        "Cross Above",
        "Cross Below",
        "Near By",
        "May Go To Cross Above",
        "May Go To Cross Below"
    )

    private val rows = mutableListOf<Row>()

    private lateinit var content: FrameLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)

        buildMainLayout()
        showUpdateScreen()
    }

    private fun buildMainLayout() {
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 14, 10, 10)
        }

        header.addView(
            TextView(this).apply {
                text = "NSE Simple Scanner"
                textSize = 22f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )

        header.addView(
            Button(this).apply {
                text = "⚙"
                setOnClickListener { showSettings() }
            }
        )

        main.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        main.addView(content)

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        navigation.addView(
            navButton("UPDATE DATA") {
                showUpdateScreen()
            }
        )

        navigation.addView(
            navButton("SCANNER") {
                showScannerScreen()
            }
        )

        navigation.addView(
            navButton("SAVED / TRACKING") {
                showSavedScreen()
            }
        )

        main.addView(
            navigation,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(main)
    }

    private fun navButton(title: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = title
            textSize = 11f
            setOnClickListener { action() }

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
    }

    // ---------------------------------------------------------
    // SCREEN 1 : UPDATE DATA
    // ---------------------------------------------------------

    private fun showUpdateScreen() {
        content.removeAllViews()

        val root = verticalScroll()

        root.addView(
            title("Update Data")
        )

        val db = AppDb(this)
        val updateStatus = db.getUpdateStatus()
        
        status = TextView(this).apply {
            text = if (updateStatus == null) {
                "Data status: Ready"
            } else {
                "Data status: ${updateStatus.processed} / ${updateStatus.total}"
            }
            textSize = 16f
        }
        
        root.addView(status, lp())
        root.addView(
            Button(this).apply {
                text = "UPDATE DATA"
                setOnClickListener {
                    status.text = "Update requested. Data update connection will be wired next."
                }
            },
            lp()
        )

        root.addView(
            label("Progress")
        )

        root.addView(
            TextView(this).apply {
                text = "0 / 0"
                textSize = 18f
                tag = "progress"
            },
            lp()
        )

        root.addView(
            TextView(this).apply {
                text = "Successful: 0\nFailed: 0\nRetry: 0\nLast update: —"
                textSize = 16f
            },
            lp()
        )

        root.addView(
            TextView(this).apply {
                text = "\nUpdate Data screen is ready.\n\n" +
                        "• NSE stock list\n" +
                        "• Incremental update\n" +
                        "• One retry for failed data\n" +
                        "• Progress and success/failure count\n" +
                        "• Last update time\n\n" +
                        "The actual Worker connection will be added in the Update Data step."
                textSize = 15f
            },
            lp()
        )

        content.addView(root)
    }

    // ---------------------------------------------------------
    // SCREEN 2 : SCANNER
    // ---------------------------------------------------------

    private fun showScannerScreen() {
        content.removeAllViews()
        rows.clear()

        val root = verticalScroll()

        root.addView(title("Scanner"))

        status = TextView(this).apply {
            text = "Ready"
            textSize = 16f
        }

        root.addView(status, lp())

        val tf = Spinner(this).apply {
            adapter = spinner(listOf("Daily", "Weekly", "Monthly"))
        }

        root.addView(label("Timeframe"))
        root.addView(tf, lp())

        val logic = Spinner(this).apply {
            adapter = spinner(listOf("AND", "OR"))
        }

        root.addView(label("Condition logic"))
        root.addView(logic, lp())

        repeat(3) {
            addConditionRow(root, it)
        }

        root.addView(
            Button(this).apply {
                text = "SCAN NOW"
                setOnClickListener {
                    val cfg = ScanConfig(
                        tf.selectedItem.toString(),
                        logic.selectedItem.toString(),
                        rows.map { it.read() }
                    )

                    ScanConfigStore.save(this@MainActivity, cfg)

                    status.text = "Scan requested"
                }
            },
            lp()
        )

        root.addView(
            Button(this).apply {
                text = "SAVE SCAN"
                setOnClickListener {
                    Toast.makeText(
                        this@MainActivity,
                        "Save Scan will be connected in the Saved Scans step.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            lp()
        )

        root.addView(
            TextView(this).apply {
                text = "\nParams examples:\n" +
                        "EMA: 50\n" +
                        "RSI: 14\n" +
                        "Reverse Stoch RSI: 14,14,50\n" +
                        "Reverse Stoch RSI %K: 14,14,3,50\n" +
                        "Reverse Stoch RSI %D: 14,14,3,3,50\n" +
                        "Near/May Cross range: enter the % separately."
            },
            lp()
        )

        content.addView(root)
    }

    private fun addConditionRow(root: LinearLayout, idx: Int) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }

        box.addView(label("Condition ${idx + 1}"))

        val left = Spinner(this).apply {
            adapter = spinner(indicators)
        }

        val op = Spinner(this).apply {
            adapter = spinner(comparators)
        }

        val right = Spinner(this).apply {
            adapter = spinner(
                listOf("Number") + indicators.filter { it != "Numeric Value" }
            )
        }

        val leftParams = text("Left params")
        val rightParams = text("Right params")
        val target = text("Number target", true)
        val range = text("Range % (Near / May Cross)", true)

        box.addView(label("Value A / left indicator"))
        box.addView(left, lp())

        box.addView(leftParams, lp())

        box.addView(label("Comparator"))
        box.addView(op, lp())

        box.addView(label("Value B / right indicator"))
        box.addView(right, lp())

        box.addView(rightParams, lp())
        box.addView(target, lp())
        box.addView(range, lp())

        root.addView(box, lp())

        rows += Row(
            left,
            op,
            right,
            leftParams,
            rightParams,
            target,
            range
        )
    }

    // ---------------------------------------------------------
    // SCREEN 3 : SAVED SCANS / TRACKING
    // ---------------------------------------------------------

    private fun showSavedScreen() {
        content.removeAllViews()

        val root = verticalScroll()

        root.addView(title("Saved Scans & Tracking"))

        root.addView(
            TextView(this).apply {
                text = "Saved scans will appear here."
                textSize = 18f
            },
            lp()
        )

        root.addView(
            TextView(this).apply {
                text = "\nThis screen will contain:\n\n" +
                        "• Saved scan list\n" +
                        "• Latest scan status\n" +
                        "• Valid / Void condition\n" +
                        "• Entry price\n" +
                        "• Current price\n" +
                        "• P&L\n" +
                        "• Daily P&L\n" +
                        "• Tracking controls\n\n" +
                        "Tracking logic will be added separately after Update Data and Scanner are complete."
                textSize = 15f
            },
            lp()
        )

        root.addView(
            Button(this).apply {
                text = "VIEW SCAN HISTORY"
                setOnClickListener {
                    showHistory()
                }
            },
            lp()
        )

        content.addView(root)
    }

    // ---------------------------------------------------------
    // SETTINGS / THEME
    // ---------------------------------------------------------

    private fun showSettings() {
        val options = arrayOf(
            "Light",
            "Dark",
            "System Default"
        )

        val current = getSharedPreferences(
            "app_settings",
            MODE_PRIVATE
        ).getInt("theme", 0)

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setSingleChoiceItems(options, current) { dialog, which ->

                saveTheme(which)

                when (which) {
                    0 -> AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_NO
                    )

                    1 -> AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_YES
                    )

                    2 -> AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    )
                }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applySavedTheme() {
        when (
            getSharedPreferences(
                "app_settings",
                MODE_PRIVATE
            ).getInt("theme", 0)
        ) {
            0 -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )

            1 -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )

            else -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }
    }

    private fun saveTheme(value: Int) {
        getSharedPreferences(
            "app_settings",
            MODE_PRIVATE
        ).edit()
            .putInt("theme", value)
            .apply()
    }

    // ---------------------------------------------------------
    // EXISTING HELPERS
    // ---------------------------------------------------------

    private fun showHistory() {
        val db = AppDb(this)
        val runs = db.recentRuns()

        if (runs.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("No saved scan results yet.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Scan History")
            .setItems(runs.toTypedArray()) { _, which ->

                val id = runs[which]
                    .substringAfter('#')
                    .substringBefore(' ')
                    .toLong()

                AlertDialog.Builder(this)
                    .setTitle(runs[which])
                    .setMessage(
                        db.results(id)
                            .joinToString("\n")
                            .ifEmpty { "No matches" }
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
            .show()
    }

    private fun verticalScroll(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 8, 18, 18)
        }
    }

    private fun title(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 24f
            setPadding(0, 8, 0, 12)
        }
    }

    private fun label(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
        }
    }

    private fun text(
        hint: String,
        number: Boolean = false
    ): EditText {
        return EditText(this).apply {
            this.hint = hint

            if (number) {
                inputType =
                    InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }
    }

    private fun spinner(
        list: List<String>
    ): ArrayAdapter<String> {
        return ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            list
        )
    }

    private fun lp(): ViewGroup.LayoutParams {
        return ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private data class Row(
        val left: Spinner,
        val op: Spinner,
        val right: Spinner,
        val lp: EditText,
        val rp: EditText,
        val target: EditText,
        val range: EditText
    ) {
        fun read(): Condition {

            val l = lp.text.toString()
                .split(',')
                .mapNotNull {
                    it.trim().toDoubleOrNull()
                }

            val r = rp.text.toString()
                .split(',')
                .mapNotNull {
                    it.trim().toDoubleOrNull()
                }

            return Condition(
                left.selectedItem.toString(),
                l,
                op.selectedItem.toString(),
                right.selectedItem.toString(),
                r,
                target.text.toString().toDoubleOrNull() ?: 0.0,
                range.text.toString().toDoubleOrNull() ?: 1.0
            )
        }
    }
}
