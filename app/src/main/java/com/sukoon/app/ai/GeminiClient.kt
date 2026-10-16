package com.sukoon.app.ai

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One chat message. `fromUser = false` is the model's reply. */
data class ChatTurn(val fromUser: Boolean, val text: String)

class AiException(message: String) : Exception(message)

/**
 * Minimal Gemini `generateContent` client over HttpURLConnection + the platform's org.json
 * (ponytail: no SDK/Retrofit for one POST). The user brings their own free AI Studio key.
 *
 * Tries [MODELS] in order and falls through on "busy / gone" answers (429/5xx/404) and on a model
 * that never answers — the free tier regularly returns 503 "high demand" (or just hangs) for the
 * newest model while older ones answer fine.
 * Auth/request errors (400/403) fail fast since every model would reject them the same way.
 */
class GeminiClient(private val apiKey: () -> String) {

    val hasKey: Boolean get() = apiKey().isNotBlank()

    /**
     * [lite]: the quick model first (voice parsing: short, structured). Thinking is off for every
     * request: Sukoon's jobs are summaries and estimates, and thinking was most of the wait. A model
     * that refuses "no thinking" is asked again without the setting.
     */
    suspend fun generate(
        systemPrompt: String,
        turns: List<ChatTurn>,
        images: List<ByteArray> = emptyList(),
        jsonOutput: Boolean = false,
        lite: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val key = apiKey().ifBlank { throw AiException(NO_KEY) }
        val body = requestBody(systemPrompt, turns, images, jsonOutput).toString()
        val plain by lazy { requestBody(systemPrompt, turns, images, jsonOutput, thinkingOff = false).toString() }
        var lastError = "No model answered"
        for (model in if (lite) LITE_FIRST else MODELS) {
            var (code, response) = post(model, key, body)
            if (code == 400 && response.contains("thinking", ignoreCase = true)) post(model, key, plain).let { code = it.first; response = it.second }
            if (code == 200) return@withContext replyText(response)
            lastError = errorMessage(code, response)
            if (code != TIMED_OUT && code != 404 && code != 429 && code < 500) break
        }
        throw AiException(lastError)
    }

    /**
     * Ask's answer as it's written: [onText] gets the text so far after each piece. Falls through
     * the models like [generate] until one starts answering.
     */
    suspend fun stream(systemPrompt: String, turns: List<ChatTurn>, onText: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val key = apiKey().ifBlank { throw AiException(NO_KEY) }
        val body = requestBody(systemPrompt, turns, emptyList(), false).toString()
        val plain by lazy { requestBody(systemPrompt, turns, emptyList(), false, thinkingOff = false).toString() }
        var lastError = "No model answered"
        for (model in MODELS) {
            var r = streamPost(model, key, body, onText)
            if (r.first == 400 && r.third.contains("thinking", ignoreCase = true)) r = streamPost(model, key, plain, onText)
            val (code, text, error) = r
            if (code == 200 && text.isNotEmpty()) return@withContext text
            lastError = if (code == 200) "Gemini returned an empty answer." else errorMessage(code, error)
            if (code != 200 && code != TIMED_OUT && code != 404 && code != 429 && code < 500) break
        }
        throw AiException(lastError)
    }

    /** (code, the answer, the error body). */
    private fun streamPost(model: String, key: String, body: String, onText: (String) -> Unit): Triple<Int, String, String> {
        val connection = URL("$BASE/$model:streamGenerateContent?alt=sse").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 40_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-goog-api-key", key)
        return try {
            connection.connect()
            try {
                connection.outputStream.use { it.write(body.toByteArray()) }
                val code = connection.responseCode
                if (code !in 200..299) return Triple(code, "", connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
                val text = StringBuilder()
                connection.inputStream.bufferedReader().useLines { lines ->
                    lines.filter { it.startsWith("data:") }.forEach { line ->
                        val piece = runCatching { partText(line.removePrefix("data:").trim()) }.getOrDefault("")
                        if (piece.isNotEmpty()) {
                            text.append(piece)
                            onText(text.toString())
                        }
                    }
                }
                Triple(200, text.toString().trim(), "")
            } catch (e: java.net.SocketTimeoutException) {
                Triple(TIMED_OUT, "", "")
            }
        } catch (e: java.io.IOException) {
            throw AiException("Couldn't reach Gemini — check your connection. (${e.message})")
        } finally {
            connection.disconnect()
        }
    }

    private fun post(model: String, key: String, body: String): Pair<Int, String> {
        val connection = URL("$BASE/$model:generateContent").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 40_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-goog-api-key", key) // header, not ?key=, so it stays out of URL logs
        return try {
            connection.connect() // failing here is the network itself, the same for every model
            try {
                connection.outputStream.use { it.write(body.toByteArray()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
            } catch (e: java.net.SocketTimeoutException) {
                // Connected, but this model never answered (seen with gemini-flash-latest): try the next.
                TIMED_OUT to ""
            }
        } catch (e: java.io.IOException) {
            throw AiException("Couldn't reach Gemini — check your connection. (${e.message})")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        /** Not an HTTP code: the model took longer than the read timeout. */
        const val TIMED_OUT = -1
        const val NO_KEY = "Add your Gemini API key in You → AI to use this."

        // `*-latest` aliases track Google's current models so the app doesn't break when one is
        // retired (gemini-2.5-flash already 404s for new keys); the pinned one is the fallback.
        val MODELS = listOf("gemini-flash-latest", "gemini-3.5-flash", "gemini-flash-lite-latest")

        /** The quick model first, for short structured jobs (voice entries). */
        val LITE_FIRST = listOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-3.5-flash")

        fun requestBody(systemPrompt: String, turns: List<ChatTurn>, images: List<ByteArray>, jsonOutput: Boolean, thinkingOff: Boolean = true): JSONObject {
            val contents = JSONArray()
            turns.forEachIndexed { index, turn ->
                val parts = JSONArray().put(JSONObject().put("text", turn.text))
                // The photos ride on the last user turn (the one being answered), every one of them.
                if (index == turns.lastIndex) images.forEach { imageJpeg ->
                    parts.put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject().put("mime_type", "image/jpeg").put("data", Base64.getEncoder().encodeToString(imageJpeg)),
                        ),
                    )
                }
                contents.put(JSONObject().put("role", if (turn.fromUser) "user" else "model").put("parts", parts))
            }
            return JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                .put("contents", contents)
                .apply {
                    val config = JSONObject()
                    if (jsonOutput) config.put("responseMimeType", "application/json")
                    if (thinkingOff) config.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                    if (config.length() > 0) put("generationConfig", config)
                }
        }

        /** One streamed piece's text, thoughts left out ("" when it has none). */
        fun partText(chunk: String): String {
            val parts = JSONObject(chunk).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            return (0 until (parts?.length() ?: 0)).mapNotNull { parts!!.optJSONObject(it) }.filterNot { it.optBoolean("thought") }.joinToString("") { it.optString("text") }
        }

        fun replyText(response: String): String {
            val json = JSONObject(response)
            val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val text = (0 until (parts?.length() ?: 0))
                .mapNotNull { parts!!.optJSONObject(it) }
                .filterNot { it.optBoolean("thought") } // thinking models may return their reasoning as a part
                .joinToString("") { it.optString("text") }
                .trim()
            if (text.isNotEmpty()) return text
            val blocked = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw AiException(if (blocked.isNotEmpty()) "Gemini declined to answer ($blocked)." else "Gemini returned an empty answer.")
        }

        fun errorMessage(code: Int, response: String): String {
            val message = runCatching { JSONObject(response).getJSONObject("error").getString("message") }.getOrNull()
            return when (code) {
                400, 403 -> if (message?.contains("API key", ignoreCase = true) == true) "Gemini rejected the API key. Check it in You → AI." else "Gemini error $code: ${message ?: "bad request"}"
                429 -> "Gemini's free quota is used up for now — try again in a minute."
                TIMED_OUT -> "Gemini took too long to answer. Try again shortly."
                else -> "Gemini is busy right now ($code). Try again shortly."
            }
        }
    }
}
