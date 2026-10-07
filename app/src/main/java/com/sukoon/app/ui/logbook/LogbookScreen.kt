package com.sukoon.app.ui.logbook

import android.graphics.BitmapFactory
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.ai.CarbEstimate
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.insights.MeterCheck
import com.sukoon.app.insulin.injectionSite
import com.sukoon.app.insulin.DoseSettings
import com.sukoon.app.ui.theme.CanvasDark
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Motion.staggerIn
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowBg
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.PillNeutralBg
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageLight
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.SukoonTheme
import com.sukoon.app.ui.widget.arrow
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sukoon.app.ui.components.PullToSync
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

internal val hmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * Logbook (design A1–A3 and "Logbook with MyFitnessPal meals"): today's timeline, newest first,
 * each meal with the rapid insulin taken for it. The + opens what to add; the entry is typed on a
 * big pad (EntryEditor) and a save can be undone from the snackbar. Meals other apps log
 * (MyFitnessPal through Health Connect) carry that app's badge, everything it brought, what the
 * meal did to glucose, and a nudge when no insulin was logged for it.
 *
 * Deliberately does NOT interleave raw glucose readings: the graph is purpose-built for those.
 */
@Composable
fun LogbookScreen(
    state: LogbookUiState,
    onSaveEntry: (EntryDraft) -> Deferred<List<Long>>,
    onUndoEntry: (List<Long>) -> Unit,
    onUpdateEvent: (EventEntity) -> Unit,
    onDeleteEvent: (EventEntity) -> Unit,
    modifier: Modifier = Modifier,
    onEstimateCarbs: (suspend (String, ByteArray?) -> CarbEstimate)? = null,
    /** Attach, replace (bytes) or remove (null) an existing entry's photo. */
    onEntryPhoto: (Long, ByteArray?) -> Unit = { _, _ -> },
    /** Set by Home's shortcuts: open a new entry of this type once, then [onOpenedEntry]. */
    openNewEntry: LogEventType? = null,
    onOpenedEntry: () -> Unit = {},
    /** Pull down: bring in MyFitnessPal's latest; returns what to say. */
    onSync: (suspend () -> String)? = null,
    /** Beta dose suggestions, read when an entry opens. */
    doseSettings: () -> DoseSettings = { DoseSettings() },
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var detail by remember { mutableStateOf<EventEntity?>(null) }
    var cascade by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1_500)
        cascade = false
    }
    LaunchedEffect(openNewEntry) {
        if (openNewEntry != null) {
            editor = EditorRequest(openNewEntry)
            onOpenedEntry()
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    val now = Instant.now()
    val groups = remember(state.events) { groupEntries(state.events) }
    val totals = remember(state.events) { todayTotals(state.events, Instant.now(), zone) }
    val appMeals = remember(state.events) {
        state.events.filter { it.source != null && it.logType == LogEventType.CARB && Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() == Instant.now().atZone(zone).toLocalDate() }
    }
    fun open(event: EventEntity) {
        if (event.source != null && event.logType == LogEventType.CARB) detail = event else editor = EditorRequest(event.logType, existing = event)
    }
    fun announce(message: String, undo: List<Long>? = null) {
        scope.launch {
            val result = snackbar.showSnackbar(message, actionLabel = undo?.let { context.getString(R.string.entry_undo) }, withDismissAction = false)
            if (result == SnackbarResult.ActionPerformed && undo != null) onUndoEntry(undo)
        }
    }

    PullToSync(onSync, modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.logbook_title), fontFamily = HeadlineSerifFontFamily, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    stringResource(R.string.logbook_today_totals, formatAmountLocalized(totals.first), formatAmountLocalized(totals.second)),
                    fontSize = 13.sp,
                    color = CaptionMuted,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (state.events.isEmpty()) {
                // Scrollable, so pulling down to sync works on an empty day too.
                LogbookEmptyState(onLogFirst = { adding = true }, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(groups, key = { _, g -> g.main.id }) { i, group ->
                        // Only when the list first appears: rows scrolled back into view just show.
                        Column(if (cascade) Modifier.staggerIn(i) else Modifier) {
                            val main = group.main
                            val mealAt = Instant.ofEpochMilli(main.timestampMillis)
                            EntryRow(
                                group = group,
                                check = state.meterChecks[main.id],
                                glucose = state.glucoseAt[main.id],
                                photo = state.photos[main.id],
                                response = if (main.logType == LogEventType.CARB && Duration.between(mealAt, now) >= Duration.ofHours(1)) {
                                    mealResponse(state.readings, mealAt, now)?.takeIf { !it.stillRising }
                                } else {
                                    null
                                },
                                askForInsulin = main.source != null && main.logType == LogEventType.CARB && group.insulin.isEmpty() && Duration.between(mealAt, now) <= Duration.ofHours(6),
                                onOpen = { open(main) },
                                onOpenDose = { open(it) },
                                onAddInsulin = { editor = EditorRequest(LogEventType.INSULIN, forMeal = main) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }

        val addEntryDescription = stringResource(R.string.logbook_add_entry)
        FloatingActionButton(
            onClick = { adding = true },
            containerColor = Sage,
            contentColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).semantics { contentDescription = addEntryDescription },
        ) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(26.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 92.dp, bottom = 16.dp)) { data ->
            Snackbar(data, containerColor = CanvasDark, contentColor = Color.White, actionColor = SageLight, shape = RoundedCornerShape(14.dp))
        }
    }

    if (adding) {
        AddSheet(
            repeats = remember(state.events) { recentRepeats(state.events) },
            glucoseNow = state.glucoseNow,
            insulinOnBoard = state.insulinOnBoard,
            mealsFromApps = state.events.any { it.source != null },
            onPick = { type, repeat ->
                adding = false
                editor = EditorRequest(type, prefill = repeat)
            },
            onDismiss = { adding = false },
        )
    }
    editor?.let { request ->
        val existing = request.existing
        EntryEditor(
            request = request,
            glucoseNow = state.glucoseNow,
            insulinOnBoard = state.insulinOnBoard,
            appMeals = appMeals,
            photoFile = existing?.let { state.photos[it.id] },
            siteHistory = state.siteHistory,
            doseSettings = remember(request) { doseSettings() },
            onEstimateCarbs = onEstimateCarbs,
            onSave = { draft ->
                editor = null
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
                val saving = onSaveEntry(draft)
                scope.launch { announce(savedMessage(context, draft), saving.await()) }
            },
            onUpdate = { event, photo, photoRemoved ->
                editor = null
                onUpdateEvent(event)
                if (photo != null || photoRemoved) onEntryPhoto(event.id, photo)
                announce(context.getString(R.string.toast_entry_updated))
            },
            onDelete = existing?.let { event ->
                {
                    editor = null
                    onDeleteEvent(event)
                    announce(context.getString(R.string.toast_entry_deleted))
                }
            },
            onDismiss = { editor = null },
        )
    }
    detail?.let { meal ->
        MealDetailSheet(
            meal = meal,
            insulin = groups.firstOrNull { it.main.id == meal.id }?.insulin.orEmpty(),
            readings = state.readings,
            onAddInsulin = {
                detail = null
                editor = EditorRequest(LogEventType.INSULIN, forMeal = meal)
            },
            onEditInsulin = { dose ->
                detail = null
                editor = EditorRequest(LogEventType.INSULIN, existing = dose)
            },
            onHide = {
                detail = null
                onDeleteEvent(meal)
                announce(context.getString(R.string.toast_meal_hidden))
            },
            onDismiss = { detail = null },
        )
    }
}

/** "Saved 45 g + 4 u", "Saved 121 mg/dL", "Saved". */
private fun savedMessage(context: android.content.Context, draft: EntryDraft): String {
    fun amount(value: Double?, type: LogEventType): String? = value?.let {
        formatAmountLocalized(it) + when (type) {
            LogEventType.CARB -> context.getString(R.string.logbook_unit_grams)
            LogEventType.INSULIN, LogEventType.BASAL -> context.getString(R.string.logbook_unit_units)
            LogEventType.FINGERSTICK -> " " + context.getString(R.string.home_unit_mgdl)
            LogEventType.ACTIVITY -> context.getString(R.string.logbook_unit_minutes)
            LogEventType.NOTE -> ""
        }
    }
    val parts = listOfNotNull(amount(draft.amount, draft.type), amount(draft.insulin?.takeIf { it > 0 }, LogEventType.INSULIN))
    return context.getString(R.string.entry_saved, parts.joinToString(" + ")).trim()
}

@Composable
private fun EntryRow(
    group: EntryGroup,
    check: MeterCheck?,
    glucose: GlucoseReading?,
    photo: File?,
    response: MealResponse?,
    askForInsulin: Boolean,
    onOpen: () -> Unit,
    onOpenDose: (EventEntity) -> Unit,
    onAddInsulin: () -> Unit,
) {
    val event = group.main
    val type = event.logType
    val isMeal = type == LogEventType.CARB
    val title = if (isMeal) mealTitle(event) else event.note ?: typeLabel(type)
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 4.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) PhotoThumb(photo, Modifier.size(40.dp)) else Box(Modifier.size(9.dp).clip(CircleShape).background(colorForLogEventType(type)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                when {
                    event.source != null -> SourceLine(
                        rememberSourceApp(event.source),
                        listOfNotNull(
                            event.note?.takeIf { it != title },
                            event.kcal?.let { "${formatAmountLocalized(it)} ${stringResource(R.string.entry_unit_kcal)}" },
                        ),
                    )
                    check != null -> Text(
                        when {
                            check.percentDiff > 2 -> stringResource(R.string.logbook_sensor_higher, check.sensorMgDl, check.percentDiff)
                            check.percentDiff < -2 -> stringResource(R.string.logbook_sensor_lower, check.sensorMgDl, -check.percentDiff)
                            else -> stringResource(R.string.logbook_sensor_same, check.sensorMgDl)
                        },
                        fontSize = 12.5.sp,
                        color = if (check.agrees) SageDeep else PillHighText,
                    )
                    // A dose: where it went, or a quiet "Where?" (the row opens the entry to add it).
                    type == LogEventType.INSULIN || type == LogEventType.BASAL -> {
                        val site = event.injectionSite
                        Text(
                            listOfNotNull(typeLabel(type).takeIf { event.note != null }, site?.let { siteName(it) } ?: stringResource(R.string.site_where)).joinToString(" · "),
                            fontSize = 12.5.sp,
                            color = if (site == null) SageDeep else CaptionMuted,
                        )
                    }
                    !isMeal && event.note != null -> Text(typeLabel(type), fontSize = 12.5.sp, color = CaptionMuted)
                }
            }
            // The glucose at that moment; a finger-prick already shows the sensor beside the meter.
            if (glucose != null && type != LogEventType.FINGERSTICK) {
                GlucosePill(glucose)
                Spacer(Modifier.width(10.dp))
            }
            event.value?.let { Text("${formatAmountLocalized(it)}${unitLabel(type)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground) }
            Text(hmFormatter.format(Instant.ofEpochMilli(event.timestampMillis)), fontSize = 13.sp, color = CaptionMuted, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
        }
        group.insulin.forEach { dose ->
            Row(Modifier.fillMaxWidth().clickable { onOpenDose(dose) }.padding(start = 21.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground))
                Text(
                    listOfNotNull(typeLabel(LogEventType.INSULIN), doseTiming(dose, event), dose.injectionSite?.let { siteName(it) }).joinToString(" · "),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                Text("${formatAmountLocalized(dose.value ?: 0.0)}${unitLabel(LogEventType.INSULIN)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(hmFormatter.format(Instant.ofEpochMilli(dose.timestampMillis)), fontSize = 13.sp, color = CaptionMuted, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
            }
        }
        response?.let { r ->
            Text(
                if (r.backInRangeAt != null) {
                    stringResource(R.string.logbook_response_back, r.start, r.peak, hmFormatter.format(r.peakAt), hmFormatter.format(r.backInRangeAt))
                } else {
                    stringResource(R.string.logbook_response, r.start, r.peak, hmFormatter.format(r.peakAt))
                },
                fontSize = 12.5.sp,
                color = if (r.peak > 180) PillHighText else CaptionMuted,
                modifier = Modifier.padding(start = 21.dp, top = 6.dp),
            )
        }
        if (askForInsulin) {
            Row(Modifier.padding(start = 21.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.logbook_no_insulin), fontSize = 13.5.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
                PillButton(stringResource(R.string.logbook_add_insulin), onClick = onAddInsulin)
            }
        }
    }
}

@Composable
private fun LogbookEmptyState(onLogFirst: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.logbook_empty_title), fontFamily = HeadlineSerifFontFamily, fontSize = 23.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.logbook_empty_body), fontSize = 13.sp, color = CaptionMuted, textAlign = TextAlign.Center)
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

/** "142 ↗", tinted like Home's pills: low red, high amber, in range neutral. */
@Composable
internal fun GlucosePill(reading: GlucoseReading, label: String = String.format(Locale.getDefault(), "%d %s", reading.glucoseMgDl, reading.trend.arrow)) {
    val (background, text) = when {
        reading.glucoseMgDl < 70 -> PillLowBg to PillLowText
        reading.glucoseMgDl > 180 -> PillHighBg to PillHighText
        else -> PillNeutralBg to MaterialTheme.colorScheme.onBackground
    }
    Text(
        label,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = text,
    )
}

/** A small rounded preview, decoded off the main thread at a fraction of full size. */
@Composable
internal fun PhotoThumb(source: Any, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, source) {
        value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            when (source) {
                is File -> BitmapFactory.decodeFile(source.path, options)
                is ByteArray -> BitmapFactory.decodeByteArray(source, 0, source.size, options)
                else -> null
            }?.asImageBitmap()
        }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
internal fun typeLabel(type: LogEventType): String = when (type) {
    LogEventType.CARB -> stringResource(R.string.logbook_type_carb)
    LogEventType.INSULIN -> stringResource(R.string.logbook_type_insulin)
    LogEventType.BASAL -> stringResource(R.string.logbook_type_basal)
    LogEventType.FINGERSTICK -> stringResource(R.string.logbook_type_fingerstick)
    LogEventType.ACTIVITY -> stringResource(R.string.logbook_type_activity)
    LogEventType.NOTE -> stringResource(R.string.logbook_type_note)
}

@Composable
internal fun unitLabel(type: LogEventType): String = when (type) {
    LogEventType.CARB -> stringResource(R.string.logbook_unit_grams)
    LogEventType.INSULIN -> stringResource(R.string.logbook_unit_units)
    LogEventType.BASAL -> stringResource(R.string.logbook_unit_units)
    LogEventType.FINGERSTICK -> " " + stringResource(R.string.home_unit_mgdl)
    LogEventType.ACTIVITY -> stringResource(R.string.logbook_unit_minutes)
    LogEventType.NOTE -> ""
}

// Plain ASCII digits for a typed amount: it must round-trip through String.toDoubleOrNull().
internal fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

// Locale-aware digits for amounts shown (Arabic-Indic in Arabic), like the rest of the app.
internal fun formatAmountLocalized(value: Double): String =
    if (value == value.toLong().toDouble()) String.format(Locale.getDefault(), "%d", value.toLong()) else String.format(Locale.getDefault(), "%.1f", value)

@Preview(showBackground = true)
@Composable
private fun LogbookScreenPreview() {
    val now = System.currentTimeMillis()
    val events = listOf(
        EventEntity(id = 1, timestampMillis = now - 30 * 60_000L, type = "CARB", value = 62.0, note = "Koshari, laban", source = "com.myfitnesspal.android", mealType = 2, protein = 28.0, fat = 22.0, kcal = 640.0),
        EventEntity(id = 2, timestampMillis = now - 40 * 60_000L, type = "INSULIN", value = 4.0),
        EventEntity(id = 3, timestampMillis = now - 150 * 60_000L, type = "ACTIVITY", value = 20.0, note = "Morning walk"),
    )
    SukoonTheme {
        LogbookScreen(state = LogbookUiState(events), onSaveEntry = { CompletableDeferred(emptyList()) }, onUndoEntry = {}, onUpdateEvent = {}, onDeleteEvent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LogbookEmptyPreview() {
    SukoonTheme {
        LogbookScreen(state = LogbookUiState(), onSaveEntry = { CompletableDeferred(emptyList()) }, onUndoEntry = {}, onUpdateEvent = {}, onDeleteEvent = {})
    }
}
