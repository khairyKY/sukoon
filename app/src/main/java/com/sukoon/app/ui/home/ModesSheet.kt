package com.sukoon.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.alarms.AlarmMonitor
import com.sukoon.app.modes.ActiveModes
import com.sukoon.app.modes.GoalTargets
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
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import java.text.NumberFormat
import java.time.Instant
import kotlinx.coroutines.launch

/** How long a workout started from Today runs, and how long alarms can pause. */
private val WORKOUT_MINUTES = listOf(60, 90, 120)
private val PAUSE_MINUTES = listOf(30, 60, 120, 240)

/**
 * Today (Now's sliders button): what you switch on for a while. One row each, no explanations:
 * the why and the sources are a Guide topic ([onGuide]); the lasting settings are You → Fitness
 * & food ([onFitness]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodaySheet(
    keeper: ModesKeeper,
    alarms: AlarmMonitor,
    glucose: Int?,
    lastKetones: Pair<Double, Instant>?,
    /** Under the parent lock: pausing asks for the PIN first. */
    askPin: ((() -> Unit) -> Unit)?,
    onLogKetones: () -> Unit,
    onFitness: () -> Unit,
    onGuide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val m by keeper.active.collectAsState()
    val paused by alarms.pausedUntil.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val time = TimeFormat.of()
    SukoonSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.today_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)

            // Pause alarms
            val pausedAt = paused
            TodayRow(stringResource(R.string.today_pause), if (pausedAt != null) stringResource(R.string.today_pause_until, time.format(pausedAt)) else stringResource(R.string.today_pause_note)) {
                if (pausedAt != null) Action(stringResource(R.string.today_resume)) { scope.launch { alarms.resumeAll() } }
            }
            if (pausedAt == null) {
                val durations = remember(context) { PAUSE_MINUTES.associateWith { lengthLabel(context, it) } }
                ChoiceChips(PAUSE_MINUTES, -1, { durations.getValue(it) }) { minutes ->
                    val pause: () -> Unit = { scope.launch { alarms.pauseAll(minutes) } }
                    if (askPin != null) askPin(pause) else pause()
                }
            }

            // Workout
            val until = m.workoutUntil
            TodayRow(
                stringResource(R.string.today_workout),
                when {
                    until != null -> stringResource(R.string.today_workout_until, time.format(until), Modes.WORKOUT_LOW)
                    m.lateLowWatch -> stringResource(R.string.today_late_low, Modes.NIGHT_LOW)
                    else -> null
                },
            ) { if (until != null) Action(stringResource(R.string.today_end)) { keeper.endWorkout() } }
            if (until == null) {
                Modes.beforeWorkout(glucose)?.let { advice ->
                    Callout(
                        stringResource(
                            when (advice) {
                                Modes.BeforeWorkout.CARBS_AND_WAIT -> R.string.before_carbs_wait
                                Modes.BeforeWorkout.SMALL_CARBS -> R.string.before_small_carbs
                                Modes.BeforeWorkout.KETONES_FIRST -> R.string.before_ketones
                            },
                            glucose ?: 0,
                        ),
                    )
                }
                val durations = remember(context) { WORKOUT_MINUTES.associateWith { lengthLabel(context, it) } }
                ChoiceChips(WORKOUT_MINUTES, -1, { durations.getValue(it) }) { keeper.startWorkout(it) }
            }

            // Fasting
            val f = m.fasting
            TodayRow(stringResource(R.string.today_fasting), if (f.on) stringResource(R.string.today_fasting_times, time.format(f.suhoorEnds), time.format(f.iftar)) else null) {
                Switch(checked = f.on, onCheckedChange = { keeper.fasting = f.copy(on = it) }, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
            }

            // Sick day
            TodayRow(
                stringResource(R.string.today_sick),
                if (m.sick) lastKetones?.let { (mmol, at) -> stringResource(R.string.today_last_ketones, String.format(java.util.Locale.getDefault(), "%.1f", mmol), time.format(at)) } else null,
            ) { Switch(checked = m.sick, onCheckedChange = { keeper.setSick(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Sage)) }
            if (m.sick) Action(stringResource(R.string.modes_log_ketones), filled = true, onClick = onLogKetones)

            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Link(stringResource(R.string.today_goal_link), onFitness)
                Link(stringResource(R.string.today_why_link), onGuide)
            }
        }
    }
}

/** "1 h", "90 min", "2 h": whole hours as hours (durationText rounds anything else to the hour). */
internal fun lengthLabel(context: android.content.Context, minutes: Int): String =
    if (minutes % 60 == 0) durationText(context, minutes.toLong()) else context.resources.getQuantityString(R.plurals.life_in_minutes, minutes, minutes)

/** A Today row: title, one line under it (or none), and its control at the end. */
@Composable
private fun TodayRow(title: String, line: String?, control: @Composable () -> Unit = {}) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(16.dp))
        .heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        line?.let { Text(it, fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.padding(top = 2.dp)) }
    }
    control()
}

@Composable
private fun Action(label: String, filled: Boolean = false, onClick: () -> Unit) = Text(
    label,
    fontSize = 14.sp,
    fontWeight = FontWeight.SemiBold,
    color = if (filled) SageDeep else Sage,
    modifier = Modifier.clip(RoundedCornerShape(12.dp)).then(if (filled) Modifier.background(SageMist) else Modifier).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
)

@Composable
private fun Link(label: String, onClick: () -> Unit) = Text(
    label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
)

@Composable
private fun Callout(text: String) = Text(
    text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = PillHighText,
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PillHighBg).padding(horizontal = 12.dp, vertical = 10.dp),
)

/** One state on Now: [attention] tints it amber (something to do), else it's a quiet outline. */
class StatusChip(val text: String, val attention: Boolean = false, val onClick: () -> Unit)

/**
 * Now, under the header: what's on, as chips (alarms paused, a workout, fasting, a sick day, the
 * goal's progress, missing readings). Replaces a stack of banners; each opens or does its one thing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusChips(chips: List<StatusChip>, modifier: Modifier = Modifier.padding(horizontal = 16.dp)) {
    if (chips.isEmpty()) return
    FlowRow(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { c ->
            Text(
                c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (c.attention) PillHighText else SageDeep,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (c.attention) PillHighBg else Color.Transparent)
                    .border(1.dp, if (c.attention) Color.Transparent else outline(), RoundedCornerShape(50))
                    .clickable(onClick = c.onClick)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/** Now's chips from what's on. */
@Composable
fun statusChips(
    m: ActiveModes,
    paused: Instant?,
    goal: Pair<GoalTargets, Pair<Double?, Double?>>?,
    gap: Pair<Instant, Instant>?,
    ketonesDue: Boolean,
    onResume: () -> Unit,
    onToday: () -> Unit,
    onFitness: () -> Unit,
    onFillGap: () -> Unit,
    /** Long-acting: when it was due and how late it is now (BasalReminder.overdue). */
    basal: Pair<Instant, java.time.Duration>? = null,
    onBasal: () -> Unit = {},
    /** With gym days set: whether today is one (the kcal chip says which). */
    gymDay: Boolean? = null,
    steps: Long? = null,
): List<StatusChip> {
    val time = TimeFormat.of()
    val n = NumberFormat.getIntegerInstance()
    val list = mutableListOf<StatusChip>()
    paused?.let { list += StatusChip(stringResource(R.string.chip_paused, time.format(it)), attention = true, onClick = onResume) }
    basal?.let { (due, late) ->
        val text = if (late >= com.sukoon.app.reminders.BasalReminder.LATE) stringResource(R.string.chip_basal_late, late.toHours().toInt())
        else stringResource(R.string.chip_basal_due, time.format(due))
        list += StatusChip(text, attention = true, onClick = onBasal)
    }
    m.workoutUntil?.let { list += StatusChip(stringResource(R.string.chip_workout, time.format(it)), onClick = onToday) }
    if (m.workoutUntil == null && m.nightWatch) list += StatusChip(stringResource(R.string.chip_late_low, Modes.NIGHT_LOW), onClick = onToday)
    if (m.fastingNow) list += StatusChip(stringResource(R.string.chip_fasting, time.format(m.fasting.iftar)), onClick = onToday)
    if (m.sick) list += StatusChip(stringResource(if (ketonesDue) R.string.chip_ketones_due else R.string.chip_sick), attention = ketonesDue, onClick = onToday)
    goal?.let { (t, today) ->
        val (protein, kcal) = today
        if (t.kcal != null) {
            val text = stringResource(R.string.chip_kcal, n.format(kcal?.toInt() ?: 0), n.format(t.kcal))
            list += StatusChip(gymDay?.let { stringResource(if (it) R.string.chip_gym_day else R.string.chip_rest_day, text) } ?: text, onClick = onFitness)
        }
        if (t.proteinGrams != null) list += StatusChip(stringResource(R.string.chip_protein, n.format(protein?.toInt() ?: 0), t.proteinGrams), onClick = onFitness)
    }
    steps?.takeIf { it > 0 }?.let { list += StatusChip(stringResource(R.string.chip_steps, n.format(it)), onClick = onFitness) }
    gap?.let { (from, to) -> list += StatusChip(stringResource(R.string.chip_gap, time.format(from), time.format(to)), onClick = onFillGap) }
    return list
}
