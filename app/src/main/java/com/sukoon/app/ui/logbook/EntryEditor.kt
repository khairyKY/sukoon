package com.sukoon.app.ui.logbook

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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
}

private fun mainField(type: LogEventType): AmountField? = when (type) {
    LogEventType.CARB -> AmountField.CARBS
    LogEventType.INSULIN -> AmountField.RAPID
    LogEventType.BASAL -> AmountField.LONG
    LogEventType.FINGERSTICK -> AmountField.METER
    LogEventType.ACTIVITY -> AmountField.MINUTES
    LogEventType.NOTE -> null
}

/** Which editor to open: a new entry (maybe repeating one), an existing one, or insulin for an imported meal. */
internal data class EditorRequest(
    val type: LogEventType,
    val existing: EventEntity? = null,
    val prefill: EventEntity? = null,
    val forMeal: EventEntity? = null,
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
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.logbook_sheet_title_new), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
            ContextPills(glucoseNow, insulinOnBoard, Modifier.padding(top = 8.dp))
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
                TILES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { type -> TypeTile(type, filled = type == LogEventType.CARB, Modifier.weight(1f)) { onPick(type, null) } }
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

@Composable
private fun TypeTile(type: LogEventType, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (icon, tint) = when (type) {
        LogEventType.CARB -> R.drawable.ic_food to Color.White
        LogEventType.INSULIN -> R.drawable.ic_insulin to MaterialTheme.colorScheme.onBackground
        LogEventType.FINGERSTICK -> R.drawable.ic_drop to PillLowText
        LogEventType.BASAL -> R.drawable.ic_moon to SageDeep
        LogEventType.ACTIVITY -> R.drawable.ic_walk to PillHighText
        LogEventType.NOTE -> R.drawable.ic_note to CaptionMuted
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
    photoFile: File?,
    onEstimateCarbs: (suspend (String, ByteArray?) -> CarbEstimate)?,
    onSave: (EntryDraft) -> Unit,
    onUpdate: (EventEntity, ByteArray?, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EditorContent(request, glucoseNow, insulinOnBoard, appMeals, photoFile, onEstimateCarbs, onSave, onUpdate, onDelete, onDismiss)
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
    photoFile: File?,
    onEstimateCarbs: (suspend (String, ByteArray?) -> CarbEstimate)?,
    onSave: (EntryDraft) -> Unit,
    onUpdate: (EventEntity, ByteArray?, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val existing = request.existing
    val base = existing ?: request.prefill
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
        }
    }
    var active by remember { mutableStateOf(if (request.forMeal != null) AmountField.RAPID else mainField(request.type)) }
    var note by remember { mutableStateOf(base?.note ?: "") }
    var preBolus by remember { mutableIntStateOf(0) }
    var minutesAgo by remember { mutableIntStateOf(if (existing == null) 0 else -1) }
    var pickedAt by remember { mutableStateOf(existing?.let { Instant.ofEpochMilli(it.timestampMillis) } ?: Instant.now()) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var photoRemoved by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(existing?.let { it.fiber != null || it.protein != null || it.fat != null || it.kcal != null } ?: false) }
    var showAppMeals by remember { mutableStateOf(false) }
    var showEstimate by remember { mutableStateOf(false) }

    fun value(field: AmountField) = values[field]?.toDoubleOrNull()?.takeIf { it > 0 }
    fun at(): Instant = if (minutesAgo >= 0) Instant.now().minusSeconds(minutesAgo * 60L) else pickedAt
    val meal = forMeal
    val isMeal = type == LogEventType.CARB && meal == null
    val main = mainField(type)
    val rapid = value(AmountField.RAPID)
    val amount = main?.let(::value)
    val valid = when {
        meal != null -> rapid != null
        type == LogEventType.NOTE -> note.isNotBlank()
        type == LogEventType.FINGERSTICK -> amount != null && amount in 20.0..600.0 // meters read LO/HI outside this
        else -> amount != null
    }
    val units = stringResource(R.string.logbook_unit_units)
    val saveLabel = when {
        existing != null -> stringResource(R.string.entry_save_changes)
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
                ),
                photo,
                photoRemoved,
            )
            return
        }
        if (meal != null) {
            onSave(EntryDraft(LogEventType.INSULIN, rapid, null, Instant.ofEpochMilli(meal.timestampMillis).minusSeconds(preBolus * 60L)))
            return
        }
        val nutrients = Nutrients(fiber = value(AmountField.FIBER), protein = value(AmountField.PROTEIN), fat = value(AmountField.FAT), kcal = value(AmountField.KCAL))
        onSave(
            EntryDraft(
                type = type,
                amount = if (type == LogEventType.NOTE) null else amount,
                note = note.ifBlank { null },
                at = at(),
                photo = photo.takeIf { isMeal },
                insulin = rapid.takeIf { isMeal },
                preBolusMinutes = preBolus,
                nutrients = nutrients.takeIf { isMeal && it != Nutrients() },
            ),
        )
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(R.drawable.ic_close, stringResource(R.string.entry_close), onDismiss)
            Text(
                if (meal != null) stringResource(R.string.entry_insulin_for, mealTitle(meal)) else typeLabel(type),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (meal == null) WhenButton(minutesAgo, pickedAt, onAgo = { minutesAgo = it }, onPicked = { pickedAt = it; minutesAgo = -1 })
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ContextPills(glucoseNow.takeIf { existing == null }, if (type == LogEventType.INSULIN || isMeal || meal != null) insulinOnBoard else 0.0)
            when {
                meal != null -> {
                    AppMealCard(meal)
                    ValueCard(stringResource(R.string.logbook_type_insulin), values[AmountField.RAPID].orEmpty(), units, active == AmountField.RAPID, Modifier.fillMaxWidth()) { active = AmountField.RAPID }
                }
                isMeal -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ValueCard(stringResource(R.string.entry_carbs), values[AmountField.CARBS].orEmpty(), stringResource(R.string.logbook_unit_grams), active == AmountField.CARBS, Modifier.weight(1f)) { active = AmountField.CARBS }
                    ValueCard(stringResource(R.string.logbook_type_insulin), values[AmountField.RAPID].orEmpty(), units, active == AmountField.RAPID, Modifier.weight(1f)) { active = AmountField.RAPID }
                }
                main != null -> ValueCard(fieldLabel(type), values[main].orEmpty(), unitLabel(type).trim(), active == main, Modifier.fillMaxWidth()) { active = main }
                else -> OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.logbook_note_hint)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if ((isMeal && (rapid != null || active == AmountField.RAPID)) || meal != null) {
                ChoiceRow(
                    listOf(stringResource(R.string.logbook_prebolus_with), stringResource(R.string.logbook_prebolus_min, 10), stringResource(R.string.logbook_prebolus_min, 20)),
                    selected = listOf(0, 10, 20).indexOf(preBolus),
                ) { preBolus = listOf(0, 10, 20)[it] }
            }
            if (type == LogEventType.FINGERSTICK && glucoseNow != null && existing == null) {
                Text(stringResource(R.string.entry_sensor_now, String.format(Locale.getDefault(), "%d", glucoseNow.glucoseMgDl)), fontSize = 13.5.sp, color = CaptionMuted)
            }
            val field = active
            if (field != null && field.quick.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    field.quick.forEach { n ->
                        PillButton(String.format(Locale.getDefault(), "%d", n), Modifier.weight(1f)) { values[field] = n.toString() }
                    }
                }
            }
            if (isMeal) {
                if (showMore) {
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.entry_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AmountField.FIBER to R.string.entry_fiber, AmountField.PROTEIN to R.string.entry_protein, AmountField.FAT to R.string.entry_fat).forEach { (f, label) ->
                            ValueCard(stringResource(label), values[f].orEmpty(), stringResource(R.string.logbook_unit_grams), active == f, Modifier.weight(1f), compact = true) { active = f }
                        }
                        ValueCard(stringResource(R.string.entry_energy), values[AmountField.KCAL].orEmpty(), stringResource(R.string.entry_unit_kcal), active == AmountField.KCAL, Modifier.weight(1.2f), compact = true) { active = AmountField.KCAL }
                    }
                }
                MealActions(
                    hasPhoto = photo != null || (photoFile != null && !photoRemoved),
                    canEstimate = onEstimateCarbs != null,
                    appMeals = appMeals,
                    onPhoto = { photo = it; photoRemoved = false },
                    onRemovePhoto = { photo = null; photoRemoved = photoFile != null },
                    onEstimate = { showEstimate = true },
                    onAppMeals = { showAppMeals = true },
                    onMore = { showMore = !showMore },
                )
            }
        }

        if (active != null && type != LogEventType.NOTE) {
            val field = active!!
            AmountPad(
                decimals = field.decimals,
                onKey = { key -> values[field] = Keypad.press(values[field].orEmpty(), key, field.decimals, field.maxWhole) },
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

    if (showAppMeals) {
        AppMealPicker(appMeals, onPick = { picked ->
            forMeal = picked
            type = LogEventType.INSULIN
            active = AmountField.RAPID
            showAppMeals = false
        }, onDismiss = { showAppMeals = false })
    }
    if (showEstimate && onEstimateCarbs != null) {
        ModalBottomSheet(onDismissRequest = { showEstimate = false }, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                CarbEstimator(onEstimateCarbs, photo, onUse = { grams, title ->
                    values[AmountField.CARBS] = grams.toString()
                    if (note.isBlank()) note = title
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
            GlucosePill(r, stringResource(R.string.entry_now_pill, String.format(Locale.getDefault(), "%d", r.glucoseMgDl), r.trend.arrow))
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
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Sage.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
                    .border(if (on) 2.dp else 1.dp, if (on) Sage else outline(), RoundedCornerShape(12.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) SageDeep else MaterialTheme.colorScheme.onBackground, maxLines = 2)
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
    hasPhoto: Boolean,
    canEstimate: Boolean,
    appMeals: List<EventEntity>,
    onPhoto: (ByteArray) -> Unit,
    onRemovePhoto: () -> Unit,
    onEstimate: () -> Unit,
    onAppMeals: () -> Unit,
    onMore: () -> Unit,
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
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(load) }
    var photoMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            ActionButton(if (hasPhoto) R.drawable.ic_check else R.drawable.ic_camera, stringResource(R.string.entry_photo), Modifier.fillMaxWidth()) { photoMenu = true }
            DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.carb_ai_camera)) }, onClick = { photoMenu = false; takePhoto.launch(captureUri) })
                DropdownMenuItem(text = { Text(stringResource(R.string.carb_ai_gallery)) }, onClick = {
                    photoMenu = false
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                if (hasPhoto) DropdownMenuItem(text = { Text(stringResource(R.string.logbook_photo_remove), color = StateLow) }, onClick = { photoMenu = false; onRemovePhoto() })
            }
        }
        if (canEstimate) ActionButton(R.drawable.ic_sparkle, stringResource(R.string.carb_ai_estimate), Modifier.weight(1f), onClick = onEstimate)
        val mfp = rememberSourceApp(HealthConnectSync.MFP)
        if (appMeals.isNotEmpty()) {
            val app = rememberSourceApp(appMeals.first().source.orEmpty())
            ActionButton(null, app.label, Modifier.weight(1.3f), appIcon = app, onClick = onAppMeals)
        } else if (mfp.installed) {
            // Nothing from MyFitnessPal today yet: open it to log the meal there; it comes back through Health Connect.
            ActionButton(null, mfp.label, Modifier.weight(1.3f), appIcon = mfp) {
                context.packageManager.getLaunchIntentForPackage(HealthConnectSync.MFP)?.let { runCatching { context.startActivity(it) } }
            }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.entry_from_app, app.label), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
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
private fun CarbEstimator(estimate: suspend (String, ByteArray?) -> CarbEstimate, photo: ByteArray?, onUse: (Int, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CarbEstimate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.carb_ai_estimate), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.carb_ai_describe)) }, modifier = Modifier.fillMaxWidth())
        if (photo != null) Text(stringResource(R.string.carb_ai_photo_added), fontSize = 12.sp, color = SageDeep)
        val canEstimate = !busy && (description.isNotBlank() || photo != null)
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
                        runCatching { estimate(description, photo) }.onSuccess { result = it }.onFailure { error = it.message }
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
            PillButton(stringResource(R.string.carb_ai_use, formatAmountLocalized(r.carbsGrams.toDouble()))) { onUse(r.carbsGrams, r.title) }
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
