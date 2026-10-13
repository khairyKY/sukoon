package com.sukoon.app.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.insulin.Profile
import com.sukoon.app.modes.GoalKind
import com.sukoon.app.modes.Goals
import com.sukoon.app.modes.Modes
import com.sukoon.app.modes.ModesKeeper
import com.sukoon.app.platform.TimeFormat
import com.sukoon.app.ui.components.ChoiceChips
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import java.text.NumberFormat
import java.time.LocalTime

/**
 * You → Fitness & food: what lasts. Your goal and its daily numbers, the training default (the
 * meal after a workout) and your Ramadan times. What you switch on for a while is Now → Today; the
 * reasons and sources are the Guide's "Workouts, fasting, sick days and your goal".
 */
@Composable
fun FitnessPage(keeper: ModesKeeper, profile: Profile, onEditProfile: () -> Unit, onWhy: () -> Unit) {
    val goal by keeper.goalFlow.collectAsState()
    val n = NumberFormat.getIntegerInstance()

    Label(stringResource(R.string.fitness_goal))
    ChoiceChips(GoalKind.entries, goal.kind, { stringResource(goalName(it)) }) { keeper.goal = goal.copy(kind = it) }
    val targets = Goals.targets(profile, goal)
    if (targets != null) {
        Card {
            if (targets.kcal != null && targets.proteinGrams != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Big(n.format(targets.kcal), stringResource(R.string.fitness_kcal))
                    Big(targets.proteinGrams.toString(), stringResource(R.string.fitness_protein))
                    targets.proteinPerMeal?.let { Big("${it.first}–${it.last}", stringResource(R.string.fitness_per_meal)) }
                }
                Text(
                    stringResource(if (targets.estimated) R.string.fitness_from_estimate else R.string.fitness_from_yours, n.format(targets.maintenance)),
                    fontSize = 12.5.sp, color = CaptionMuted,
                )
                Goals.weeklyRate(goal.kind, profile.weightKg)?.let { r ->
                    val kg = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
                    Text(
                        stringResource(if (goal.kind == GoalKind.CUT) R.string.fitness_rate_lose else R.string.fitness_rate_gain, kg.format(r.start), kg.format(r.endInclusive)),
                        fontSize = 12.5.sp, color = CaptionMuted,
                    )
                }
                if (goal.kind == GoalKind.CUT) Text(stringResource(R.string.fitness_cut_note), fontSize = 12.5.sp, color = com.sukoon.app.ui.theme.PillHighText)
            } else {
                Text(stringResource(R.string.fitness_need_profile), fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    stringResource(R.string.fitness_add_profile), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onEditProfile).padding(vertical = 6.dp),
                )
            }
        }
        Small(stringResource(R.string.fitness_protein_label))
        ChoiceChips(Goals.proteinChoices(goal.kind), targets.proteinPerKg, { stringResource(R.string.fitness_per_kg, it.toString()) }) { keeper.goal = goal.copy(proteinPerKg = it) }
        Goals.changeChoices(goal.kind).takeIf { it.isNotEmpty() }?.let { choices ->
            Small(stringResource(if (goal.kind == GoalKind.CUT) R.string.fitness_deficit else R.string.fitness_surplus))
            ChoiceChips(choices, targets.change, { stringResource(R.string.fitness_kcal_change, (if (it > 0) "+" else "−") + kotlin.math.abs(it)) }) { keeper.goal = goal.copy(change = it) }
        }
        var typed by remember(goal.kind) { mutableStateOf(goal.maintenance?.toString().orEmpty()) }
        OutlinedTextField(
            typed,
            { v -> typed = v.filter(Char::isDigit).take(4); keeper.goal = goal.copy(maintenance = typed.toIntOrNull()?.takeIf { it in 1200..6000 }) },
            label = { Text(stringResource(R.string.fitness_maintenance_field)) },
            placeholder = { Goals.maintenance(profile)?.let { Text("~" + n.format(it)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }

    Label(stringResource(R.string.fitness_training))
    var cut by remember { mutableStateOf(keeper.afterWorkoutCut) }
    Small(stringResource(R.string.fitness_after_meal))
    ChoiceChips(listOf(0, 25, 50), cut, { if (it == 0) stringResource(R.string.fitness_as_usual) else stringResource(R.string.fitness_less, it) }) {
        cut = it
        keeper.afterWorkoutCut = it
    }
    Small(stringResource(R.string.fitness_training_note, Modes.NIGHT_LOW), muted = true)

    Label(stringResource(R.string.fitness_ramadan))
    val active by keeper.active.collectAsState()
    val f = active.fasting
    Card {
        TimeRow(stringResource(R.string.fitness_fajr), f.suhoorEnds) { keeper.fasting = f.copy(suhoorEnds = it) }
        TimeRow(stringResource(R.string.fitness_maghrib), f.iftar) { keeper.fasting = f.copy(iftar = it) }
    }
    Small(stringResource(R.string.fitness_ramadan_note), muted = true)

    Text(
        stringResource(R.string.today_why_link), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
        modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onWhy).padding(vertical = 8.dp),
    )
}

/** The goal's name, for chips and the You hub. */
fun goalName(kind: GoalKind): Int = when (kind) {
    GoalKind.NONE -> R.string.goal_none
    GoalKind.MAINTAIN -> R.string.goal_maintain
    GoalKind.BULK -> R.string.goal_bulk
    GoalKind.CUT -> R.string.goal_cut
}

@Composable
private fun Label(text: String) = Text(
    text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted, letterSpacing = 1.sp,
    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
)

@Composable
private fun Small(text: String, muted: Boolean = false) = Text(
    text, fontSize = 13.sp, fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold, color = CaptionMuted,
    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
)

@Composable
private fun Card(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, outline(), RoundedCornerShape(18.dp)).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) { content() }

@Composable
private fun Big(value: String, unit: String) = Column {
    Text(value, fontFamily = HeadlineSerifFontFamily, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
    Text(unit, fontSize = 12.sp, color = CaptionMuted)
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
