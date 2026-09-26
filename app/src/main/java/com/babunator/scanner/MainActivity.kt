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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

        private val indicators = listOf(
            "Close",
            "EMA",
            "HMA",
            "RSI",
            "EMA of RSI",
            "SMA of RSI",
            "MACD",
            "MACD Signal",
            "MACD Histogram",
            "Stoch RSI",
            "Stoch RSI %K",
            "Stoch RSI %D",
            "Numeric Value",
        
            "Reverse RSI",
            "Reverse SMA of RSI",
            "Reverse RSI Level",
        
            "Reverse Stoch RSI Level",
            "Reverse Stoch RSI %K",
            "Reverse Stoch RSI %D",
        
            "Reverse MACD",
            "Reverse MACD Signal",
            "Reverse MACD Zero Line"
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
    private var updateMonitorJob: Job? = null
    private var scanMonitorJob: Job? = null
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
                styleButton(this)    
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

        styleButton(this)

        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
    }
}

private fun styleButton(button: Button) {

    val night =
        (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

    val normalBackground =
        if (night) {
            android.graphics.Color.rgb(24, 58, 34)
        } else {
            android.graphics.Color.rgb(225, 239, 229)
        }

    val pressedBackground =
        if (night) {
            android.graphics.Color.rgb(35, 92, 50)
        } else {
            android.graphics.Color.rgb(199, 224, 207)
        }

    val disabledBackground =
        if (night) {
            android.graphics.Color.rgb(27, 38, 30)
        } else {
            android.graphics.Color.rgb(222, 226, 223)
        }

    val normalText =
        if (night) {
            android.graphics.Color.rgb(190, 255, 205)
        } else {
            android.graphics.Color.rgb(25, 67, 39)
        }

    val pressedText =
        if (night) {
            android.graphics.Color.WHITE
        } else {
            android.graphics.Color.rgb(18, 55, 31)
        }

    val disabledText =
        if (night) {
            android.graphics.Color.rgb(105, 130, 111)
        } else {
            android.graphics.Color.rgb(105, 112, 108)
        }

    button.backgroundTintList =
        android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            ),
            intArrayOf(
                disabledBackground,
                pressedBackground,
                normalBackground
            )
        )

    button.setTextColor(
        android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            ),
            intArrayOf(
                disabledText,
                pressedText,
                normalText
            )
        )
    )
}

    // ---------------------------------------------------------
    // SCREEN 1 : UPDATE DATA
    // ---------------------------------------------------------
private fun showUpdateScreen() {
    updateMonitorJob?.cancel()
    scanMonitorJob?.cancel()
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
    var lastUpdatedText: TextView
    // ---------------------------------------------------------
    // MARKET DATA CARD
    // ---------------------------------------------------------

    val marketCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 18, 18, 18)
        background = cardBackground()
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
        textSize = 14f
        minHeight = 52
        minimumHeight = 52
        
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
            lastUpdatedText = TextView(this).apply {
            text = if (updateStatus?.lastUpdateTime != null) {
                "Last updated: ${updateStatus.lastUpdateTime}"
            } else {
                "Last updated: —"
            }
            textSize = 15f
            setPadding(0, 8, 0, 0)
            visibility = View.VISIBLE
    }

    marketCard.addView(lastUpdatedText, lp())

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
        background = cardBackground()
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
                    setPadding(0, 4, 0, 8)
                },
                lp()
        )

                autoCard.addView(
                Button(this).apply {
                    val schedulePrefs =
                        getSharedPreferences(
                            "schedule",
                            MODE_PRIVATE
                        )

                    val hasSchedule =
                        schedulePrefs.contains("h") &&
                        schedulePrefs.contains("m")

                    val savedHour =
                        schedulePrefs.getInt("h", 16)

                    val savedMinute =
                        schedulePrefs.getInt("m", 30)

                    text =
                        if (hasSchedule) {
                            val amPm =
                                if (savedHour >= 12) "PM" else "AM"

                            val displayHour =
                                when {
                                    savedHour == 0 -> 12
                                    savedHour > 12 -> savedHour - 12
                                    else -> savedHour
                                }

                            String.format(
                                Locale.ENGLISH,
                                "AUTO UPDATE: ON • %02d:%02d %s",
                                displayHour,
                                savedMinute,
                                amPm
                            )
                        } else {
                            "AUTO UPDATE: OFF • SET TIME"
                        }

                    textSize = 14f
                    minHeight = 52
                    minimumHeight = 52

                    setOnClickListener {
                        TimePickerDialog(
                            this@MainActivity,
                            { _, hourOfDay, minute ->

                                Scheduler.schedule(
                                    this@MainActivity,
                                    hourOfDay,
                                    minute
                                )

                                val amPm =
                                    if (hourOfDay >= 12) "PM" else "AM"

                                val displayHour =
                                    when {
                                        hourOfDay == 0 -> 12
                                        hourOfDay > 12 -> hourOfDay - 12
                                        else -> hourOfDay
                                    }

                                text =
                                    String.format(
                                        Locale.ENGLISH,
                                        "AUTO UPDATE: ON • %02d:%02d %s",
                                        displayHour,
                                        minute,
                                        amPm
                                    )

                                Toast.makeText(
                                    this@MainActivity,
                                    "Daily auto update scheduled.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            savedHour,
                            savedMinute,
                            false
                        ).show()
                    }
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
        background = cardBackground()
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

    updateMonitorJob = lifecycleScope.launch {
    while (isActive) {

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

                    val completed =
                        current.total > 0 &&
                        current.processed >= current.total &&
                        current.failed == 0
                
                    progressText.text =
                        "${current.processed} / ${current.total}"
                
                    statsText.text =
                        "Successful: ${current.successful}\n" +
                        "Failed: ${current.failed}\n" +
                        "Retry: ${current.retryCount}"
                
                    lastUpdatedText.text =
                        "Last updated: ${current.lastUpdateTime ?: "—"}"
                
                    progressText.visibility =
                        if (completed) View.GONE else View.VISIBLE
                
                    statsText.visibility =
                        if (completed) View.GONE else View.VISIBLE
                
                    status.text =
                        if (completed) {
                            "Data status: Up to date"
                        } else {
                            "Data status: ${current.processed} / ${current.total}"
                        }
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
    updateMonitorJob?.cancel()
    scanMonitorJob?.cancel()
    content.removeAllViews()
    rows.clear()

    val root = verticalScroll()

    root.addView(title("Scanner"))

    status = TextView(this).apply {
        text = "Ready"
        textSize = 16f
        setPadding(0, 0, 0, 12)
    }

    root.addView(status, lp())

    val timeframeOptions = listOf(
        "Daily",
        "Weekly",
        "Monthly"
    )

    val selectedTimeframes =
        mutableListOf("Daily")

    val timeframeButton =
        Button(this).apply {
            text = "Daily"
            styleButton(this)
        }

    timeframeButton.setOnClickListener {

        val checked =
            timeframeOptions.map {
                selectedTimeframes.contains(it)
            }.toBooleanArray()

        AlertDialog.Builder(this@MainActivity)
            .setTitle("Select Timeframes")
            .setMultiChoiceItems(
                timeframeOptions.toTypedArray(),
                checked
            ) { _, which, isChecked ->

                val value =
                    timeframeOptions[which]

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

    val logicHolder =
        arrayOf("AND")

    val conditionEditor =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

    root.addView(conditionEditor, lp())

    var currentIndex = 0

    fun renderCondition() {

        conditionEditor.removeAllViews()

        val row = rows[currentIndex]

        conditionEditor.addView(
            label("Condition ${currentIndex + 1}"),
            lp()
        )

        if (currentIndex > 0) {

            conditionEditor.addView(
                TextView(this).apply {
                    text =
                        "Logic: ${logicHolder[0]} between conditions"
                    textSize = 13f
                    setPadding(0, 0, 0, 10)
                },
                lp()
            )
        }

        val leftLabel =
            label("Value A / Indicator")

        conditionEditor.addView(
            leftLabel,
            lp()
        )

        val left =
            Spinner(this).apply {
                adapter = spinner(indicators)

                setSelection(
                    indicators.indexOf(
                        row.leftIndicator
                    ).coerceAtLeast(0)
                )
            }

        conditionEditor.addView(left, lp())

        val leftParamsContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        conditionEditor.addView(
            leftParamsContainer,
            lp()
        )

        val opLabel =
            label("Comparator")

        conditionEditor.addView(
            opLabel,
            lp()
        )

        val op =
            Spinner(this).apply {
                adapter = spinner(comparators)

                setSelection(
                    comparators.indexOf(
                        row.comparator
                    ).coerceAtLeast(0)
                )
            }

        conditionEditor.addView(op, lp())

        val rightLabel =
            label("Value B / Indicator")

        conditionEditor.addView(
            rightLabel,
            lp()
        )

        val rightOptions =
            listOf("Number") +
                    indicators.filter {
                        it != "Numeric Value"
                    }

        val right =
            Spinner(this).apply {
                adapter = spinner(rightOptions)

                setSelection(
                    rightOptions.indexOf(
                        row.rightIndicator
                    ).coerceAtLeast(0)
                )
            }

        conditionEditor.addView(right, lp())

        val rightParamsContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        conditionEditor.addView(
            rightParamsContainer,
            lp()
        )

        val targetContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        conditionEditor.addView(
            targetContainer,
            lp()
        )

        val rangeContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        conditionEditor.addView(
            rangeContainer,
            lp()
        )

        fun createParameterFields(
            container: LinearLayout,
            indicator: String,
            existing: MutableList<Double>,
            onChanged: (List<Double>) -> Unit
        ) {

            container.removeAllViews()

            if (indicator == "Close") {
                onChanged(emptyList())
                return
            }

            val defaults =
                parameterDefaults(indicator)

            if (existing.size != defaults.size) {
                existing.clear()
                existing.addAll(defaults)
            }

            defaults.forEachIndexed { index, default ->

                val field =
                    text(
                        "${parameterName(indicator, index)}  (default $default)",
                        true
                    )

                field.setText(
                    formatInputNumber(
                        existing[index]
                    )
                )

                field.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val value =
                            field.text.toString()
                                .toDoubleOrNull()

                        if (value != null) {
                            if (index < existing.size) {
                                existing[index] = value
                            }
                            onChanged(existing.toList())
                        }
                    }
                }

                field.addTextChangedListener(
                    object : android.text.TextWatcher {

                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                        ) {
                        }

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                        ) {
                            val value =
                                s?.toString()
                                    ?.toDoubleOrNull()

                            if (value != null &&
                                index < existing.size
                            ) {
                                existing[index] = value
                                onChanged(existing.toList())
                            }
                        }

                        override fun afterTextChanged(
                            s: android.text.Editable?
                        ) {
                        }
                    }
                )

                container.addView(
                    label(
                        parameterName(
                            indicator,
                            index
                        )
                    ),
                    lp()
                )

                container.addView(
                    field,
                    lp()
                )
            }
        }

        fun renderRight() {

            rightParamsContainer.removeAllViews()
            targetContainer.removeAllViews()

            if (row.rightIndicator == "Number") {

                val target =
                    text(
                        "Number value",
                        true
                    )

                target.setText(
                    formatInputNumber(
                        row.rightTarget
                    )
                )

                target.addTextChangedListener(
                    object :
                        android.text.TextWatcher {

                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                        ) {
                        }

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                        ) {
                            row.rightTarget =
                                s?.toString()
                                    ?.toDoubleOrNull()
                                    ?: 0.0
                        }

                        override fun afterTextChanged(
                            s: android.text.Editable?
                        ) {
                        }
                    }
                )

                targetContainer.addView(
                    label("Number"),
                    lp()
                )

                targetContainer.addView(
                    target,
                    lp()
                )

            } else {

                createParameterFields(
                    rightParamsContainer,
                    row.rightIndicator,
                    row.rightParams
                ) {
                    row.rightParams.clear()
                    row.rightParams.addAll(it)
                }
            }
        }

        fun renderRange() {

            rangeContainer.removeAllViews()

            val needsRange =
                row.comparator == "Near By" ||
                row.comparator ==
                    "May Go To Cross Above" ||
                row.comparator ==
                    "May Go To Cross Below"

            if (!needsRange) {
                row.rangePct = 1.0
                return
            }

            val range =
                text(
                    "Range %",
                    true
                )

            range.setText(
                formatInputNumber(
                    row.rangePct
                )
            )

            range.addTextChangedListener(
                object :
                    android.text.TextWatcher {

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        row.rangePct =
                            s?.toString()
                                ?.toDoubleOrNull()
                                ?: 0.0
                    }

                    override fun afterTextChanged(
                        s: android.text.Editable?
                    ) {
                    }
                }
            )

            rangeContainer.addView(
                label("Allowed range (%)"),
                lp()
            )

            rangeContainer.addView(
                range,
                lp()
            )
        }

        left.setOnItemSelectedListener(
            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    row.leftIndicator =
                        indicators[position]

                    createParameterFields(
                        leftParamsContainer,
                        row.leftIndicator,
                        row.leftParams
                    ) {
                        row.leftParams.clear()
                        row.leftParams.addAll(it)
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }
        )

        op.setOnItemSelectedListener(
            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    row.comparator =
                        comparators[position]

                    renderRange()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }
        )

        right.setOnItemSelectedListener(
            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    row.rightIndicator =
                        rightOptions[position]

                    renderRight()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }
        )

        createParameterFields(
            leftParamsContainer,
            row.leftIndicator,
            row.leftParams
        ) {
            row.leftParams.clear()
            row.leftParams.addAll(it)
        }

        renderRight()
        renderRange()

        val actionRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

        if (currentIndex > 0) {

            val previous =
                Button(this).apply {
                    text = "← PREVIOUS"
                    styleButton(this)

                    setOnClickListener {
                        currentIndex--
                        renderCondition()
                    }
                }

            actionRow.addView(
                previous,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }

        if (currentIndex == rows.lastIndex) {

            val add =
                Button(this).apply {
                    text = "+ ADD MORE CONDITION"
                    styleButton(this)

                    setOnClickListener {

                        AlertDialog.Builder(
                            this@MainActivity
                        )
                            .setTitle(
                                "Logic for next condition"
                            )
                            .setItems(
                                arrayOf(
                                    "AND",
                                    "OR"
                                )
                            ) { _, which ->

                                logicHolder[0] =
                                    if (which == 0) {
                                        "AND"
                                    } else {
                                        "OR"
                                    }

                                rows.add(
                                    Row()
                                )

                                currentIndex =
                                    rows.lastIndex

                                renderCondition()
                            }
                            .setNegativeButton(
                                "CANCEL",
                                null
                            )
                            .show()
                    }
                }

            actionRow.addView(
                add,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }

        conditionEditor.addView(
            actionRow,
            lp()
        )
    }

    rows.add(Row())
    renderCondition()

    val scanButton =
        Button(this).apply {
            text = "SCAN NOW"
            minHeight = 52
            minimumHeight = 52
            styleButton(this)

            setOnClickListener {

                val conditions =
                    rows.map {
                        it.toCondition()
                    }

                val error =
                    validateConditions(
                        conditions
                    )

                if (error != null) {

                    AlertDialog.Builder(
                        this@MainActivity
                    )
                        .setTitle(
                            "Invalid Scan Condition"
                        )
                        .setMessage(error)
                        .setPositiveButton(
                            "OK",
                            null
                        )
                        .show()

                    return@setOnClickListener
                }

                val cfg =
                    ScanConfig(
                        timeframe =
                            selectedTimeframes.first(),
                        logic =
                            logicHolder[0],
                        conditions =
                            conditions,
                        timeframes =
                            selectedTimeframes.toList()
                    )

                ScanConfigStore.save(
                    this@MainActivity,
                    cfg
                )

                val request =
                    OneTimeWorkRequestBuilder<ScanWorker>()
                        .setInputData(
                            Data.Builder()
                                .putString(
                                    "timeframe",
                                    cfg.timeframe
                                )
                                .build()
                        )
                        .build()

                WorkManager
                    .getInstance(this@MainActivity)
                    .enqueueUniqueWork(
                        "nse_scan",
                        ExistingWorkPolicy.REPLACE,
                        request
                    )

                text = "SCAN IN PROGRESS"
                isEnabled = false
                status.text =
                    "Scan in progress..."
            }
        }

    root.addView(
        scanButton,
        lp()
    )

    val saveButton =
        Button(this).apply {
            text = "SAVE SCAN"
            styleButton(this)

            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Save Scan will be connected in the Saved Scans step.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    root.addView(
        saveButton,
        lp()
    )

    root.addView(
        TextView(this).apply {
            text =
                "\nParameters are shown automatically for the selected indicator.\n" +
                "Values are prefilled with the default settings."
            textSize = 14f
            setPadding(0, 10, 0, 18)
        },
        lp()
    )

    scanMonitorJob =
        lifecycleScope.launch {

            while (isActive) {

                val workInfo =
                    kotlinx.coroutines.withContext(
                        kotlinx.coroutines.Dispatchers.IO
                    ) {
                        WorkManager
                            .getInstance(
                                this@MainActivity
                            )
                            .getWorkInfosForUniqueWork(
                                "nse_scan"
                            )
                            .get()
                            .firstOrNull()
                    }

                if (workInfo != null) {

                    when {
                        workInfo.state ==
                                androidx.work.WorkInfo.State.RUNNING -> {

                            scanButton.text =
                                "SCAN IN PROGRESS"

                            scanButton.isEnabled =
                                false

                            status.text =
                                "Scan in progress..."
                        }

                        workInfo.state ==
                                androidx.work.WorkInfo.State.SUCCEEDED -> {

                            scanButton.text =
                                "SCAN NOW"

                            scanButton.isEnabled =
                                true

                            status.text =
                                "Scan completed."
                        }

                        workInfo.state ==
                                androidx.work.WorkInfo.State.FAILED -> {

                            scanButton.text =
                                "SCAN NOW"

                            scanButton.isEnabled =
                                true

                            status.text =
                                "Scan failed. You can try again."
                        }

                        workInfo.state ==
                                androidx.work.WorkInfo.State.CANCELLED -> {

                            scanButton.text =
                                "SCAN NOW"

                            scanButton.isEnabled =
                                true

                            status.text =
                                "Scan cancelled."
                        }
                    }
                }

                delay(500)
            }
        }

    content.addView(root)
}

private fun addConditionRow(
    root: ViewGroup,
    idx: Int
) {
    // Scanner conditions are now rendered
    // one at a time by showScannerScreen().
}

private fun parameterDefaults(
    indicator: String
): List<Double> {

    return when (indicator) {

        "Close" ->
            emptyList()

        "EMA",
        "HMA",
        "RSI" ->
            listOf(14.0)

        "EMA of RSI",
        "SMA of RSI" ->
            listOf(
                14.0,
                14.0
            )

        "MACD",
        "MACD Signal",
        "MACD Histogram" ->
            listOf(
                12.0,
                26.0,
                9.0
            )

        "Stoch RSI" ->
            listOf(
                14.0,
                14.0
            )

        "Stoch RSI %K" ->
            listOf(
                14.0,
                14.0,
                3.0
            )

        "Stoch RSI %D" ->
            listOf(
                14.0,
                14.0,
                3.0,
                3.0
            )

        "Numeric Value" ->
            listOf(0.0)

        "Reverse RSI",
        "Reverse SMA of RSI" ->
            listOf(
                14.0,
                14.0
            )

        "Reverse RSI Level" ->
            listOf(
                14.0,
                14.0,
                50.0
            )

        "Reverse Stoch RSI Level" ->
            listOf(
                14.0,
                14.0,
                50.0,
                9.0
            )

        "Reverse Stoch RSI %K" ->
            listOf(
                14.0,
                14.0,
                3.0
            )

        "Reverse Stoch RSI %D" ->
            listOf(
                14.0,
                14.0,
                3.0,
                3.0
            )

        "Reverse MACD",
        "Reverse MACD Signal",
        "Reverse MACD Zero Line" ->
            listOf(
                12.0,
                26.0,
                9.0,
                1.0
            )

        else ->
            emptyList()
    }
}

private fun parameterName(
    indicator: String,
    index: Int
): String {

    return when (indicator) {

        "EMA",
        "HMA" ->
            "Length"

        "RSI" ->
            "RSI Length"

        "EMA of RSI",
        "SMA of RSI" ->
            if (index == 0) {
                "RSI Length"
            } else {
                "SMA / EMA Length"
            }

        "MACD",
        "MACD Signal",
        "MACD Histogram" ->
            when (index) {
                0 -> "Fast Length"
                1 -> "Slow Length"
                else -> "Signal Length"
            }

        "Stoch RSI" ->
            if (index == 0) {
                "RSI Length"
            } else {
                "Stoch Length"
            }

        "Stoch RSI %K" ->
            when (index) {
                0 -> "RSI Length"
                1 -> "Stoch Length"
                else -> "K Length"
            }

        "Stoch RSI %D" ->
            when (index) {
                0 -> "RSI Length"
                1 -> "Stoch Length"
                2 -> "K Length"
                else -> "D Length"
            }

        "Numeric Value" ->
            "Value"

        "Reverse RSI",
        "Reverse SMA of RSI" ->
            if (index == 0) {
                "RSI Length"
            } else {
                "Smoothing / SMA Length"
            }

        "Reverse RSI Level" ->
            when (index) {
                0 -> "RSI Length"
                1 -> "Smoothing Length"
                else -> "Target Level"
            }

        "Reverse Stoch RSI Level" ->
            when (index) {
                0 -> "RSI Length"
                1 -> "Stoch Length"
                2 -> "Target Level"
                else -> "Smooth Length"
            }

        "Reverse Stoch RSI %K" ->
            when (index) {
                0 -> "RSI Length"
                1 -> "Stoch Length"
                else -> "K Length"
            }

        "Reverse Stoch RSI %D" ->
            when (index) {
                0 -> "RSI Length"
                1 -> "Stoch Length"
                2 -> "K Length"
                else -> "D Length"
            }

        "Reverse MACD",
        "Reverse MACD Signal",
        "Reverse MACD Zero Line" ->
            when (index) {
                0 -> "Fast Length"
                1 -> "Slow Length"
                2 -> "Signal Length"
                else -> "Smooth Length"
            }

        else ->
            "Parameter ${index + 1}"
    }
}

private fun formatInputNumber(
    value: Double
): String {

    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        value.toString()
    }
}
    // ---------------------------------------------------------
    // SCREEN 3 : SAVED SCANS / TRACKING
    // ---------------------------------------------------------

    private fun showSavedScreen() {
        updateMonitorJob?.cancel()
        scanMonitorJob?.cancel()
        
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
            "SMA of RSI" -> 2

            "MACD" -> 3
            "MACD Signal" -> 3
            "MACD Histogram" -> 3

            "Stoch RSI" -> 2
            "Stoch RSI %K" -> 3
            "Stoch RSI %D" -> 4

            "Numeric Value" -> 1

            "Reverse RSI" -> 2
            "Reverse SMA of RSI" -> 2
            "Reverse RSI Level" -> 3

            "Reverse Stoch RSI Level" -> 4
            "Reverse Stoch RSI %K" -> 3
            "Reverse Stoch RSI %D" -> 4

            "Reverse MACD" -> 4
            "Reverse MACD Signal" -> 4
            "Reverse MACD Zero Line" -> 4

            else -> 0
        }
    }

    fun validParams(
        indicator: String,
        params: List<Double>
    ): Boolean {

        val required =
            requiredParams(indicator)

        if (params.size != required) {
            return false
        }

        return params.all {
            it.isFinite() &&
                    if (indicator == "Numeric Value") {
                        true
                    } else {
                        it > 0.0
                    }
        }
    }

    for ((index, c) in conditions.withIndex()) {

        val number =
            index + 1

        if (
            !validParams(
                c.leftIndicator,
                c.leftParams
            )
        ) {
            return "Condition $number:\n" +
                    "${c.leftIndicator} requires " +
                    "${requiredParams(c.leftIndicator)} " +
                    "valid parameter(s)."
        }

        if (c.rightIndicator == "Number") {

            if (!c.rightTarget.isFinite()) {
                return "Condition $number:\n" +
                        "Number target is invalid."
            }

        } else {

            if (
                !validParams(
                    c.rightIndicator,
                    c.rightParams
                )
            ) {
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

            if (
                !c.rangePct.isFinite() ||
                c.rangePct <= 0.0
            ) {
                return "Condition $number:\n" +
                        "Range % must be greater than 0."
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
        
        private fun cardBackground(): android.graphics.drawable.GradientDrawable {
            val typedValue = android.util.TypedValue()
        
            theme.resolveAttribute(
                android.R.attr.colorBackground,
                typedValue,
                true
            )
        
            return android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20f
                setColor(
                    typedValue.data
                )
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
            var leftIndicator: String = "Close",
            var leftParams: MutableList<Double> =
                mutableListOf(),
        
            var comparator: String = "Above",
        
            var rightIndicator: String = "Number",
            var rightParams: MutableList<Double> =
                mutableListOf(),
        
            var rightTarget: Double = 0.0,
        
            var rangePct: Double = 1.0
        ) {
        
            fun toCondition(): Condition {
        
                return Condition(
                    leftIndicator = leftIndicator,
                    leftParams = leftParams.toList(),
                    comparator = comparator,
                    rightIndicator = rightIndicator,
                    rightParams = rightParams.toList(),
                    rightTarget = rightTarget,
                    rangePct = rangePct
                )
            }
        }    
  }
