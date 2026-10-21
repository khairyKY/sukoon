package com.sukoon.app.ui.logbook

import kotlinx.coroutines.flow.first
import com.sukoon.app.ui.theme.PillLowBg

import com.sukoon.app.ui.theme.SageMist
import androidx.compose.runtime.mutableStateListOf
import com.sukoon.app.food.PlateItem
import com.sukoon.app.food.MyFoods
import com.sukoon.app.food.Food
import android.app.TimePickerDialog
import android.net.Uri
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.sukoon.app.ui.components.SukoonSheet
import com.sukoon.app.ui.components.SukoonTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.sukoon.app.R
import com.sukoon.app.ai.CarbEstimate
import com.sukoon.app.ai.MealPhoto
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Motion
import com.sukoon.app.ui.theme.Motion.staggerIn
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.StateLow
import com.sukoon.app.ui.widget.arrow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sukoon.app.health.HealthConnectSync
import androidx.compose.ui.res.pluralStringResource
import com.sukoon.app.insulin.InjectionSites
import com.sukoon.app.insulin.FingerSites
import com.sukoon.app.insulin.finger
import com.sukoon.app.insulin.Dose
import com.sukoon.app.insulin.DoseSettings
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.insulin.injectionSite

/** What one amount box takes: decimals or whole numbers, how many digits, and its one-tap amounts. */
internal enum class AmountField(val decimals: Boolean, val maxWhole: Int, val quick: List<Int>) {
    CARBS(false, 3, listOf(15, 30, 45, 60, 75)),
    RAPID(true, 2, listOf(1, 2, 3, 4, 6)),
    LONG(true, 3, listOf(10, 14, 18, 22)),
    METER(false, 3, emptyList()),
    MINUTES(false, 3, listOf(15, 30, 45, 60)),
    FIBER(false, 3, emptyList()),
    PROTEIN(false, 3, emptyList()),
    FAT(false, 3, emptyList()),
    KCAL(false, 4, emptyList()),
    KETONES(true, 1, emptyList()),
    WEIGHT(true, 3, emptyList()),
}

private fun mainField(type: LogEventType): AmountField? = when (type) {
    LogEventType.CARB -> AmountField.CARBS
    LogEventType.INSULIN -> AmountField.RAPID
    LogEventType.BASAL -> AmountField.LONG
    LogEventType.FINGERSTICK -> AmountField.METER
    LogEventType.ACTIVITY -> AmountField.MINUTES
    LogEventType.NOTE -> null
    LogEventType.KETONE -> AmountField.KETONES
    LogEventType.WEIGHT -> AmountField.WEIGHT
}

private val KINDS = listOf(R.string.workout_strength, R.string.workout_walk, R.string.workout_run, R.string.workout_bike, R.string.workout_swim, R.string.workout_hiit, R.string.workout_football, R.string.workout_yoga, R.string.workout_other)
private val EFFORTS = listOf(R.string.effort_easy, R.string.effort_moderate, R.string.effort_hard)

/**
 * An activity: what and how hard (kept in its note as "Strength training · Hard", the way workouts
 * from MyFitnessPal arrive), a word before starting from the glucose now, and what saving it changes.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ActivityPanel(note: String, glucose: Int?, onNote: (String) -> Unit) {
    val kinds = KINDS.map { stringResource(it) }
    val efforts = EFFORTS.map { stringResource(it) }
    val parts = note.split(" · ")
    val kind = parts.firstOrNull()?.takeIf { it in kinds }
    val effort = parts.getOrNull(1)?.takeIf { it in efforts }
    fun set(k: String?, e: String?) = onNote(listOfNotNull(k, e).joinToString(" · "))
    @Composable
    fun Chips(options: List<String>, picked: String?, onPick: (String) -> Unit) =
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                val on = o == picked
                Text(
                    o, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).then(if (on) Modifier.background(Sage) else Modifier.border(1.dp, outline(), RoundedCornerShape(12.dp)))
                        .clickable { onPick(o) }.heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(stringResource(R.string.activity_what), Modifier.padding(top = 4.dp))
        Chips(kinds, kind) { set(it, effort) }
        Eyebrow(stringResource(R.string.activity_effort))
        Chips(efforts, effort) { set(kind ?: kinds.last(), it) }
        com.sukoon.app.modes.Modes.beforeWorkout(glucose)?.let { b ->
            val text = when (b) {
                com.sukoon.app.modes.Modes.BeforeWorkout.CARBS_AND_WAIT -> R.string.before_carbs_wait
                com.sukoon.app.modes.Modes.BeforeWorkout.SMALL_CARBS -> R.string.before_small_carbs
                com.sukoon.app.modes.Modes.BeforeWorkout.KETONES_FIRST -> R.string.before_ketones
            }
            Text(stringResource(text, glucose ?: 0), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = PillHighText,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PillHighBg).padding(horizontal = 14.dp, vertical = 12.dp))
        }
        val cut = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.modes.afterWorkoutCut
        Text(
            if (cut > 0) stringResource(R.string.activity_after, com.sukoon.app.modes.Modes.NIGHT_LOW, cut) else stringResource(R.string.activity_after_no_cut, com.sukoon.app.modes.Modes.NIGHT_LOW),
            fontSize = 13.sp, lineHeight = 18.sp, color = CaptionMuted,
        )
    }
}

/** Under a dose suggestion: long-acting not logged for 26 h (when you log it at all), so highs may keep coming. */
@Composable
private fun NoBasalLately() {
    val logbook = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.logbookRepository
    val last by remember { logbook.latest(LogEventType.BASAL) }.collectAsState(initial = null as EventEntity?)
    val hours = last?.let { java.time.Duration.between(Instant.ofEpochMilli(it.timestampMillis), Instant.now()).toHours() } ?: return
    if (hours < 26) return
    Text(stringResource(R.string.basal_caution, hours.toInt()), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = PillHighText,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PillHighBg).padding(horizontal = 14.dp, vertical = 12.dp))
}

/** Logging long-acting with one logged in the last 8 hours: the double-dose guard. */
@Composable
private fun AlreadyTaken() {
    val logbook = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.logbookRepository
    val last by remember { logbook.latest(LogEventType.BASAL) }.collectAsState(initial = null as EventEntity?)
    val e = last?.let { com.sukoon.app.reminders.BasalReminder.recent(listOf(it), Instant.now()) } ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PillHighBg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.basal_already_title, formatAmountLocalized(e.value ?: 0.0), com.sukoon.app.platform.TimeFormat.of().format(Instant.ofEpochMilli(e.timestampMillis))),
            fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = PillHighText,
        )
        Text(stringResource(R.string.basal_already_body), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** "Treating a low": carbs for a low, kept out of meal maths and counted in the low-treatment insight. */
@Composable
private fun TreatmentToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) PillLowBg else MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(14.dp)).clickable { onChange(!on) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.entry_treating_low), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = if (on) PillLowText else MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.entry_treating_low_body), fontSize = 12.sp, color = CaptionMuted)
        }
        androidx.compose.material3.Switch(checked = on, onCheckedChange = onChange, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = PillLowText))
    }
}

/** Which editor to open: a new entry (maybe repeating one), an existing one, or insulin for an imported meal. */
internal data class EditorRequest(
    val type: LogEventType,
    val existing: EventEntity? = null,
    val prefill: EventEntity? = null,
    val forMeal: EventEntity? = null,
    /** Opened to log what treated a low. */
    val treatment: Boolean = false,
    /** From the eat box: its foods on the plate, the dose said with them, its photo, and what else was said (saved with the meal). */
    val plate: List<PlateItem> = emptyList(),
    val insulin: Double? = null,
    val photos: List<ByteArray> = emptyList(),
    val extras: List<EntryDraft> = emptyList(),
)

private val TILES = listOf(LogEventType.CARB, LogEventType.INSULIN, LogEventType.FINGERSTICK, LogEventType.BASAL, LogEventType.ACTIVITY, LogEventType.NOTE)

/**
 * Add, step one (design A1): the moment's context, "Again" for a recent entry (it opens filled in,
 * one more tap saves it), and a tile per kind of entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSheet(
    repeats: List<EventEntity>,
    glucoseNow: GlucoseReading?,
    insulinOnBoard: Double,
    mealsFromApps: Boolean,
    onPick: (LogEventType, EventEntity?) -> Unit,
    onDismiss: () -> Unit,
    /** A sick day, or high for a while: ketones get a tile too. */
    showKetones: Boolean = false,
    /** Meals you log by name, to start from. */
    usual: List<UsualMeal> = emptyList(),
    /** The eat box's words, voice, photo or barcode; [ai] (a Gemini key) offers the mic and the photo. */
    onEat: (EatInput) -> Unit = {},
    ai: Boolean = false,
) {
    SukoonSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.logbook_sheet_title_new), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, lineHeight = 31.sp, color = MaterialTheme.colorScheme.onBackground)
            ContextPills(glucoseNow, insulinOnBoard, Modifier.padding(top = 8.dp))
            // Say it, in Sotto's colours (Kai's own dictation app), back where it was; the box below takes words, a photo or a barcode.
            if (ai) com.sukoon.app.ui.components.SottoBanner(stringResource(R.string.voice_title), stringResource(R.string.voice_hint), { onEat(EatInput.Voice) }, Modifier.padding(top = 14.dp).fillMaxWidth())
            EatBox(ai, onEat, Modifier.padding(top = 14.dp))
            if (usual.isNotEmpty()) {
                Eyebrow(stringResource(R.string.entry_usual), Modifier.padding(top = 18.dp))
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    usual.forEach { m -> UsualChip(m) { onPick(LogEventType.CARB, m.last.copy(value = m.carbs)) } }
                }
            }
            if (repeats.isNotEmpty()) {
                Eyebrow(stringResource(R.string.entry_again), Modifier.padding(top = 18.dp))
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeats.forEach { e ->
                        PillButton(
                            label = "${e.note ?: typeLabel(e.logType)} ${formatAmountLocalized(e.value ?: 0.0)}${unitLabel(e.logType)}",
                            dot = colorForLogEventType(e.logType),
                        ) { onPick(e.logType, e) }
                    }
                }
            }
            Eyebrow(stringResource(R.string.entry_new), Modifier.padding(top = 18.dp))
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (if (showKetones) TILES + LogEventType.KETONE else TILES).chunked(3).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // The tiles stagger in as the sheet rises (reduced: one short fade).
                        row.forEachIndexed { c, type -> TypeTile(type, filled = type == LogEventType.CARB, Modifier.weight(1f).staggerIn(r * 3 + c)) { onPick(type, null) } }
                    }
                }
            }
            if (mealsFromApps) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_sync), contentDescription = null, tint = SageDeep, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.entry_meals_arrive), fontSize = 13.sp, color = CaptionMuted)
                }
            }
        }
    }
}

/** "Koshari · 85 g" and, under it, the last time: "6 u, 128 two hours later". */
@Composable
private fun UsualChip(m: UsualMeal, onClick: () -> Unit) {
    val logbook = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.glucoseRepository
    val after by androidx.compose.runtime.produceState<Int?>(null, m.last.id) {
        val at = m.last.timestampMillis + 2 * 3_600_000L
        value = runCatching {
            logbook.readingsBetween(at - 15 * 60_000L, at + 15 * 60_000L).first().minByOrNull { kotlin.math.abs(it.timestamp.toEpochMilli() - at) }?.glucoseMgDl
        }.getOrNull()
    }
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("${m.name} · ${formatAmountLocalized(m.carbs)}${stringResource(R.string.logbook_unit_grams)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        val last = listOfNotNull(
            m.lastDose?.let { formatAmountLocalized(it) + stringResource(R.string.logbook_unit_units) },
            after?.let { stringResource(R.string.entry_usual_after, String.format(Locale.getDefault(), "%d", it)) },
        )
        if (last.isNotEmpty()) Text(stringResource(R.string.entry_usual_last, last.joinToString(", ")), fontSize = 12.sp, color = CaptionMuted)
    }
}

@Composable
private fun TypeTile(type: LogEventType, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (icon, tint) = when (type) {
        LogEventType.CARB -> R.drawable.ic_food to Color.White
        LogEventType.INSULIN -> R.drawable.ic_insulin to MaterialTheme.colorScheme.onBackground
        LogEventType.FINGERSTICK -> R.drawable.ic_drop to PillLowText
        LogEventType.BASAL -> R.drawable.ic_moon to SageDeep
        LogEventType.ACTIVITY -> R.drawable.ic_walk to PillHighText
        LogEventType.NOTE -> R.drawable.ic_note to CaptionMuted
        LogEventType.KETONE -> R.drawable.ic_drop to PillHighText
        LogEventType.WEIGHT -> R.drawable.ic_scale to SageDeep
    }
    Column(
        modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (filled) Modifier.background(Sage) else Modifier.background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(18.dp)))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Text(typeLabel(type), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * Add or edit, step two (design A2): the amount on a big pad. A meal takes the rapid insulin for it
 * on the same screen (and when it was injected); a MyFitnessPal meal brings its own carbs and every
 * nutrient, so only its insulin is typed. One-tap amounts fill the box; only Save logs.
 */
@Composable
internal fun EntryEditor(
    request: EditorRequest,
    glucoseNow: GlucoseReading?,
    insulinOnBoard: Double,
    appMeals: List<EventEntity>,
    photoFiles: List<File>,
    siteHistory: List<EventEntity>,
    doseSettings: DoseSettings,
    /** The last value under 70 in the past 2 hours: then no dose is suggested. */
    lowAt: java.time.Instant?,
    onEstimateCarbs: (suspend (String, List<ByteArray>) -> CarbEstimate)?,
    onSave: (EntryDraft) -> Unit,
    /** The entry, and its photos when they changed (null: as they were). */
    onUpdate: (EventEntity, List<ByteArray>?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** A new meal by voice instead (the eat box's Say it); null without a Gemini key. */
    onVoice: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EditorContent(request, glucoseNow, insulinOnBoard, appMeals, photoFiles, siteHistory, doseSettings, lowAt, onEstimateCarbs, onSave, onUpdate, onDelete, onDismiss, onVoice)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(
    request: EditorRequest,
    glucoseNow: GlucoseReading?,
    insulinOnBoard: Double,
    appMeals: List<EventEntity>,
    photoFiles: List<File>,
    siteHistory: List<EventEntity>,
    doseSettings: DoseSettings,
    lowAt: java.time.Instant?,
    onEstimateCarbs: (suspend (String, List<ByteArray>) -> CarbEstimate)?,
    onSave: (EntryDraft) -> Unit,
    onUpdate: (EventEntity, List<ByteArray>?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    onVoice: (() -> Unit)? = null,
) {
    val existing = request.existing
    val base = existing ?: request.prefill
    // A dose repeated with "Again" already has its amount: straight to where it goes.
    val repeatedDose = existing == null && request.prefill?.value != null && (request.type == LogEventType.INSULIN || request.type == LogEventType.BASAL)
    var type by remember { mutableStateOf(request.type) }
    var forMeal by remember { mutableStateOf(request.forMeal) }
    val values = remember {
        mutableStateMapOf<AmountField, String>().apply {
            mainField(request.type)?.let { put(it, base?.value?.let(::formatAmount) ?: "") }
            if (existing != null) {
                existing.fiber?.let { put(AmountField.FIBER, formatAmount(it)) }
                existing.protein?.let { put(AmountField.PROTEIN, formatAmount(it)) }
                existing.fat?.let { put(AmountField.FAT, formatAmount(it)) }
                existing.kcal?.let { put(AmountField.KCAL, formatAmount(it)) }
            }
            request.insulin?.let { put(AmountField.RAPID, formatAmount(it)) }
        }
    }
    // A new meal starts from its foods (design "Meal · start from foods"): no pad until you want one.
    val freshMeal = request.type == LogEventType.CARB && existing == null && request.forMeal == null && request.prefill?.value == null
    var active by remember { mutableStateOf(if (request.forMeal != null) AmountField.RAPID else if (repeatedDose || freshMeal) null else mainField(request.type)) }
    val plate = remember { mutableStateListOf<PlateItem>().apply { addAll(request.plate) } }
    val myFoods = MyFoods.get(LocalContext.current)
    var picking by remember { mutableStateOf<Boolean?>(null) } // the food picker: false = search, true = scan first
    var plateNote by remember { mutableStateOf("") }
    var site by remember { mutableStateOf(existing?.injectionSite) }
    var siteOpen by remember { mutableStateOf(repeatedDose) }
    var finger by remember { mutableStateOf(existing?.finger) }
    // Carbs for a low aren't a meal: on when asked for, or when logged while under 70.
    var treatment by remember { mutableStateOf(existing?.treatment ?: (request.treatment || (glucoseNow?.glucoseMgDl ?: 999) < 70)) }
    var fingerOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(base?.note ?: "") }
    var preBolus by remember { mutableIntStateOf(0) }
    var minutesAgo by remember { mutableIntStateOf(if (existing == null) 0 else -1) }
    var pickedAt by remember { mutableStateOf(existing?.let { Instant.ofEpochMilli(it.timestampMillis) } ?: Instant.now()) }
    // Every photo of the meal, as many as you like (the ones it had first); the AI estimate reads them all.
    val photos = remember { mutableStateListOf<ByteArray>().apply { addAll(photoFiles.mapNotNull { runCatching { it.readBytes() }.getOrNull() }); addAll(request.photos) } }
    var photosChanged by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(existing?.let { it.fiber != null || it.protein != null || it.fat != null || it.kcal != null } ?: false) }
    var showAppMeals by remember { mutableStateOf(false) }
    var showEstimate by remember { mutableStateOf(false) }

    fun value(field: AmountField) = values[field]?.toDoubleOrNull()?.takeIf { it > 0 }

    /** The plate's sums into the amounts, and its foods as the meal's name (unless you named it). */
    fun platePut() {
        val sums = plateSums(plate)
        fun put(field: AmountField, x: Double) { values[field] = if (x > 0) Math.round(x).toString() else "" }
        put(AmountField.CARBS, sums[Food.CARBS])
        put(AmountField.FIBER, sums[1])
        put(AmountField.PROTEIN, sums[3])
        put(AmountField.FAT, sums[4])
        put(AmountField.KCAL, sums[Food.KCAL])
        val names = plate.joinToString(", ") { it.food.name }
        if (note.isBlank() || note == plateNote) note = names
        plateNote = names
    }
    // The eat box's foods: their sums and names in the boxes before anything reads them.
    remember { if (request.plate.isNotEmpty()) platePut() }
    fun at(): Instant = if (minutesAgo >= 0) Instant.now().minusSeconds(minutesAgo * 60L) else pickedAt
    val meal = forMeal
    val isMeal = type == LogEventType.CARB && meal == null
    val main = mainField(type)
    val rapid = value(AmountField.RAPID)
    val amount = main?.let(::value)
    // Which insulin's rotation "Where?" follows: the dose's own kind, or a meal's rapid once it has one.
    val siteType = when {
        meal != null -> LogEventType.INSULIN
        type == LogEventType.INSULIN || type == LogEventType.BASAL -> type
        isMeal && existing == null && rapid != null -> LogEventType.INSULIN
        else -> null
    }
    // Beta: what the carbs, the glucose now and the insulin still working add up to (new rapid doses only).
    val advice = if (doseSettings.enabled && existing == null && (meal != null || isMeal || type == LogEventType.INSULIN)) {
        val carbs = if (meal != null) meal.value.takeUnless { meal.summary } else if (isMeal) value(AmountField.CARBS) else null
        val hour = (if (meal != null) Instant.ofEpochMilli(meal.timestampMillis) else at()).atZone(ZoneId.systemDefault()).hour
        val keeper = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.modes
        val mealAt = if (meal != null) Instant.ofEpochMilli(meal.timestampMillis) else if (isMeal) at() else null
        val change = com.sukoon.app.modes.Modes.doseChange(keeper.now(), Instant.now(), mealAt, ZoneId.systemDefault(), keeper.afterWorkoutCut)
        Dose.advise(doseSettings, InsightEngine.slotFor(hour), carbs, glucoseNow?.glucoseMgDl, glucoseNow?.trend, insulinOnBoard, lowAt, change = change)
    } else {
        null
    }
    val valid = when {
        meal != null -> rapid != null
        type == LogEventType.NOTE -> note.isNotBlank()
        type == LogEventType.FINGERSTICK -> amount != null && amount in 20.0..600.0 // meters read LO/HI outside this
        type == LogEventType.WEIGHT -> amount != null && amount in 20.0..300.0 // the profile's range
        type == LogEventType.KETONE -> amount != null && amount in 0.0..8.0 // blood ketone meters read up to 8 mmol/L
        else -> amount != null
    }
    val units = stringResource(R.string.logbook_unit_units)
    val saveLabel = when {
        existing != null -> stringResource(R.string.entry_save_changes)
        !valid && type != LogEventType.NOTE -> stringResource(R.string.entry_type_amount)
        meal != null -> stringResource(R.string.entry_add_to_meal, formatAmountLocalized(rapid ?: 0.0), mealTitle(meal))
        isMeal && rapid != null -> stringResource(R.string.entry_save_meal_insulin, formatAmountLocalized(amount ?: 0.0), formatAmountLocalized(rapid))
        type == LogEventType.NOTE -> stringResource(R.string.entry_save)
        else -> stringResource(R.string.entry_save_amount, formatAmountLocalized(amount ?: 0.0) + unitLabel(type))
    }

    fun save() {
        if (!valid) return
        if (existing != null) {
            onUpdate(
                existing.copy(
                    value = if (type == LogEventType.NOTE) null else amount,
                    note = note.ifBlank { null },
                    timestampMillis = at().toEpochMilli(),
                    fiber = value(AmountField.FIBER),
                    protein = value(AmountField.PROTEIN),
                    fat = value(AmountField.FAT),
                    kcal = value(AmountField.KCAL),
                    site = when {
                        siteType != null -> site?.name
                        type == LogEventType.FINGERSTICK -> finger?.name
                        else -> existing.site
                    },
                    treatment = treatment && type == LogEventType.CARB,
                ),
                photos.toList().takeIf { photosChanged },
            )
            return
        }
        if (meal != null) {
            onSave(EntryDraft(LogEventType.INSULIN, rapid, null, at(), site = site, mealId = meal.id))
            return
        }
        val nutrients = Nutrients(fiber = value(AmountField.FIBER), protein = value(AmountField.PROTEIN), fat = value(AmountField.FAT), kcal = value(AmountField.KCAL))
        onSave(
            EntryDraft(
                type = type,
                amount = if (type == LogEventType.NOTE) null else amount,
                note = note.ifBlank { null },
                at = at(),
                photos = if (isMeal) photos.toList() else emptyList(),
                insulin = rapid.takeIf { isMeal },
                preBolusMinutes = preBolus,
                nutrients = nutrients.takeIf { isMeal && it != Nutrients() },
                site = site.takeIf { siteType != null },
                finger = finger.takeIf { type == LogEventType.FINGERSTICK },
                treatment = treatment && type == LogEventType.CARB,
            ),
        )
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(R.drawable.ic_close, stringResource(R.string.entry_close), onDismiss)
            Text(
                if (meal != null) stringResource(R.string.entry_insulin_for, mealTitle(meal)) else typeLabel(type),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = if (meal != null && mealTitle(meal).length > 18) 21.sp else 26.sp,
                lineHeight = if (meal != null && mealTitle(meal).length > 18) 26.sp else 31.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            WhenButton(minutesAgo, pickedAt, onAgo = { minutesAgo = it }, onPicked = { pickedAt = it; minutesAgo = -1 })
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ContextPills(glucoseNow.takeIf { existing == null }, if (type == LogEventType.INSULIN || isMeal || meal != null) insulinOnBoard else 0.0)
            if (type == LogEventType.CARB && meal == null) TreatmentToggle(treatment) { treatment = it }
            if (type == LogEventType.BASAL && existing == null) AlreadyTaken()
            val starting = isMeal && existing == null && plate.isEmpty() && values[AmountField.CARBS].isNullOrEmpty() && active == null
            when {
                starting -> MealStart(
                    onSearch = { picking = false },
                    onScan = { picking = true },
                    onUsual = { item -> plate += item; myFoods.remember(item); platePut() },
                )
                meal != null -> {
                    AppMealCard(meal)
                    ValueCard(stringResource(R.string.logbook_type_insulin), values[AmountField.RAPID].orEmpty(), units, active == AmountField.RAPID, Modifier.fillMaxWidth()) { active = AmountField.RAPID }
                }
                isMeal -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ValueCard(stringResource(R.string.entry_carbs), values[AmountField.CARBS].orEmpty(), stringResource(R.string.logbook_unit_grams), active == AmountField.CARBS, Modifier.weight(1f)) { active = AmountField.CARBS }
                    ValueCard(stringResource(R.string.logbook_type_insulin), values[AmountField.RAPID].orEmpty(), units, active == AmountField.RAPID, Modifier.weight(1f)) { active = AmountField.RAPID }
                }
                main != null -> ValueCard(fieldLabel(type), values[main].orEmpty(), unitLabel(type).trim(), active == main, Modifier.fillMaxWidth()) { active = main }
                else -> SukoonTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(R.string.logbook_note_hint),
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (type == LogEventType.ACTIVITY) ActivityPanel(note, glucoseNow?.glucoseMgDl) { note = it }
            if (advice != null) NoBasalLately()
            advice?.let { a ->
                DoseLine(a) { units ->
                    values[AmountField.RAPID] = formatAmount(units)
                    active = AmountField.RAPID
                }
            }
            if (isMeal && (rapid != null || active == AmountField.RAPID)) {
                // Before the meal, with it, or after it when it was forgotten (minutes before; negative is after).
                val before = listOf(20, 10, 0)
                val after = listOf(-15, -30, -60)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceRow(
                        listOf(stringResource(R.string.logbook_prebolus_min, 20), stringResource(R.string.logbook_prebolus_min, 10), stringResource(R.string.logbook_prebolus_with)),
                        selected = before.indexOf(preBolus),
                    ) { preBolus = before[it] }
                    ChoiceRow(after.map { stringResource(R.string.entry_dose_after, -it) }, selected = after.indexOf(preBolus)) { preBolus = after[it] }
                }
            }
            if (type == LogEventType.FINGERSTICK && glucoseNow != null && existing == null) {
                Text(stringResource(R.string.entry_sensor_now, String.format(Locale.getDefault(), "%d", glucoseNow.glucoseMgDl)), fontSize = 13.5.sp, color = CaptionMuted)
            }
            val field = active
            if (field != null && field.quick.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    field.quick.forEach { n ->
                        PillButton(String.format(Locale.getDefault(), "%d", n), Modifier.weight(1f)) {
                            values[field] = n.toString()
                            // A one-tap dose is a whole dose: on to where it goes.
                            if (field == AmountField.RAPID || field == AmountField.LONG) {
                                active = null
                                siteOpen = true
                            }
                        }
                    }
                }
            }
            if (siteType != null) {
                val history = remember(siteHistory, existing) { siteHistory.filter { it.id != existing?.id } }
                WherePanel(
                    site = site,
                    suggested = remember(history, siteType) { InjectionSites.next(history, siteType) },
                    lastUsed = { InjectionSites.lastUsed(history, siteType, it) },
                    open = siteOpen,
                    onOpen = {
                        siteOpen = true
                        // With no amount yet the pad stays: the amount comes first.
                        if ((if (meal != null || isMeal) rapid else amount) != null) active = null
                    },
                    onPick = { site = it },
                    onSkip = {
                        site = null
                        siteOpen = false
                    },
                    name = { siteName(it) },
                    picture = { selected, next, pick -> BodyMap(selected, next, pick, Modifier.fillMaxWidth()) },
                )
            }
            if (type == LogEventType.FINGERSTICK) {
                val history = remember(siteHistory, existing) { siteHistory.filter { it.id != existing?.id } }
                WherePanel(
                    site = finger,
                    suggested = remember(history) { FingerSites.next(history) },
                    lastUsed = { FingerSites.lastUsed(history, it) },
                    open = fingerOpen,
                    onOpen = {
                        fingerOpen = true
                        if (amount != null) active = null
                    },
                    onPick = { finger = it },
                    onSkip = {
                        finger = null
                        fingerOpen = false
                    },
                    name = { fingerName(it) },
                    picture = { selected, next, pick -> HandMap(selected, next, pick, Modifier.fillMaxWidth()) },
                    title = stringResource(R.string.finger_where),
                    dashedHint = stringResource(R.string.finger_dashed),
                )
            }
            if (isMeal && plate.isNotEmpty()) {
                PlateCard(plate, onRemove = { plate.remove(it); platePut() }, onAdd = { picking = false })
            }
            if (isMeal) {
                if (showMore) {
                    SukoonTextField(value = note, onValueChange = { note = it }, label = stringResource(R.string.entry_name), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AmountField.FIBER to R.string.entry_fiber, AmountField.PROTEIN to R.string.entry_protein, AmountField.FAT to R.string.entry_fat).forEach { (f, label) ->
                            ValueCard(stringResource(label), values[f].orEmpty(), stringResource(R.string.logbook_unit_grams), active == f, Modifier.weight(1f), compact = true) { active = f }
                        }
                        ValueCard(stringResource(R.string.entry_energy), values[AmountField.KCAL].orEmpty(), stringResource(R.string.entry_unit_kcal), active == AmountField.KCAL, Modifier.weight(1.2f), compact = true) { active = AmountField.KCAL }
                    }
                }
                MealActions(
                    start = starting,
                    onFood = { picking = false },
                    onJustCarbs = { active = AmountField.CARBS },
                    photos = photos,
                    canEstimate = onEstimateCarbs != null,
                    appMeals = appMeals,
                    onPhoto = { photos += it; photosChanged = true },
                    onRemovePhoto = { photos.removeAt(it); photosChanged = true },
                    onEstimate = { showEstimate = true },
                    onAppMeals = { showAppMeals = true },
                    onMore = { showMore = !showMore },
                    onVoice = onVoice.takeIf { existing == null },
                )
            }
        }

        if (active != null && type != LogEventType.NOTE) {
            val field = active!!
            // Insulin takes a decimal point only for a half-unit pen (You → Insulin).
            val decimals = if (field == AmountField.RAPID || field == AmountField.LONG) doseSettings.step < 1 else field.decimals
            AmountPad(
                decimals = decimals,
                onKey = { key -> values[field] = Keypad.press(values[field].orEmpty(), key, decimals, field.maxWhole) },
                onClear = { values[field] = "" },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.logbook_delete), color = StateLow, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (valid) Sage else Sage.copy(alpha = 0.4f))
                    .clickable(enabled = valid, onClick = ::save),
                contentAlignment = Alignment.Center,
            ) {
                Text(saveLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }

    picking?.let { scan ->
        FoodPicker(scanFirst = scan, estimate = onEstimateCarbs, onAdd = { item ->
            plate += item
            platePut()
            picking = null
        }, onDismiss = { picking = null })
    }
    if (showAppMeals) {
        AppMealPicker(appMeals, onPick = { picked ->
            forMeal = picked
            type = LogEventType.INSULIN
            active = AmountField.RAPID
            showAppMeals = false
        }, onDismiss = { showAppMeals = false })
    }
    if (showEstimate && onEstimateCarbs != null) {
        SukoonSheet(onDismissRequest = { showEstimate = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                CarbEstimator(onEstimateCarbs, photos.toList(), onUse = { r ->
                    values[AmountField.CARBS] = r.carbsGrams.toString()
                    // Protein, fat and kcal too (eggs: almost no carbs, but the protein counts).
                    r.proteinGrams?.let { values[AmountField.PROTEIN] = it.toString() }
                    r.fatGrams?.let { values[AmountField.FAT] = it.toString() }
                    r.kcal?.let { values[AmountField.KCAL] = it.toString() }
                    if (note.isBlank()) note = r.title
                    active = AmountField.CARBS
                    showEstimate = false
                })
            }
        }
    }
}

/** "142 ↗ now" and, before a dose, the insulin still working. */
@Composable
internal fun ContextPills(glucoseNow: GlucoseReading?, insulinOnBoard: Double, modifier: Modifier = Modifier) {
    if (glucoseNow == null && insulinOnBoard < 0.05) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        glucoseNow?.let { r ->
            GlucosePill(r, stringResource(R.string.entry_now_suffix))
        }
        if (insulinOnBoard >= 0.05) {
            Text(
                stringResource(R.string.entry_iob_pill, String.format(Locale.getDefault(), "%.1f", insulinOnBoard)),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(PillHighBg).padding(horizontal = 10.dp, vertical = 6.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PillHighText,
            )
        }
    }
}

@Composable
private fun ValueCard(label: String, value: String, unit: String, active: Boolean, modifier: Modifier, compact: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier
            .heightIn(min = if (compact) 72.dp else 104.dp)
            .clip(RoundedCornerShape(if (compact) 14.dp else 18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (active) 2.dp else 1.dp, if (active) Sage else outline(), RoundedCornerShape(if (compact) 14.dp else 18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (compact) label else label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (compact) 0.sp else 1.sp,
            color = CaptionMuted,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                localizeDigits(value.ifEmpty { "0" }),
                fontFamily = HeadlineSerifFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = if (compact) 24.sp else 46.sp,
                color = if (value.isEmpty()) CaptionMuted.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground,
            )
            if (active) Caret(if (compact) 18.dp else 34.dp)
            Text(unit, fontSize = if (compact) 12.sp else 16.sp, color = CaptionMuted, modifier = Modifier.padding(bottom = if (compact) 3.dp else 6.dp))
        }
    }
}

@Composable
private fun Caret(height: Dp) {
    val blink by if (Motion.reduced()) {
        remember { mutableStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "caret").animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "blink")
    }
    Box(Modifier.padding(bottom = 6.dp).width(2.dp).height(height).alpha(if (blink > 0.5f) 1f else 0f).background(Sage))
}

/** The number pad: big keys, a decimal point only where the unit has one; hold delete to clear. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AmountPad(decimals: Boolean, onKey: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(Keypad.POINT, "0", Keypad.DELETE)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val enabled = key != Keypad.POINT || decimals
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .combinedClickable(
                                enabled = enabled,
                                onLongClick = if (key == Keypad.DELETE) onClear else null,
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onKey(key)
                            }
                            .alpha(if (enabled) 1f else 0.35f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == Keypad.DELETE) {
                            Icon(painterResource(R.drawable.ic_backspace), contentDescription = stringResource(R.string.logbook_delete), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                        } else {
                            Text(localizeDigits(key), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            // Selection crossfades its fill, border and ink (reduced: at once).
            val ring by androidx.compose.animation.core.animateDpAsState(if (on) 2.dp else 1.dp, tween(if (Motion.reduced()) 0 else Motion.BASE, easing = Motion.Standard), label = "ring")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Motion.tint(if (on) Sage.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface))
                    .border(ring, Motion.tint(if (on) Sage else outline()), RoundedCornerShape(12.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Motion.tint(if (on) SageDeep else MaterialTheme.colorScheme.onBackground), maxLines = 2)
            }
        }
    }
}

@Composable
internal fun PillButton(label: String, modifier: Modifier = Modifier, dot: Color? = null, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, outline(), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        dot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)) }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
    }
}

@Composable
internal fun RoundIconButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
    }
}

/** When it happened: now, a few minutes back, or a picked time within the last day. */
@Composable
private fun WhenButton(minutesAgo: Int, pickedAt: Instant, onAgo: (Int) -> Unit, onPicked: (Instant) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, outline(), RoundedCornerShape(50))
                .clickable { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(R.drawable.ic_clock), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(16.dp))
            Text(
                when {
                    minutesAgo == 0 -> stringResource(R.string.logbook_when_now)
                    minutesAgo > 0 -> pluralStringResource(R.plurals.logbook_when_ago, minutesAgo.toInt(), minutesAgo)
                    else -> hmFormatter.format(pickedAt)
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f))) {
            listOf(0, 15, 30, 60).forEach { ago ->
                DropdownMenuItem(
                    text = { Text(if (ago == 0) stringResource(R.string.logbook_when_now) else pluralStringResource(R.plurals.logbook_when_ago, ago.toInt(), ago)) },
                    onClick = { onAgo(ago); open = false },
                )
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.logbook_when_pick)) }, onClick = {
                open = false
                val start = (if (minutesAgo < 0) pickedAt else Instant.now()).atZone(ZoneId.systemDefault())
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val now = ZonedDateTime.now()
                        val chosen = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                        onPicked((if (chosen.isAfter(now)) chosen.minusDays(1) else chosen).toInstant()) // later than now = yesterday
                    },
                    start.hour,
                    start.minute,
                    DateFormat.is24HourFormat(context),
                ).show()
            })
        }
    }
}

/** A meal's extras: its photo, the AI estimate, a meal from MyFitnessPal, and more details. */
@Composable
private fun MealActions(
    start: Boolean,
    onFood: () -> Unit,
    onJustCarbs: () -> Unit,
    photos: List<ByteArray>,
    canEstimate: Boolean,
    appMeals: List<EventEntity>,
    onPhoto: (ByteArray) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onEstimate: () -> Unit,
    onAppMeals: () -> Unit,
    onMore: () -> Unit,
    onVoice: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val load: (Uri) -> Unit = { uri ->
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { MealPhoto.loadScaledJpeg(context, uri) } }
                .onSuccess(onPhoto)
                .onFailure { context.toast(it.message ?: it.javaClass.simpleName) }
        }
    }
    val captureUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", MealPhoto.newCaptureFile(context)) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved -> if (saved) load(captureUri) }
    // No limit: pick as many as you like at once.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> uris.forEach(load) }
    val hasPhoto = photos.isNotEmpty()
    var photoMenu by remember { mutableStateOf(false) }
    val mfp = rememberSourceApp(HealthConnectSync.MFP)
    val app = appMeals.firstOrNull()?.let { rememberSourceApp(it.source.orEmpty()) } ?: mfp.takeIf { it.installed }
    // Today's meals from the other app to pick from; with none yet, open it to log the meal there (it comes back through Health Connect).
    val openApp: () -> Unit = {
        if (appMeals.isNotEmpty()) onAppMeals()
        else context.packageManager.getLaunchIntentForPackage(HealthConnectSync.MFP)?.let { runCatching { context.startActivity(it) } }
    }
    val photoChoices: @Composable () -> Unit = {
        if (photoMenu) MealSourceSheet(
            canEstimate = canEstimate,
            onEstimate = { photoMenu = false; onEstimate() },
            onCamera = { photoMenu = false; takePhoto.launch(captureUri) },
            onGallery = { photoMenu = false; pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDismiss = { photoMenu = false },
        )
    }
    if (hasPhoto) {
        // Every photo, each removable: as many as you took.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEachIndexed { i, bytes ->
                Box {
                    PhotoThumb(bytes, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                    Box(
                        Modifier.align(Alignment.TopEnd).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { onRemovePhoto(i) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.logbook_photo_remove), tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }
    }
    if (start) {
        // Three even tiles (design "Meal · start from foods"), so nothing is squeezed.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                StartTile(if (hasPhoto) R.drawable.ic_check else R.drawable.ic_camera, null, stringResource(R.string.entry_photo), Modifier.fillMaxWidth()) { photoMenu = true }
                photoChoices()
            }
            // Say it, where people look for it when they're already in the meal.
            onVoice?.let { StartTile(R.drawable.ic_mic, null, stringResource(R.string.voice_title), Modifier.weight(1f), onClick = it) }
            app?.let { StartTile(null, it, it.label, Modifier.weight(1f), onClick = openApp) }
            StartTile(R.drawable.ic_keypad, null, stringResource(R.string.food_just_carbs), Modifier.weight(1f), onClick = onJustCarbs)
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(R.drawable.ic_plus, stringResource(R.string.food_add), Modifier.weight(1f), onClick = onFood)
        Box(Modifier.weight(1f)) {
            ActionButton(if (hasPhoto) R.drawable.ic_check else R.drawable.ic_camera, stringResource(R.string.entry_photo), Modifier.fillMaxWidth()) { photoMenu = true }
            photoChoices()
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.entry_more), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StartTile(icon: Int?, app: SourceApp?, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 76.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        if (app != null) AppIcon(app, 22.dp) else if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, softWrap = false)
    }
}

/** A new meal: search (with the barcode inside), and your usual foods one tap away at the amount you last had. */
@Composable
private fun MealStart(onSearch: () -> Unit, onScan: () -> Unit, onUsual: (PlateItem) -> Unit) {
    val context = LocalContext.current
    val usual = remember { MyFoods.get(context).all().take(5) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, outline(), RoundedCornerShape(16.dp)).clickable(onClick = onSearch).padding(start = 14.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = CaptionMuted, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.food_search_hint), fontSize = 15.5.sp, color = CaptionMuted, modifier = Modifier.weight(1f).padding(start = 10.dp))
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(SageMist).clickable(onClick = onScan), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_barcode), contentDescription = stringResource(R.string.food_scan), tint = SageDeep, modifier = Modifier.size(22.dp))
            }
        }
        if (usual.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Eyebrow(stringResource(R.string.food_your_usual), Modifier.padding(vertical = 8.dp))
                usual.forEach { food ->
                    val amount = food.lastAmount ?: if (food.own) 1.0 else 100.0
                    Row(Modifier.fillMaxWidth().clickable { onUsual(PlateItem(food, amount)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FoodImage(food, 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(food.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                            Text(amountText(food, amount), fontSize = 13.sp, color = CaptionMuted)
                        }
                        Text(gramsText(food.of(amount)[Food.CARBS]) + " " + stringResource(R.string.logbook_unit_grams).trim(), fontSize = 13.5.sp, color = CaptionMuted, modifier = Modifier.padding(end = 10.dp))
                        Box(Modifier.size(40.dp).clip(CircleShape).background(SageMist), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_plus), contentDescription = stringResource(R.string.food_add_named, food.name), tint = SageDeep, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** What's on the plate: each food and its carbs (typed over with [onCarbs]), removable; and another. */
@Composable
internal fun PlateCard(plate: List<PlateItem>, onRemove: (PlateItem) -> Unit, onAdd: () -> Unit, onCarbs: ((Int, Double) -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Eyebrow(stringResource(R.string.food_on_plate), Modifier.padding(vertical = 8.dp))
        plate.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FoodImage(item.food, 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(item.food.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                    Text(amountText(item.food, item.amount), fontSize = 13.sp, color = CaptionMuted)
                }
                if (onCarbs != null) {
                    var typed by remember(item.food) { mutableStateOf(formatAmount(Math.round(item.sums[Food.CARBS]).toDouble())) }
                    androidx.compose.foundation.text.BasicTextField(
                        typed, { v -> typed = v.filter { it.isDigit() || it == '.' }.take(5); typed.toDoubleOrNull()?.let { onCarbs(i, it) } },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Sage),
                        modifier = Modifier.width(60.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background)
                            .border(1.dp, outline(), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 13.dp),
                    )
                    Text(stringResource(R.string.logbook_unit_grams).trim(), fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.padding(start = 4.dp))
                } else {
                    Text(gramsText(item.sums[Food.CARBS]) + " " + stringResource(R.string.logbook_unit_grams).trim(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { onRemove(item) }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.food_remove, item.food.name), tint = CaptionMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(stringResource(R.string.food_add_another), modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAdd).padding(vertical = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
    }
}

@Composable
private fun ActionButton(icon: Int?, label: String, modifier: Modifier, appIcon: SourceApp? = null, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (appIcon != null) AppIcon(appIcon, 17.dp)
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(17.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
    }
}

/** Today's meals from MyFitnessPal (or another food app): pick one to add the insulin you took for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppMealPicker(meals: List<EventEntity>, onPick: (EventEntity) -> Unit, onDismiss: () -> Unit) {
    val app = rememberSourceApp(meals.firstOrNull()?.source.orEmpty())
    SukoonSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.entry_from_app, app.label), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, lineHeight = 29.sp, color = MaterialTheme.colorScheme.onBackground)
            Eyebrow(stringResource(R.string.entry_today_in_app, app.label), Modifier.padding(top = 6.dp))
            meals.forEach { meal ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, outline(), RoundedCornerShape(14.dp))
                        .clickable { onPick(meal) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${mealTitle(meal)} · ${hmFormatter.format(Instant.ofEpochMilli(meal.timestampMillis))}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        meal.note?.let { Text(it, fontSize = 12.5.sp, color = CaptionMuted, maxLines = 1) }
                    }
                    Text("${formatAmountLocalized(meal.value ?: 0.0)}${stringResource(R.string.logbook_unit_grams)}", fontFamily = HeadlineSerifFontFamily, fontSize = 21.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            Text(stringResource(R.string.entry_app_stays, app.label), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Where the meal comes from: the AI estimate (sparkle, filled), the camera or the gallery, as tiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealSourceSheet(canEstimate: Boolean, onEstimate: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    SukoonSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.meal_source_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, lineHeight = 31.sp, color = MaterialTheme.colorScheme.onBackground)
            if (canEstimate) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sage).clickable(onClick = onEstimate).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_sparkle), null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.carb_ai_estimate), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(stringResource(R.string.meal_source_estimate_body), fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SourceTile(R.drawable.ic_camera, stringResource(R.string.carb_ai_camera), Modifier.weight(1f), onCamera)
                SourceTile(R.drawable.ic_gallery, stringResource(R.string.carb_ai_gallery), Modifier.weight(1f), onGallery)
            }
        }
    }
}

@Composable
private fun SourceTile(icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 96.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(com.sukoon.app.ui.theme.SageMist), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = SageDeep, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** The imported meal the insulin is for, with what it brought. */
@Composable
private fun AppMealCard(meal: EventEntity) {
    val app = rememberSourceApp(meal.source.orEmpty())
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceLine(app, listOfNotNull(hmFormatter.format(Instant.ofEpochMilli(meal.timestampMillis)), meal.note))
        NutrientGrid(meal)
        if (slowMeal(meal.fat, meal.protein)) Text(stringResource(R.string.entry_slow_meal), fontSize = 13.sp, color = CaptionMuted)
    }
}

/** AI carb estimate (Gemini): describe the meal and/or use its photo; "Use" fills the carbs, nothing is logged. */
@Composable
private fun CarbEstimator(estimate: suspend (String, List<ByteArray>) -> CarbEstimate, photos: List<ByteArray>, onUse: (CarbEstimate) -> Unit) {
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CarbEstimate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.carb_ai_estimate), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, lineHeight = 29.sp, color = MaterialTheme.colorScheme.onBackground)
        SukoonTextField(value = description, onValueChange = { description = it }, label = stringResource(R.string.carb_ai_describe), modifier = Modifier.fillMaxWidth())
        if (photos.isNotEmpty()) Text(pluralStringResource(R.plurals.carb_ai_photos_added, photos.size, photos.size), fontSize = 12.sp, color = SageDeep)
        val canEstimate = !busy && (description.isNotBlank() || photos.isNotEmpty())
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (canEstimate) Sage else Sage.copy(alpha = 0.4f))
                .clickable(enabled = canEstimate) {
                    scope.launch {
                        busy = true
                        error = null
                        runCatching { estimate(description, photos) }.onSuccess { result = it }.onFailure { error = it.message }
                        busy = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(if (busy) R.string.carb_ai_busy else R.string.carb_ai_estimate), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        result?.let { r ->
            Text(stringResource(R.string.carb_ai_result, formatAmountLocalized(r.carbsGrams.toDouble()), r.title), fontFamily = HeadlineSerifFontFamily, fontSize = 19.sp, color = MaterialTheme.colorScheme.onBackground)
            if (r.items.isNotEmpty()) {
                Text(r.items.joinToString(" · ") { (name, grams) -> "$name ${formatAmountLocalized(grams.toDouble())}g" }, fontSize = 12.5.sp, color = CaptionMuted)
            }
            Text(stringResource(R.string.carb_ai_confidence, r.confidence, r.note), fontSize = 12.sp, color = CaptionMuted)
            listOfNotNull(
                r.proteinGrams?.let { stringResource(R.string.carb_ai_protein, formatAmountLocalized(it.toDouble())) },
                r.fatGrams?.let { stringResource(R.string.carb_ai_fat, formatAmountLocalized(it.toDouble())) },
                r.kcal?.let { formatAmountLocalized(it.toDouble()) + " " + stringResource(R.string.entry_unit_kcal) },
            ).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), fontSize = 12.5.sp, color = CaptionMuted) }
            PillButton(stringResource(R.string.carb_ai_use, formatAmountLocalized(r.carbsGrams.toDouble()))) { onUse(r) }
        }
        error?.let { Text(it, fontSize = 12.5.sp, color = StateLow) }
        Text(stringResource(R.string.carb_ai_disclaimer), fontSize = 11.5.sp, color = CaptionMuted)
    }
}

@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = CaptionMuted)
}

@Composable
private fun fieldLabel(type: LogEventType): String = when (type) {
    LogEventType.FINGERSTICK -> stringResource(R.string.entry_meter)
    LogEventType.ACTIVITY -> stringResource(R.string.entry_minutes)
    else -> typeLabel(type)
}

@Composable
internal fun outline(): Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)

/** Digits as the locale writes them (Arabic-Indic in Arabic); the typed value itself stays ASCII. */
internal fun localizeDigits(text: String): String =
    text.map { c -> if (c.isDigit()) String.format(Locale.getDefault(), "%d", c.digitToInt()) else c.toString() }.joinToString("")
