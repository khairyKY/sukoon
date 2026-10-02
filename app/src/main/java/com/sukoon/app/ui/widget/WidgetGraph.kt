package com.sukoon.app.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.sukoon.app.domain.metrics.GlucoseSample
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageLight
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import java.time.Instant
import kotlin.math.max

/**
 * Glucose line for the widgets, drawn to a bitmap because widgets (RemoteViews/Glance) can't draw
 * paths. Same visual language as the in-app graph: shaded 70–180 band, line in sage, points
 * outside range in amber/coral. Rendered at the widget's exact pixel size so it stays crisp.
 */
object WidgetGraph {

    // RemoteViews ship bitmaps across processes; past ~1–2 MP the launcher can refuse them.
    private const val MAX_PIXELS = 1_200_000

    fun render(samples: List<GlucoseSample>, from: Instant, to: Instant, widthPx: Int, heightPx: Int, dark: Boolean): Bitmap {
        var w = widthPx.coerceAtLeast(1)
        var h = heightPx.coerceAtLeast(1)
        if (w * h > MAX_PIXELS) {
            val scale = Math.sqrt(MAX_PIXELS.toDouble() / (w * h))
            w = (w * scale).toInt()
            h = (h * scale).toInt()
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = h / 60f + 2f

        // Adaptive top (>= 200 keeps the 70–180 band readable) so low/in-range days fill the space
        // instead of leaving empty headroom under the number.
        val top = max(200, (samples.maxOfOrNull { it.glucoseMgDl } ?: 0) + 20).toFloat()
        val bottom = 40f
        val margin = stroke * 2 // room for the end dot (radius 1.8 × stroke) at the edges
        fun y(mgDl: Int) = h - (mgDl.coerceIn(bottom.toInt(), top.toInt()) - bottom) / (top - bottom) * (h - margin * 2) - margin
        val span = (to.toEpochMilli() - from.toEpochMilli()).coerceAtLeast(1).toFloat()
        fun x(at: Instant) = (at.toEpochMilli() - from.toEpochMilli()) / span * (w - margin * 2) + margin

        val band = Paint().apply { color = (if (dark) SageLight else Sage).toArgb(); alpha = if (dark) 40 else 30 }
        canvas.drawRect(0f, y(180), w.toFloat(), y(70), band)

        if (samples.isEmpty()) return bitmap
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (if (dark) SageLight else Sage).toArgb()
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path()
        samples.sortedBy { it.timestamp }.forEachIndexed { i, s ->
            if (i == 0) path.moveTo(x(s.timestamp), y(s.glucoseMgDl)) else path.lineTo(x(s.timestamp), y(s.glucoseMgDl))
        }
        canvas.drawPath(path, line)

        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        for (s in samples) {
            val color = when {
                s.glucoseMgDl < 70 -> StateLow
                s.glucoseMgDl > 180 -> StateHigh
                else -> continue
            }
            dot.color = color.toArgb()
            canvas.drawCircle(x(s.timestamp), y(s.glucoseMgDl), stroke * 0.9f, dot)
        }
        val last = samples.maxBy { it.timestamp }
        dot.color = line.color
        canvas.drawCircle(x(last.timestamp), y(last.glucoseMgDl), stroke * 1.8f, dot)
        return bitmap
    }
}
