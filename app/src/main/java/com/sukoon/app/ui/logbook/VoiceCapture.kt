package com.sukoon.app.ui.logbook

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.sukoon.app.ui.components.PillState

/** Two stretches of speech as one text: trimmed, a space between, blanks dropped. */
internal fun joinSpeech(before: String, after: String): String =
    listOf(before, after).map(String::trim).filter(String::isNotEmpty).joinToString(" ")

/** Whether the app is in Arabic (the recognizer's first language then, English the second). */
internal fun Context.speaksArabic() = resources.configuration.locales[0].language == "ar"

/**
 * Egyptian Arabic and English both, the app's language first: Kai mixes them in one sentence. On
 * Android 14+ the recognizer may tell them apart and switch mid-sentence; ones that can't ignore it.
 */
internal fun Intent.bilingual(arabic: Boolean): Intent {
    val (first, second) = if (arabic) "ar-EG" to "en-US" else "en-US" to "ar-EG"
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, first)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, first)
    // Not in the SDK, but Google's recognizer reads it: the other language as a second choice.
    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(second))
    if (Build.VERSION.SDK_INT >= 34) {
        val both = arrayListOf(first, second)
        putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
        putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, both)
        putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
        putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, both)
    }
    return this
}

/**
 * Listening inside Sukoon (Sotto's way: the pill shows your voice and the words as they come) with
 * Android's SpeechRecognizer: on the phone itself where it has both languages, else the phone's
 * speech service. It keeps listening across pauses until [finish] (the pill, or Done) or two
 * minutes, then hands the whole text to onFinal to check: nothing is sent on a pause. Without the
 * microphone permission, or with no recognizer at all, [rememberVoiceCapture] falls back to the
 * system's speech window ([rememberSpeech]).
 */
class VoiceCapture(private val context: Context, private val onFinal: (String?) -> Unit) {
    var state by mutableStateOf<PillState?>(null)
        private set
    /** The microphone's loudness, 0..1, for the pill's bars. */
    var level by mutableFloatStateOf(0f)
        private set
    /** The words so far, across pauses, while listening. */
    var partial by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null
    private var onDevice = false
    /** Whether the phone's own recognizer has Arabic and English installed (Android 14+); null until asked. */
    private var onDeviceBoth: Boolean? = null
    /** Everything heard this session before the current stretch of speech. */
    private var heard = ""
    /** Until [finish]: each pause ends one recognition and the next one starts straight away. */
    private var live = false
    private val main = Handler(Looper.getMainLooper())
    private val cap = Runnable { finish() }
    private val giveUp = Runnable { complete() }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        // A pause to think doesn't end it too soon (and when it does, it starts again).
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        .bilingual(context.speaksArabic())

    /** Starts a session; [after] is text already there, which "Say more" adds to. */
    fun listen(after: String = "") {
        heard = after.trim()
        partial = heard
        level = 0f
        live = true
        state = PillState.LISTENING
        main.removeCallbacksAndMessages(null)
        main.postDelayed(cap, 120_000) // a safety cap on one session
        onDevice = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) && onDeviceBoth != false
        open()
    }

    // ponytail: a new recognizer for every stretch of speech; a word said in the ~0.3 s gap can be
    // missed and some phones beep at each start. EXTRA_SEGMENTED_SESSION (API 33) avoids it where supported.
    private fun open() {
        release()
        val r = if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(listener)
        if (onDevice && Build.VERSION.SDK_INT >= 34 && onDeviceBoth == null) {
            // Once: can the phone's own recognizer hear both languages? If not, the speech service
            // (online, which can detect and switch) does it. When it can't tell, stay on the phone.
            r.checkRecognitionSupport(intent(), ContextCompat.getMainExecutor(context), object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    onDeviceBoth = listOf("ar", "en").all { l -> support.installedOnDeviceLanguages.any { it.startsWith(l) } }
                    go(r)
                }
                override fun onError(error: Int) { onDeviceBoth = true; go(r) }
            })
            return
        }
        r.startListening(intent())
    }

    private fun go(r: SpeechRecognizer) {
        if (!live || recognizer !== r) return
        if (onDeviceBoth == false) { onDevice = false; open() } else r.startListening(intent())
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        // RMS arrives in dB, about -2 (quiet) to 10 (loud speech).
        override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        // A pause: the pill stays LISTENING, the next stretch starts on its own.
        override fun onEndOfSpeech() { level = 0f }
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { partial = joinSpeech(heard, it) }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onResults(results: Bundle?) {
            heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { joinSpeech(heard, it) } ?: partial
            partial = heard
            if (live) open() else complete()
        }
        override fun onError(error: Int) {
            heard = partial // what was said before it stopped
            // The phone's own recognizer can't do this language (often Arabic): the speech service can.
            if (live && onDevice && error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_CLIENT)) {
                onDevice = false
                onDeviceBoth = false
                open()
                return
            }
            // Silence, nothing it could make out, or not free yet: just a pause, keep listening (a beat
            // later, so a recognizer that fails at once doesn't spin).
            if (live && error in setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY)) {
                release()
                main.postDelayed({ if (live) open() }, 250)
                return
            }
            complete()
        }
    }

    /** All of it to onFinal: null (and the error pill) when nothing was heard, else the pill goes. */
    private fun complete() {
        live = false
        main.removeCallbacksAndMessages(null)
        release()
        val text = heard.takeIf { it.isNotBlank() }
        state = if (text == null) PillState.ERROR else null
        onFinal(text)
    }

    /** Tapping the pill or Done: the last words are written down, then it's all handed over. */
    fun finish() {
        if (!live) return
        live = false
        state = PillState.TRANSCRIBING
        val r = recognizer ?: return complete() // between two stretches
        r.stopListening()
        main.postDelayed(giveUp, 3000) // some recognizers never answer a stop between stretches
    }

    fun cancel() { live = false; main.removeCallbacksAndMessages(null); release(); partial = ""; state = null }

    /** The entries are worked out (POLISHING, DONE) or failed (ERROR); null hides the pill. */
    fun settle(to: PillState?) { state = to }

    private fun release() { recognizer?.destroy(); recognizer = null }
}

/**
 * A [VoiceCapture] for this screen, asking for the microphone the first time. The start function
 * listens in the app when it can, else opens the system's speech window ([fallbackPrompt]); what it
 * takes is text already there, which the new words are added to.
 */
@Composable
fun rememberVoiceCapture(fallbackPrompt: String, onUnavailable: () -> Unit, onFinal: (String?) -> Unit): Pair<VoiceCapture, (String) -> Unit> {
    val context = LocalContext.current
    val capture = remember { VoiceCapture(context, onFinal) }
    DisposableEffect(capture) { onDispose { capture.cancel() } }
    var after by remember { mutableStateOf("") }
    val system = rememberSpeech(fallbackPrompt) { said -> onFinal(joinSpeech(after, said.orEmpty()).ifEmpty { null }) }
    val fallback = { if (!system()) onUnavailable() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capture.listen(after) else fallback()
    }
    val start: (String) -> Unit = { text ->
        after = text
        when {
            !SpeechRecognizer.isRecognitionAvailable(context) -> fallback()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> capture.listen(text)
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return capture to start
}
