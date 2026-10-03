package com.sukoon.app.ui.logbook

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sukoon.app.R
import com.sukoon.app.ai.CarbEstimate
import com.sukoon.app.ai.MealPhoto
import com.sukoon.app.ui.components.NumberChips
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateLow
import com.sukoon.app.ui.theme.SukoonTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sukoon.app.ui.components.toast
import com.sukoon.app.insights.MeterCheck
import com.sukoon.app.ui.theme.StateHigh

// Quick-entry preset amounts per type (design 6g) — tapping one logs immediately, satisfying the
// "one tap + one number" quick-entry principle (docs/PLAN.md §11) without a stepper. A custom
// field below covers anything off-preset. Note has no numeric amount, just free text.
private val PRESETS: Map<LogEventType, List<Double>> = mapOf(
    LogEventType.CARB to listOf(15.0, 30.0, 45.0, 60.0),
    LogEventType.INSULIN to listOf(1.0, 2.0, 4.0, 6.0),
    LogEventType.BASAL to listOf(10.0, 14.0, 18.0, 22.0),
    LogEventType.ACTIVITY to listOf(15.0, 30.0, 60.0),
)

private val hmFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private sealed interface SheetTarget {
    data class New(val type: LogEventType = LogEventType.CARB) : SheetTarget
    data class Edit(val event: EventEntity) : SheetTarget
}

/**
 * Logbook (A4, design 8k/8l) — a timeline of logged events (meal/insulin/activity/note) plus a
 * quick-entry sheet. Currently a sub-tab of Trends alongside the Graph (A3); becomes a true nested
 * destination if/when deep-linking (e.g. tap a graph pin → jump here) is needed.
 *
 * Deliberately does NOT interleave raw glucose readings into this list (unlike the design mock,
 * which shows a couple for illustration) — readings already have a dedicated, purpose-built view
 * (the graph), and merging every ~5-minute reading into this list would bury the events it exists
 * to show. Add reading rows here if a design review wants literal parity with 8k.
 */
@Composable
fun LogbookScreen(
    state: LogbookUiState,
    onQuickLog: (LogEventType, Double?, String?) -> Unit,
    onUpdateEvent: (EventEntity) -> Unit,
    onDeleteEvent: (EventEntity) -> Unit,
    modifier: Modifier = Modifier,
    onEstimateCarbs: (suspend (String, ByteArray?) -> CarbEstimate)? = null,
    /** Meal + the insulin taken for it in one save: (carbs g, note, rapid units, minutes injected before eating). */
    onLogMeal: ((Double, String?, Double, Int) -> Unit)? = null,
    /** Set by Home's shortcuts: open a new entry of this type once, then [onOpenedEntry]. */
    openNewEntry: LogEventType? = null,
    onOpenedEntry: () -> Unit = {},
) {
    val context = LocalContext.current
    var sheetTarget by remember { mutableStateOf<SheetTarget?>(null) }
    LaunchedEffect(openNewEntry) {
        if (openNewEntry != null) {
            sheetTarget = SheetTarget.New(openNewEntry)
            onOpenedEntry()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(R.string.logbook_title),
                    fontFamily = HeadlineSerifFontFamily,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(stringResource(R.string.logbook_today), fontSize = 11.5.sp, color = CaptionMuted)
            }
            Spacer(Modifier.height(16.dp))

            if (state.events.isEmpty()) {
                LogbookEmptyState(onLogFirst = { sheetTarget = SheetTarget.New() }, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.events, key = { it.id }) { event ->
                        LogbookRow(event, state.meterChecks[event.id], onClick = { sheetTarget = SheetTarget.Edit(event) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                    }
                }
            }
            Spacer(Modifier.height(84.dp)) // clearance for the FAB
        }

        val addEntryDescription = stringResource(R.string.logbook_add_entry)
        FloatingActionButton(
            onClick = { sheetTarget = SheetTarget.New() },
            containerColor = Sage,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).semantics { contentDescription = addEntryDescription },
        ) {
            Text("+", fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
    }

    sheetTarget?.let { target ->
        val editing = (target as? SheetTarget.Edit)?.event
        QuickEntrySheet(
            existing = editing,
            newType = (target as? SheetTarget.New)?.type ?: LogEventType.CARB,
            onDismiss = { sheetTarget = null },
            onSave = { type, value, note ->
                if (editing != null) {
                    onUpdateEvent(editing.copy(type = type.name, value = value, note = note))
                } else {
                    onQuickLog(type, value, note)
                }
                context.toast(context.getString(if (editing != null) R.string.toast_entry_updated else R.string.toast_logged))
                sheetTarget = null
            },
            onDelete = editing?.let { toDelete ->
                {
                    onDeleteEvent(toDelete)
                    context.toast(context.getString(R.string.toast_entry_deleted))
                    sheetTarget = null
                }
            },
            onEstimateCarbs = onEstimateCarbs,
            onSaveMeal = onLogMeal?.let { log -> { carbs, note, insulin, minutes ->
                    log(carbs, note, insulin, minutes)
                    context.toast(context.getString(R.string.toast_logged))
                    sheetTarget = null
                } },
        )
    }
}

@Composable
private fun LogbookEmptyState(onLogFirst: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.logbook_empty_title),
            fontFamily = HeadlineSerifFontFamily,
            fontSize = 23.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.logbook_empty_body),
            fontSize = 13.sp,
            color = CaptionMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(14.dp))
                .background(Sage)
                .clickable(onClick = onLogFirst)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.logbook_empty_cta), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LogbookRow(event: EventEntity, check: MeterCheck?, onClick: () -> Unit) {
    val type = event.logType
    val hasNote = !event.note.isNullOrBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(colorForLogEventType(type)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (hasNote) event.note!! else typeLabel(type),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (hasNote) {
                Text(typeLabel(type), fontSize = 10.5.sp, color = CaptionMuted)
            }
            check?.let { c ->
                Text(
                    when {
                        c.percentDiff > 2 -> stringResource(R.string.logbook_sensor_higher, c.sensorMgDl, c.percentDiff)
                        c.percentDiff < -2 -> stringResource(R.string.logbook_sensor_lower, c.sensorMgDl, -c.percentDiff)
                        else -> stringResource(R.string.logbook_sensor_same, c.sensorMgDl)
                    },
                    fontSize = 10.5.sp,
                    color = if (c.agrees) Sage else StateHigh,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            event.value?.let {
                Text(
                    text = "${formatAmountLocalized(it)}${unitLabel(type)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(hmFormatter.format(Instant.ofEpochMilli(event.timestampMillis)), fontSize = 10.sp, color = CaptionMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEntrySheet(
    existing: EventEntity?,
    newType: LogEventType,
    onDismiss: () -> Unit,
    onSave: (LogEventType, Double?, String?) -> Unit,
    onDelete: (() -> Unit)?,
    onEstimateCarbs: (suspend (String, ByteArray?) -> CarbEstimate)?,
    onSaveMeal: ((Double, String?, Double, Int) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(existing?.logType ?: newType) }
    var amountText by remember { mutableStateOf(existing?.value?.let(::formatAmount) ?: "") }
    var noteText by remember { mutableStateOf(existing?.note ?: "") }
    // New meals only: optional rapid insulin logged alongside, injected N minutes before eating.
    var mealInsulinText by remember { mutableStateOf("") }
    var preBolusMinutes by remember { mutableIntStateOf(0) }
    val mealInsulin = mealInsulinText.toDoubleOrNull()?.takeIf { it > 0 && existing == null && onSaveMeal != null }
    fun save(saveType: LogEventType, amount: Double?) {
        if (saveType == LogEventType.CARB && amount != null && mealInsulin != null) {
            onSaveMeal!!(amount, noteText.ifBlank { null }, mealInsulin, preBolusMinutes)
        } else {
            onSave(saveType, amount, noteText.ifBlank { null })
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                text = stringResource(if (existing != null) R.string.logbook_sheet_title_edit else R.string.logbook_sheet_title_new),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))

            // Two rows of three: six types don't fit one row on a phone.
            LogEventType.entries.chunked(3).forEachIndexed { i, rowTypes ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowTypes.forEach { candidate ->
                        TypeChip(
                            label = typeLabel(candidate),
                            selected = candidate == type,
                            color = colorForLogEventType(candidate),
                            onClick = { type = candidate; amountText = "" },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            if (type == LogEventType.NOTE) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.logbook_note_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val unit = unitLabel(type)
                val presets = PRESETS[type].orEmpty() // none for a finger-prick: it's whatever the meter says
                if (presets.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { preset ->
                            AmountChip(
                                label = "${formatAmountLocalized(preset)}$unit",
                                onClick = { save(type, preset) },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(if (type == LogEventType.FINGERSTICK) R.string.logbook_meter_label else R.string.logbook_custom_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == LogEventType.CARB && existing == null && onSaveMeal != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = mealInsulinText,
                        onValueChange = { mealInsulinText = it },
                        label = { Text(stringResource(R.string.logbook_meal_insulin)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (mealInsulin != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.logbook_prebolus), fontSize = 12.sp, color = CaptionMuted)
                        Spacer(Modifier.height(6.dp))
                        NumberChips(listOf(0, 5, 10, 15, 20, 30), preBolusMinutes, 0..90, {
                            if (it == 0) stringResource(R.string.logbook_prebolus_with) else stringResource(R.string.logbook_prebolus_min, it)
                        }) { preBolusMinutes = it }
                    }
                }
                if (type == LogEventType.CARB && onEstimateCarbs != null) {
                    CarbEstimator(
                        estimate = onEstimateCarbs,
                        onUse = { grams, title ->
                            amountText = grams.toString()
                            if (noteText.isBlank()) noteText = title
                        },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.logbook_delete), color = StateLow) }
                    Spacer(Modifier.weight(1f))
                }
                val saveEnabled = when (type) {
                    LogEventType.NOTE -> noteText.isNotBlank()
                    // Meters show LO/HI outside about 20–600 mg/dL, so anything else is a typo.
                    LogEventType.FINGERSTICK -> amountText.toDoubleOrNull()?.let { it in 20.0..600.0 } == true
                    else -> amountText.toDoubleOrNull()?.let { it > 0 } == true
                }
                Box(
                    modifier = (if (onDelete == null) Modifier.fillMaxWidth() else Modifier)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (saveEnabled) Sage else Sage.copy(alpha = 0.4f))
                        .clickable(enabled = saveEnabled) {
                            save(type, amountText.toDoubleOrNull())
                        }
                        .padding(horizontal = 24.dp, vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.logbook_save), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * AI carb estimate (Gemini): describe the meal and/or attach a photo -> an editable suggestion.
 * "Use" only fills the amount field; nothing is logged until the user taps Save.
 */
@Composable
private fun CarbEstimator(estimate: suspend (String, ByteArray?) -> CarbEstimate, onUse: (Int, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CarbEstimate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val loadPhoto: (Uri) -> Unit = { uri ->
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { MealPhoto.loadScaledJpeg(context, uri) } }
                .onSuccess { photo = it; error = null }
                .onFailure { error = it.message }
        }
    }
    val captureUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", MealPhoto.newCaptureFile(context)) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved -> if (saved) loadPhoto(captureUri) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(loadPhoto) }

    Spacer(Modifier.height(10.dp))
    if (!open) {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.carb_ai_open), color = Sage, fontWeight = FontWeight.SemiBold) }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.carb_ai_describe)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AmountChip(stringResource(R.string.carb_ai_camera)) { takePhoto.launch(captureUri) }
            AmountChip(stringResource(R.string.carb_ai_gallery)) {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (photo != null) Text(stringResource(R.string.carb_ai_photo_added), fontSize = 11.5.sp, color = Sage)
        }
        val canEstimate = !busy && (description.isNotBlank() || photo != null)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (canEstimate) Sage else Sage.copy(alpha = 0.4f))
                .clickable(enabled = canEstimate) {
                    scope.launch {
                        busy = true
                        error = null
                        runCatching { estimate(description, photo) }
                            .onSuccess { result = it }
                            .onFailure { error = it.message }
                        busy = false
                    }
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (busy) R.string.carb_ai_busy else R.string.carb_ai_estimate),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
        result?.let { r ->
            Text(
                stringResource(R.string.carb_ai_result, formatAmountLocalized(r.carbsGrams.toDouble()), r.title),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (r.items.isNotEmpty()) {
                Text(
                    r.items.joinToString(" · ") { (name, grams) -> "$name ${formatAmountLocalized(grams.toDouble())}g" },
                    fontSize = 12.sp,
                    color = CaptionMuted,
                )
            }
            Text(stringResource(R.string.carb_ai_confidence, r.confidence, r.note), fontSize = 11.5.sp, color = CaptionMuted)
            AmountChip(stringResource(R.string.carb_ai_use, formatAmountLocalized(r.carbsGrams.toDouble()))) { onUse(r.carbsGrams, r.title) }
        }
        error?.let { Text(it, fontSize = 12.sp, color = StateLow) }
        Text(stringResource(R.string.carb_ai_disclaimer), fontSize = 10.5.sp, color = CaptionMuted)
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.background(color)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (selected) Color.White else color))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun AmountChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun typeLabel(type: LogEventType): String = when (type) {
    LogEventType.CARB -> stringResource(R.string.logbook_type_carb)
    LogEventType.INSULIN -> stringResource(R.string.logbook_type_insulin)
    LogEventType.BASAL -> stringResource(R.string.logbook_type_basal)
    LogEventType.FINGERSTICK -> stringResource(R.string.logbook_type_fingerstick)
    LogEventType.ACTIVITY -> stringResource(R.string.logbook_type_activity)
    LogEventType.NOTE -> stringResource(R.string.logbook_type_note)
}

@Composable
private fun unitLabel(type: LogEventType): String = when (type) {
    LogEventType.CARB -> stringResource(R.string.logbook_unit_grams)
    LogEventType.INSULIN -> stringResource(R.string.logbook_unit_units)
    LogEventType.BASAL -> stringResource(R.string.logbook_unit_units)
    LogEventType.FINGERSTICK -> " " + stringResource(R.string.home_unit_mgdl)
    LogEventType.ACTIVITY -> stringResource(R.string.logbook_unit_minutes)
    LogEventType.NOTE -> ""
}

// Plain ASCII-digit formatting for the editable custom-amount field — it must round-trip through
// String.toDoubleOrNull() to save, which doesn't understand Arabic-Indic digits.
private fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

// Locale-aware formatting for display-only amounts (preset chips, timeline values) — matches the
// app's existing convention (e.g. graph_range_hours) of localizing digits to Arabic-Indic.
private fun formatAmountLocalized(value: Double): String =
    if (value == value.toLong().toDouble()) {
        String.format(Locale.getDefault(), "%d", value.toLong())
    } else {
        String.format(Locale.getDefault(), "%s", value)
    }

@Preview(showBackground = true)
@Composable
private fun LogbookScreenPreview() {
    val now = System.currentTimeMillis()
    val events = listOf(
        EventEntity(id = 1, timestampMillis = now - 30 * 60_000L, type = "CARB", value = 30.0, note = "Dates & yogurt"),
        EventEntity(id = 2, timestampMillis = now - 90 * 60_000L, type = "INSULIN", value = 4.0),
        EventEntity(id = 3, timestampMillis = now - 150 * 60_000L, type = "ACTIVITY", value = 20.0, note = "Morning walk"),
    )
    SukoonTheme {
        LogbookScreen(state = LogbookUiState(events), onQuickLog = { _, _, _ -> }, onUpdateEvent = {}, onDeleteEvent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LogbookEmptyPreview() {
    SukoonTheme {
        LogbookScreen(state = LogbookUiState(), onQuickLog = { _, _, _ -> }, onUpdateEvent = {}, onDeleteEvent = {})
    }
}
