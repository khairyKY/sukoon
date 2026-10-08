package com.sukoon.app.ai

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AiTest {

    private val zone: ZoneId = ZoneOffset.ofHours(3) // Cairo in summer, fixed so the test never depends on tzdata
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    // 30 h of 1-min readings: a flat 100 except a 250 spike 2 h ago and a 60 low 20 h ago.
    private val readings = (0 until 30 * 60).map { minutesAgo ->
        val mgDl = when (minutesAgo) {
            in 110..130 -> 250
            in 1190..1200 -> 60
            else -> 100
        }
        GlucoseReading(now.minus(Duration.ofMinutes(minutesAgo.toLong())), mgDl, TrendDirection.STEADY, SourceKind.LIBRE_BLE)
    }.reversed()

    @Test
    fun `data brief carries the latest value, both windows, per-day lines and the logbook`() {
        val events = listOf(EventEntity(timestampMillis = now.minusSeconds(3 * 3600).toEpochMilli(), type = "CARB", value = 60.0, note = "koshari"))
        val brief = AiPrompts.dataBrief(readings, events, now, zone)

        assertTrue(brief, brief.contains("LATEST: 100 mg/dL, trend steady, 0 min ago"))
        assertTrue(brief, brief.contains("DATA SOURCE: LIBRE_BLE"))
        assertTrue(brief, brief.contains("LAST 24H: mean"))
        assertTrue(brief, brief.contains("lowest 60 at") && brief.contains("highest 250 at")) // exact extremes survive averaging
        assertTrue(brief, brief.contains("PER DAY"))
        assertTrue(brief, brief.contains("60 g carbs · koshari"))
        assertTrue(brief, brief.contains("13:00 210")) // 15-min bucket 13:00-13:14 local: 11 x 250 + 4 x 100
        // 7 days of 1-min rows would be ~10k lines; the brief must stay small for mobile data.
        assertTrue("brief is ${brief.length} chars", brief.length < 6_000)
    }

    @Test
    fun `empty data still produces a usable brief`() {
        val brief = AiPrompts.dataBrief(emptyList(), emptyList(), now, zone)
        assertTrue(brief.contains("No glucose readings recorded yet."))
        assertTrue(brief.contains("(nothing logged)"))
    }

    @Test
    fun `carb estimate parses plain or fenced json and rejects nonsense`() {
        val json = """{"title":"Koshari","carbs_g":92.4,"items":[{"name":"rice & pasta","carbs_g":80},{"name":"lentils","carbs_g":12}],"confidence":"medium","note":"portion size"}"""
        val estimate = CarbEstimate.parse("```json\n$json\n```")
        assertEquals(92, estimate.carbsGrams)
        assertEquals("Koshari", estimate.title)
        assertEquals(listOf("rice & pasta" to 80, "lentils" to 12), estimate.items)
        assertTrue(runCatching { CarbEstimate.parse("""{"carbs_g": 5000}""") }.isFailure)
    }

    @Test
    fun `gemini request alternates roles and attaches the photo to the last turn only`() {
        val body = GeminiClient.requestBody(
            systemPrompt = "sys",
            turns = listOf(ChatTurn(true, "q1"), ChatTurn(false, "a1"), ChatTurn(true, "q2")),
            images = listOf(byteArrayOf(1, 2, 3)),
            jsonOutput = true,
        )
        val contents = body.getJSONArray("contents")
        assertEquals(listOf("user", "model", "user"), (0 until 3).map { contents.getJSONObject(it).getString("role") })
        assertEquals(1, contents.getJSONObject(0).getJSONArray("parts").length())
        assertEquals("AQID", contents.getJSONObject(2).getJSONArray("parts").getJSONObject(1).getJSONObject("inline_data").getString("data"))
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
    }

    @Test
    fun `gemini reply skips thought parts and maps errors to readable messages`() {
        val reply = """{"candidates":[{"content":{"parts":[{"text":"thinking…","thought":true},{"text":"You were steady."}]}}]}"""
        assertEquals("You were steady.", GeminiClient.replyText(reply))
        assertTrue(GeminiClient.errorMessage(400, """{"error":{"message":"API key not valid."}}""").contains("rejected the API key"))
        assertTrue(GeminiClient.errorMessage(503, "").contains("busy"))
    }

    /** Hits the real API with the dev machine's key; skipped anywhere GEMINI_API_KEY isn't set. */
    @Test
    fun `live - gemini answers from the brief and estimates carbs`() = runBlocking {
        val key = System.getenv("GEMINI_API_KEY").orEmpty()
        assumeTrue("GEMINI_API_KEY not set", key.isNotBlank())
        val client = GeminiClient { key }

        val system = AiPrompts.askSystemPrompt(readings, emptyList(), now, zone)
        val answer = client.generate(system, listOf(ChatTurn(true, "What was my highest reading in the last 24 hours? Just the number.")))
        assertTrue(answer, answer.contains("250"))

        val carbs = CarbEstimate.parse(
            client.generate(AiPrompts.carbSystemPrompt(arabic = false), listOf(ChatTurn(true, "Two slices of white toast with jam")), jsonOutput = true),
        )
        assertTrue("estimate ${carbs.carbsGrams}", carbs.carbsGrams in 20..90)
        assertFalse(carbs.title.isBlank())
    }
}
