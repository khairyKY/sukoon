package com.sukoon.app.ui.home

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.modes.ActiveModes
import com.sukoon.app.modes.Modes
import com.sukoon.app.modes.ModesKeeper
import com.sukoon.app.platform.TimeFormat
import com.sukoon.app.ui.components.ChoiceChips
import com.sukoon.app.ui.components.SukoonSheet
import com.sukoon.app.ui.components.durationText
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowBg
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import java.time.Instant
import java.time.LocalTime

/** How long a workout started from Modes runs. */
private val WORKOUT_MINUTES = listOf(60, 90, 120)

/**
 * Modes, from Now's header: a workout (low alarm at 100 during it, a day of watching for late lows),
 * fasting (suhoor and iftar times, when to break the fast) and a sick day (ketones every 2 h). Every
 * rule and number is from docs/research/modes-sources.md; the lines here say where each comes from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModesSheet(keeper: ModesKeeper, glucose: Int?, onLogKetones: () -> Unit, onDismiss: () -> Unit) {
    val m by keeper.active.collectAsState()
    SukoonSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.modes_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.modes_body), fontSize = 13.5.sp, lineHeight = 19.sp, color = CaptionMuted)
            WorkoutCard(keeper, m, glucose)
            FastingCard(keeper, m)
            SickCard(keeper, m, onLogKetones)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(18.dp)).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) { content() }

@Composable
private fun Heading(text: String, on: Boolean? = null, onToggle: (Boolean) -> Unit = {}) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
    if (on != null) Switch(checked = on, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
}

@Composable
private fun Note(text: String) = Text(text, fontSize = 12.5.sp, lineHeight = 17.sp, color = CaptionMuted)

@Composable
private fun Callout(text: String, warn: Boolean = false) = Text(
    text,
    fontSize = 13.5.sp,
    lineHeight = 19.sp,
    fontWeight = FontWeight.SemiBold,
    color = if (warn) PillLowText else PillHighText,
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (warn) PillLowBg else PillHighBg).padding(12.dp),
)

@Composable
private fun WorkoutCard(keeper: ModesKeeper, m: ActiveModes, glucose: Int?) = Card {
    val time = TimeFormat.of()
    Heading(stringResource(R.string.modes_workout))
    val until = m.workoutUntil
    if (until != null) {
        Text(stringResource(R.string.modes_workout_on, time.format(until), Modes.WORKOUT_LOW), fontSize = 14.sp, color = SageDeep, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.modes_workout_end),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { keeper.endWorkout() }.padding(vertical = 8.dp),
        )
    } else {
        Note(stringResource(R.string.modes_workout_body, Modes.WORKOUT_LOW))
        Modes.beforeWorkout(glucose)?.let { advice ->
            Callout(
                stringResource(
                    when (advice) {
                        Modes.BeforeWorkout.CARBS_AND_WAIT -> R.string.modes_before_carbs_wait
                        Modes.BeforeWorkout.SMALL_CARBS -> R.string.modes_before_small_carbs
                        Modes.BeforeWorkout.KETONES_FIRST -> R.string.modes_before_ketones
                    },
                    glucose ?: 0,
                ),
                warn = advice != Modes.BeforeWorkout.SMALL_CARBS,
            )
        }
        val context = LocalContext.current
        ChoiceChips(WORKOUT_MINUTES, -1, { stringResource(R.string.modes_workout_start, durationText(context, it.toLong())) }) { keeper.startWorkout(it) }
    }
    if (m.lateLowWatch) Callout(stringResource(R.string.modes_late_low, Modes.NIGHT_LOW, time.format(m.lastWorkoutEnd!!.plus(Modes.LATE_LOW))))
    var cut by remember { mutableStateOf(keeper.afterWorkoutCut) }
    Text(stringResource(R.string.modes_after_meal), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted, modifier = Modifier.padding(top = 4.dp))
    ChoiceChips(listOf(0, 25, 50), cut, { if (it == 0) stringResource(R.string.modes_cut_none) else stringResource(R.string.modes_cut, it) }) {
        cut = it
        keeper.afterWorkoutCut = it
    }
    Note(stringResource(R.string.modes_workout_sources))
}

@Composable
private fun FastingCard(keeper: ModesKeeper, m: ActiveModes) = Card {
    val f = m.fasting
    Heading(stringResource(R.string.modes_fasting), f.on) { keeper.fasting = f.copy(on = it) }
    if (!f.on) {
        Note(stringResource(R.string.modes_fasting_body))
        return@Card
    }
    TimeRow(stringResource(R.string.modes_suhoor_ends), f.suhoorEnds) { keeper.fasting = f.copy(suhoorEnds = it) }
    TimeRow(stringResource(R.string.modes_iftar), f.iftar) { keeper.fasting = f.copy(iftar = it) }
    Callout(stringResource(R.string.modes_break_fast, Modes.FAST_BREAK_LOW, Modes.FAST_BREAK_HIGH), warn = true)
    Note(stringResource(R.string.modes_fasting_doses, Modes.SUHOOR_CUT_PERCENT))
    Note(stringResource(R.string.modes_fasting_basal))
}

@Composable
private fun TimeRow(label: String, value: LocalTime, onPick: (LocalTime) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).clickable {
            TimePickerDialog(context, { _, h, min -> onPick(LocalTime.of(h, min)) }, value.hour, value.minute, DateFormat.is24HourFormat(context)).show()
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(TimeFormat.of().format(value), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Sage)
    }
}

@Composable
private fun SickCard(keeper: ModesKeeper, m: ActiveModes, onLogKetones: () -> Unit) = Card {
    Heading(stringResource(R.string.modes_sick), m.sick) { keeper.setSick(it) }
    if (!m.sick) {
        Note(stringResource(R.string.modes_sick_body))
        return@Card
    }
    Callout(stringResource(R.string.modes_sick_rules))
    Text(
        stringResource(R.string.modes_log_ketones),
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(SageMist).clickable(onClick = onLogKetones).padding(horizontal = 14.dp, vertical = 10.dp),
    )
    Note(stringResource(R.string.modes_ketone_bands))
}

/** Now, under the header: the modes that are on, one line each; tap opens Modes. */
@Composable
fun ModesBanner(m: ActiveModes, lastKetones: Pair<Double, Instant>?, onClick: () -> Unit) {
    if (!m.any) return
    val time = TimeFormat.of()
    val lines = buildList {
        m.workoutUntil?.let { add(stringResource(R.string.modes_banner_workout, time.format(it), Modes.WORKOUT_LOW)) }
        if (m.lateLowWatch) add(stringResource(R.string.modes_banner_late_low, Modes.NIGHT_LOW))
        if (m.fasting.on) add(
            if (m.fastingNow) stringResource(R.string.modes_banner_fasting, time.format(m.fasting.iftar))
            else stringResource(R.string.modes_banner_fasting_off, time.format(m.fasting.suhoorEnds)),
        )
        if (m.sick) add(
            lastKetones?.let { (mmol, at) -> stringResource(R.string.modes_banner_sick_last, String.format(java.util.Locale.getDefault(), "%.1f", mmol), time.format(at)) }
                ?: stringResource(R.string.modes_banner_sick),
        )
    }
    Text(
        lines.joinToString("\n"),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SageMist)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
        color = SageDeep,
    )
}
