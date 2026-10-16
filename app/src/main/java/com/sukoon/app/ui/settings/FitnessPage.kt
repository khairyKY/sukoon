package com.sukoon.app.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.modes.GoalKind
import com.sukoon.app.modes.Pace
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
                    Big(n.format(targets.proteinGrams), stringResource(R.string.fitness_protein))
                    targets.proteinPerMeal?.let { Big("${n.format(it.first)}–${n.format(it.last)}", stringResource(R.string.fitness_per_meal)) }
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
        ChoiceChips(Goals.proteinChoices(goal.kind), targets.proteinPerKg, { stringResource(R.string.fitness_per_kg, NumberFormat.getNumberInstance().apply { minimumFractionDigits = 1 }.format(it)) }) { keeper.goal = goal.copy(proteinPerKg = it) }
        Goals.changeChoices(goal.kind).takeIf { it.isNotEmpty() }?.let { choices ->
            Small(stringResource(if (goal.kind == GoalKind.CUT) R.string.fitness_deficit else R.string.fitness_surplus))
            ChoiceChips(choices, targets.change, { stringResource(R.string.fitness_kcal_change, (if (it > 0) "+" else "−") + n.format(kotlin.math.abs(it))) }) { keeper.goal = goal.copy(change = it) }
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

    if (targets?.kcal != null) GymDays(keeper, goal, targets.kcal)
    StepsToday()
    WeightSection(goal.kind, profile)

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
    RamadanPlace(keeper)
    Card {
        val auto = keeper.prayerPlace != null
        TimeRow(stringResource(R.string.fitness_fajr), f.suhoorEnds, enabled = !auto) { keeper.fasting = f.copy(suhoorEnds = it) }
        TimeRow(stringResource(R.string.fitness_maghrib), f.iftar, enabled = !auto) { keeper.fasting = f.copy(iftar = it) }
    }
    Small(stringResource(R.string.fitness_ramadan_note), muted = true)
    if (keeper.prayerPlace != null) Small(stringResource(R.string.ramadan_source), muted = true)

    Text(
        stringResource(R.string.today_why_link), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
        modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onWhy).padding(vertical = 8.dp),
    )
}

/**
 * Your weight, its weekly trend from the logbook's weigh-ins and, with a bulk or cut, whether that's
 * on pace. Logging opens the logbook's weight entry; the profile follows the latest weigh-in.
 */
@Composable
private fun WeightSection(kind: GoalKind, profile: Profile) {
    val container = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container
    val since = remember { java.time.Instant.now().minus(java.time.Duration.ofDays(28)).toEpochMilli() }
    val events by remember { container.logbookRepository.eventsSince(since) }.collectAsState(emptyList())
    val weighIns = events.filter { it.logType == LogEventType.WEIGHT && it.value != null }.map { java.time.Instant.ofEpochMilli(it.timestampMillis) to it.value!! }
    val trend = Goals.trend(weighIns, java.time.Instant.now())
    val kg = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }

    Label(stringResource(R.string.fitness_weight))
    Card {
        profile.weightKg?.let { Big(kg.format(it), stringResource(R.string.profile_kg)) }
        if (trend == null) {
            Text(stringResource(R.string.fitness_weight_need), fontSize = 12.5.sp, color = CaptionMuted)
        } else {
            val signed = (if (trend.kgPerWeek >= 0) "+" else "−") + NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2; minimumFractionDigits = 1 }.format(kotlin.math.abs(trend.kgPerWeek))
            Text(stringResource(R.string.fitness_weight_trend, signed, trend.days), fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onBackground)
            Goals.pace(kind, profile.weightKg, trend)?.let { pace ->
                val text = when (pace) {
                    Pace.ON_TRACK -> R.string.fitness_pace_on
                    Pace.SLOW -> if (kind == GoalKind.CUT) R.string.fitness_pace_cut_slow else R.string.fitness_pace_bulk_slow
                    Pace.FAST -> if (kind == GoalKind.CUT) R.string.fitness_pace_cut_fast else R.string.fitness_pace_bulk_fast
                }
                Text(stringResource(text), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (pace == Pace.ON_TRACK) SageDeep else com.sukoon.app.ui.theme.PillHighText)
            }
        }
        Text(
            stringResource(R.string.fitness_log_weight), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { container.requestedEntry.value = LogEventType.WEIGHT }.padding(vertical = 6.dp),
        )
    }
}

/** Gym days: tap the days you train; gym and rest days then get their own calories, the week the same. */
@Composable
private fun GymDays(keeper: ModesKeeper, goal: com.sukoon.app.modes.GoalSettings, kcal: Int) {
    val n = NumberFormat.getIntegerInstance()
    Label(stringResource(R.string.fitness_gym_days))
    val first = java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0L until 7L).map { first.plus(it) }.forEach { d ->
            val on = d in goal.gymDays
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Sage else MaterialTheme.colorScheme.surface)
                    .border(1.dp, outline(), RoundedCornerShape(12.dp))
                    .clickable { keeper.goal = goal.copy(gymDays = if (on) goal.gymDays - d else goal.gymDays + d) },
                contentAlignment = Alignment.Center,
            ) {
                Text(d.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (on) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onBackground)
            }
        }
    }
    if (goal.gymDays.isNotEmpty() && goal.gymDays.size < 7) {
        val gym = Goals.dayKcal(kcal, goal, true)
        val rest = Goals.dayKcal(kcal, goal, false)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DayCard(stringResource(R.string.fitness_gym_day), n.format(gym), "+" + n.format(gym - kcal), true, Modifier.weight(1f))
            DayCard(stringResource(R.string.fitness_rest_day), n.format(rest), "−" + n.format(kcal - rest), false, Modifier.weight(1f))
        }
        Small(stringResource(R.string.fitness_gym_note, n.format(kcal)), muted = true)
    } else {
        Small(stringResource(R.string.fitness_gym_hint), muted = true)
    }
}

@Composable
private fun DayCard(label: String, kcal: String, change: String, gym: Boolean, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(18.dp)).background(if (gym) com.sukoon.app.ui.theme.SageMist else MaterialTheme.colorScheme.surface)
        .border(1.dp, outline(), RoundedCornerShape(18.dp)).padding(14.dp),
) {
    Text(label.uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = if (gym) SageDeep else CaptionMuted)
    Text(kcal, fontFamily = HeadlineSerifFontFamily, fontSize = 30.sp, color = MaterialTheme.colorScheme.onBackground)
    Text(stringResource(R.string.fitness_kcal_change, change), fontSize = 12.sp, color = CaptionMuted)
}

/** Steps so far today, from Health Connect: they count as easy activity. */
@Composable
private fun StepsToday() {
    val health = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.healthConnect
    val steps by androidx.compose.runtime.produceState<Long?>(null) { value = runCatching { health.today().steps }.getOrNull() }
    val s = steps ?: return
    Label(stringResource(R.string.fitness_steps))
    Card {
        Big(NumberFormat.getIntegerInstance().format(s), stringResource(R.string.fitness_steps_unit))
        Text(stringResource(R.string.fitness_steps_note), fontSize = 12.5.sp, color = CaptionMuted)
    }
}

/**
 * Ramadan times from where you are: the phone's rough location (coarse is enough), asked for when you
 * turn it on, followed as you move (ModesKeeper.followPlace); Fajr and Maghrib are worked out every day
 * (PrayerTimes) by the chosen authority. Location off: pick the city you're in.
 */
@Composable
private fun RamadanPlace(keeper: ModesKeeper) {
    val context = LocalContext.current
    val place by keeper.place.collectAsState()
    var failed by remember { mutableStateOf(false) }
    fun granted() = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    androidx.compose.runtime.LaunchedEffect(Unit) { keeper.followPlace() }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> failed = !(ok && keeper.followPlace(start = true)) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ramadan_auto), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(if (place != null) R.string.ramadan_auto_on else R.string.ramadan_auto_off), fontSize = 12.sp, color = CaptionMuted)
        }
        androidx.compose.material3.Switch(
            checked = place != null,
            onCheckedChange = { on ->
                failed = false
                if (!on) keeper.prayerPlace = null
                else if (granted()) failed = !keeper.followPlace(start = true)
                else ask.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Sage),
        )
    }
    val pickCity: @Composable (com.sukoon.app.modes.PrayerTimes.City?) -> Unit = { now ->
        ChoiceChips<com.sukoon.app.modes.PrayerTimes.City?>(com.sukoon.app.modes.PrayerTimes.City.entries, now, { cityName(it) }) { c ->
            c?.let { keeper.prayerPlace = ModesKeeper.PrayerPlace(it.lat, it.lng, place?.method ?: com.sukoon.app.modes.PrayerTimes.Method.EGYPT) }
            failed = false
        }
    }
    if (failed && place == null) {
        Small(stringResource(R.string.ramadan_no_place), muted = true)
        pickCity(null)
    }
    place?.let { p ->
        // Where the times are for, so you can see they follow you.
        val city = com.sukoon.app.modes.PrayerTimes.nearest(p.lat, p.lng)
        val coords = String.format(java.util.Locale.getDefault(), "%.2f, %.2f", p.lat, p.lng)
        Text(city?.let { cityName(it) + " · " + coords } ?: coords, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))
        if (granted()) Small(stringResource(R.string.ramadan_follows), muted = true)
        else {
            Small(stringResource(R.string.ramadan_pick_city), muted = true)
            pickCity(city)
        }
        Small(stringResource(R.string.ramadan_method))
        ChoiceChips(com.sukoon.app.modes.PrayerTimes.Method.entries, p.method, {
            stringResource(when (it) {
                com.sukoon.app.modes.PrayerTimes.Method.EGYPT -> R.string.ramadan_method_egypt
                com.sukoon.app.modes.PrayerTimes.Method.MWL -> R.string.ramadan_method_mwl
                com.sukoon.app.modes.PrayerTimes.Method.UMM_AL_QURA -> R.string.ramadan_method_uq
            })
        }) { m -> keeper.prayerPlace = p.copy(method = m) }
    }
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
private fun TimeRow(label: String, value: LocalTime, enabled: Boolean = true, onPick: (LocalTime) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) {
            TimePickerDialog(context, { _, h, min -> onPick(LocalTime.of(h, min)) }, value.hour, value.minute, DateFormat.is24HourFormat(context)).show()
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(TimeFormat.of().format(value), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Sage)
    }
}

@Composable
private fun cityName(city: com.sukoon.app.modes.PrayerTimes.City?): String = stringResource(
    when (city) {
        com.sukoon.app.modes.PrayerTimes.City.ALEXANDRIA -> R.string.ramadan_city_alexandria
        com.sukoon.app.modes.PrayerTimes.City.TANTA -> R.string.ramadan_city_tanta
        else -> R.string.ramadan_city_cairo
    },
)
