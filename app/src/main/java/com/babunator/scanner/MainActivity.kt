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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            "Stoch RSI",
            "Stoch RSI %K",
            "Stoch RSI %D",
            "Numeric Value",
            "Reverse RSI Level 40",
            "Reverse RSI Level 50",
            "Reverse RSI Level 60",
            "Reverse Stoch RSI Level 20",
            "Reverse Stoch RSI Level 50",
            "Reverse Stoch RSI Level 80",
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

    root.addView(
        TextView(this).apply {
            text = "Daily market data • Incremental update"
            textSize = 15f
            setPadding(0, 0, 0, 12)
        },
        lp()
    )

    val db = AppDb(this)
    val updateStatus = db.getUpdateStatus()

    var progressText: TextView
    var statsText: TextView

    // ---------------------------------------------------------
    // MARKET DATA CARD
    // ---------------------------------------------------------

    val marketCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 18, 18, 18)
        setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
    }

    marketCard.addView(
        TextView(this).apply {
            text = "MARKET DATA"
            textSize = 18f
        },
        lp()
    )

    marketCard.addView(
        TextView(this).apply {
            text = "NSE stock data update"
            textSize = 14f
            setPadding(0, 4, 0, 12)
        },
        lp()
    )

    val updateButton = Button(this).apply {
        text = "UPDATE DATA"

        setOnClickListener {
            val workManager =
                WorkManager.getInstance(this@MainActivity)

            val request =
                OneTimeWorkRequestBuilder<UpdateWorker>()
                    .setInputData(
                        Data.Builder()
                            .putInt("offset", 0)
                            .putInt("limit", 25)
                            .build()
                    )
                    .build()

            workManager.enqueueUniqueWork(
                "nse_data_update",
                ExistingWorkPolicy.KEEP,
                request
            )

            text = "UPDATE IN PROGRESS"
            isEnabled = false
            status.text = "Update started..."
        }
    }

    marketCard.addView(
        updateButton,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    marketCard.addView(
        label("Progress"),
        lp()
    )

    progressText = TextView(this).apply {
        text = if (updateStatus == null) {
            "0 / 0"
        } else {
            "${updateStatus.processed} / ${updateStatus.total}"
        }
        textSize = 18f
        setPadding(0, 6, 0, 6)
    }

    marketCard.addView(progressText, lp())

    statsText = TextView(this).apply {
        text = if (updateStatus == null) {
            "Successful: 0\n" +
                    "Failed: 0\n" +
                    "Retry: 0\n" +
                    "Last update: —"
        } else {
            "Successful: ${updateStatus.successful}\n" +
                    "Failed: ${updateStatus.failed}\n" +
                    "Retry: ${updateStatus.retryCount}\n" +
                    "Last update: ${updateStatus.lastUpdateTime ?: "—"}"
        }
        textSize = 15f
        setPadding(0, 6, 0, 0)
    }

    marketCard.addView(statsText, lp())

    root.addView(
        marketCard,
        lp()
    )

    // ---------------------------------------------------------
    // DAILY AUTO UPDATE CARD
    // ---------------------------------------------------------

    val autoCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 18, 18, 18)
        setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
    }

    autoCard.addView(
        TextView(this).apply {
            text = "DAILY AUTO UPDATE"
            textSize = 18f
        },
        lp()
    )

    autoCard.addView(
        TextView(this).apply {
            text = "Automatic daily update will use the same incremental data flow."
            textSize = 14f
            setPadding(0, 4, 0, 0)
        },
        lp()
    )

    root.addView(
        autoCard,
        lp()
    )

    // ---------------------------------------------------------
    // HOW RESUME WORKS CARD
    // ---------------------------------------------------------

    val resumeCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 18, 18, 18)
        setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
    }

    resumeCard.addView(
        TextView(this).apply {
            text = "HOW RESUME WORKS"
            textSize = 18f
        },
        lp()
    )

    resumeCard.addView(
        TextView(this).apply {
            text =
                "• Existing data is kept\\n" +
                "• Update continues incrementally\\n" +
                "• Failed data is retried\\n" +
                "• Progress and status are saved"
            textSize = 14f
            setPadding(0, 8, 0, 0)
        },
        lp()
    )

    root.addView(
        resumeCard,
        lp()
    )

    // ---------------------------------------------------------
    // CURRENT STATUS
    // ---------------------------------------------------------

    status = TextView(this).apply {
        text = if (updateStatus == null) {
            "Data status: Ready"
        } else {
            "Data status: ${updateStatus.processed} / ${updateStatus.total}"
        }
        textSize = 15f
        setPadding(0, 12, 0, 8)
    }

    root.addView(status, lp())

    // ---------------------------------------------------------
    // EXISTING WORKMANAGER PROGRESS MONITOR
    // ---------------------------------------------------------

    lifecycleScope.launch {
        while (true) {

            val current =
                AppDb(this@MainActivity).getUpdateStatus()

            val updateRunning =
                kotlinx.coroutines.withContext(
                    kotlinx.coroutines.Dispatchers.IO
                ) {
                    WorkManager.getInstance(this@MainActivity)
                        .getWorkInfosForUniqueWork(
                            "nse_data_update"
                        )
                        .get()
                        .any { !it.state.isFinished }
                }

            updateButton.text =
                if (updateRunning) {
                    "UPDATE IN PROGRESS"
                } else {
                    "UPDATE DATA"
                }

            updateButton.isEnabled = !updateRunning

            if (current != null) {

                progressText.text =
                    "${current.processed} / ${current.total}"

                statsText.text =
                    "Successful: ${current.successful}\n" +
                    "Failed: ${current.failed}\n" +
                    "Retry: ${current.retryCount}\n" +
                    "Last update: ${current.lastUpdateTime ?: "—"}"

                status.text =
                    "Data status: ${current.processed} / ${current.total}"
            }

            delay(1000)
        }
    }

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

        val timeframeOptions = listOf(
            "Daily",
            "Weekly",
            "Monthly"
        )
        
        val selectedTimeframes = mutableListOf("Daily")
        
        val timeframeButton = Button(this)

        timeframeButton.text = "Daily"
        
        timeframeButton.setOnClickListener {
        
            val checked = timeframeOptions.map {
                selectedTimeframes.contains(it)
            }.toBooleanArray()
        
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Select Timeframes")
                .setMultiChoiceItems(
                    timeframeOptions.toTypedArray(),
                    checked
                ) { _, which, isChecked ->
        
                    val value = timeframeOptions[which]
        
                    if (isChecked) {
                        if (!selectedTimeframes.contains(value)) {
                            selectedTimeframes.add(value)
                        }
                    } else {
                        selectedTimeframes.remove(value)
                    }
                }
                .setPositiveButton("DONE") { _, _ ->
        
                    if (selectedTimeframes.isEmpty()) {
                        selectedTimeframes.add("Daily")
                    }
        
                    timeframeButton.text =
                        selectedTimeframes.joinToString(", ")
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }
        
        root.addView(label("Timeframes"))
        root.addView(timeframeButton, lp())
        val logic = Spinner(this).apply {
            adapter = spinner(listOf("AND", "OR"))
        }

        root.addView(label("Condition logic"))
        root.addView(logic, lp())

        addConditionRow(root, 0)

        root.addView(
            Button(this).apply {
                text = "+ ADD MORE CONDITION"
        
                setOnClickListener {
                    addConditionRow(root, rows.size)
                }
            },
            lp()
        )

        root.addView(
            Button(this).apply {
                text = "SCAN NOW"
                        setOnClickListener {
                            val conditions = rows.map { it.read() }
                        
                            val error = validateConditions(conditions)
                        
                            if (error != null) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Invalid Scan Condition")
                                    .setMessage(error)
                                    .setPositiveButton("OK", null)
                                    .show()
                        
                                return@setOnClickListener
                            }
                        
                            val cfg = ScanConfig(
                                timeframe = selectedTimeframes.first(),
                                logic = logic.selectedItem.toString(),
                                conditions = conditions,
                                timeframes = selectedTimeframes.toList()
                            )
                        
                            ScanConfigStore.save(this@MainActivity, cfg)
                        
                            val request = OneTimeWorkRequestBuilder<ScanWorker>()
                                .setInputData(
                                    Data.Builder()
                                        .putString("timeframe", cfg.timeframe)
                                        .build()
                                )
                                .build()
                        
                            WorkManager.getInstance(this@MainActivity)
                                .enqueueUniqueWork(
                                    "nse_scan",
                                    ExistingWorkPolicy.REPLACE,
                                    request
                                )
                        
                            status.text = "Scan running..."
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
                           "EMA of RSI: 14,9\n" +
                           "MACD: 12,26,9\n" +
                           "Stoch RSI: 14,14\n" +
                           "Reverse RSI Level 60: 14,9\n" +
                           "Reverse Stoch RSI Level 50: 14,14\n" +
                           "Reverse Stoch RSI %K: 14,14,3,50\n" +
                           "Reverse Stoch RSI %D: 14,14,3,3,50\n" +
                           "Near/May Cross range: enter the % separately."
            },
            lp()
        )

        content.addView(root)
    }

    private fun addConditionRow(root: ViewGroup, idx: Int) {
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
                    listOf("Number") + indicators.filter {
                        it != "Numeric Value"
                    }
                )
            }
        
            val leftParams = text("Left params")
        
            val rightParams = text("Right params")
        
            val target = text("Number target", true)
        
            val range = text(
                "Range % (Near / May Cross)",
                true
            )
        
            box.addView(
                label("Value A / left indicator")
            )
            box.addView(left, lp())
            box.addView(leftParams, lp())
        
            box.addView(
                label("Comparator")
            )
            box.addView(op, lp())
        
            box.addView(
                label("Value B / right indicator")
            )
            box.addView(right, lp())
            box.addView(rightParams, lp())
            box.addView(target, lp())
            box.addView(range, lp())
        
            left.setOnItemSelectedListener(
                object : AdapterView.OnItemSelectedListener {
        
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        leftParams.visibility =
                            View.VISIBLE
                    }
        
                    override fun onNothingSelected(
                        parent: AdapterView<*>?
                    ) {
                        leftParams.visibility =
                            View.VISIBLE
                    }
                }
            )
        
            right.setOnItemSelectedListener(
                object : AdapterView.OnItemSelectedListener {
        
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val selected =
                            right.selectedItem.toString()
        
                        target.visibility =
                            if (selected == "Number") {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }
        
                        rightParams.visibility =
                            if (selected == "Number") {
                                View.GONE
                            } else {
                                View.VISIBLE
                            }
                    }
        
                    override fun onNothingSelected(
                        parent: AdapterView<*>?
                    ) {
                        target.visibility =
                            View.VISIBLE
        
                        rightParams.visibility =
                            View.GONE
                    }
                }
            )
        
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
    
                val selectedRun = runs[which]
    
                val id = selectedRun
                    .substringAfter('#')
                    .substringBefore(' ')
                    .toLong()
    
                val results = db.results(id)
    
                val message = buildString {
                    append("SCAN DETAILS\n")
                    append("==============================\n\n")
    
                    append(selectedRun)
                    append("\n\n")
    
                    append("RESULTS\n")
                    append("==============================\n\n")
    
                    append(
                        results
                            .joinToString("\n\n")
                            .ifEmpty { "No matches" }
                    )
                }
    
                AlertDialog.Builder(this)
                    .setTitle("Scan #$id")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
            .show()
    }

    private fun validateConditions(
        conditions: List<Condition>
    ): String? {
    
        fun requiredParams(indicator: String): Int {
            return when (indicator) {
                "Close" -> 0
                "EMA" -> 1
                "HMA" -> 1
                "RSI" -> 1
                "EMA of RSI" -> 2
        
                "MACD" -> 3
                "MACD Signal" -> 3
                "MACD Histogram" -> 3
        
                "Stoch RSI" -> 2
                "Stoch RSI %K" -> 3
                "Stoch RSI %D" -> 4
        
                "Numeric Value" -> 1
        
                "Reverse RSI Level 40" -> 2
                "Reverse RSI Level 50" -> 2
                "Reverse RSI Level 60" -> 2
        
                "Reverse Stoch RSI Level 20" -> 2
                "Reverse Stoch RSI Level 50" -> 2
                "Reverse Stoch RSI Level 80" -> 2
        
                "Reverse Stoch RSI %K" -> 4
                "Reverse Stoch RSI %D" -> 5
        
                else -> 0
            }
        }
    
        fun validParams(
            indicator: String,
            params: List<Double>
        ): Boolean {
            val required = requiredParams(indicator)
    
            if (params.size != required) {
                return false
            }
    
            return params.all {
                it.isFinite() && it > 0.0
            }
        }
    
        for ((index, c) in conditions.withIndex()) {
    
            val number = index + 1
    
            if (!validParams(c.leftIndicator, c.leftParams)) {
                return "Condition $number:\n" +
                        "${c.leftIndicator} requires " +
                        "${requiredParams(c.leftIndicator)} " +
                        "valid parameter(s)."
            }
    
            if (c.rightIndicator == "Number") {
                if (!c.rightTarget.isFinite()) {
                    return "Condition $number:\nNumber target is invalid."
                }
            } else {
                if (!validParams(c.rightIndicator, c.rightParams)) {
                    return "Condition $number:\n" +
                            "${c.rightIndicator} requires " +
                            "${requiredParams(c.rightIndicator)} " +
                            "valid parameter(s)."
                }
            }
    
            if (
                c.comparator == "Near By" ||
                c.comparator == "May Go To Cross Above" ||
                c.comparator == "May Go To Cross Below"
            ) {
                if (!c.rangePct.isFinite() || c.rangePct <= 0.0) {
                    return "Condition $number:\nRange % must be greater than 0."
                }
            }
        }
    
        return null
    }
    private fun verticalScroll(): ViewGroup {
        val scroll = android.widget.ScrollView(this)
    
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 8, 18, 18)
        }
    
        scroll.addView(
            inner,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    
        return object : android.widget.FrameLayout(this) {
            init {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(scroll)
            }
    
            override fun addView(
                child: View?,
                params: ViewGroup.LayoutParams?
            ) {
                if (child == null || child === scroll) {
                    super.addView(child, params)
                } else {
                    inner.addView(child, params)
                }
            }
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
        
                val l =
                    lp.text.toString()
                        .split(',')
                        .mapNotNull {
                            it.trim().toDoubleOrNull()
                        }
        
                val r =
                    rp.text.toString()
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
