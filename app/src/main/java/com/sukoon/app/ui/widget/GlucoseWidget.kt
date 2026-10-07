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
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import androidx.glance.appwidget.action.actionSendBroadcast
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
import androidx.glance.layout.size
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
import com.sukoon.app.domain.metrics.GlucoseMetrics
import com.sukoon.app.domain.metrics.GlucoseMetrics.RangeBracket
import com.sukoon.app.reminders.BasalReminder
import com.sukoon.app.sharing.Followed
import com.sukoon.app.ui.theme.NeutralWarm
import com.sukoon.app.ui.theme.OnCanvasDark
import com.sukoon.app.ui.theme.OnCanvasLight
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import com.sukoon.app.ui.theme.SurfaceDark
import com.sukoon.app.ui.theme.SurfaceLight
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The home-screen widgets: one Glance widget in seven styles ([WidgetStyle]), each laid out for the
 * space the launcher gives it. Five widget entries ([WidgetSize]) share it, so the widget maker can
 * drop one at the size the user picked; all of them stay resizable.
 */
class GlucoseWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as SukoonApp).container
        val since = System.currentTimeMillis() - Duration.ofHours(48).toMillis()
        val readings = container.glucoseRepository.readingsSince(since)
        val events = container.logbookRepository.eventsSince(since)
        val firstReadings = readings.first() // first frame already has data — no "no readings" flash
        val firstEvents = events.first()
        provideContent {
            val current by readings.collectAsState(firstReadings)
            val logged by events.collectAsState(firstEvents)
            val people by container.followerWatch.people.collectAsState()
            val options = currentState<Preferences>().toWidgetOptions()
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            WidgetBody(
                WidgetModel.build(current, now, zone, options.graphHours),
                WidgetExtras.build(logged, now, zone, container.settings.insulinAction, personFor(options, people, now)),
                options,
                LocalSize.current,
            )
        }
    }

    companion object {
        private val STYLE = stringPreferencesKey("style")
        private val INFO = stringSetPreferencesKey("info")
        private val GRAPH_HOURS = intPreferencesKey("graph_hours")
        private val SHOW_DETAILS = booleanPreferencesKey("show_details") // before styles: kept to carry old widgets over
        private val BACKGROUND = stringPreferencesKey("background")
        private val PERSON = stringPreferencesKey("person")

        fun Preferences.toWidgetOptions(): WidgetOptions {
            val style = WidgetStyle.entries.firstOrNull { it.name == this[STYLE] }
            val background = WidgetBackground.entries.firstOrNull { it.name == this[BACKGROUND] } ?: WidgetBackground.AUTO
            val hours = this[GRAPH_HOURS]
            if (style == null) {
                // A widget from before styles: the adaptive number + graph, as it was set up.
                val details = this[SHOW_DETAILS] ?: true
                val info = buildSet {
                    add(WidgetInfo.ARROW)
                    if (details) addAll(listOf(WidgetInfo.CHANGE, WidgetInfo.AGO, WidgetInfo.TIR))
                    if (hours == null || hours > 0) add(WidgetInfo.GRAPH)
                }
                return WidgetOptions(WidgetStyle.CARD, hours?.takeIf { it > 0 } ?: 3, info, background)
            }
            return WidgetOptions(
                style = style,
                graphHours = hours?.takeIf { it > 0 } ?: 3,
                info = this[INFO]?.mapNotNull { name -> WidgetInfo.entries.firstOrNull { it.name == name } }?.toSet() ?: style.defaults,
                background = background,
                person = this[PERSON],
            )
        }

        suspend fun saveOptions(context: Context, id: GlanceId, options: WidgetOptions) {
            updateAppWidgetState(context, id) { prefs ->
                prefs[STYLE] = options.style.name
                prefs[INFO] = options.info.map { it.name }.toSet()
                prefs[GRAPH_HOURS] = options.graphHours
                prefs[BACKGROUND] = options.background.name
                if (options.person != null) prefs[PERSON] = options.person else prefs.remove(PERSON)
            }
            GlucoseWidget().update(context, id)
        }

        /** Re-render every placed widget, whatever its size entry (new reading, a log entry, the clock moving on). */
        suspend fun refreshAll(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(GlucoseWidget::class.java).isNotEmpty()) {
                GlucoseWidget().updateAll(context)
            }
        }

        /** The widget entry that lands at [size]. */
        fun receiverFor(size: WidgetSize): Class<out GlanceAppWidgetReceiver> = when (size) {
            WidgetSize.SMALL -> WidgetSmallReceiver::class.java
            WidgetSize.WIDE -> WidgetWideReceiver::class.java
            WidgetSize.SQUARE -> GlucoseWidgetReceiver::class.java
            WidgetSize.STRIP -> WidgetStripReceiver::class.java
            WidgetSize.LARGE -> WidgetLargeReceiver::class.java
        }

        val RECEIVERS = WidgetSize.entries.map(::receiverFor)

        private fun personFor(options: WidgetOptions, people: List<Followed>, now: Instant): PersonReading? {
            if (options.style != WidgetStyle.FOLLOWING) return null
            val who = people.firstOrNull { it.id == options.person } ?: people.firstOrNull() ?: return null
            return WidgetExtras.person(who.name.ifBlank { "…" }, who.latest, now)
        }
    }
}

// One entry per landing size (res/xml/glucose_widget_*.xml); the square one is the original entry.
class GlucoseWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GlucoseWidget() }
class WidgetSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GlucoseWidget() }
class WidgetWideReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GlucoseWidget() }
class WidgetStripReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GlucoseWidget() }
class WidgetLargeReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GlucoseWidget() }

private val WIDGET_CAPTION = Color(0xFF5E6B64) // CaptionMuted's light tone: widgets draw outside the app theme
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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

/** The whole widget for [options] at [size]: also the widget maker's live preview. */
@Composable
internal fun WidgetBody(model: WidgetModel, extras: WidgetExtras, options: WidgetOptions, size: DpSize, interactive: Boolean = true) {
    CompositionLocalProvider(LocalInteractive provides interactive) { WidgetBodyContent(model, extras, options, size) }
}

/** False in the maker's preview: nothing in it opens the app or logs a dose. */
private val LocalInteractive = staticCompositionLocalOf { true }

@Composable
private fun WidgetBodyContent(model: WidgetModel, extras: WidgetExtras, options: WidgetOptions, size: DpSize) {
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
            .let { if (LocalInteractive.current) it.clickable(actionStartActivity<MainActivity>()) else it }
            .padding(pad),
        contentAlignment = Alignment.Center,
    ) {
        val inner = DpSize(size.width - pad * 2, size.height - pad * 2)
        when (options.style) {
            WidgetStyle.CARD -> when (layoutFor(size.width.value, size.height.value)) {
                WidgetLayout.TINY -> Tiny(model, options, palette, inner)
                WidgetLayout.STRIP -> Strip(model, options, palette, inner)
                WidgetLayout.CARD -> Card(model, options, palette, inner)
            }
            WidgetStyle.NUMBER -> if (inner.width < inner.height * 1.6f) Tiny(model, options, palette, inner) else WideNumber(model, options, palette, inner)
            WidgetStyle.GRAPH -> GraphStyle(model, options, palette, inner)
            WidgetStyle.RING -> RingStyle(model, options, palette, inner)
            WidgetStyle.TODAY -> TodayStyle(model, extras, options, palette, inner)
            WidgetStyle.INSULIN -> InsulinStyle(extras, options, palette, inner)
            WidgetStyle.FOLLOWING -> FollowingStyle(extras.person, options, palette, inner)
        }
    }
}

// --- number + graph (the original, adaptive) ------------------------------------------------------

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
        Reading(model, palette, numberSp, showArrow = !stacked && options.shows(WidgetInfo.ARROW))
        if (stacked) {
            if (options.shows(WidgetInfo.ARROW)) model.trend?.takeIf { !model.stale }?.let { Text(it.arrow, style = small(palette, numberSp * 0.45f)) }
            if (options.shows(WidgetInfo.AGO)) Text(ago(model), style = small(palette, detailSp).copy(textAlign = TextAlign.Center), maxLines = 1)
        } else if (options.shows(WidgetInfo.AGO) && size.height >= 76.dp) {
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
        Reading(model, palette, numberSp, showArrow = options.shows(WidgetInfo.ARROW))
        if (options.showDetails) {
            Spacer(GlanceModifier.width(10.dp))
            Details(model, options, palette, 12f)
        }
        if (options.graphShown && graphWidth >= 48.dp) {
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
    val footer = options.shows(WidgetInfo.TIR) || (options.graphShown && !narrow)
    val footerHeight = if (footer && size.height >= 190.dp) 22.dp else 0.dp
    val graphHeight = size.height - headerHeight - footerHeight - 8.dp
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Reading(model, palette, numberSp, showArrow = options.shows(WidgetInfo.ARROW))
            Spacer(GlanceModifier.defaultWeight())
            if (options.showDetails && !narrow) Details(model, options, palette, detailSp)
        }
        if (detailsBelow) Text(metaLine(model, options), style = small(palette, detailSp), maxLines = 1)
        if (options.graphShown && graphHeight >= 40.dp) {
            Spacer(GlanceModifier.height(8.dp))
            Graph(model, options, palette, DpSize(size.width, graphHeight), GlanceModifier.fillMaxWidth().height(graphHeight))
        }
        if (footerHeight > 0.dp) {
            val parts = listOfNotNull(
                if (options.shows(WidgetInfo.TIR)) model.tirTodayPercent?.let { context.getString(R.string.widget_tir_today, it) } else null,
                if (options.graphShown && !narrow) context.getString(R.string.widget_last_hours, options.graphHours) else null,
            )
            Text(parts.joinToString(" · "), style = small(palette, 11.5f), maxLines = 1, modifier = GlanceModifier.padding(top = 6.dp))
        }
    }
}

// --- the other styles ---------------------------------------------------------------------------

/** Number (2×1): a range dot, the number, and the arrow over the change and minutes ago. */
@Composable
private fun WideNumber(model: WidgetModel, options: WidgetOptions, palette: Palette, size: DpSize) {
    val numberSp = (size.height.value * 0.62f).coerceIn(18f, 56f)
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(rangeColor(model.mgDl, model.stale, palette))) {}
        Spacer(GlanceModifier.width(10.dp))
        Reading(model, palette, numberSp, showArrow = false)
        Spacer(GlanceModifier.width(8.dp))
        Column {
            if (options.shows(WidgetInfo.ARROW) && !model.stale) model.trend?.let { Text(it.arrow, style = TextStyle(color = palette.text, fontSize = (numberSp * 0.5f).sp)) }
            if (options.showDetails) Text(metaLine(model, options), style = small(palette, 11.5f), maxLines = 1)
        }
    }
}

/** Graph (4×1, 4×2): the line takes the space; the number sits small above it (or beside it, one row tall). */
@Composable
private fun GraphStyle(model: WidgetModel, options: WidgetOptions, palette: Palette, size: DpSize) {
    val context = LocalContext.current
    if (size.height < 100.dp) {
        val numberSp = (size.height.value * 0.5f).coerceIn(16f, 36f)
        val numberWidth = (numberSp * 2.1f).dp
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Reading(model, palette, numberSp, showArrow = options.shows(WidgetInfo.ARROW))
            Spacer(GlanceModifier.defaultWeight())
            val w = size.width - numberWidth - 8.dp
            if (w >= 48.dp) Graph(model, options, palette, DpSize(w, size.height), GlanceModifier.width(w).height(size.height))
        }
        return
    }
    val numberSp = (size.height.value * 0.18f).coerceIn(20f, 36f)
    val header = (numberSp * 1.3f).dp
    val footer = 16.dp
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Reading(model, palette, numberSp, showArrow = options.shows(WidgetInfo.ARROW))
            Spacer(GlanceModifier.defaultWeight())
            val parts = listOfNotNull(
                context.getString(R.string.widget_last_hours, options.graphHours),
                if (options.shows(WidgetInfo.AGO)) ago(model) else null,
            )
            Text(parts.joinToString(" · "), style = small(palette, 11.5f), maxLines = 1)
        }
        val h = size.height - header - footer - 6.dp
        if (h >= 40.dp) {
            Spacer(GlanceModifier.height(6.dp))
            Graph(model, options, palette, DpSize(size.width, h), GlanceModifier.fillMaxWidth().height(h))
        }
        if (options.shows(WidgetInfo.TIR)) model.tirTodayPercent?.let { Text(context.getString(R.string.widget_tir_today, it), style = small(palette, 11f), maxLines = 1) }
    }
}

/** Ring (2×2): today's time in range around the number. */
@Composable
private fun RingStyle(model: WidgetModel, options: WidgetOptions, palette: Palette, size: DpSize) {
    val context = LocalContext.current
    val side = minOf(size.width, size.height)
    val density = context.resources.displayMetrics.density
    Box(GlanceModifier.size(side), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(WidgetGraph.ring(model.tirTodayPercent, (side.value * density).toInt(), palette.darkGraph)),
            contentDescription = model.tirTodayPercent?.let { context.getString(R.string.widget_ring_today, it) },
            modifier = GlanceModifier.size(side),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Reading(model, palette, (side.value * 0.26f).coerceIn(18f, 52f), showArrow = options.shows(WidgetInfo.ARROW))
            val below = if (options.shows(WidgetInfo.AGO)) ago(model) else model.tirTodayPercent?.let { context.getString(R.string.widget_ring_today, it) }
            below?.let { Text(it, style = small(palette, (side.value * 0.075f).coerceIn(9f, 13f)), maxLines = 1) }
        }
    }
}

/** Today (4×1, 2×2, 4×2): the numbers chosen in the maker, and long-acting's status when there's room. */
@Composable
private fun TodayStyle(model: WidgetModel, extras: WidgetExtras, options: WidgetOptions, palette: Palette, size: DpSize) {
    val context = LocalContext.current
    val cells = buildList {
        if (options.shows(WidgetInfo.CARBS)) add(amount(extras.carbs) to context.getString(R.string.widget_g_carbs))
        if (options.shows(WidgetInfo.KCAL)) add((extras.kcal?.let(::amount) ?: "–") to context.getString(R.string.widget_kcal))
        if (options.shows(WidgetInfo.IOB)) add(String.format(Locale.getDefault(), "%.1f", extras.iob) to context.getString(R.string.widget_u_active))
        if (options.shows(WidgetInfo.TIR)) add((model.tirTodayPercent?.let { "$it%" } ?: "–") to context.getString(R.string.widget_in_range))
    }
    val oneRow = size.height < 100.dp
    val valueSp = if (oneRow) (size.height.value * 0.34f).coerceIn(14f, 26f) else 24f
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = if (oneRow) Alignment.CenterVertically else Alignment.Top) {
        if (!oneRow) Text(context.getString(R.string.widget_today_title).uppercase(), style = small(palette, 11f).copy(fontWeight = FontWeight.Bold))
        val perRow = if (oneRow || size.width >= 250.dp) cells.size.coerceAtLeast(1) else 2
        cells.chunked(perRow).forEach { row ->
            Row(GlanceModifier.fillMaxWidth().padding(top = if (oneRow) 0.dp else 8.dp)) {
                row.forEach { (value, unit) ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = if (oneRow) Alignment.CenterHorizontally else Alignment.Start) {
                        Text(value, style = TextStyle(color = palette.text, fontSize = valueSp.sp, fontFamily = FontFamily.Serif), maxLines = 1)
                        Text(unit, style = small(palette, 10.5f), maxLines = 1)
                    }
                }
            }
        }
        if (options.shows(WidgetInfo.LONG) && !oneRow) {
            Spacer(GlanceModifier.defaultWeight())
            LongStatus(extras, palette, button = false)
        }
    }
}

/** Insulin (2×2, 4×1): active insulin, the last rapid dose, and long-acting with "Took it" right there. */
@Composable
private fun InsulinStyle(extras: WidgetExtras, options: WidgetOptions, palette: Palette, size: DpSize) {
    val context = LocalContext.current
    val iob = @Composable {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format(Locale.getDefault(), "%.1f", extras.iob), style = TextStyle(color = palette.text, fontSize = 30.sp, fontFamily = FontFamily.Serif))
            Text(" " + context.getString(R.string.widget_u_active), style = small(palette, 12f))
        }
    }
    if (size.height < 100.dp) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (options.shows(WidgetInfo.IOB)) iob()
            Spacer(GlanceModifier.defaultWeight())
            if (options.shows(WidgetInfo.LONG)) LongStatus(extras, palette, button = true)
        }
        return
    }
    Column(GlanceModifier.fillMaxSize()) {
        Text(context.getString(R.string.widget_insulin_title).uppercase(), style = small(palette, 11f).copy(fontWeight = FontWeight.Bold))
        if (options.shows(WidgetInfo.IOB)) {
            Spacer(GlanceModifier.height(6.dp))
            iob()
            if (extras.lastRapidUnits != null && extras.lastRapidAt != null) {
                Text(
                    context.getString(R.string.widget_rapid_at, amount(extras.lastRapidUnits), TIME.format(extras.lastRapidAt.atZone(ZoneId.systemDefault()))),
                    style = small(palette, 11.5f),
                    maxLines = 1,
                )
            }
        }
        if (options.shows(WidgetInfo.LONG)) {
            Spacer(GlanceModifier.defaultWeight())
            LongStatus(extras, palette, button = true)
        }
    }
}

/** "✓ Long-acting 21:58", or not logged yet with "Took it · 20 U" (logs the last amount, like the reminder). */
@Composable
private fun LongStatus(extras: WidgetExtras, palette: Palette, button: Boolean) {
    val context = LocalContext.current
    val taken = extras.longTakenAt
    if (taken != null) {
        Text(
            context.getString(R.string.widget_long_taken, TIME.format(taken.atZone(ZoneId.systemDefault()))),
            style = TextStyle(color = ColorProvider(Sage), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        return
    }
    val dose = extras.lastLongDose
    if (button && dose != null) {
        Box(
            GlanceModifier
                .cornerRadius(16.dp)
                .background(ColorProvider(Sage))
                .let { if (LocalInteractive.current) it.clickable(actionSendBroadcast(BasalReminder.tookIntent(context))) else it }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(context.getString(R.string.reminder_basal_took, amount(dose)), style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.5.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    } else {
        Text(context.getString(R.string.widget_long_not), style = TextStyle(color = ColorProvider(StateHigh), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/** Following (1×1, 2×1, 2×2): the person's name, their number in its range colour, and how fresh it is. */
@Composable
private fun FollowingStyle(person: PersonReading?, options: WidgetOptions, palette: Palette, size: DpSize) {
    val context = LocalContext.current
    if (person == null) {
        Text(context.getString(R.string.widget_follow_none), style = small(palette, 11.5f).copy(textAlign = TextAlign.Center))
        return
    }
    val color = rangeColor(person.mgDl, person.stale, palette)
    val numberSp = if (size.height < 100.dp) (size.height.value * 0.5f).coerceIn(16f, 40f) else minOf(size.width.value * 0.3f, 56f)
    val number = if (person.stale || person.mgDl == null) "---" else person.mgDl.toString()
    val arrow = if (options.shows(WidgetInfo.ARROW) && !person.stale) person.trend?.arrow?.let { " $it" }.orEmpty() else ""
    val ago = person.minutesAgo?.let { if (it < 1) context.getString(R.string.widget_now) else context.getString(R.string.widget_minutes_ago, it.toInt()) }
    val wide = size.width > size.height * 1.6f
    if (wide) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(color)) {}
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text(person.name, style = small(palette, 11.5f), maxLines = 1)
                Text(number + arrow, style = TextStyle(color = color, fontSize = numberSp.sp, fontFamily = FontFamily.Serif), maxLines = 1)
            }
            Spacer(GlanceModifier.defaultWeight())
            if (options.shows(WidgetInfo.AGO) && ago != null) Text(ago, style = small(palette, 11f), maxLines = 1)
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Text(person.name, style = small(palette, (numberSp * 0.32f).coerceIn(10f, 14f)), maxLines = 1)
            Text(number + arrow, style = TextStyle(color = color, fontSize = numberSp.sp, fontFamily = FontFamily.Serif), maxLines = 1)
            if (options.shows(WidgetInfo.AGO) && ago != null) Text(ago, style = small(palette, (numberSp * 0.28f).coerceIn(9f, 13f)), maxLines = 1)
        }
    }
}

// --- pieces --------------------------------------------------------------------------------------

private fun rangeColor(mgDl: Int?, stale: Boolean, palette: Palette): ColorProvider = when {
    stale || mgDl == null -> palette.muted
    mgDl < 70 -> ColorProvider(StateLow)
    mgDl > 180 -> ColorProvider(StateHigh)
    else -> ColorProvider(Sage)
}

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
private fun Details(model: WidgetModel, options: WidgetOptions, palette: Palette, sp: Float) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.End) {
        if (options.shows(WidgetInfo.AGO)) Text(ago(model), style = small(palette, sp))
        val delta = model.delta
        if (!model.stale && delta != null && options.shows(WidgetInfo.CHANGE)) {
            Text(String.format(Locale.getDefault(), "%+d", delta), style = small(palette, sp))
        } else if (model.stale && model.mgDl != null) {
            Text(context.getString(R.string.widget_last_value, model.mgDl), style = small(palette, sp))
        }
    }
}

/** "+2 · 1 min ago" (or the last value once stale), as far as the options allow. */
@Composable
private fun metaLine(model: WidgetModel, options: WidgetOptions): String {
    val context = LocalContext.current
    val change = if (model.stale) {
        model.mgDl?.let { context.getString(R.string.widget_last_value, it) }
    } else if (options.shows(WidgetInfo.CHANGE)) {
        model.delta?.let { String.format(Locale.getDefault(), "%+d", it) }
    } else {
        null
    }
    return listOfNotNull(change, if (options.shows(WidgetInfo.AGO)) ago(model) else null).joinToString(" · ")
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

private fun amount(value: Double): String =
    if (value % 1.0 == 0.0) String.format(Locale.getDefault(), "%,.0f", value) else String.format(Locale.getDefault(), "%.1f", value)

private fun small(palette: Palette, sp: Float) = TextStyle(color = palette.muted, fontSize = sp.sp, fontWeight = FontWeight.Medium)
