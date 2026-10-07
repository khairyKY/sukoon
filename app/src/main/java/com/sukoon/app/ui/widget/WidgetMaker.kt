package com.sukoon.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import com.sukoon.app.ui.widget.GlucoseWidget.Companion.toWidgetOptions
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The widget maker (design "Make a widget"): style, size, what it shows and its look, with the real
 * widget as the preview, built from your current readings. New widgets go straight onto the home
 * screen at the chosen size; [editId] edits one already there (its size is changed on the home screen).
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
fun WidgetMaker(editId: Int?, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as SukoonApp).container
    val scope = rememberCoroutineScope()
    val placedSize = remember(editId) { editId?.let { sizeOfPlaced(context, it) } }
    val loaded by produceState<WidgetOptions?>(if (editId == null) WidgetOptions(WidgetStyle.CARD) else null, editId) {
        if (editId != null) {
            value = getAppWidgetState(context, PreferencesGlanceStateDefinition, GlanceAppWidgetManager(context).getGlanceIdBy(editId)).toWidgetOptions()
        }
    }
    val start = loaded ?: return
    var options by remember(start) { mutableStateOf(start) }
    var size by remember(start) { mutableStateOf(placedSize ?: WidgetSize.LARGE) }

    // Real data for the preview: the same flows the widget reads.
    val since = remember { System.currentTimeMillis() - Duration.ofHours(48).toMillis() }
    val readings by remember { container.glucoseRepository.readingsSince(since) }.collectAsState(emptyList())
    val events by remember { container.logbookRepository.eventsSince(since) }.collectAsState(emptyList())
    val people by container.followerWatch.people.collectAsState()

    fun pick(style: WidgetStyle) {
        options = options.copy(style = style, info = style.defaults, graphHours = if (style == WidgetStyle.GRAPH) 6 else options.graphHours)
        if (editId == null && size !in style.sizes) size = style.sizes.first()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.sensor_cancel), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp)) }
            Text(
                stringResource(if (editId == null) R.string.widget_maker_title else R.string.widget_maker_edit),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // The preview: the widget itself, on a wallpaper-like backdrop.
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val model = WidgetModel.build(readings, now, zone, options.graphHours)
        val person = if (options.style == WidgetStyle.FOLLOWING) (people.firstOrNull { it.id == options.person } ?: people.firstOrNull())?.let { WidgetExtras.person(it.name, it.latest, now) } else null
        val extras = WidgetExtras.build(events, now, zone, container.settings.insulinAction, person)
        val shown = if (editId == null) size else placedSize ?: size
        val dp = DpSize((shown.cols * 82 + (shown.cols - 1) * 10).dp * if (shown.cols == 4) 0.95f else 1f, (shown.rows * 92 + (shown.rows - 1) * 10).dp)
        val views by produceState<RemoteViews?>(null, options, shown, model, extras) {
            value = runCatching { GlanceRemoteViews().compose(context, dp) { WidgetBody(model, extras, options, dp, interactive = false) }.remoteViews }.getOrNull()
        }
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(216.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (options.background == WidgetBackground.CLEAR) Brush.linearGradient(listOf(Color(0xFF22302B), Color(0xFF0F1916)))
                    else Brush.linearGradient(listOf(Color(0xFFE9E3D6), Color(0xFFC9D8CF))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    views?.let { frame.addView(it.apply(frame.context, frame)) }
                },
                modifier = Modifier.size(dp),
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Label(R.string.widget_maker_style)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetStyle.entries.forEach { style -> Chip(styleName(style), style == options.style, rounded = true) { pick(style) } }
            }

            Label(R.string.widget_maker_size)
            if (editId == null) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.style.sizes.forEach { s -> Chip(sizeName(s), s == size) { size = s } }
                }
            } else {
                Text(stringResource(R.string.widget_maker_resize), fontSize = 13.sp, lineHeight = 18.sp, color = CaptionMuted)
            }

            Label(R.string.widget_maker_show)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(18.dp)),
            ) {
                options.style.infos.forEachIndexed { i, item ->
                    val on = item in options.info
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { options = options.copy(info = if (on) options.info - item else options.info + item) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(stringResource(infoName(item)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            infoHint(item)?.let { Text(stringResource(it), fontSize = 12.sp, color = CaptionMuted) }
                        }
                        Switch(checked = on, onCheckedChange = { options = options.copy(info = if (it) options.info + item else options.info - item) }, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
                    }
                    if (i < options.style.infos.lastIndex) HorizontalDivider(color = outline().copy(alpha = 0.08f))
                }
            }

            if (options.graphShown) {
                Label(R.string.widget_config_graph)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetOptions.GRAPH_CHOICES.forEach { h -> Chip(stringResource(R.string.widget_config_hours, h), h == options.graphHours) { options = options.copy(graphHours = h) } }
                }
            }

            if (options.style == WidgetStyle.FOLLOWING) {
                Label(R.string.widget_maker_person)
                if (people.isEmpty()) {
                    Text(stringResource(R.string.widget_follow_none), fontSize = 13.sp, color = CaptionMuted)
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val chosen = options.person ?: people.first().id
                        people.forEach { p -> Chip(p.name.ifBlank { "…" }, p.id == chosen) { options = options.copy(person = p.id) } }
                    }
                }
            }

            Label(R.string.widget_config_background)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    WidgetBackground.AUTO to R.string.theme_auto,
                    WidgetBackground.LIGHT to R.string.widget_config_bg_light,
                    WidgetBackground.DARK to R.string.widget_config_bg_dark,
                    WidgetBackground.CLEAR to R.string.widget_config_bg_clear,
                ).forEach { (bg, name) -> Chip(stringResource(name), bg == options.background, Modifier.weight(1f)) { options = options.copy(background = bg) } }
            }
            Spacer(Modifier.height(16.dp))
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Sage)
                    .clickable {
                        if (editId != null) {
                            scope.launch {
                                GlucoseWidget.saveOptions(context, GlanceAppWidgetManager(context).getGlanceIdBy(editId), options)
                                context.toast(context.getString(R.string.toast_widget_saved))
                                onClose()
                            }
                        } else if (WidgetPin.request(context, size, options)) {
                            context.toast(context.getString(R.string.widget_maker_confirm), long = true)
                            onClose()
                        } else {
                            context.toast(context.getString(R.string.widgets_add_manually), long = true)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (editId == null) R.string.widget_maker_add else R.string.widget_config_save),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun Label(text: Int) = Text(
    stringResource(text).uppercase(),
    fontSize = 12.sp,
    letterSpacing = 1.sp,
    fontWeight = FontWeight.SemiBold,
    color = CaptionMuted,
    modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
)

@Composable
private fun Chip(label: String, on: Boolean, modifier: Modifier = Modifier, rounded: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (rounded) 22.dp else 12.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (on) (if (rounded) SageDeep else SageMist) else MaterialTheme.colorScheme.surface)
            .border(if (on && !rounded) 1.5.dp else 1.dp, if (on) Sage else outline(), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                on && rounded -> Color.White
                on -> SageDeep
                else -> MaterialTheme.colorScheme.onBackground
            },
            maxLines = 1,
        )
    }
}

@Composable
internal fun styleName(style: WidgetStyle) = stringResource(
    when (style) {
        WidgetStyle.NUMBER -> R.string.widget_style_number
        WidgetStyle.CARD -> R.string.widget_style_card
        WidgetStyle.GRAPH -> R.string.widget_style_graph
        WidgetStyle.RING -> R.string.widget_style_ring
        WidgetStyle.TODAY -> R.string.widget_style_today
        WidgetStyle.INSULIN -> R.string.widget_style_insulin
        WidgetStyle.FOLLOWING -> R.string.widget_style_following
    },
)

@Composable
internal fun sizeName(size: WidgetSize) = stringResource(
    when (size) {
        WidgetSize.SMALL -> R.string.widget_size_small
        WidgetSize.WIDE -> R.string.widget_size_wide
        WidgetSize.SQUARE -> R.string.widget_size_square
        WidgetSize.STRIP -> R.string.widget_size_strip
        WidgetSize.LARGE -> R.string.widget_size_large
    },
)

private fun infoName(item: WidgetInfo) = when (item) {
    WidgetInfo.ARROW -> R.string.widget_info_arrow
    WidgetInfo.CHANGE -> R.string.widget_info_change
    WidgetInfo.AGO -> R.string.widget_info_ago
    WidgetInfo.GRAPH -> R.string.widget_info_graph
    WidgetInfo.TIR -> R.string.widget_info_tir
    WidgetInfo.CARBS -> R.string.widget_info_carbs
    WidgetInfo.KCAL -> R.string.widget_info_kcal
    WidgetInfo.IOB -> R.string.widget_info_iob
    WidgetInfo.LONG -> R.string.widget_info_long
}

private fun infoHint(item: WidgetInfo) = when (item) {
    WidgetInfo.CHANGE -> R.string.widget_info_change_hint
    WidgetInfo.AGO -> R.string.widget_info_ago_hint
    WidgetInfo.CARBS -> R.string.widget_info_carbs_hint
    WidgetInfo.LONG -> R.string.widget_info_long_hint
    else -> null
}

/** The size entry a placed widget came from (its size is the launcher's from then on). */
internal fun sizeOfPlaced(context: Context, appWidgetId: Int): WidgetSize? {
    val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className ?: return null
    return WidgetSize.entries.firstOrNull { GlucoseWidget.receiverFor(it).name == provider }
}

/**
 * "Add to home screen": asks the launcher to place the widget entry for the chosen size, and keeps
 * the chosen options until the launcher says which widget it became ([WidgetPinnedReceiver]).
 */
object WidgetPin {
    private const val PREFS = "sukoon_widget_pending"

    /** False when the launcher can't place widgets for apps; the user adds it from the widget list instead. */
    fun request(context: Context, size: WidgetSize, options: WidgetOptions): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("style", options.style.name)
            .putStringSet("info", options.info.map { it.name }.toSet())
            .putInt("graph_hours", options.graphHours)
            .putString("background", options.background.name)
            .putString("person", options.person)
            .apply()
        // Mutable: the launcher fills in the new widget's id.
        val placed = PendingIntent.getBroadcast(
            context, 0, Intent(context, WidgetPinnedReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return manager.requestPinAppWidget(ComponentName(context, GlucoseWidget.receiverFor(size)), null, placed)
    }

    internal fun pending(context: Context): WidgetOptions? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val style = WidgetStyle.entries.firstOrNull { it.name == p.getString("style", null) } ?: return null
        return WidgetOptions(
            style = style,
            graphHours = p.getInt("graph_hours", 3),
            info = p.getStringSet("info", null)?.mapNotNull { n -> WidgetInfo.entries.firstOrNull { it.name == n } }?.toSet() ?: style.defaults,
            background = WidgetBackground.entries.firstOrNull { it.name == p.getString("background", null) } ?: WidgetBackground.AUTO,
            person = p.getString("person", null),
        )
    }

    internal fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

/** The launcher placed a widget from the maker: give it the options that were chosen. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val options = WidgetPin.pending(context)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || options == null) return
        val done = goAsync()
        (context.applicationContext as SukoonApp).container.appScope.launch {
            try {
                GlucoseWidget.saveOptions(context, GlanceAppWidgetManager(context).getGlanceIdBy(id), options)
                WidgetPin.clear(context)
            } finally {
                done.finish()
            }
        }
    }
}
