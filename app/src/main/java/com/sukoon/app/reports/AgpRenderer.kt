package com.sukoon.app.reports

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.Locale

/**
 * Draws the AGP percentile bands on any Android canvas, so the Report tab (through Compose's
 * native canvas) and the PDF page are the same picture: light band 5–95 %, dark band 25–75 %,
 * median line, the 70–180 target lines and the 54/250 lines, three-hourly times.
 */
object AgpRenderer {
    const val Y_MIN = 40
    const val Y_MAX = 350

    /** Colors as ARGB ints; [unit] is 1 dp (screen) or 1 pt (PDF). */
    data class Look(val outer: Int, val inner: Int, val median: Int, val target: Int, val grid: Int, val text: Int, val textSize: Float, val unit: Float)

    fun draw(canvas: Canvas, area: RectF, profile: List<AgpPoint?>, look: Look) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = look.text
            textSize = look.textSize
        }
        val plot = RectF(area.left + text.measureText("000") + 4 * look.unit, area.top + look.textSize, area.right, area.bottom - look.textSize * 1.8f)
        fun x(minute: Int) = plot.left + minute / 1440f * plot.width()
        fun y(mgDl: Int) = plot.bottom - (mgDl.coerceIn(Y_MIN, Y_MAX) - Y_MIN).toFloat() / (Y_MAX - Y_MIN) * plot.height()

        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = look.grid
            strokeWidth = look.unit
        }
        val target = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = look.target
            strokeWidth = 1.5f * look.unit
        }
        listOf(54, 70, 180, 250).forEach { level ->
            canvas.drawLine(plot.left, y(level), plot.right, y(level), if (level == 70 || level == 180) target else grid)
            canvas.drawText(String.format(Locale.getDefault(), "%d", level), area.left, y(level) + look.textSize / 3, text)
        }
        // Labels every 3 h when they fit (the PDF), else every 6 or 12 h (a phone): nine "00:00"s
        // don't fit in ~300 dp, and the end ones get pushed into their neighbours. Grid stays 3-hourly.
        val every = listOf(1, 2, 4).first { it == 4 || plot.width() / 8 * it > text.measureText("00:00") * 1.5f }
        (0..8).forEach { i ->
            val minute = i * 180
            canvas.drawLine(x(minute), plot.top, x(minute), plot.bottom, grid)
            if (i % every != 0) return@forEach
            val label = String.format(Locale.getDefault(), "%02d:00", (i * 3) % 24)
            val width = text.measureText(label)
            canvas.drawText(label, (x(minute) - width / 2).coerceIn(plot.left, area.right - width), area.bottom - look.textSize * 0.3f, text)
        }

        // The day wraps round at midnight, so the first point closes the curve at 24:00.
        val points = profile + listOf(profile.firstOrNull()?.copy(minuteOfDay = 1440))
        runs(points).forEach { run ->
            band(canvas, run, { it.p95 }, { it.p5 }, look.outer, ::x, ::y)
            band(canvas, run, { it.p75 }, { it.p25 }, look.inner, ::x, ::y)
            val median = Path()
            run.forEachIndexed { i, p -> if (i == 0) median.moveTo(x(p.minuteOfDay), y(p.p50)) else median.lineTo(x(p.minuteOfDay), y(p.p50)) }
            canvas.drawPath(
                median,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = look.median
                    style = Paint.Style.STROKE
                    strokeWidth = 2.2f * look.unit
                    strokeJoin = Paint.Join.ROUND
                },
            )
        }
    }

    /** Unbroken stretches of known points (a gap = too few readings at that time of day). */
    private fun runs(points: List<AgpPoint?>): List<List<AgpPoint>> {
        val out = mutableListOf<MutableList<AgpPoint>>()
        var current = mutableListOf<AgpPoint>()
        points.forEach { p ->
            if (p == null) {
                if (current.size >= 2) out += current
                current = mutableListOf()
            } else {
                current += p
            }
        }
        if (current.size >= 2) out += current
        return out
    }

    private fun band(canvas: Canvas, run: List<AgpPoint>, upper: (AgpPoint) -> Int, lower: (AgpPoint) -> Int, color: Int, x: (Int) -> Float, y: (Int) -> Float) {
        val path = Path()
        run.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.minuteOfDay), y(upper(p))) else path.lineTo(x(p.minuteOfDay), y(upper(p))) }
        run.asReversed().forEach { p -> path.lineTo(x(p.minuteOfDay), y(lower(p))) }
        path.close()
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            },
        )
    }
}
