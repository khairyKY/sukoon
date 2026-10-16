package com.sukoon.app.ui.logbook

import com.sukoon.app.ui.components.DayBar
import com.sukoon.app.platform.TimeFormat
import com.sukoon.app.health.HealthConnectSync
import android.graphics.BitmapFactory
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarDuration
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.sukoon.app.ui.theme.Motion
import kotlinx.coroutines.withTimeoutOrNull
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
import com.sukoon.app.insulin.finger
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

internal val hmFormatter: DateTimeFormatter get() = TimeFormat.of()

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
    /** Another day to look at (null: back to today). */
    onDay: (java.time.LocalDate?) -> Unit = {},
    onDeleteEvent: (EventEntity) -> Unit,
    modifier: Modifier = Modifier,
    onEstimateCarbs: (suspend (String, List<ByteArray>) -> CarbEstimate)? = null,
    /** Attach, replace (bytes) or remove (null) an existing entry's photo. */
    onEntryPhoto: (Long, List<ByteArray>) -> Unit = { _, _ -> },
    /** Set by Home's shortcuts: open a new entry of this type once, then [onOpenedEntry]. */
    openNewEntry: LogEventType? = null,
    onOpenedEntry: () -> Unit = {},
    /** Pull down: bring in MyFitnessPal's latest; returns what to say. */
    onSync: (suspend () -> String)? = null,
    /** Beta dose suggestions, read when an entry opens. */
    doseSettings: () -> DoseSettings = { DoseSettings() },
    /** Set by Home's "meal without insulin": open insulin for this meal once, then [onOpenedInsulinForMeal]. */
    openInsulinForMeal: Long? = null,
    onOpenedInsulinForMeal: () -> Unit = {},
    /** An imported meal's Health Connect record, for its detail. */
    mealRecord: suspend (Long) -> HealthConnectSync.MealRecord? = { null },
) {
    val context = LocalContext.current
    // Say it and the AI estimate need a Gemini key: without one they aren't offered (the key is in You → Apps & data).
    val hasAiKey = (context.applicationContext as com.sukoon.app.SukoonApp).container.gemini.hasKey
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var voicing by remember { mutableStateOf(false) }
    var mealGap by remember { mutableStateOf<com.sukoon.app.insulin.MealGap?>(null) }
    var deleting by remember { mutableStateOf<Pair<EventEntity, EventEntity>?>(null) }
    var confirmDelete by remember { mutableStateOf<Pair<EventEntity, Boolean>?>(null) }
    val app = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var detail by remember { mutableStateOf<EventEntity?>(null) }
    var cascade by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1_500)
        cascade = false
    }
    LaunchedEffect(openNewEntry) {
        if (openNewEntry != null) {
            val container = (context.applicationContext as com.sukoon.app.SukoonApp).container
            editor = EditorRequest(openNewEntry, treatment = openNewEntry == LogEventType.CARB && container.requestedTreatment.value)
            container.requestedTreatment.value = false
            onOpenedEntry()
        }
    }
    LaunchedEffect(openInsulinForMeal, state.events) {
        val meal = openInsulinForMeal?.let { id -> state.events.firstOrNull { it.id == id } } ?: return@LaunchedEffect
        editor = EditorRequest(LogEventType.INSULIN, forMeal = meal)
        onOpenedInsulinForMeal()
    }
    val zone = remember { ZoneId.systemDefault() }
    val now = Instant.now()
    val groups = remember(state.events) { groupEntries(state.events) }
    val totals = remember(state.events, state.day) { todayTotals(state.events, state.day?.atTime(12, 0)?.atZone(zone)?.toInstant() ?: Instant.now(), zone) }
    val appMeals = remember(state.events) {
        state.events.filter { it.source != null && it.logType == LogEventType.CARB && Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() == Instant.now().atZone(zone).toLocalDate() }
    }
    fun open(event: EventEntity) {
        // Every meal opens on what it did; yours have Edit meal there.
        if (event.logType == LogEventType.CARB) detail = event else editor = EditorRequest(event.logType, existing = event)
    }
    val accessibility = LocalAccessibilityManager.current
    fun announce(message: String, undo: List<Long>? = null) {
        scope.launch {
            // Toast confirm (motion spec): rise, hold ~2.2 s, lift away on its own; longer when accessibility settings ask.
            val shown = Motion.BASE + 2_200L
            val hold = accessibility?.calculateRecommendedTimeoutMillis(shown, containsIcons = false, containsText = true, containsControls = undo != null) ?: shown
            val result = withTimeoutOrNull(hold) {
                snackbar.showSnackbar(message, actionLabel = undo?.let { context.getString(R.string.entry_undo) }, withDismissAction = false, duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed && undo != null) onUndoEntry(undo)
        }
    }

    PullToSync(onSync, modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.logbook_title), fontFamily = HeadlineSerifFontFamily, fontSize = 28.sp, lineHeight = 34.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    stringResource(if (state.day == null) R.string.logbook_today_totals else R.string.logbook_day_totals, formatAmountLocalized(totals.first), formatAmountLocalized(totals.second)),
                    fontSize = 13.sp,
                    color = CaptionMuted,
                )
            }
            DayBar(state.day, onDay)
            if (state.events.isEmpty() && state.day != null) {
                Text(stringResource(R.string.logbook_day_empty), fontSize = 14.sp, color = CaptionMuted, modifier = Modifier.weight(1f).padding(top = 24.dp, start = 4.dp))
            } else if (state.events.isEmpty()) {
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
                                photo = state.photos[main.id]?.firstOrNull(),
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
        val toast = snackbar.currentSnackbarData
        LaunchedEffect(toast) {
            if (toast != null) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
        }
        val reduced = Motion.reduced()
        val (rise, lift) = with(LocalDensity.current) { 10.dp.roundToPx() to 6.dp.roundToPx() }
        // Our own host, not SnackbarHost: its fade-and-scale can't be retimed to the spec's rise in / lift out.
        AnimatedContent(
            toast,
            Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 92.dp, bottom = 16.dp),
            transitionSpec = {
                val move = if (reduced) {
                    fadeIn(tween(Motion.REDUCED)) togetherWith fadeOut(tween(Motion.REDUCED))
                } else {
                    slideInVertically(tween(Motion.BASE, easing = Motion.Out)) { rise } + fadeIn(tween(Motion.BASE, easing = Motion.Out)) togetherWith
                        slideOutVertically(tween(Motion.BASE, easing = Motion.In)) { -lift } + fadeOut(tween(Motion.BASE, easing = Motion.In))
                }
                move using SizeTransform(clip = false)
            },
            contentAlignment = Alignment.BottomStart,
            label = "toast",
        ) { data ->
            data?.let {
                Snackbar(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, containerColor = CanvasDark, contentColor = Color.White, actionColor = SageLight, shape = RoundedCornerShape(14.dp))
            }
        }
    }

    if (adding) {
        AddSheet(
            showKetones = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.modes.active.value.sick || (state.glucoseNow?.glucoseMgDl ?: 0) > 250,
            repeats = remember(state.events) { recentRepeats(state.events) },
            usual = remember(state.events) { usualMeals(state.events, java.time.Instant.now()) },
            onVoice = if (hasAiKey) ({ adding = false; voicing = true }) else null,
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
    mealGap?.let { gap ->
        MealChangedSheet(
            gap = gap,
            target = doseSettings().target,
            onEatNow = { grams ->
                mealGap = null
                app.modes.startMealWatch(gap.by)
                editor = EditorRequest(LogEventType.CARB, prefill = EventEntity(timestampMillis = System.currentTimeMillis(), type = LogEventType.CARB.name, value = grams.toDouble()))
            },
            onLater = {
                mealGap = null
                app.modes.startMealWatch(gap.by)
                announce(context.getString(R.string.meal_changed_later_toast))
            },
            onWatch = {
                mealGap = null
                app.modes.startMealWatch(gap.by)
                announce(context.getString(R.string.meal_changed_watching))
            },
            onTopUp = { units ->
                mealGap = null
                editor = EditorRequest(LogEventType.INSULIN, prefill = EventEntity(timestampMillis = System.currentTimeMillis(), type = LogEventType.INSULIN.name, value = units))
            },
            // Heading low: the watch stays on even if the sheet is just closed.
            onDismiss = { if (gap.ateLess && (gap.ahead ?: 0) < 70) app.modes.startMealWatch(gap.by); mealGap = null },
        )
    }
    confirmDelete?.let { (event, hide) ->
        ConfirmDeleteSheet(
            event, hide,
            onDelete = {
                confirmDelete = null
                onDeleteEvent(event)
                announce(context.getString(if (hide) R.string.toast_meal_hidden else R.string.toast_entry_deleted))
            },
            onDismiss = { confirmDelete = null },
        )
    }
    deleting?.let { (meal, dose) ->
        TookInsulinDialog(
            units = dose.value ?: 0.0,
            onKeep = {
                deleting = null
                onDeleteEvent(meal)
                announce(context.getString(R.string.toast_entry_deleted))
                // The insulin stays: with nothing eaten, all of it is extra.
                mealGap = assessMealEdit(meal.value ?: 0.0, 0.0, meal, state, doseSettings(), app.settings.insulinAction)
            },
            onDeleteBoth = {
                deleting = null
                onDeleteEvent(meal)
                onDeleteEvent(dose)
                announce(context.getString(R.string.toast_entry_deleted))
            },
            onDismiss = { deleting = null },
        )
    }
    if (voicing) {
        VoiceSheet(
            glucoseNow = state.glucoseNow?.glucoseMgDl,
            onSave = { drafts ->
                voicing = false
                scope.launch {
                    val ids = drafts.flatMap { onSaveEntry(it).await() }
                    announce(context.resources.getQuantityString(R.plurals.voice_saved, drafts.size, drafts.size), ids)
                }
            },
            onDismiss = { voicing = false },
        )
    }
    editor?.let { request ->
        val existing = request.existing
        EntryEditor(
            request = request,
            glucoseNow = state.glucoseNow,
            insulinOnBoard = state.insulinOnBoard,
            appMeals = appMeals,
            photoFiles = existing?.let { state.photos[it.id] }.orEmpty(),
            siteHistory = state.siteHistory,
            doseSettings = remember(request) { doseSettings() },
            lowAt = state.lowAt,
            onEstimateCarbs = onEstimateCarbs.takeIf { hasAiKey },
            onSave = { draft ->
                editor = null
                val saving = onSaveEntry(draft)
                scope.launch { announce(savedMessage(context, draft), saving.await()) }
            },
            onUpdate = { event, photos ->
                editor = null
                onUpdateEvent(event)
                photos?.let { onEntryPhoto(event.id, it) }
                announce(context.getString(R.string.toast_entry_updated))
                // A meal changed after its insulin: what that means now (meal-edit-logic.md).
                val before = existing
                if (before != null && event.logType == LogEventType.CARB && !event.treatment && before.value != event.value) {
                    mealGap = assessMealEdit(before.value ?: 0.0, event.value ?: 0.0, event, state, doseSettings(), app.settings.insulinAction)
                }
            },
            onDelete = existing?.let { event ->
                {
                    editor = null
                    val dose = if (event.logType == LogEventType.CARB) linkedDose(event, state.events) else null
                    // Did you take the insulin? (its entry stays unless you say no); anything else is confirmed first.
                    if (dose != null) deleting = event to dose else confirmDelete = event to false
                }
            },
            onDismiss = { editor = null },
        )
    }
    detail?.let { meal ->
        MealDetailSheet(
            loadRecord = { mealRecord(meal.id) },
            onSetTime = { at ->
                val moved = meal.copy(timestampMillis = at.toEpochMilli(), timeSet = true)
                onUpdateEvent(moved)
                detail = moved
            },
            onRename = { name ->
                val named = meal.copy(note = name.ifBlank { null })
                onUpdateEvent(named)
                detail = named
            },
            meal = meal,
            photos = state.photos[meal.id].orEmpty(),
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
                confirmDelete = meal to true
            },
            onEdit = {
                detail = null
                editor = EditorRequest(LogEventType.CARB, existing = meal)
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
            LogEventType.KETONE -> " " + context.getString(R.string.unit_mmol)
            LogEventType.WEIGHT -> " " + context.getString(R.string.profile_kg)
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
            if (photo != null) PhotoThumb(photo, Modifier.size(34.dp)) else TypeChip(type)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                when {
                    event.source != null -> SourceLine(
                        rememberSourceApp(event.source),
                        listOfNotNull(
                            event.note?.takeIf { it != title },
                            event.kcal?.let { "${formatAmountLocalized(Math.round(it).toDouble())} ${stringResource(R.string.entry_unit_kcal)}" },
                        ),
                    )
                    check != null -> Text(
                        listOfNotNull(
                            when {
                                check.percentDiff > 2 -> stringResource(R.string.logbook_sensor_higher, check.sensorMgDl, check.percentDiff)
                                check.percentDiff < -2 -> stringResource(R.string.logbook_sensor_lower, check.sensorMgDl, -check.percentDiff)
                                else -> stringResource(R.string.logbook_sensor_same, check.sensorMgDl)
                            },
                            event.finger?.let { fingerName(it) },
                        ).joinToString(" · "),
                        fontSize = 12.5.sp,
                        color = if (check.agrees) SageDeep else PillHighText,
                    )
                    type == LogEventType.FINGERSTICK && event.finger != null -> Text(fingerName(event.finger!!), fontSize = 12.5.sp, color = CaptionMuted)
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
            Row(Modifier.fillMaxWidth().clickable { onOpenDose(dose) }.padding(start = 46.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground))
                Text(
                    listOfNotNull(typeLabel(LogEventType.INSULIN), doseTiming(dose, event), dose.injectionSite?.let { siteName(it) }).joinToString(" · "),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                Text("${formatAmountLocalized(dose.value ?: 0.0)}${unitLabel(LogEventType.INSULIN)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
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
                modifier = Modifier.padding(start = 46.dp, top = 6.dp),
            )
        }
        if (askForInsulin) {
            Row(Modifier.padding(start = 46.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.logbook_no_insulin), fontSize = 13.5.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
                PillButton(stringResource(R.string.logbook_add_insulin), onClick = onAddInsulin)
            }
        }
    }
}

/** The entry's type as a soft tinted circle with its line icon, in the type's colour. */
@Composable
private fun TypeChip(type: LogEventType) {
    val tint = colorForLogEventType(type)
    Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(iconForLogEventType(type)), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LogbookEmptyState(onLogFirst: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.logbook_empty_title), fontFamily = HeadlineSerifFontFamily, fontSize = 23.sp, lineHeight = 28.sp, color = MaterialTheme.colorScheme.onBackground)
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

/** "142" and Sukoon's arrow (then [suffix], e.g. "now"), tinted like Home's pills: low red, high amber, in range neutral. */
@Composable
internal fun GlucosePill(reading: GlucoseReading, suffix: String? = null) {
    val (background, text) = when {
        reading.glucoseMgDl < 70 -> PillLowBg to PillLowText
        reading.glucoseMgDl > 180 -> PillHighBg to PillHighText
        else -> PillNeutralBg to MaterialTheme.colorScheme.onBackground
    }
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        com.sukoon.app.ui.components.NumberWithTrend(String.format(Locale.getDefault(), "%d", reading.glucoseMgDl), reading.trend, text, 12.sp, fontWeight = FontWeight.SemiBold)
        suffix?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = text) }
    }
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
    LogEventType.KETONE -> stringResource(R.string.logbook_type_ketone)
    LogEventType.WEIGHT -> stringResource(R.string.logbook_type_weight)
}

@Composable
internal fun unitLabel(type: LogEventType): String = when (type) {
    LogEventType.CARB -> stringResource(R.string.logbook_unit_grams)
    LogEventType.INSULIN -> stringResource(R.string.logbook_unit_units)
    LogEventType.BASAL -> stringResource(R.string.logbook_unit_units)
    LogEventType.FINGERSTICK -> " " + stringResource(R.string.home_unit_mgdl)
    LogEventType.ACTIVITY -> stringResource(R.string.logbook_unit_minutes)
    LogEventType.NOTE -> ""
    LogEventType.KETONE -> " " + stringResource(R.string.unit_mmol)
    LogEventType.WEIGHT -> " " + stringResource(R.string.profile_kg)
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

/** The rapid insulin taken for [meal]: linked to it, or within 30 minutes of it. */
internal fun linkedDose(meal: EventEntity, events: List<EventEntity>): EventEntity? =
    events.filter { it.logType == LogEventType.INSULIN && (it.value ?: 0.0) > 0 && (it.mealId == meal.id || (it.mealId == null && kotlin.math.abs(it.timestampMillis - meal.timestampMillis) <= 30 * 60_000L)) }
        .minByOrNull { kotlin.math.abs(it.timestampMillis - meal.timestampMillis) }

/** What an edit from [planned] to [eaten] grams means for [meal]'s insulin now; null when it doesn't matter. */
internal fun assessMealEdit(planned: Double, eaten: Double, meal: EventEntity, state: LogbookUiState, dose: com.sukoon.app.insulin.DoseSettings, action: com.sukoon.app.insulin.InsulinAction): com.sukoon.app.insulin.MealGap? {
    val insulin = linkedDose(meal, state.events) ?: return null
    val mealAt = java.time.Instant.ofEpochMilli(meal.timestampMillis)
    val slot = com.sukoon.app.insights.InsightEngine.slotFor(mealAt.atZone(java.time.ZoneId.systemDefault()).hour)
    return com.sukoon.app.insulin.MealChange.assess(
        planned = planned, eaten = eaten, dose = insulin.value ?: 0.0, doseAt = java.time.Instant.ofEpochMilli(insulin.timestampMillis),
        mealAt = mealAt, now = java.time.Instant.now(), ratio = dose.carbRatio[slot], factor = dose.correctionFactor,
        glucose = state.glucoseNow?.glucoseMgDl, action = action, step = dose.step,
        lowSince2h = state.lowAt != null,
    )
}

