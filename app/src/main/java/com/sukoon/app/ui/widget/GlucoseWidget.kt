package com.sukoon.app.ui.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.domain.metrics.GlucoseMetrics.RangeBracket
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.NeutralWarm
import com.sukoon.app.ui.theme.OnCanvasDark
import com.sukoon.app.ui.theme.OnCanvasLight
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import com.sukoon.app.ui.theme.SurfaceDark
import com.sukoon.app.ui.theme.SurfaceLight
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import androidx.glance.color.ColorProvider as DayNightColor

/**
 * Home-screen glucose widget (A11). Resizable from a single cell to half the screen: Glance's
 * SizeMode.Exact hands us the real size on every resize and [layoutFor] picks the arrangement —
 * a big number at 1×1, a strip with details and sparkline when wide, a card whose graph grows
 * to fill the space when tall. Per-widget options live in Glance state (WidgetConfigActivity).
 */
class GlucoseWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val readings = (context.applicationContext as SukoonApp).container.glucoseRepository
            .readingsSince(System.currentTimeMillis() - Duration.ofHours(24).toMillis())
        val initial = readings.first() // first frame already has data — no "no readings" flash
        provideContent {
            val current by readings.collectAsState(initial)
            val options = currentState<Preferences>().toWidgetOptions()
            val model = WidgetModel.build(current, Instant.now(), ZoneId.systemDefault(), options.graphHours)
            WidgetContent(model, options, LocalSize.current)
        }
    }

    companion object {
        private val GRAPH_HOURS = intPreferencesKey("graph_hours")
        private val SHOW_DETAILS = booleanPreferencesKey("show_details")
        private val BACKGROUND = stringPreferencesKey("background")

        fun Preferences.toWidgetOptions() = WidgetOptions(
            graphHours = this[GRAPH_HOURS] ?: WidgetOptions().graphHours,
            showDetails = this[SHOW_DETAILS] ?: true,
            background = WidgetBackground.entries.firstOrNull { it.name == this[BACKGROUND] } ?: WidgetBackground.AUTO,
        )

        suspend fun saveOptions(context: Context, id: GlanceId, options: WidgetOptions) {
            updateAppWidgetState(context, id) { prefs ->
                prefs[GRAPH_HOURS] = options.graphHours
                prefs[SHOW_DETAILS] = options.showDetails
                prefs[BACKGROUND] = options.background.name
            }
            GlucoseWidget().update(context, id)
        }

        /** Re-render every placed widget (new reading, or the "minutes ago" clock moving on). */
        suspend fun refreshAll(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(GlucoseWidget::class.java).isNotEmpty()) {
                GlucoseWidget().updateAll(context)
            }
        }
    }
}

class GlucoseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlucoseWidget()
}

private val WIDGET_CAPTION = Color(0xFF5E6B64) // CaptionMuted's light tone: widgets draw outside the app theme

/** Resolved colors for one background choice; [darkGraph] picks the bitmap's line/band shade. */
private class Palette(val background: ColorProvider, val text: ColorProvider, val muted: ColorProvider, val darkGraph: Boolean) {
    companion object {
        fun of(choice: WidgetBackground, nightNow: Boolean) = when (choice) {
            WidgetBackground.AUTO -> Palette(
                DayNightColor(day = SurfaceLight, night = SurfaceDark),
                DayNightColor(day = OnCanvasLight, night = OnCanvasDark),
                DayNightColor(day = WIDGET_CAPTION, night = NeutralWarm),
                nightNow,
            )
            WidgetBackground.LIGHT -> Palette(ColorProvider(SurfaceLight), ColorProvider(OnCanvasLight), ColorProvider(WIDGET_CAPTION), false)
            WidgetBackground.DARK -> Palette(ColorProvider(SurfaceDark), ColorProvider(OnCanvasDark), ColorProvider(NeutralWarm), true)
            WidgetBackground.CLEAR -> Palette(ColorProvider(Color.Transparent), ColorProvider(Color.White), ColorProvider(Color.White.copy(alpha = 0.8f)), true)
        }
    }
}

@Composable
private fun WidgetContent(model: WidgetModel, options: WidgetOptions, size: DpSize) {
    val context = LocalContext.current
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val palette = Palette.of(options.background, night)
    val pad = if (minOf(size.width, size.height) < 80.dp) 6.dp else 14.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(palette.background)
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(pad),
        contentAlignment = Alignment.Center,
    ) {
        val inner = DpSize(size.width - pad * 2, size.height - pad * 2)
        when (layoutFor(size.width.value, size.height.value)) {
            WidgetLayout.TINY -> Tiny(model, options, palette, inner)
            WidgetLayout.STRIP -> Strip(model, options, palette, inner)
            WidgetLayout.CARD -> Card(model, options, palette, inner)
        }
    }
}

// --- layouts -------------------------------------------------------------------------------------

@Composable
private fun Tiny(model: WidgetModel, options: WidgetOptions, palette: Palette, size: DpSize) {
    // Tall, narrow cells (1×2, or 1×1 on launchers with tall rows): arrow moves under the number so
    // the number can use the whole width.
    val stacked = size.height > size.width * 1.4f
    val numberSp = if (stacked) {
        minOf(size.width.value * 0.58f, size.height.value * 0.4f).coerceIn(14f, 72f)
    } else {
        minOf(size.width.value * 0.42f, size.height.value * 0.55f).coerceIn(14f, 64f)
    }
    val detailSp = (numberSp * 0.3f).coerceIn(9f, 14f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Reading(model, palette, numberSp, showArrow = !stacked)
        if (stacked) {
            model.trend?.takeIf { !model.stale }?.let { Text(it.arrow, style = small(palette, numberSp * 0.45f)) }
            if (options.showDetails) Text(ago(model), style = small(palette, detailSp).copy(textAlign = TextAlign.Center), maxLines = 1)
        } else if (options.showDetails && size.height >= 76.dp) {
            Text(ago(model), style = small(palette, detailSp))
        }
    }
}

@Composable
private fun Strip(model: WidgetModel, options: WidgetOptions, palette: Palette, size: DpSize) {
    val numberSp = (size.height.value * 0.62f).coerceIn(18f, 52f)
    val detailsWidth = if (options.showDetails) 76.dp else 0.dp
    val numberWidth = (numberSp * 2.1f).dp // three serif digits ≈ 1.6 em + the half-size arrow
    val graphWidth = size.width - numberWidth - detailsWidth - 16.dp
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Reading(model, palette, numberSp)
        if (options.showDetails) {
            Spacer(GlanceModifier.width(10.dp))
            Details(model, palette, 12f)
        }
        if (options.graphHours > 0 && graphWidth >= 48.dp) {
            // Fixed graph width (the bitmap is drawn at exactly this size, so it never stretches);
            // the weighted spacer soaks up any error in the number-width estimate.
            Spacer(GlanceModifier.defaultWeight())
            Graph(model, options, palette, DpSize(graphWidth, size.height), GlanceModifier.width(graphWidth).height(size.height))
        }
    }
}

@Composable
private fun Card(model: WidgetModel, options: WidgetOptions, palette: Palette, size: DpSize) {
    // Narrow cards (2×2 on launchers with tall cells) put the details under the number instead
    // of beside it, and drop the "last N h" footer part, so nothing clips.
    val narrow = size.width < 200.dp
    val numberSp = minOf(size.height.value * 0.2f, size.width.value * if (narrow) 0.34f else 0.3f).coerceIn(28f, 76f)
    val detailSp = (numberSp * 0.2f).coerceIn(11f, 15f)
    val detailsBelow = narrow && options.showDetails
    val headerHeight = (numberSp * 1.3f).dp + if (detailsBelow) (detailSp * 1.5f).dp else 0.dp
    val footerHeight = if (options.showDetails && size.height >= 190.dp) 22.dp else 0.dp
    val graphHeight = size.height - headerHeight - footerHeight - 8.dp
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Reading(model, palette, numberSp)
            Spacer(GlanceModifier.defaultWeight())
            if (options.showDetails && !narrow) Details(model, palette, detailSp)
        }
        if (detailsBelow) {
            val extra = if (model.stale) {
                model.mgDl?.let { context.getString(R.string.widget_last_value, it) }
            } else {
                model.delta?.let { String.format(Locale.getDefault(), "%+d", it) }
            }
            Text(listOfNotNull(ago(model), extra).joinToString(" · "), style = small(palette, detailSp), maxLines = 1)
        }
        if (options.graphHours > 0 && graphHeight >= 40.dp) {
            Spacer(GlanceModifier.height(8.dp))
            Graph(model, options, palette, DpSize(size.width, graphHeight), GlanceModifier.fillMaxWidth().height(graphHeight))
        }
        if (footerHeight > 0.dp) {
            val parts = listOfNotNull(
                model.tirTodayPercent?.let { context.getString(R.string.widget_tir_today, it) },
                if (options.graphHours > 0 && !narrow) context.getString(R.string.widget_last_hours, options.graphHours) else null,
            )
            Text(parts.joinToString(" · "), style = small(palette, 11.5f), maxLines = 1, modifier = GlanceModifier.padding(top = 6.dp))
        }
    }
}

// --- pieces --------------------------------------------------------------------------------------

@Composable
private fun Reading(model: WidgetModel, palette: Palette, numberSp: Float, showArrow: Boolean = true) {
    val color = when {
        model.stale || model.mgDl == null -> palette.muted
        model.bracket == RangeBracket.LOW || model.bracket == RangeBracket.VERY_LOW -> ColorProvider(StateLow)
        model.bracket == RangeBracket.HIGH || model.bracket == RangeBracket.VERY_HIGH -> ColorProvider(StateHigh)
        else -> palette.text
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Stale values are never shown as current (PLAN §5) — the number gives way to dashes.
        Text(
            if (model.stale || model.mgDl == null) "---" else model.mgDl.toString(),
            style = TextStyle(color = color, fontSize = numberSp.sp, fontFamily = FontFamily.Serif),
        )
        if (showArrow && !model.stale && model.trend != null) {
            Text(" " + model.trend.arrow, style = TextStyle(color = color, fontSize = (numberSp * 0.5f).sp))
        }
    }
}

@Composable
private fun Details(model: WidgetModel, palette: Palette, sp: Float) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.End) {
        Text(ago(model), style = small(palette, sp))
        val delta = model.delta
        if (!model.stale && delta != null) {
            Text(String.format(Locale.getDefault(), "%+d", delta), style = small(palette, sp))
        } else if (model.stale && model.mgDl != null) {
            Text(context.getString(R.string.widget_last_value, model.mgDl), style = small(palette, sp))
        }
    }
}

@Composable
private fun Graph(model: WidgetModel, options: WidgetOptions, palette: Palette, size: DpSize, modifier: GlanceModifier) {
    val density = LocalContext.current.resources.displayMetrics.density
    val now = Instant.now()
    val bitmap = WidgetGraph.render(
        model.graph,
        from = now.minus(Duration.ofHours(options.graphHours.toLong())),
        to = now,
        widthPx = (size.width.value * density).toInt(),
        heightPx = (size.height.value * density).toInt(),
        dark = palette.darkGraph,
    )
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = LocalContext.current.getString(R.string.widget_graph_description, options.graphHours),
        contentScale = ContentScale.FillBounds,
        modifier = modifier,
    )
}

@Composable
private fun ago(model: WidgetModel): String {
    val context = LocalContext.current
    val minutes = model.minutesAgo ?: return context.getString(R.string.widget_no_readings)
    return if (minutes < 1) context.getString(R.string.widget_now) else context.getString(R.string.widget_minutes_ago, minutes.toInt())
}

private fun small(palette: Palette, sp: Float) = TextStyle(color = palette.muted, fontSize = sp.sp, fontWeight = FontWeight.Medium)
