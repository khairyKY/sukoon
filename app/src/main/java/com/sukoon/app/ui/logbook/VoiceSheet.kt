package com.sukoon.app.ui.logbook

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.ai.ChatTurn
import com.sukoon.app.ai.VoiceEntry
import com.sukoon.app.ai.VoiceLog
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.reminders.BasalReminder
import com.sukoon.app.ui.components.SukoonSheet
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.launch

/** The phone's speech recognition, in the app's language; null text when nothing was heard. */
@Composable
fun rememberSpeech(prompt: String, onText: (String?) -> Unit): () -> Boolean {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        onText(r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() })
    }
    return {
        runCatching {
            launcher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt),
            )
        }.isSuccess // false: no speech recognition on this phone
    }
}

/**
 * Say it (design: the add sheet's mic): listen, work out the entries, show them to check and
 * correct, then save. Nothing is saved until Save. Carbs for a low (said, or with glucose under 70)
 * are marked as a treatment; long-acting within 8 hours of the last one shows the double-dose guard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceSheet(glucoseNow: Int?, onSave: (List<EntryDraft>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = (context.applicationContext as com.sukoon.app.SukoonApp).container
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var said by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val entries = remember { mutableStateListOf<VoiceEntry>() }
    val amounts = remember { mutableStateListOf<String>() }

    fun understand(text: String) {
        said = text
        working = true
        problem = null
        entries.clear()
        amounts.clear()
        scope.launch {
            runCatching { VoiceLog.parse(app.gemini.generate(VoiceLog.systemPrompt(), listOf(ChatTurn(fromUser = true, text = text)), jsonOutput = true)) }
                .onSuccess { found ->
                    entries += found
                    amounts += found.map { e -> e.amount?.let(::formatAmount).orEmpty() }
                    if (found.isEmpty()) problem = context.getString(R.string.voice_nothing)
                }
                .onFailure { problem = context.getString(R.string.voice_failed, it.message ?: "") }
            working = false
        }
    }
    val listen = rememberSpeech(stringResource(R.string.voice_hint)) { text -> if (text == null) { if (said == null) onDismiss() } else understand(text) }
    LaunchedEffect(Unit) { if (!listen()) problem = context.getString(R.string.voice_no_recognizer) }

    val lastBasal by remember { app.logbookRepository.latest(LogEventType.BASAL) }.collectAsState(initial = null as EventEntity?)

    SukoonSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.voice_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
            said?.let { Text(stringResource(R.string.voice_heard, it), fontSize = 14.sp, lineHeight = 20.sp, color = CaptionMuted) }
            if (working) Text(stringResource(R.string.voice_understanding), fontSize = 14.sp, color = SageDeep)
            problem?.let { Text(it, fontSize = 14.sp, color = PillHighText) }
            entries.forEachIndexed { i, e ->
                val treatment = e.type == LogEventType.CARB && (e.treatment || (glucoseNow ?: 999) < 70)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, outline(), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(iconForLogEventType(e.type)), null, tint = colorForLogEventType(e.type), modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label(e), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        val notes = listOfNotNull(
                            stringResource(R.string.voice_estimated).takeIf { e.estimated },
                            stringResource(R.string.voice_treatment).takeIf { treatment },
                        )
                        if (notes.isNotEmpty()) Text(notes.joinToString(" · "), fontSize = 12.sp, color = CaptionMuted)
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
                    Text("✕", fontSize = 16.sp, color = CaptionMuted,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { entries.removeAt(i); amounts.removeAt(i) }.padding(10.dp))
                }
            }
            if (entries.isNotEmpty() && !working) {
                val kinds = activityKinds()
                Text(
                    stringResource(R.string.voice_save_all), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sage).clickable {
                        val now = Instant.now()
                        onSave(entries.mapIndexedNotNull { i, e ->
                            val amount = if (e.amount == null) null else amounts[i].toDoubleOrNull() ?: return@mapIndexedNotNull null
                            val note = when (e.type) {
                                LogEventType.ACTIVITY -> kinds(e.name, e.effort)
                                LogEventType.CARB, LogEventType.NOTE -> e.name
                                else -> null
                            }
                            EntryDraft(e.type, amount, note, now, treatment = e.type == LogEventType.CARB && (e.treatment || (glucoseNow ?: 999) < 70))
                        })
                    }.heightIn(min = 54.dp).padding(16.dp),
                )
            }
            if (!working) Text(
                stringResource(R.string.voice_again), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { if (!listen()) problem = context.getString(R.string.voice_no_recognizer) }.heightIn(min = 44.dp).padding(vertical = 12.dp),
            )
        }
    }
}

/** "Koshari · 85 g", "Rapid · 6 u", "Strength training · 60 min". */
@Composable
private fun label(e: VoiceEntry): String {
    val kinds = activityKinds()
    val what = when (e.type) {
        LogEventType.CARB -> e.name ?: typeLabel(e.type)
        LogEventType.ACTIVITY -> kinds(e.name, e.effort)
        LogEventType.NOTE -> e.name.orEmpty()
        else -> typeLabel(e.type)
    }
    return what
}

/** The activity editor's words for a kind and effort ("Strength training · Hard"), so voice entries read the same. */
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
