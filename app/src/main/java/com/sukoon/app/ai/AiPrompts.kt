package com.sukoon.app.ai

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.domain.metrics.GlucoseMetrics
import com.sukoon.app.domain.metrics.GlucoseMetrics.RangeBracket
import com.sukoon.app.domain.metrics.GlucoseSample
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Everything the AI is told. Pure (clock + zone injected) so the data brief is unit-tested; the
 * model never sees raw rows — 7 days of 1-min readings is ~10k lines — only the summaries below,
 * which also keeps requests small on mobile data.
 */
object AiPrompts {

    /**
     * Facts about you the assistant should always know — insulin types, carb ratios, targets,
     * habits. Empty by default; anything written here is sent with every question.
     */
    const val PERSONAL_CONTEXT = ""

    private val ASK_RULES = """
        You are the assistant inside Sukoon, a calm glucose app for a person with type 1 diabetes
        who wears a FreeStyle Libre 2. You help them understand their own data and logbook.
        Rules:
        - Ground every claim in the DATA below and cite times and numbers. If the data doesn't show it, say so.
        - Never tell them how many insulin units to take and never suggest changing doses, ratios or
          basal; for treatment changes, suggest raising it with their diabetes team. Describing patterns is fine.
        - If the latest reading is under 70 mg/dL or falling fast toward it, start by saying to treat with
          15 g of fast carbs and recheck in 15 minutes.
        - Be calm and brief: 2–6 short sentences or a few "•" bullets. Plain text — no markdown, no headings.
        - Reply in the language the user writes in (English or Egyptian Arabic).
        - If DATA SOURCE is SIMULATED, say once that this is demo data, not their real readings.
        Targets: in range 70–180 mg/dL, low < 70, very low < 54, high > 180, very high > 250.
    """.trimIndent()

    private val CARB_RULES = """
        You estimate the carbohydrate content of a meal for someone with type 1 diabetes who counts
        carbs to dose insulin. Meals are often Egyptian home cooking (aish baladi, ful, ta'meya,
        koshari, mahshi, rice, pasta, fruit, sweets) as well as international food.
        From the photo and/or description, identify each item and its likely portion, then estimate
        grams of carbohydrate (not total weight). If the portion is unclear, assume one typical serving.
        Answer with JSON only:
        {"title": "short meal name", "carbs_g": <integer total>, "items": [{"name": "...", "carbs_g": <integer>}],
         "confidence": "low" | "medium" | "high", "note": "one short sentence on the biggest uncertainty"}
    """.trimIndent()

    fun askSystemPrompt(readings: List<GlucoseReading>, events: List<EventEntity>, now: Instant, zone: ZoneId): String =
        listOf(ASK_RULES, PERSONAL_CONTEXT, "DATA\n" + dataBrief(readings, events, now, zone))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

    fun carbSystemPrompt(arabic: Boolean): String =
        CARB_RULES + "\nWrite \"title\" and \"note\" in " + (if (arabic) "Egyptian Arabic." else "English.") +
            (if (PERSONAL_CONTEXT.isNotBlank()) "\n\n$PERSONAL_CONTEXT" else "")

    /** [readings] chronological (oldest first), typically the last 7 days. */
    fun dataBrief(readings: List<GlucoseReading>, events: List<EventEntity>, now: Instant, zone: ZoneId): String = buildString {
        // Locale.US throughout: the model gets one consistent numeral/day-name format whatever the UI language.
        val dayTime = DateTimeFormatter.ofPattern("EEE MM-dd HH:mm", Locale.US).withZone(zone)
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(zone)
        appendLine("NOW: ${dayTime.format(now)} ($zone)")

        val latest = readings.lastOrNull()
        if (latest == null) {
            appendLine("No glucose readings recorded yet.")
        } else {
            val minutesAgo = Duration.between(latest.timestamp, now).toMinutes()
            appendLine("DATA SOURCE: ${latest.source}")
            appendLine("LATEST: ${latest.glucoseMgDl} mg/dL, trend ${latest.trend.name.lowercase()}, $minutesAgo min ago")
            appendLine(summary("LAST 24H", readings.filter { it.timestamp >= now.minus(Duration.ofHours(24)) }))
            appendLine(summary("ALL ${readings.size} READINGS SHOWN (up to 7 days)", readings))

            appendLine("PER DAY (date: mean, % in range, min–max, readings < 70):")
            readings.groupBy { it.timestamp.atZone(zone).toLocalDate() }.forEach { (date, day) ->
                val values = day.map { it.glucoseMgDl }
                val tir = values.count { it in 70..180 } * 100 / values.size
                appendLine("$date: ${values.average().roundToInt()}, $tir%, ${values.min()}–${values.max()}, ${values.count { it < 70 }}")
            }

            appendLine("HOUR-OF-DAY MEAN across these days (hour: mg/dL):")
            appendLine(
                readings.groupBy { it.timestamp.atZone(zone).hour }.toSortedMap()
                    .entries.joinToString(", ") { (hour, r) -> String.format(Locale.US, "%02d: %d", hour, r.map { it.glucoseMgDl }.average().roundToInt()) },
            )

            appendLine("LAST 24H, 15-MIN AVERAGES (time value):")
            appendLine(
                readings.filter { it.timestamp >= now.minus(Duration.ofHours(24)) }
                    .groupBy { it.timestamp.epochSecond / (15 * 60) }
                    .values.joinToString(", ") { bucket -> "${time.format(bucket.first().timestamp)} ${bucket.map { it.glucoseMgDl }.average().roundToInt()}" },
            )
        }

        appendLine("LOGBOOK (time, type, amount, note):")
        if (events.isEmpty()) appendLine("(nothing logged)")
        events.sortedBy { it.timestampMillis }.forEach { event ->
            val amount = event.value?.let { v ->
                val unit = when (event.logType) {
                    LogEventType.CARB -> " g carbs"
                    LogEventType.INSULIN -> " units insulin"
                    LogEventType.ACTIVITY -> " min activity"
                    LogEventType.NOTE -> ""
                }
                "${if (v % 1.0 == 0.0) v.toLong() else v}$unit"
            }.orEmpty()
            appendLine(listOf(dayTime.format(Instant.ofEpochMilli(event.timestampMillis)), event.logType.name.lowercase(), amount, event.note.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
        }
    }.trim()

    private fun summary(label: String, readings: List<GlucoseReading>): String {
        if (readings.isEmpty()) return "$label: no readings"
        val samples = readings.map { GlucoseSample(it.timestamp, it.glucoseMgDl) }
        val tir = GlucoseMetrics.timeInRange(samples)
        val mean = GlucoseMetrics.mean(samples)
        fun pct(bracket: RangeBracket) = tir.getValue(bracket).roundToInt()
        return "$label: mean ${mean.roundToInt()} mg/dL, GMI ${String.format(Locale.US, "%.1f", GlucoseMetrics.gmiPercent(mean))}%, " +
            "CV ${GlucoseMetrics.coefficientOfVariationPercent(samples).roundToInt()}%, " +
            "very low ${pct(RangeBracket.VERY_LOW)}% / low ${pct(RangeBracket.LOW)}% / in range ${pct(RangeBracket.IN_RANGE)}% / " +
            "high ${pct(RangeBracket.HIGH)}% / very high ${pct(RangeBracket.VERY_HIGH)}%"
    }
}

data class CarbEstimate(
    val title: String,
    val carbsGrams: Int,
    val items: List<Pair<String, Int>>,
    val confidence: String,
    val note: String,
) {
    companion object {
        /** Parses the model's JSON; tolerates a ```json fence around it. */
        fun parse(text: String): CarbEstimate {
            val json = JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            val items = json.optJSONArray("items")
            return CarbEstimate(
                title = json.optString("title"),
                carbsGrams = json.getDouble("carbs_g").roundToInt().also { if (it !in 0..1000) throw AiException("Implausible estimate: $it g") },
                items = (0 until (items?.length() ?: 0)).mapNotNull { i ->
                    items!!.optJSONObject(i)?.let { it.optString("name") to it.optDouble("carbs_g", 0.0).roundToInt() }
                },
                confidence = json.optString("confidence", "low"),
                note = json.optString("note"),
            )
        }
    }
}
