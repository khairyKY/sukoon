package com.sukoon.app.ui.logbook

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
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
import java.util.Locale

/**
 * Listening inside Sukoon (Sotto's way: the pill shows your voice and the words as they come) with
 * Android's SpeechRecognizer: on the phone itself where it speaks the language (Android 12+), else
 * the phone's speech service. Without the microphone permission, or with no recognizer at all,
 * [start] falls back to the system's speech window ([rememberSpeech]).
 */
class VoiceCapture(private val context: Context, private val onFinal: (String?) -> Unit) {
    var state by mutableStateOf<PillState?>(null)
        private set
    /** The microphone's loudness, 0..1, for the pill's bars. */
    var level by mutableFloatStateOf(0f)
        private set
    /** The words so far, while listening. */
    var partial by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null
    private var triedOnDevice = false

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        // A pause to think doesn't end it too soon.
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)

    fun listen(onDevice: Boolean = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
        release()
        triedOnDevice = onDevice
        val r = if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        partial = ""
        level = 0f
        state = PillState.LISTENING
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            // RMS arrives in dB, about -2 (quiet) to 10 (loud speech).
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (state == PillState.LISTENING) state = PillState.TRANSCRIBING; level = 0f }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: partial.takeIf { it.isNotBlank() }
                release()
                state = if (text == null) PillState.ERROR else PillState.POLISHING
                onFinal(text)
            }
            override fun onError(error: Int) {
                // The phone's own recognizer can't do this language (often Arabic): the speech service can.
                if (triedOnDevice && error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_CLIENT)) {
                    listen(onDevice = false)
                    return
                }
                release()
                val heard = partial.takeIf { it.isNotBlank() }
                state = if (heard == null) PillState.ERROR else PillState.POLISHING
                onFinal(heard)
            }
        })
        r.startListening(intent())
    }

    /** Tapping the pill: done talking. */
    fun finish() { recognizer?.stopListening(); if (state == PillState.LISTENING) state = PillState.TRANSCRIBING }

    fun cancel() { release(); state = null }

    /** The entries are worked out (DONE) or failed (ERROR); null hides the pill. */
    fun settle(to: PillState?) { state = to }

    fun release() { recognizer?.destroy(); recognizer = null }
}

/**
 * A [VoiceCapture] for this screen, asking for the microphone the first time. [start] listens in
 * the app when it can, else opens the system's speech window ([fallbackPrompt]).
 */
@Composable
fun rememberVoiceCapture(fallbackPrompt: String, onUnavailable: () -> Unit, onFinal: (String?) -> Unit): Pair<VoiceCapture, () -> Unit> {
    val context = LocalContext.current
    val capture = remember { VoiceCapture(context, onFinal) }
    DisposableEffect(capture) { onDispose { capture.release() } }
    val system = rememberSpeech(fallbackPrompt, onFinal)
    val fallback = { if (!system()) onUnavailable() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capture.listen() else fallback()
    }
    val start: () -> Unit = {
        when {
            !SpeechRecognizer.isRecognitionAvailable(context) -> fallback()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> capture.listen()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return capture to start
}
