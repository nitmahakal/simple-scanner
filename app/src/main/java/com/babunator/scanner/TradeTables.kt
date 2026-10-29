package com.babunator.scanner

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import java.util.Locale

/*
 * Tables of one tracked scan:
 *   TRACKED   : trades that are open right now
 *   COMPLETED : trades that have been exited
 *
 * Every cell is (text, optional colour). The tables scroll sideways
 * when the phone screen is too narrow for all columns.
 */
object TradeTables {

    private val green = Color.parseColor("#2E7D32")
    private val red = Color.parseColor("#C62828")

    private data class Cell(val text: String, val color: Int? = null)

    fun build(context: Context, db: AppDb, savedScanId: Long): View {

        val store = TrackingStore(db)
        val open = store.getTrades(savedScanId, "OPEN")
        val closed = store.getTrades(savedScanId, "CLOSED")

        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL

        // ---------------- tracked (open) ----------------

        box.addView(title(context, "TRACKED (open): ${open.size}"))

        box.addView(
            note(
                context,
                "Entry = open of the candle after the signal. " +
                    "Now = latest close. " +
                    "P/L = (Now - Entry) / Entry. " +
                    "Max up / Max down = highest / lowest close since entry, as % of Entry."
            )
        )

        if (open.isEmpty()) {
            box.addView(note(context, "No open trades yet."))
        } else {
            box.addView(
                table(
                    context,
                    listOf(
                        "Symbol", "TF", "Entry date", "Entry",
                        "Now", "P/L %", "Max up %", "Max down %"
                    ),
                    open.map { t ->
                        listOf(
                            Cell(t.symbol),
                            Cell(shortTimeframe(t.timeframe)),
                            Cell(t.entryDate.take(10)),
                            Cell(price(t.entryPrice)),
                            Cell(price(t.lastPrice)),
                            Cell(percent(t.pnlPercent), signColor(t.pnlPercent)),
                            Cell(percent(t.maxUpPercent), green),
                            Cell(percent(t.maxDownPercent), red)
                        )
                    }
                )
            )
        }

        // ---------------- completed ----------------

        box.addView(title(context, "COMPLETED: ${closed.size}"))

        if (closed.isNotEmpty()) {
            val wins = closed.count { it.pnlPercent > 0.0 }
            val average = closed.map { it.pnlPercent }.average()

            box.addView(
                note(
                    context,
                    "Profitable: $wins of ${closed.size}.  " +
                        "Average P/L: ${percent(average)}.  " +
                        "P/L = (Exit - Entry) / Entry. " +
                        "Exit = open of the candle after the exit signal."
                )
            )

            box.addView(
                table(
                    context,
                    listOf(
                        "Symbol", "TF", "Entry date", "Entry",
                        "Exit date", "Exit", "P/L %",
                        "Max up %", "Max down %", "Reason"
                    ),
                    closed.map { t ->
                        listOf(
                            Cell(t.symbol),
                            Cell(shortTimeframe(t.timeframe)),
                            Cell(t.entryDate.take(10)),
                            Cell(price(t.entryPrice)),
                            Cell((t.exitDate ?: "").take(10)),
                            Cell(price(t.exitPrice ?: t.lastPrice)),
                            Cell(percent(t.pnlPercent), signColor(t.pnlPercent)),
                            Cell(percent(t.maxUpPercent), green),
                            Cell(percent(t.maxDownPercent), red),
                            Cell(t.exitReason ?: "")
                        )
                    }
                )
            )
        } else {
            box.addView(note(context, "No completed trades yet."))
        }

        return box
    }

    // ---------------- small helpers ----------------

    private fun price(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun percent(value: Double): String {
        return String.format(Locale.US, "%+.2f%%", value)
    }

    private fun signColor(value: Double): Int? {
        return when {
            value > 0.0 -> green
            value < 0.0 -> red
            else -> null
        }
    }

    private fun shortTimeframe(timeframe: String): String {
        return when (timeframe) {
            "Daily" -> "D"
            "Weekly" -> "W"
            "Monthly" -> "M"
            else -> timeframe
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun title(context: Context, text: String): TextView {
        val view = TextView(context)
        view.text = text
        view.textSize = 18f
        view.setTypeface(view.typeface, Typeface.BOLD)
        view.setPadding(0, dp(context, 16), 0, dp(context, 6))
        return view
    }

    private fun note(context: Context, text: String): TextView {
        val view = TextView(context)
        view.text = text
        view.textSize = 12f
        view.setPadding(0, 0, 0, dp(context, 8))
        return view
    }

    private fun cell(
        context: Context,
        text: String,
        bold: Boolean,
        color: Int?
    ): TextView {
        val view = TextView(context)
        view.text = text
        view.textSize = 12.5f
        view.gravity = Gravity.CENTER_VERTICAL
        view.setPadding(
            dp(context, 8),
            dp(context, 6),
            dp(context, 8),
            dp(context, 6)
        )

        if (bold) {
            view.setTypeface(view.typeface, Typeface.BOLD)
        }

        if (color != null) {
            view.setTextColor(color)
        }

        return view
    }

    private fun table(
        context: Context,
        headers: List<String>,
        rows: List<List<Cell>>
    ): View {

        val layout = TableLayout(context)

        val headerRow = TableRow(context)
        for (header in headers) {
            headerRow.addView(cell(context, header, true, null))
        }
        layout.addView(headerRow)

        for (row in rows) {
            val tableRow = TableRow(context)

            for (item in row) {
                tableRow.addView(cell(context, item.text, false, item.color))
            }

            layout.addView(tableRow)
        }

        val scroll = HorizontalScrollView(context)
        scroll.addView(layout)

        return scroll
    }
}
