package com.sukoon.app.ui.logbook

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sukoon.app.R
import com.sukoon.app.ai.CarbEstimate
import com.sukoon.app.ai.ChatTurn
import com.sukoon.app.ai.MealPhoto
import com.sukoon.app.ai.VoiceEntry
import com.sukoon.app.ai.VoiceLog
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.food.Food
import com.sukoon.app.food.PlateItem
import com.sukoon.app.reminders.BasalReminder
import com.sukoon.app.ui.components.PillState
import com.sukoon.app.ui.components.SukoonSheet
import com.sukoon.app.ui.components.SukoonTextField
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Motion
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The phone's speech window, the app's language first and the other allowed; null text when nothing was heard. */
@Composable
fun rememberSpeech(prompt: String, onText: (String?) -> Unit): () -> Boolean {
    val arabic = LocalContext.current.speaksArabic()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        onText(r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() })
    }
    return {
        runCatching {
            launcher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .bilingual(arabic)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt),
            )
        }.isSuccess // false: no speech recognition on this phone
    }
}

/** What came into the eat box. */
internal sealed interface EatInput {
    data class Words(val text: String) : EatInput
    data object Voice : EatInput
    class Photo(val jpeg: ByteArray) : EatInput
    data object Scan : EatInput
}

/** One place to say what you ate (UX review: not six ways to choose from first): words, the mic, a photo or a barcode. */
@Composable
internal fun EatBox(ai: Boolean, onEat: (EatInput) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val send = { if (text.isNotBlank()) onEat(EatInput.Words(text.trim())) }
    val sendButton: @Composable () -> Unit = {
        Box(Modifier.padding(end = 4.dp).size(44.dp).clip(RoundedCornerShape(12.dp)).background(Sage).clickable(onClick = send), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_check), stringResource(R.string.eat_send), tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
    val captureUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", MealPhoto.newCaptureFile(context)) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) scope.launch {
            runCatching { withContext(Dispatchers.IO) { MealPhoto.loadScaledJpeg(context, captureUri) } }
                .onSuccess { onEat(EatInput.Photo(it)) }
                .onFailure { context.toast(it.message ?: it.javaClass.simpleName) }
        }
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.eat_title), fontFamily = HeadlineSerifFontFamily, fontSize = 20.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 4.dp))
        SukoonTextField(
            text, { text = it },
            Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.eat_hint),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            trailingIcon = sendButton.takeIf { text.isNotBlank() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ai) EatButton(R.drawable.ic_mic, stringResource(R.string.voice_title), Modifier.weight(1f)) { onEat(EatInput.Voice) }
            if (ai) EatButton(R.drawable.ic_camera, stringResource(R.string.entry_photo), Modifier.weight(1f)) { takePhoto.launch(captureUri) }
            EatButton(R.drawable.ic_barcode, stringResource(R.string.eat_scan), Modifier.weight(1f)) { onEat(EatInput.Scan) }
        }
    }
}

@Composable
private fun EatButton(icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(SageMist).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = SageDeep, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = SageDeep, maxLines = 1)
    }
}

/**
 * What the eat box took in, as one meal to check: the words or speech worked out by the AI (the
 * Say it prompt), the photo's carb estimate, or the barcode / food search. Each food's carbs can be
 * typed over or removed; Log meal opens the meal editor with them (dose suggestion, insulin, timing,
 * photo). Long-acting, a walk or a treatment said along with it are saved with the meal, or on their
 * own when nothing was eaten. Nothing is saved until then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EatSheet(
    start: EatInput,
    /** The AI's carb estimate; null without a Gemini key (words then go to the food search). */
    estimate: (suspend (String, List<ByteArray>) -> CarbEstimate)?,
    onMeal: (EditorRequest) -> Unit,
    onSave: (List<EntryDraft>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = (context.applicationContext as com.sukoon.app.SukoonApp).container
    val scope = rememberCoroutineScope()
    val voiced = start is EatInput.Voice
    var said by remember { mutableStateOf<String?>(null) }
    /** What was heard, to fix before it's worked out; null until heard and once it's used. */
    var draft by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(estimate != null && (start is EatInput.Words || start is EatInput.Photo)) }
    var problem by remember { mutableStateOf<String?>(null) }
    val plate = remember { mutableStateListOf<PlateItem>() }
    var insulin by remember { mutableStateOf<Double?>(null) }
    val rest = remember { mutableStateListOf<VoiceEntry>() }
    val amounts = remember { mutableStateListOf<String>() }
    val photos = remember { listOfNotNull((start as? EatInput.Photo)?.jpeg) }
    // The food search: true opens on the barcode; words go there straight away without the AI.
    var picking by remember { mutableStateOf(if (start is EatInput.Scan) true else if (start is EatInput.Words && estimate == null) false else null) }
    val pickerOnly = remember { picking != null }
    val unnamed = typeLabel(LogEventType.CARB)
    val kinds = activityKinds()

    var capture: VoiceCapture? = null
    fun understand(text: String) {
        said = text
        working = true
        capture?.settle(PillState.POLISHING)
        problem = null
        plate.clear(); insulin = null; rest.clear(); amounts.clear()
        scope.launch {
            runCatching { VoiceLog.parse(app.gemini.generate(VoiceLog.systemPrompt(), listOf(ChatTurn(fromUser = true, text = text)), jsonOutput = true, lite = true)) }
                .onSuccess { found ->
                    val plan = eatPlan(found, unnamed)
                    plate += plan.plate
                    insulin = plan.insulin
                    rest += plan.rest
                    amounts += plan.rest.map { e -> e.amount?.let(::formatAmount).orEmpty() }
                    if (found.isEmpty()) problem = context.getString(R.string.voice_nothing)
                }
                .onFailure { problem = context.getString(R.string.voice_failed, it.message ?: "") }
            working = false
            capture?.settle(if (problem == null) PillState.DONE else PillState.ERROR)
        }
    }
    // Sotto's way: listen in the app, the pill showing your voice and your words as they come.
    val (voice, listen) = rememberVoiceCapture(stringResource(R.string.voice_hint), onUnavailable = { problem = context.getString(R.string.voice_no_recognizer) }) { text ->
        if (text == null) { if (said == null) problem = context.getString(R.string.voice_nothing_heard) } else draft = text
    }
    capture = voice
    LaunchedEffect(Unit) {
        when (start) {
            EatInput.Voice -> listen("")
            is EatInput.Words -> if (estimate != null) understand(start.text)
            is EatInput.Photo -> if (estimate != null) {
                working = true
                runCatching { estimate("", photos) }.onSuccess { plate += photoPlate(it) }.onFailure { problem = context.getString(R.string.voice_failed, it.message ?: "") }
                working = false
            }
            EatInput.Scan -> Unit
        }
    }
    val hearing = voice.state == PillState.LISTENING || voice.state == PillState.TRANSCRIBING
    val lastBasal by remember { app.logbookRepository.latest(LogEventType.BASAL) }.collectAsState(initial = null as EventEntity?)

    /** What else was said, as entries (their amounts as corrected). */
    fun restDrafts(): List<EntryDraft> {
        val now = Instant.now()
        return rest.mapIndexedNotNull { i, e ->
            val amount = if (e.amount == null) null else amounts[i].toDoubleOrNull() ?: return@mapIndexedNotNull null
            val note = when (e.type) {
                LogEventType.ACTIVITY -> kinds(e.name, e.effort)
                LogEventType.CARB, LogEventType.NOTE -> e.name
                else -> null
            }
            EntryDraft(
                e.type, amount, note, now,
                nutrients = Nutrients(protein = e.protein, fat = e.fat, kcal = e.kcal).takeIf { e.type == LogEventType.CARB && it != Nutrients() },
                treatment = e.type == LogEventType.CARB,
            )
        }
    }

    SukoonSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
                .animateContentSize(tween(if (Motion.reduced()) 0 else Motion.BASE, easing = Motion.Standard)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.eat_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, lineHeight = 31.sp, color = MaterialTheme.colorScheme.onBackground)
            voice.state?.let { st ->
                com.sukoon.app.ui.components.SottoPill(st, voice.level, onTap = voice::finish, onCancel = if (st == PillState.LISTENING) voice::cancel else null,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp))
            }
            if (hearing) {
                Text(stringResource(R.string.voice_tap_done), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(voice.partial.ifBlank { stringResource(R.string.voice_listening_hint) }, fontSize = 15.sp, lineHeight = 21.sp,
                    color = if (voice.partial.isBlank()) CaptionMuted else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = TextAlign.Center)
                if (voice.state == PillState.LISTENING) Text(
                    stringResource(R.string.voice_done), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(10.dp)).clickable(onClick = voice::finish).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            draft?.takeIf { !hearing }?.let { text ->
                // Nothing goes on by itself: the words to fix first, then Use this, or Say more to add to them.
                SukoonTextField(text, { draft = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.voice_heard_label), minLines = 2)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.voice_use_this), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (text.isBlank()) Sage.copy(alpha = 0.4f) else Sage)
                            .clickable(enabled = text.isNotBlank()) { draft = null; understand(text.trim()) }.heightIn(min = 54.dp).padding(16.dp),
                    )
                    Text(
                        stringResource(R.string.voice_say_more), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { listen(text) }.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
            said?.let { Text(stringResource(if (voiced) R.string.voice_heard else R.string.eat_wrote, it), fontSize = 14.sp, lineHeight = 20.sp, color = CaptionMuted) }
            if (working && voice.state == null) Text(stringResource(R.string.voice_understanding), fontSize = 14.sp, color = SageDeep)
            problem?.let { Text(it, fontSize = 14.sp, color = PillHighText) }
            if (plate.isNotEmpty()) {
                PlateCard(plate, onRemove = { plate.remove(it) }, onAdd = { picking = false }, onCarbs = { i, g -> plate[i] = withCarbs(plate[i], g) })
                val sums = plateSums(plate)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.eat_total, gramsText(sums[Food.CARBS])), fontFamily = HeadlineSerifFontFamily, fontSize = 21.sp, color = MaterialTheme.colorScheme.onBackground)
                    listOfNotNull(
                        sums[3].takeIf { it >= 0.5 }?.let { stringResource(R.string.carb_ai_protein, gramsText(it)) },
                        sums[4].takeIf { it >= 0.5 }?.let { stringResource(R.string.carb_ai_fat, gramsText(it)) },
                        sums[Food.KCAL].takeIf { it >= 0.5 }?.let { formatAmountLocalized(Math.round(it).toDouble()) + " " + stringResource(R.string.entry_unit_kcal) },
                    ).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), fontSize = 13.sp, color = CaptionMuted) }
                    insulin?.let { Text(stringResource(R.string.eat_insulin, formatAmountLocalized(it)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SageDeep) }
                }
            } else if (!working && !hearing && draft == null && (problem != null || !voiced)) {
                // Nothing on the plate: the food search is one tap away.
                Text(
                    stringResource(R.string.food_search_hint), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picking = false }.heightIn(min = 48.dp).padding(vertical = 12.dp),
                )
            }
            if (rest.isNotEmpty() && plate.isNotEmpty()) Eyebrow(stringResource(R.string.eat_also), Modifier.padding(top = 4.dp))
            rest.forEachIndexed { i, e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, outline(), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(iconForLogEventType(e.type)), null, tint = colorForLogEventType(e.type), modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label(e), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        if (e.type == LogEventType.CARB) Text(stringResource(R.string.voice_treatment), fontSize = 12.sp, color = CaptionMuted)
                        if (e.type == LogEventType.BASAL) lastBasal?.let { BasalReminder.recent(listOf(it), Instant.now()) }?.let { b ->
                            Text(stringResource(R.string.basal_already_title, formatAmountLocalized(b.value ?: 0.0), com.sukoon.app.platform.TimeFormat.of().format(Instant.ofEpochMilli(b.timestampMillis))),
                                fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = PillHighText,
                                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(PillHighBg).padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    if (e.amount != null) {
                        // The amount, to correct before saving.
                        BasicTextField(
                            amounts[i], { v -> amounts[i] = v.filter { it.isDigit() || it == '.' }.take(5) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(Sage),
                            modifier = Modifier.width(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        Text(unitLabel(e.type).trim(), fontSize = 13.sp, color = CaptionMuted)
                    }
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable { rest.removeAt(i); amounts.removeAt(i) }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.food_remove, label(e)), tint = CaptionMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (!working && (plate.isNotEmpty() || rest.isNotEmpty())) {
                val carbs = plateSums(plate)[Food.CARBS]
                Text(
                    if (plate.isNotEmpty()) stringResource(R.string.eat_log_meal, gramsText(carbs)) else stringResource(R.string.voice_save_all),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sage).clickable {
                        if (plate.isEmpty()) onSave(restDrafts())
                        else onMeal(EditorRequest(LogEventType.CARB, plate = plate.toList(), insulin = insulin, photos = photos, extras = restDrafts()))
                    }.heightIn(min = 54.dp).padding(16.dp),
                )
            }
            if (voiced && !working && !hearing && draft == null) Text(
                stringResource(R.string.voice_again), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { said = null; problem = null; plate.clear(); insulin = null; rest.clear(); amounts.clear(); listen("") }.heightIn(min = 48.dp).padding(vertical = 12.dp),
            )
            if (voiced) com.sukoon.app.ui.components.PoweredBySotto(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
        }
    }

    picking?.let { scan ->
        FoodPicker(
            scanFirst = scan,
            startQuery = (start as? EatInput.Words)?.text?.takeIf { pickerOnly && plate.isEmpty() }.orEmpty(),
            estimate = estimate,
            onAdd = { plate += it; picking = null },
            // Opened straight from the box: closing it with nothing chosen closes this too.
            onDismiss = { picking = null; if (pickerOnly && plate.isEmpty()) onDismiss() },
        )
    }
}

/** "Koshari · 85 g", "Rapid · 6 u", "Strength training · 60 min". */
@Composable
private fun label(e: VoiceEntry): String {
    val kinds = activityKinds()
    return when (e.type) {
        LogEventType.CARB -> e.name ?: typeLabel(e.type)
        LogEventType.ACTIVITY -> kinds(e.name, e.effort)
        LogEventType.NOTE -> e.name.orEmpty()
        else -> typeLabel(e.type)
    }
}

/** The activity editor's words for a kind and effort ("Strength training · Hard"), so spoken entries read the same. */
@Composable
private fun activityKinds(): (String?, String?) -> String {
    val kind = mapOf(
        "STRENGTH" to stringResource(R.string.workout_strength), "WALK" to stringResource(R.string.workout_walk), "RUN" to stringResource(R.string.workout_run),
        "BIKE" to stringResource(R.string.workout_bike), "SWIM" to stringResource(R.string.workout_swim), "HIIT" to stringResource(R.string.workout_hiit),
        "FOOTBALL" to stringResource(R.string.workout_football), "YOGA" to stringResource(R.string.workout_yoga),
    )
    val other = stringResource(R.string.workout_other)
    val effort = mapOf("EASY" to stringResource(R.string.effort_easy), "MODERATE" to stringResource(R.string.effort_moderate), "HARD" to stringResource(R.string.effort_hard))
    return { k, e -> listOfNotNull(kind[k] ?: other, effort[e]).joinToString(" · ") }
}
