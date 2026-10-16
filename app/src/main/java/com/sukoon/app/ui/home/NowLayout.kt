package com.sukoon.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sukoon.app.R
import com.sukoon.app.ui.components.SukoonSheet
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage

/** The parts of Now a user can turn off; the ones in [NowLayout.order] can also be moved. The number itself always shows. */
enum class NowPart(val label: Int, val what: Int) {
    CHIPS(R.string.now_part_chips, R.string.now_part_chips_what),
    SENSOR_ENDING(R.string.now_part_sensor, R.string.now_part_sensor_what),
    NEXT(R.string.now_part_next, R.string.now_part_next_what),
    GRAPH(R.string.now_part_graph, R.string.now_part_graph_what),
    STATS(R.string.now_part_stats, R.string.now_part_stats_what),
    DOSE(R.string.now_part_dose, R.string.now_part_dose_what),
}

/**
 * Now, as the user arranged it (design A, "Make Now yours"): chips and the sensor-ending banner
 * keep their place above the number; the rest show under it in [order]. Low and urgent screens
 * aren't arranged: they always show the same few things.
 */
data class NowLayout(val order: List<NowPart> = MOVABLE, val hidden: Set<NowPart> = emptySet()) {
    fun shows(p: NowPart) = p !in hidden

    /** "NEXT,GRAPH,-STATS,DOSE;-CHIPS": the order, then the fixed parts; "-" is off. */
    fun encode(): String = order.joinToString(",") { (if (it in hidden) "-" else "") + it.name } + ";" +
        FIXED.joinToString(",") { (if (it in hidden) "-" else "") + it.name }

    companion object {
        val FIXED = listOf(NowPart.CHIPS, NowPart.SENSOR_ENDING)
        val MOVABLE = listOf(NowPart.NEXT, NowPart.GRAPH, NowPart.STATS, NowPart.DOSE)

        fun decode(s: String?): NowLayout {
            if (s.isNullOrBlank()) return NowLayout()
            val names = s.split(';', ',').filter { it.isNotBlank() }
            fun part(n: String) = NowPart.entries.firstOrNull { it.name == n.removePrefix("-") }
            val order = names.mapNotNull(::part).filter { it in MOVABLE }.distinct()
            return NowLayout(
                order = order + MOVABLE.filter { it !in order }, // a part added in a later version shows, at the end
                hidden = names.filter { it.startsWith("-") }.mapNotNull(::part).toSet(),
            )
        }
    }
}

/** "Make Now yours": a switch for each part, and a handle to drag the ones under the number. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowLayoutSheet(layout: NowLayout, onChange: (NowLayout) -> Unit, onDismiss: () -> Unit) {
    var order by remember { mutableStateOf(layout.order) }
    var hidden by remember { mutableStateOf(layout.hidden) }
    fun save() = onChange(NowLayout(order, hidden))
    fun toggle(p: NowPart, on: Boolean) { hidden = if (on) hidden - p else hidden + p; save() }
    fun move(p: NowPart, by: Int) {
        val i = order.indexOf(p)
        val j = (i + by).coerceIn(0, order.lastIndex)
        if (i != j) order = order.toMutableList().apply { add(j, removeAt(i)) }
    }
    val rowHeight = 64.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var dragging by remember { mutableStateOf<NowPart?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }

    SukoonSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.now_layout_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.now_layout_body), fontSize = 13.sp, lineHeight = 18.sp, color = CaptionMuted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            Eyebrow(stringResource(R.string.now_layout_top))
            NowLayout.FIXED.forEach { p -> PartRow(p, !hidden.contains(p), rowHeight, handle = null) { toggle(p, it) } }
            Eyebrow(stringResource(R.string.now_layout_below))
            order.forEach { p ->
                key(p) {
                    val up = stringResource(R.string.now_layout_up)
                    val down = stringResource(R.string.now_layout_down)
                    PartRow(
                        p, !hidden.contains(p), rowHeight,
                        modifier = Modifier
                            .zIndex(if (dragging == p) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging == p) offset else 0f }
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(up) { move(p, -1); save(); true },
                                    CustomAccessibilityAction(down) { move(p, 1); save(); true },
                                )
                            },
                        handle = Modifier.pointerInput(p) {
                            detectDragGestures(
                                onDragStart = { dragging = p; offset = 0f },
                                onDragEnd = { dragging = null; offset = 0f; save() },
                                onDragCancel = { dragging = null; offset = 0f; save() },
                            ) { change, drag ->
                                change.consume()
                                offset += drag.y
                                // Past half a row, it swaps with its neighbour and keeps following the finger.
                                val i = order.indexOf(p)
                                if (offset > rowPx / 2 && i < order.lastIndex) { move(p, 1); offset -= rowPx }
                                if (offset < -rowPx / 2 && i > 0) { move(p, -1); offset += rowPx }
                            }
                        },
                    ) { toggle(p, it) }
                }
            }
            Text(
                stringResource(R.string.now_layout_reset),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Sage,
                modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { order = NowLayout.MOVABLE; hidden = emptySet(); save() }.padding(vertical = 10.dp, horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Eyebrow(text: String) = Text(
    text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = CaptionMuted,
    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
)

@Composable
private fun PartRow(p: NowPart, on: Boolean, height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, handle: Modifier?, onToggle: (Boolean) -> Unit) {
    Row(
        modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (handle != null) {
            val dot = CaptionMuted
            // Six dots: the drag handle.
            Canvas(handle.size(28.dp).padding(6.dp)) {
                val r = 1.8.dp.toPx()
                for (x in 0..1) for (y in 0..2) drawCircle(dot, r, Offset(size.width * (0.3f + 0.4f * x), size.height * (0.15f + 0.35f * y)))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(p.label), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(p.what), fontSize = 12.5.sp, color = CaptionMuted, maxLines = 2)
        }
        Switch(checked = on, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
    }
}
