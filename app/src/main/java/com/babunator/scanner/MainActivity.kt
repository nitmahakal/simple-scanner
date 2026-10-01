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

    private lateinit var updateNavButton: Button
    private lateinit var scannerNavButton: Button
    private lateinit var savedNavButton: Button

    private var updateMonitorJob: Job? = null
    private var scanMonitorJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)

        val retentionPrefs =
            getSharedPreferences(
                "app_settings",
                MODE_PRIVATE
            )

        if (
            retentionPrefs.getBoolean(
                "auto_delete_scans",
                true
            )
        ) {
            AppDb(this).deleteOldNormalScans(
                retentionPrefs.getInt(
                    "scan_retention_days",
                    7
                )
            )
        }

        buildMainLayout()
        showUpdateScreen()
    }
private fun buildMainLayout() {
    val main = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

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

        updateNavButton = navButton("UPDATE DATA") {
            showUpdateScreen()
        }
        
        scannerNavButton = navButton("SCANNER") {
            showScannerScreen()
        }
        
        savedNavButton = navButton("SAVED / TRACKING") {
            showSavedScreen()
        }
        
        navigation.addView(updateNavButton)
        navigation.addView(scannerNavButton)
        navigation.addView(savedNavButton)

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
private fun setActiveNavigation(active: Button) {
    val buttons = listOf(
        updateNavButton,
        scannerNavButton,
        savedNavButton
    )

    buttons.forEach {
        styleButton(it)
        it.alpha = if (it === active) 1.0f else 0.55f
    }
}


private fun styleButton(button: Button) {

    val night =
        (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

    val normalBackground =
        if (night) {
            android.graphics.Color.rgb(35, 155, 82)   // #239B52
        } else {
            android.graphics.Color.rgb(23, 107, 58)   // #176B3A
        }

    val pressedBackground =
        if (night) {
            android.graphics.Color.rgb(45, 191, 99)   // #2DBF63
        } else {
            android.graphics.Color.rgb(14, 77, 41)    // #0E4D29
        }

    val disabledBackground =
        if (night) {
            android.graphics.Color.rgb(45, 55, 48)
        } else {
            android.graphics.Color.rgb(205, 213, 208)
        }

    val normalText =
        android.graphics.Color.WHITE

    val pressedText =
        android.graphics.Color.WHITE

    val disabledText =
        if (night) {
            android.graphics.Color.rgb(130, 145, 135)
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

    button.setTypeface(
        button.typeface,
        android.graphics.Typeface.BOLD
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

    val screenHeader = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 4, 0, 10)
    }

    screenHeader.addView(
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

    screenHeader.addView(
        Button(this).apply {
            text = "⚙"
            styleButton(this)
            setOnClickListener {
                showSettings()
            }
        }
    )

    root.addView(screenHeader, lp())

    setActiveNavigation(updateNavButton)

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

    marketCard.setPadding(18, 18, 18, 18)
        
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
            text = "AUTO SCHEDULE"
            textSize = 16f
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

        setActiveNavigation(scannerNavButton)
        
                root.addView(
            TextView(this).apply {
                text = "SCANNER"
                textSize = 16f
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
                setPadding(0, 8, 0, 14)
            },
            lp()
        )

        status = TextView(this).apply {
            text = "Ready"
            textSize = 15f
            setPadding(0, 0, 0, 8)
        }

        root.addView(status, lp())
val timeframeOptions = listOf(
    "Daily",
    "Weekly",
    "Monthly"
)

var selectedTimeframe = "Daily"

val selectedTimeframes =
    mutableListOf("Daily")

val timeframeButton =
    Button(this).apply {

        fun updateText() {
            text =
                if (selectedTimeframes.size == 1) {
                    "TIMEFRAME: ${selectedTimeframes[0]} ▼"
                } else {
                    "TIMEFRAMES: ${selectedTimeframes.size} SELECTED ▼"
                }
        }

        updateText()
        textSize = 14f
        minHeight = 52
        minimumHeight = 52

        styleButton(this)

        setOnClickListener {

            val checked =
                BooleanArray(timeframeOptions.size) { index ->
                    selectedTimeframes.contains(
                        timeframeOptions[index]
                    )
                }

            AlertDialog.Builder(
                this@MainActivity
            )
                .setTitle("Select Timeframes")
                .setMultiChoiceItems(
                    timeframeOptions.toTypedArray(),
                    checked
                ) { _, which, isChecked ->

                    val timeframe =
                        timeframeOptions[which]

                    if (isChecked) {
                        if (!selectedTimeframes.contains(timeframe)) {
                            selectedTimeframes.add(timeframe)
                        }
                    } else {
                        selectedTimeframes.remove(timeframe)
                    }
                }
                .setPositiveButton("DONE") { _, _ ->

                    if (selectedTimeframes.isEmpty()) {
                        selectedTimeframes.add("Daily")
                    }

                    selectedTimeframe =
                        selectedTimeframes.first()

                    updateText()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }
    }

root.addView(
    timeframeButton,
    lp()
)
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
        text = "TRACK SCAN"
        styleButton(this)

        setOnClickListener {

            val db = AppDb(this@MainActivity)

            val latestRun =
                db.recentRuns(1)
                    .firstOrNull()

            if (latestRun == null) {
                Toast.makeText(
                    this@MainActivity,
                    "Run a scan first.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val runId =
                latestRun
                    .substringAfter("#")
                    .substringBefore(" ")
                    .toLongOrNull()

            if (runId == null) {
                Toast.makeText(
                    this@MainActivity,
                    "Scan result could not be read.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val config =
                ScanConfigStore.load(
                    this@MainActivity
                )

            val matches =
                db.resultMatches(runId)

            if (matches.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    "This scan has no matched stocks.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val savedId =
                db.saveSavedScan(
                    configJson =
                        ScanConfigStore.toJson(config),
                    timeframe =
                        config.timeframes
                            .ifEmpty {
                                listOf(config.timeframe)
                            }
                            .joinToString(", "),
                    conditions =
                        db.runConditions(runId),
                    matches =
                        matches,
                    autoTrack = false
                )

            Toast.makeText(
                this@MainActivity,
                "Scan added to User Track Scans.",
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
                        
                            val latestRun =
                                AppDb(this@MainActivity)
                                    .recentRuns(1)
                                    .firstOrNull()
                        
                            if (latestRun != null) {
                        
                                val runId =
                                    latestRun
                                        .substringAfter("#")
                                        .substringBefore(" ")
                                        .toLongOrNull()
                        
                                if (runId != null) {
                                    showScanResultScreen(runId)
                                }
                            }
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
private fun showScanResultScreen(runId: Long) {

    scanMonitorJob?.cancel()
    updateMonitorJob?.cancel()

    content.removeAllViews()

    setActiveNavigation(scannerNavButton)

    val root = verticalScroll()

    val db = AppDb(this)

    val runHeader =
        db.recentRuns(30)
            .firstOrNull {
                it.startsWith("#$runId ")
            }

    root.addView(
        TextView(this).apply {
            text = "SCAN RESULT"
            textSize = 20f
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
            setPadding(0, 8, 0, 14)
        },
        lp()
    )

        root.addView(
        TextView(this).apply {
            text = runHeader ?: "#$runId"
            textSize = 14f
            setPadding(0, 0, 0, 12)
        },
        lp()
    )

    val config = ScanConfigStore.load(this)

    root.addView(
        TextView(this).apply {
            text = "Timeframes: " +
                    config.timeframes
                        .ifEmpty { listOf(config.timeframe) }
                        .joinToString(", ")
            textSize = 14f
            setPadding(0, 0, 0, 12)
        },
        lp()
    )

    root.addView(
        TextView(this).apply {
            text = db.runConditions(runId)
            textSize = 14f
            setPadding(0, 0, 0, 14)
        },
        lp()
    )

    root.addView(
        TextView(this).apply {
            text = "MATCHED STOCKS"
            textSize = 16f
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
            setPadding(0, 8, 0, 8)
        },
        lp()
    )

    val matches = db.resultMatches(runId)

    if (matches.isEmpty()) {

        root.addView(
            TextView(this).apply {
                text = "No stocks matched this scan."
                textSize = 15f
                setPadding(0, 8, 0, 16)
            },
            lp()
        )

    } else {

        val indicatorNames =
            linkedSetOf<String>()

        val parsedRows =
            matches.map { match ->

                val values =
                    linkedMapOf<String, String>()

                match.note
                    .substringAfter("\n\n", "")
                    .lines()
                    .forEach { line ->

                        if (line.startsWith("Close:")) {
                            return@forEach
                        }

                        val separator =
                            line.indexOf(": ")

                        if (separator > 0) {

                            val name =
                                line.substring(
                                    0,
                                    separator
                                )

                            val value =
                                line.substring(
                                    separator + 2
                                )

                            values[name] = value
                            indicatorNames.add(name)
                        }
                    }

                match to values
            }

        val columns =
            mutableListOf<String>().apply {
                add("STOCK")
                add("TIMEFRAME")
                add("LTP")
                addAll(indicatorNames)
            }

        val horizontalScroll =
            HorizontalScrollView(this).apply {
                isFillViewport = false
            }

        val table =
            TableLayout(this).apply {
                isStretchAllColumns = false
                isShrinkAllColumns = false
                setPadding(0, 0, 0, 16)
            }

        fun cell(
            value: String,
            header: Boolean = false
        ): TextView {

            return TextView(this).apply {

                text = value
                textSize = if (header) 13f else 14f

                if (header) {
                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )
                }

                setPadding(
                    18,
                    12,
                    18,
                    12
                )

                setSingleLine(true)

                layoutParams =
                    TableRow.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            }
        }

        val headerRow =
            TableRow(this)

        columns.forEach { column ->
            headerRow.addView(
                cell(
                    column,
                    true
                )
            )
        }

        table.addView(headerRow)

        parsedRows.forEach { (match, values) ->

            val row =
                TableRow(this)

            row.addView(
                cell(match.symbol)
            )

            row.addView(
                cell(match.timeframe)
            )

            row.addView(
                cell(
                    String.format(
                        Locale.US,
                        "%.2f",
                        match.close
                    )
                )
            )

            indicatorNames.forEach { name ->

                row.addView(
                    cell(
                        values[name] ?: "—"
                    )
                )
            }

            table.addView(row)
        }

        horizontalScroll.addView(table)

        root.addView(
            horizontalScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    root.addView(
        Button(this).apply {
            text = "← BACK TO SCANNER"
            styleButton(this)

            setOnClickListener {
                showScannerScreen()
            }
        },
        lp()
    )

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
            content.removeAllViews()
        
            val root = verticalScroll()
        
            setActiveNavigation(savedNavButton)
        
            root.addView(
                TextView(this).apply {
                    text = "SAVED / TRACKING"
                    textSize = 22f
                    setPadding(0, 8, 0, 14)
                },
                lp()
            )
        
            val db = AppDb(this)
            val savedScans = db.getSavedScans()
            val historyRuns = db.recentRuns()
        
            // 1. SCAN HISTORY
            val historyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 18, 18, 18)
                background = cardBackground()
            }
        
            historyCard.addView(
                TextView(this).apply {
                    text = "SCAN HISTORY"
                    textSize = 18f
                },
                lp()
            )
        
            historyCard.addView(
                TextView(this).apply {
                    text =
                        if (historyRuns.isEmpty()) {
                            "No normal scans yet."
                        } else {
                            "${historyRuns.size} normal scan(s) stored."
                        }
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                },
                lp()
            )
        
            historyCard.addView(
                Button(this).apply {
                    text = "VIEW SCAN HISTORY"
                    styleButton(this)
        
                    setOnClickListener {
                        showHistory()
                    }
                },
                lp()
            )
        
            root.addView(
                historyCard,
                lp()
            )
        
            // 2. USER TRACK SCANS
            val userTrackScans =
                savedScans.filter {
                    !it.autoTrack
                }
        
            val userTrackCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 18, 18, 18)
                background = cardBackground()
            }
        
            userTrackCard.addView(
                TextView(this).apply {
                    text = "USER TRACK SCANS"
                    textSize = 18f
                },
                lp()
            )
        
            userTrackCard.addView(
                TextView(this).apply {
                    text =
                        if (userTrackScans.isEmpty()) {
                            "No scans selected for tracking."
                        } else {
                            "${userTrackScans.size} scan(s) selected by you."
                        }
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                },
                lp()
            )
        
            userTrackCard.addView(
                Button(this).apply {
                    text = "VIEW USER TRACK SCANS"
                    styleButton(this)
                    setOnClickListener {
                        showSavedScanList(false)
                    }
                },
                lp()
            )
        
            root.addView(
                userTrackCard,
                lp()
            )
        
            // 3. AUTO TRACKING
            val autoTrackScans =
                savedScans.filter {
                    it.autoTrack
                }
        
            val autoTrackCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 18, 18, 18)
                background = cardBackground()
            }
        
            autoTrackCard.addView(
                TextView(this).apply {
                    text = "AUTO TRACKING"
                    textSize = 18f
                },
                lp()
            )
        
            autoTrackCard.addView(
                TextView(this).apply {
                    text =
                        if (autoTrackScans.isEmpty()) {
                            "No scans are under automatic tracking."
                        } else {
                            "${autoTrackScans.size} scan(s) under auto tracking."
                        }
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                },
                lp()
            )
        
            autoTrackCard.addView(
                Button(this).apply {
                    text = "VIEW AUTO TRACKING"
                    styleButton(this)
        
                    setOnClickListener {
                        showSavedScanList(true)
                    }
                },
                lp()
            )
        
            root.addView(
                autoTrackCard,
                lp()
            )
        
            content.addView(root)
        }    

        private fun showSavedScanList(autoTrack: Boolean) {
        
            updateMonitorJob?.cancel()
            scanMonitorJob?.cancel()
            content.removeAllViews()
        
            setActiveNavigation(savedNavButton)
        
            val root = verticalScroll()
        
            val db = AppDb(this)
        
            val scans =
                db.getSavedScans().filter {
                    it.autoTrack == autoTrack
                }
        
            root.addView(
                TextView(this).apply {
                    text =
                        if (autoTrack) {
                            "AUTO TRACKING"
                        } else {
                            "USER TRACK SCANS"
                        }
        
                    textSize = 22f
                    setPadding(0, 8, 0, 14)
                },
                lp()
            )
        
            if (scans.isEmpty()) {
        
                root.addView(
                    TextView(this).apply {
                        text =
                            if (autoTrack) {
                                "No scans are under automatic tracking."
                            } else {
                                "No scans selected for tracking."
                            }
        
                        textSize = 15f
                        setPadding(0, 8, 0, 16)
                    },
                    lp()
                )
        
            } else {
        
                scans.forEach { scan ->
        
                    val card =
                        LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(18, 18, 18, 18)
                            background = cardBackground()
                        }
        
                    card.addView(
                        TextView(this).apply {
                            text = "SCAN #${scan.id}"
                            textSize = 17f
                            setTypeface(
                                typeface,
                                android.graphics.Typeface.BOLD
                            )
                        },
                        lp()
                    )
        
                    card.addView(
                        TextView(this).apply {
                            text = "Created: ${scan.created}"
                            textSize = 14f
                            setPadding(0, 6, 0, 0)
                        },
                        lp()
                    )
        
                    card.addView(
                        TextView(this).apply {
                            text = "Timeframe: ${scan.timeframe}"
                            textSize = 14f
                            setPadding(0, 4, 0, 0)
                        },
                        lp()
                    )
        
                                        card.addView(
                        TextView(this).apply {
                     text = "Status: ${scan.status}"
                            textSize = 14f
                            setPadding(0, 4, 0, 0)
                        },
                        lp()
                    )
                    card.setOnClickListener {
                        showTrackingDetails(scan.id)
                    }

                    root.addView(
                        card,
                        lp()
                    )
                }
            }
        
            root.addView(
                Button(this).apply {
                    text = "← BACK TO SAVED / TRACKING"
                    styleButton(this)
        
                    setOnClickListener {
                        showSavedScreen()
                    }
                },
                lp()
            )
        
            content.addView(root)
        }
    private fun showTrackingDetails(
        savedScanId: Long
    ) {
        updateMonitorJob?.cancel()
        scanMonitorJob?.cancel()
        content.removeAllViews()

        setActiveNavigation(savedNavButton)

        val root = verticalScroll()
        val db = AppDb(this)

        val scan = db.getSavedScan(savedScanId)

        root.addView(
            TextView(this).apply {
                text = "SCAN #$savedScanId"
                textSize = 22f
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
                setPadding(0, 8, 0, 14)
            },
            lp()
        )

        if (scan != null) {

            root.addView(
                TextView(this).apply {
                    text =
                        "Created: ${scan.created}\n" +
                        "Timeframe: ${scan.timeframe}\n" +
                        "Status: ${scan.status}"
                    textSize = 14f
                    setPadding(0, 0, 0, 14)
                },
                lp()
            )

            root.addView(
                TextView(this).apply {
                    text =
                        "CONDITIONS\n\n" +
                        scan.conditions.ifBlank {
                            "Condition details not available."
                        }
                    textSize = 14f
                    setPadding(0, 0, 0, 14)
                },
                lp()
            )
        }

        val openPositions =
            db.getTrackingPositions(savedScanId)

        root.addView(
            TextView(this).apply {
                text = "OPEN TRACKING"
                textSize = 18f
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
                setPadding(0, 8, 0, 8)
            },
            lp()
        )

        if (openPositions.isEmpty()) {

            root.addView(
                TextView(this).apply {
                    text = "No open positions."
                    textSize = 14f
                    setPadding(0, 0, 0, 12)
                },
                lp()
            )

        } else {

            openPositions.forEach { position ->

                val current =
                    position.currentPrice
                        ?: position.entryPrice

                val pnl =
                    position.pnl ?: 0.0

                root.addView(
                    TextView(this).apply {
                        text =
                            "${position.symbol} / " +
                            "${position.timeframe}\n" +
                            "Entry: " +
                            String.format(
                                Locale.US,
                                "%.2f",
                                position.entryPrice
                            ) +
                            "\nCurrent: " +
                            String.format(
                                Locale.US,
                                "%.2f",
                                current
                            ) +
                            "\nP&L: " +
                            String.format(
                                Locale.US,
                                "%.2f%%",
                                pnl
                            ) +
                            "\nLowest: " +
                            String.format(
                                Locale.US,
                                "%.2f",
                                position.lowestPrice
                            ) +
                            "\nMax downside: " +
                            String.format(
                                Locale.US,
                                "%.2f%%",
                                position.maxDownside
                            ) +
                            "\nOpened: ${position.opened}"
                        textSize = 14f
                        setPadding(0, 6, 0, 12)
                    },
                    lp()
                )
            }
        }

        val performance =
            db.getTrackingPerformance(savedScanId)

        root.addView(
            TextView(this).apply {
                text = "CLOSED PERFORMANCE"
                textSize = 18f
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
                setPadding(0, 14, 0, 8)
            },
            lp()
        )

        root.addView(
            TextView(this).apply {
                text =
                    "Closed: ${performance.totalClosed}\n" +
                    "Profit: ${performance.profitable}\n" +
                    "Loss: ${performance.loss}\n" +
                    "Win rate: " +
                    String.format(
                        Locale.US,
                        "%.2f%%",
                        performance.winRate
                    ) +
                    "\nAvg profit: " +
                    String.format(
                        Locale.US,
                        "%.2f%%",
                        performance.avgProfit
                    ) +
                    "\nAvg loss: " +
                    String.format(
                        Locale.US,
                        "%.2f%%",
                        performance.avgLoss
                    ) +
                    "\nNet avg P&L: " +
                    String.format(
                        Locale.US,
                        "%.2f%%",
                        performance.netAvgPnl
                    ) +
                    "\nBest: " +
                    String.format(
                        Locale.US,
                        "%.2f%%",
                        performance.best
                    ) +
                    "\nWorst: " +
                    String.format(
                        Locale.US,
                        "%.2f%%",
                        performance.worst
                    ) +
                    "\nAvg max downside: " +
                    String.format(
                        Locale.US,
                        "%.2f%%",
                        performance.avgMaxDownside
                    ) +
                    "\nNever below entry: " +
                    "${performance.neverBelowEntry} / " +
                    "${performance.totalClosed} (" +
                    String.format(
                        Locale.US,
                        "%.2f%%",
                        performance.neverBelowEntryPct
                    ) +
                    ")"
                textSize = 14f
                setPadding(0, 0, 0, 14)
            },
            lp()
        )

        root.addView(
            TextView(this).apply {
                text = "CLOSED RESULTS"
                textSize = 18f
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
                setPadding(0, 8, 0, 8)
            },
            lp()
        )

        val closed =
            db.closedTrackingResults(savedScanId)

        root.addView(
            TextView(this).apply {
                text =
                    if (closed.isEmpty()) {
                        "No closed positions yet."
                    } else {
                        closed.joinToString("\n\n")
                    }

                textSize = 14f
                setPadding(0, 0, 0, 16)
            },
            lp()
        )

        root.addView(
            Button(this).apply {
                text = "← BACK TO TRACKING"
                styleButton(this)

                setOnClickListener {
                    showSavedScanList(
                        scan?.autoTrack ?: false
                    )
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
            .setPositiveButton("SCAN RETENTION") { _, _ ->
                showScanRetentionSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showScanRetentionSettings() {
        val prefs = getSharedPreferences(
            "app_settings",
            MODE_PRIVATE
        )

        val autoDelete =
            prefs.getBoolean("auto_delete_scans", true)

        val days =
            prefs.getInt("scan_retention_days", 7)

        val options = arrayOf(
            "Auto delete: ON",
            "Auto delete: OFF"
        )

        AlertDialog.Builder(this)
            .setTitle("Scan Retention")
            .setSingleChoiceItems(
                options,
                if (autoDelete) 0 else 1
            ) { dialog, which ->

                prefs.edit()
                    .putBoolean(
                        "auto_delete_scans",
                        which == 0
                    )
                    .apply()

                dialog.dismiss()

                if (which == 0) {
                    showRetentionDaysDialog()
                }
            }
            .setPositiveButton("SET DAYS") { _, _ ->
                showRetentionDaysDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRetentionDaysDialog() {
        val prefs = getSharedPreferences(
            "app_settings",
            MODE_PRIVATE
        )

        val field = EditText(this).apply {
            inputType =
                InputType.TYPE_CLASS_NUMBER

            setText(
                prefs.getInt(
                    "scan_retention_days",
                    7
                ).toString()
            )

            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("Normal Scan Retention (Days)")
            .setView(field)
            .setPositiveButton("SAVE") { _, _ ->

                val value =
                    field.text.toString()
                        .toIntOrNull()
                        ?.coerceAtLeast(1)
                        ?: 7

                prefs.edit()
                    .putInt(
                        "scan_retention_days",
                        value
                    )
                    .apply()
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
                .toLongOrNull()

            if (id != null) {
                showRunDetails(db, selectedRun, id)
            }
        }
        .show()
}
private fun showRunDetails(
    db: AppDb,
    run: String,
    id: Long
) {
    val parts = run.split("  ")

    val created =
        parts.getOrNull(1) ?: "Unknown"

    val timeframe =
        parts.getOrNull(2) ?: "Unknown"

    val matched =
        parts.getOrNull(3) ?: ""

    val conditions =
        db.runConditions(id)

    val results =
        db.results(id)

    val message = buildString {

        append("SCAN DETAILS\n")
        append("==============================\n\n")

        append("Scan ID: #")
        append(id)
        append("\n")

        append("Date & Time: ")
        append(formatHistoryDateTime(created))
        append("\n")

        append("Timeframe: ")
        append(timeframe)
        append("\n")

        if (matched.isNotBlank()) {
            append("Matched: ")
            append(matched.substringAfter("matched="))
            append("\n")
        }

        append("\nCONDITIONS\n")
        append("==============================\n\n")

        append(
            conditions.ifBlank {
                "Condition details not available."
            }
        )

        append("\n\nRESULTS\n")
        append("==============================\n\n")

        append(
            formatHistoryResults(results)
        )
    }

    AlertDialog.Builder(this)
        .setTitle("Scan #$id")
        .setMessage(message)
        .setPositiveButton("OK", null)
        .show()
}

private fun formatHistoryResults(
    results: List<String>
): String {

    if (results.isEmpty()) {
        return "No matches"
    }

    val grouped =
        linkedMapOf<String, MutableList<String>>()

    results.forEach { result ->

        val parts =
            result.split("  ", limit = 3)

        val timeframe =
            parts.getOrNull(1) ?: "Other"

        grouped
            .getOrPut(timeframe) {
                mutableListOf()
            }
            .add(result)
    }

    return grouped.entries.joinToString("\n\n") { entry ->

        val title =
            when (entry.key) {
                "Daily" -> "DAILY (D)"
                "Weekly" -> "WEEKLY (W)"
                "Monthly" -> "MONTHLY (M)"
                else -> entry.key
            }

        buildString {

            append(title)
            append("\n")
            append("------------------------------")
            append("\n\n")

            append(
                entry.value.joinToString("\n\n") {
                    formatHistoryResult(it)
                }
            )
        }
    }
}

private fun formatHistoryResult(
    result: String
): String {

    val parts =
        result.split("  ", limit = 3)

    val symbol =
        parts.getOrNull(0) ?: result

    val rest =
        parts.getOrNull(2) ?: ""

    val closeText =
        rest
            .substringAfter("close=", "")
            .substringBefore("  ")

    val close =
        closeText.toDoubleOrNull()

    val note =
        if (rest.contains("  ")) {
            rest.substringAfter("  ")
        } else {
            ""
        }

    return buildString {

        append(symbol)

        if (close != null) {
            append("  LTP: ")
            append(
                String.format(
                    Locale.US,
                    "%.1f",
                    close
                )
            )
        }

        if (note.isNotBlank()) {
            append("\n")
            append(
                formatHistoryNote(note)
            )
        }
    }
}

private fun formatHistoryNote(
    note: String
): String {

    var inValues = false

    return note
        .lines()
        .joinToString("\n") { line ->

            if (line.startsWith("Close:")) {
                inValues = true
            }

            if (
                inValues &&
                !line.startsWith("Condition ") &&
                line.contains(":")
            ) {

                val prefix =
                    line.substringBeforeLast(":")

                val value =
                    line
                        .substringAfterLast(":")
                        .trim()
                        .toDoubleOrNull()

                if (value != null) {
                    return@joinToString(
                        "$prefix: " +
                                String.format(
                                    Locale.US,
                                    "%.1f",
                                    value
                                )
                    )
                }
            }

            line
        }
}

private fun formatHistoryDateTime(
    value: String
): String {

    return try {

        val dateTime =
            java.time.LocalDateTime.parse(value)

        dateTime.format(
            java.time.format.DateTimeFormatter.ofPattern(
                "dd/MM/yyyy  hh:mm:ss a",
                Locale.ENGLISH
            )
        )

    } catch (_: Exception) {
        value
    }
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
        
            val night =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
        
            return android.graphics.drawable.GradientDrawable().apply {
        
                cornerRadius = 20f
        
                setColor(
                    if (night) {
                        android.graphics.Color.rgb(
                            24,
                            34,
                            28
                        )   // #18221C
                    } else {
                        android.graphics.Color.WHITE
                    }
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
