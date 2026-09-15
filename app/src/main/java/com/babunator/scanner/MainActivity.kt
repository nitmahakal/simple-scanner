package com.babunator.scanner

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val indicators = listOf("Close","EMA","HMA","RSI","EMA of RSI","MACD","MACD Signal","MACD Histogram","Stoch RSI %K","Stoch RSI %D","Numeric Value","Reverse RSI","Reverse Stoch RSI","Reverse Stoch RSI %K","Reverse Stoch RSI %D")
    private val comparators = listOf("Above","Below","Equal","Cross Above","Cross Below","Near By","May Go To Cross Above","May Go To Cross Below")
    private val rows = mutableListOf<Row>()
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,18) }
        root.addView(TextView(this).apply { text = "NSE Simple Scanner"; textSize = 24f }, lp())
        status = TextView(this).apply { text = "Ready"; textSize = 16f }; root.addView(status, lp())

        val tf = Spinner(this).apply { adapter = spinner(listOf("Daily","Weekly","Monthly")) }
        root.addView(label("Timeframe")); root.addView(tf, lp())
        val logic = Spinner(this).apply { adapter = spinner(listOf("AND","OR")) }
        root.addView(label("Condition logic")); root.addView(logic, lp())
        repeat(3) { addConditionRow(root, it) }

        root.addView(Button(this).apply { text = "UPDATE + SCAN NOW"; setOnClickListener {
            val cfg = ScanConfig(tf.selectedItem.toString(), logic.selectedItem.toString(), rows.map { it.read() })
            ScanConfigStore.save(this@MainActivity, cfg); WorkChain.enqueue(this@MainActivity); status.text = "Update + scan queued"
        } }, lp())
        root.addView(Button(this).apply { text = "VIEW SCAN HISTORY"; setOnClickListener { showHistory() } }, lp())
        root.addView(Button(this).apply { text = "SET DAILY UPDATE + SCAN TIME"; setOnClickListener { pickScheduleTime() } }, lp())
        root.addView(TextView(this).apply {
            text = "Params examples:\nEMA: 50\nRSI: 14\nReverse Stoch RSI: 14,14,50\nReverse Stoch RSI %K: 14,14,3,50\nReverse Stoch RSI %D: 14,14,3,3,50\nNear/May Cross range: enter the % separately."
        }, lp())
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun addConditionRow(root: LinearLayout, idx: Int) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,10,0,10) }
        box.addView(label("Condition ${idx + 1}"))
        val left = Spinner(this).apply { adapter = spinner(indicators) }
        val op = Spinner(this).apply { adapter = spinner(comparators) }
        val right = Spinner(this).apply { adapter = spinner(listOf("Number") + indicators.filter { it != "Numeric Value" }) }
        val leftParams = text("Left params")
        val rightParams = text("Right params")
        val target = text("Number target", true)
        val range = text("Range % (Near / May Cross)", true)
        box.addView(label("Value A / left indicator")); box.addView(left, lp())
        box.addView(leftParams, lp())
        box.addView(label("Comparator")); box.addView(op, lp())
        box.addView(label("Value B / right indicator")); box.addView(right, lp())
        box.addView(rightParams, lp()); box.addView(target, lp()); box.addView(range, lp())
        root.addView(box, lp()); rows += Row(left, op, right, leftParams, rightParams, target, range)
    }

    private fun spinner(list: List<String>) = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list)
    private fun label(s: String) = TextView(this).apply { text = s; textSize = 15f }
    private fun text(hint: String, number: Boolean = false) = EditText(this).apply { this.hint = hint; if (number) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
    private fun lp() = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun pickScheduleTime() {
        val now = java.util.Calendar.getInstance()
        TimePickerDialog(this, { _, h, m -> Scheduler.schedule(this, h, m); status.text = String.format(Locale.US, "Daily schedule set: %02d:%02d", h, m) }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true).show()
    }

    private fun showHistory() {
        val db = AppDb(this); val runs = db.recentRuns()
        if (runs.isEmpty()) { AlertDialog.Builder(this).setMessage("No saved scan results yet.").setPositiveButton("OK", null).show(); return }
        AlertDialog.Builder(this).setTitle("Scan History").setItems(runs.toTypedArray()) { _, which ->
            val id = runs[which].substringAfter('#').substringBefore(' ').toLong()
            AlertDialog.Builder(this).setTitle(runs[which]).setMessage(db.results(id).joinToString("\n").ifEmpty { "No matches" }).setPositiveButton("OK", null).show()
        }.show()
    }

    private data class Row(val left: Spinner, val op: Spinner, val right: Spinner, val lp: EditText, val rp: EditText, val target: EditText, val range: EditText) {
        fun read(): Condition {
            val l = lp.text.toString().split(',').mapNotNull { it.trim().toDoubleOrNull() }
            val r = rp.text.toString().split(',').mapNotNull { it.trim().toDoubleOrNull() }
            return Condition(left.selectedItem.toString(), l, op.selectedItem.toString(), right.selectedItem.toString(), r,
                target.text.toString().toDoubleOrNull() ?: 0.0, range.text.toString().toDoubleOrNull() ?: 1.0)
        }
    }
}
