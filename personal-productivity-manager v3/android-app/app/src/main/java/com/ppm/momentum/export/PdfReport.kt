package com.ppm.momentum.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ppm.momentum.domain.ChartPoint
import com.ppm.momentum.domain.TaskStats
import java.io.OutputStream
import java.util.Locale

/** Professional-looking A4 report: summary cards, two charts, then a paginated task table. */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 36f
    private val INDIGO = Color.rgb(79, 91, 213)
    private val GREEN = Color.rgb(46, 173, 107)
    private val AMBER = Color.rgb(245, 165, 36)
    private val RED = Color.rgb(229, 72, 77)
    private val GREY = Color.rgb(139, 144, 160)
    private val INK = Color.rgb(26, 28, 36)

    private fun paint(size: Float, color: Int = INK, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun fit(text: String, p: Paint, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        val n = p.breakText(text, true, maxW - p.measureText("…"), null)
        return text.take(n) + "…"
    }

    fun write(
        out: OutputStream, title: String, subtitle: String, stats: TaskStats, focusMinutes: Long,
        daily: List<ChartPoint>, header: List<String>, rows: List<List<String>>,
    ) {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c = page.canvas

        fun footer() {
            val p = paint(9f, GREY)
            c.drawText("Momentum · page $pageNo", M, H - 20f, p)
        }

        // Title block
        c.drawRect(0f, 0f, W.toFloat(), 70f, Paint().apply { color = INDIGO })
        c.drawText(title, M, 34f, paint(20f, Color.WHITE, true))
        c.drawText(subtitle, M, 54f, paint(11f, Color.WHITE))

        // Summary cards (2 rows x 4)
        val cards = listOf(
            Triple("Total", stats.total.toString(), INDIGO), Triple("Completed", stats.completed.toString(), GREEN),
            Triple("On time", stats.onTime.toString(), GREEN), Triple("Late", stats.late.toString(), AMBER),
            Triple("Overdue", stats.overdue.toString(), RED), Triple("Pending", stats.pending.toString(), INDIGO),
            Triple("Cancelled", stats.cancelled.toString(), GREY),
            Triple("Completion", String.format(Locale.US, "%.0f%%", stats.completionRate), INDIGO),
        )
        val cw = (W - 2 * M - 3 * 10f) / 4f
        cards.forEachIndexed { i, (label, value, col) ->
            val x = M + (i % 4) * (cw + 10f)
            val y = 88f + (i / 4) * 62f
            c.drawRoundRect(RectF(x, y, x + cw, y + 52f), 10f, 10f, Paint().apply { color = Color.rgb(243, 244, 250) })
            c.drawText(value, x + 10f, y + 29f, paint(20f, col, true))
            c.drawText(label, x + 10f, y + 44f, paint(9f, GREY))
        }
        c.drawText("Total focus time: ${focusMinutes / 60}h ${focusMinutes % 60}m", M, 228f, paint(11f, INK, true))

        // Pie chart: completed / incomplete / cancelled
        val pie = listOf(stats.completed to GREEN, stats.incomplete to AMBER, stats.cancelled to GREY)
        val total = pie.sumOf { it.first }
        val rect = RectF(M, 246f, M + 110f, 356f)
        if (total == 0) {
            c.drawOval(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 232, 240) })
        } else {
            var start = -90f
            pie.forEach { (v, col) ->
                val sweep = v * 360f / total
                c.drawArc(rect, start, sweep, true, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
                start += sweep
            }
        }
        listOf("Completed" to GREEN, "Incomplete" to AMBER, "Cancelled" to GREY).forEachIndexed { i, (l, col) ->
            val y = 270f + i * 20f
            c.drawRect(M + 124f, y - 9f, M + 134f, y + 1f, Paint().apply { color = col })
            c.drawText("$l (${pie[i].first})", M + 142f, y, paint(10f))
        }

        // Bar chart: daily completions
        val bx = 300f; val by = 256f; val bw = W - M - bx; val bh = 90f
        c.drawText("Completed per day (last ${daily.size} days)", bx, 246f, paint(10f, INK, true))
        val maxV = (daily.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
        val slot = bw / daily.size.coerceAtLeast(1)
        daily.forEachIndexed { i, pt ->
            val h = bh * pt.value / maxV
            c.drawRect(bx + i * slot + 2f, by + bh - h, bx + (i + 1) * slot - 2f, by + bh, Paint().apply { color = INDIGO })
            if (i % 2 == 0) c.drawText(pt.label, bx + i * slot, by + bh + 12f, paint(7f, GREY))
        }

        // Task table
        val pick = listOf(0 to 180f, 2 to 52f, 9 to 62f, 7 to 88f, 10 to 88f, 11 to 45f)
        var y = 392f
        fun tableHeader() {
            c.drawRect(M, y - 12f, W - M, y + 6f, Paint().apply { color = Color.rgb(228, 231, 248) })
            var x = M + 4f
            val hp = paint(9f, INK, true)
            pick.forEach { (idx, w) -> c.drawText(header[idx], x, y, hp); x += w }
            y += 20f
        }
        tableHeader()
        val rp = paint(9f)
        rows.forEach { row ->
            if (y > H - 50f) {
                footer(); doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()); c = page.canvas
                y = 50f; tableHeader()
            }
            var x = M + 4f
            pick.forEach { (idx, w) -> c.drawText(fit(row[idx], rp, w - 6f), x, y, rp); x += w }
            c.drawLine(M, y + 5f, W - M, y + 5f, Paint().apply { color = Color.rgb(236, 238, 245); strokeWidth = 0.5f })
            y += 16f
        }
        if (rows.isEmpty()) c.drawText("No tasks in this period.", M + 4f, y, rp)
        footer(); doc.finishPage(page)
        doc.writeTo(out); doc.close()
    }
}
