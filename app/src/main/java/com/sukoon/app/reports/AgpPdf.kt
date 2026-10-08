package com.sukoon.app.reports

import com.sukoon.app.platform.TimeFormat
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.sukoon.app.R
import com.sukoon.app.insights.RangeSummary
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The report as a one-page A4 PDF for the doctor (Android's own PdfDocument, no library): header,
 * the consensus metrics, time in range with its targets, the AGP, and what it is and isn't.
 */
object AgpPdf {
    private const val WIDTH = 595 // A4 in points
    private const val HEIGHT = 842
    private const val MARGIN = 40f
    private val DARK = 0xFF2B2822.toInt()
    private val MUTED = 0xFF5E6B64.toInt()
    private val BANDS = listOf(0xFFA23F35, 0xFFC9564B, 0xFF3E7A63, 0xFFC88A3E, 0xFF8A5A1E).map { it.toInt() }

    /** Writes the PDF into the app's cache (shared through the FileProvider) and returns it. */
    fun write(context: Context, report: AgpReport, name: String): File {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, 1).create())
        val c = page.canvas
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DARK
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val heading = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DARK
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DARK
            textSize = 10.5f
        }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            textSize = 8.5f
        }
        val day = DateTimeFormatter.ofPattern("d MMM yyyy")
        val content = WIDTH - 2 * MARGIN

        var y = 56f
        c.drawText(context.getString(R.string.report_pdf_title), MARGIN, y, title)
        y += 18f
        val period = "${day.format(report.from)} – ${day.format(report.to)} · ${context.resources.getQuantityString(R.plurals.report_days, report.days.toInt(), report.days)}"
        c.drawText(listOfNotNull(name.trim().takeIf { it.isNotEmpty() }, period).joinToString(" · "), MARGIN, y, body)
        y += 14f
        c.drawText(context.getString(R.string.report_pdf_generated, TimeFormat.of("d MMM yyyy, HH:mm").format(LocalDateTime.now())), MARGIN, y, small)
        y += 28f

        val metrics = listOf(
            context.getString(R.string.report_active) to context.getString(R.string.report_active_value, report.activePercent, report.daysWithData),
            context.getString(R.string.report_mean) to context.getString(R.string.report_mean_value, report.summary.meanMgDl),
            context.getString(R.string.report_gmi) to context.getString(R.string.report_gmi_value, String.format(Locale.getDefault(), "%.1f", report.summary.gmiPercent)),
            context.getString(R.string.report_cv) to context.getString(R.string.report_cv_value, report.cvPercent),
        )
        metrics.forEachIndexed { i, (label, value) ->
            val x = MARGIN + (i % 2) * (content / 2)
            val row = y + (i / 2) * 32f
            c.drawText(label, x, row, small)
            c.drawText(value, x, row + 14f, body)
        }
        y += 74f

        c.drawText(context.getString(R.string.report_tir), MARGIN, y, heading)
        y += 10f
        timeInRange(c, RectF(MARGIN, y, WIDTH - MARGIN, y + 14f), report.summary, small)
        y += 14f + 30f
        y += paragraph(c, String.format(Locale.getDefault(), context.getString(R.string.report_targets)), MARGIN, y, content, small) // "%%" → "%" + 18f

        c.drawText(context.getString(R.string.report_profile), MARGIN, y, heading)
        y += 8f
        AgpRenderer.draw(
            c,
            RectF(MARGIN, y, WIDTH - MARGIN, y + 270f),
            report.profile,
            AgpRenderer.Look(
                outer = 0xFFCFE0D6.toInt(),
                inner = 0xFF82BBA0.toInt(),
                median = 0xFF2E5C4A.toInt(),
                target = 0xFF3E7A63.toInt(),
                grid = 0xFFE7E2D8.toInt(),
                text = MUTED,
                textSize = 8f,
                unit = 1f,
            ),
        )
        y += 280f
        y += paragraph(c, context.getString(R.string.report_legend), MARGIN, y, content, small) + 6f

        paragraph(c, context.getString(R.string.report_pdf_footer), MARGIN, HEIGHT - 64f, content, small)
        document.finishPage(page)

        val file = File(context.cacheDir, "reports").apply { mkdirs() }.resolve("sukoon-report-${report.to}.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    /** The five bands as one bar, each labelled with its share underneath. */
    private fun timeInRange(c: Canvas, bar: RectF, s: RangeSummary, label: TextPaint) {
        val shares = listOf(s.veryLow, s.low, s.inRange, s.high, s.veryHigh)
        val names = listOf("<54", "54–69", "70–180", "181–250", ">250")
        var x = bar.left
        shares.forEachIndexed { i, share ->
            val width = (share / 100.0 * bar.width()).toFloat()
            c.drawRect(x, bar.top, x + width, bar.bottom, Paint().apply { color = BANDS[i] })
            x += width
        }
        val slot = bar.width() / shares.size
        shares.forEachIndexed { i, share ->
            val percent = if (share > 0 && share < 0.5) "<1%" else String.format(Locale.getDefault(), "%d%%", share.roundToInt())
            c.drawRect(bar.left + i * slot, bar.bottom + 8f, bar.left + i * slot + 7f, bar.bottom + 15f, Paint().apply { color = BANDS[i] })
            c.drawText("${names[i]}  $percent", bar.left + i * slot + 11f, bar.bottom + 15f, label)
        }
    }

    /** Wrapped text at (x, y); returns its height. */
    private fun paragraph(c: Canvas, text: String, x: Float, y: Float, width: Float, paint: TextPaint): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt()).build()
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
        return layout.height.toFloat()
    }
}
