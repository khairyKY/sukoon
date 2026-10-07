package com.sukoon.app.ai

import com.sukoon.app.insulin.Profile
import com.sukoon.app.domain.metrics.TargetRange
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
import com.sukoon.app.data.source.nearestTo
import com.sukoon.app.insights.Insight
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.insulin.InjectionRegion
import com.sukoon.app.insulin.DoseSettings
import com.sukoon.app.insights.MealSlot
import com.sukoon.app.insulin.InjectionSite
import com.sukoon.app.insulin.InsulinAction
import com.sukoon.app.insulin.InsulinOnBoard
import com.sukoon.app.insulin.injectionSite

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

    private const val NO_DOSE_RULE = "Never tell them how many insulin units to take and never suggest changing doses, ratios or basal; for treatment changes, suggest raising it with their diabetes team. Describing patterns is fine."

    // Beta, Kai testing on himself (docs/research/dosing-sources.md): the same maths as the app's Dose.advise.
    private const val DOSE_RULE = "Dose suggestions are on (beta; they test on themselves). You may work out a rapid dose from DOSE SETTINGS with the app's maths: carbs ÷ that meal's ratio, plus (glucose − target) ÷ correction factor, where active insulin offsets only the correction; round down to their pen step and never go above their maximum. Show the maths and call it a suggestion to check. Never suggest insulin under 70, or under 100 and falling: say to treat the low first. If a number is missing, say which one instead of guessing it. You may point out when logged meals suggest a ratio looks off, with those meals as evidence. Long-acting changes stay with their diabetes team."

    private fun askRules(doses: Boolean) = """
        You are the assistant inside Sukoon, a calm glucose app for a person with type 1 diabetes
        who wears a FreeStyle Libre 2. You help them understand their own data and logbook.
        Rules:
        - Ground every claim in the DATA below and cite times and numbers. If the data doesn't show it, say so.
        - ${if (doses) DOSE_RULE else NO_DOSE_RULE}
        - If the latest reading is under 70 mg/dL or falling fast toward it, start by saying to treat with
          15 g of fast carbs and recheck in 15 minutes.
        - Be calm and brief: 2–6 short sentences or a few "•" bullets. Plain text — no markdown, no headings.
        - Reply in the language the user writes in (English or Egyptian Arabic).
        - If DATA SOURCE is SIMULATED, say once that this is demo data, not their real readings.
        - INSIGHTS are computed by the app from these readings with the cited consensus rules: use their
          numbers rather than re-deriving them, and keep their caveats. ACTIVE INSULIN is rapid insulin
          still working; mention stacking risk if it matters.
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

    fun askSystemPrompt(
        readings: List<GlucoseReading>,
        events: List<EventEntity>,
        now: Instant,
        zone: ZoneId,
        insulinAction: InsulinAction = InsulinAction(),
        dose: DoseSettings = DoseSettings(),
        profile: Profile = Profile(),
    ): String =
        listOf(askRules(dose.enabled), PERSONAL_CONTEXT, aboutLine(profile), "DATA\n" + dataBrief(readings, events, now, zone, insulinAction), doseLine(dose))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

    fun carbSystemPrompt(arabic: Boolean): String =
        CARB_RULES + "\nWrite \"title\" and \"note\" in " + (if (arabic) "Egyptian Arabic." else "English.") +
            (if (PERSONAL_CONTEXT.isNotBlank()) "\n\n$PERSONAL_CONTEXT" else "")

    /** [readings] chronological (oldest first), typically the last 7 days. */
    fun dataBrief(
        readings: List<GlucoseReading>,
        events: List<EventEntity>,
        now: Instant,
        zone: ZoneId,
        insulinAction: InsulinAction = InsulinAction(),
    ): String = buildString {
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
            appendLine(summary("LAST 24H", readings.filter { it.timestamp >= now.minus(Duration.ofHours(24)) }, dayTime))
            appendLine(summary("ALL ${readings.size} READINGS SHOWN (up to 7 days)", readings, dayTime))

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

        val active = InsulinOnBoard.total(events, now, insulinAction)
        appendLine(
            String.format(
                Locale.US,
                "ACTIVE INSULIN now: %.1f units rapid (curve peaks at %d min, lasts %d h; basal not counted)",
                active, insulinAction.peakMinutes, insulinAction.durationMinutes / 60,
            ),
        )

        // The ratio rules (500/1800) and per-meal carbs-per-unit stay out on purpose: they invite dose maths.
        val insights = InsightEngine.analyze(readings, events, now, zone, TargetRange.high).filterNot { it is Insight.Formulas }
        if (insights.isNotEmpty()) {
            appendLine("INSIGHTS (14-day rules, with sources):")
            insights.forEach { appendLine("- " + insightLine(it)) }
        }

        appendLine("LOGBOOK (time, type, amount, note, sensor glucose then):")
        if (events.isEmpty()) appendLine("(nothing logged)")
        events.sortedBy { it.timestampMillis }.forEach { event ->
            val amount = event.value?.let { v ->
                val unit = when (event.logType) {
                    LogEventType.CARB -> " g carbs"
                    LogEventType.INSULIN -> " units rapid insulin"
                    LogEventType.BASAL -> " units basal insulin"
                    LogEventType.FINGERSTICK -> " mg/dL finger-prick (blood meter)"
                    LogEventType.ACTIVITY -> " min activity"
                    LogEventType.NOTE -> ""
                }
                "${if (v % 1.0 == 0.0) v.toLong() else v}$unit"
            }.orEmpty()
            val glucose = readings.nearestTo(event.timestampMillis)?.let { "sensor ${it.glucoseMgDl} ${it.trend.name.lowercase().replace('_', ' ')}" }.orEmpty()
            val site = event.injectionSite?.let { "injected in ${siteLabel(it)}" }.orEmpty()
            appendLine(
                listOf(dayTime.format(Instant.ofEpochMilli(event.timestampMillis)), event.logType.name.lowercase(), amount, site, event.note.orEmpty(), glucose)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
            )
        }
    }.trim()

    private fun insightLine(insight: Insight): String {
        fun hour(h: Int) = String.format(Locale.US, "%02d:00", h)
        return when (insight) {
            is Insight.NotEnoughData -> "Not enough data for patterns yet (${insight.daysWithData} days, ${insight.coveragePercent}% coverage)."
            is Insight.Targets -> "Targets over ${insight.days} days (${insight.coveragePercent}% coverage): in range ${insight.inRange}% (goal >70), " +
                "below 70 ${insight.below70}% (<4), below 54 ${insight.below54}% (<1), above 180 ${insight.above180}% (<25), " +
                "above 250 ${insight.above250}% (<5); mean ${insight.meanMgDl}, GMI ${insight.gmiPercent}% [Battelino et al. 2019]." +
                (insight.inYourRange?.let { " Their own tighter range 70–${insight.yourHigh}: $it%." } ?: "")
            is Insight.Variability -> "Variability: CV ${insight.cvPercent}% (stable at 36 or less) [Danne et al. 2017]."
            is Insight.RecurringLows -> "Recurring lows starting ${hour(insight.fromHour)}–${hour(insight.toHour)} on ${insight.days} days " +
                "(${insight.episodes} of ${insight.totalEpisodes} lows)."
            is Insight.RecurringHighs -> "Usually above 180 between ${hour(insight.fromHour)} and ${hour(insight.toHour)} (${insight.percentOfDays}% of days)."
            is Insight.DawnRise -> "Dawn rise: ${insight.nightsWithRise} of ${insight.nights} nights rose 20+ from the 03–06 low by 07–08 " +
                "(median +${insight.medianRise}) [Monnier et al. 2013]."
            is Insight.MealOutcomes -> "${insight.slot.name.lowercase()} meals: ${insight.meals}; above 180 two hours after: ${insight.highAt2h}; " +
                "low within 4 h: ${insight.lowWithin4h}; average rise ${insight.averageRise}."
            is Insight.PreBolus -> "Insulin 10+ min before eating: average rise ${insight.earlyRise} (${insight.earlyMeals} meals) vs " +
                "${insight.lateRise} when taken at or after eating (${insight.lateMeals}) [Slattery et al. 2018]."
            is Insight.Stacking -> "${insight.lowsAfterStacking} of ${insight.totalLows} lows came within 4 h of two rapid doses taken under 3 h apart."
            is Insight.Formulas -> ""
            is Insight.MeterAgreement -> "Sensor vs finger-pricks: ${insight.agreeing} of ${insight.checks} within the 20/20 band; " +
                "the sensor averages ${insight.meanDiffPercent}% against the meter."
            is Insight.WeekOverWeek -> "Last 7 days vs the 7 before: in range ${insight.inRange}% vs ${insight.inRangeBefore}%, " +
                "average ${insight.mean} vs ${insight.meanBefore}, lows ${insight.lows} vs ${insight.lowsBefore}."
            is Insight.CarbResponse -> "Rise per 10 g carbs with the insulin taken: " +
                insight.slots.joinToString { "${it.slot.name.lowercase()} +${it.per10g} (${it.meals} meals)" } +
                "; meals usually peak ${insight.peakMinutes} min after eating [Hinshaw et al. 2013]."
            is Insight.RichMeals -> "Meals with 20 g+ fat or 25 g+ protein (${insight.rich}) peaked at ${insight.richPeak} min vs ${insight.leanPeak} for " +
                "others (${insight.lean}), and were ${insight.richAt4h} vs ${insight.leanAt4h} mg/dL from the start 4 h after eating [Bell et al. 2015]."
            is Insight.Rebounds -> "${insight.rebounds} of ${insight.lows} lows were followed by >180 within 2 h (possible over-treatment; 15-15 rule)."
            is Insight.ActivityLows -> "${insight.followed} of ${insight.workouts} workouts were followed by a low within 24 h " +
                "(${insight.overnight} overnight) [Riddell et al. 2017]."
            is Insight.Nights -> "${insight.inRange} of ${insight.nights} nights stayed 70–180 from 00 to 06; ${insight.withLows} nights had a low."
            is Insight.CarbDays -> "Days over ${insight.splitGrams} g carbs: ${insight.higherTir}% in range vs ${insight.lowerTir}% on lighter days " +
                "(${insight.days} days) [Evert et al. 2019]."
            is Insight.Rotation -> "Injection sites: ${siteLabel(insight.site)} took ${insight.count} of ${insight.total} " +
                "${if (insight.longActing) "long-acting" else "rapid"} doses in 2 weeks; using one spot often can cause lipohypertrophy [Frid et al. 2016]."
        }
    }

    /** "left abdomen", "right upper arm" (the person's own left and right). */
    private fun siteLabel(site: InjectionSite): String =
        (if (site.left) "left " else "right ") + when (site.region) {
            InjectionRegion.ABDOMEN -> "abdomen"
            InjectionRegion.THIGH -> "thigh"
            InjectionRegion.ARM -> "upper arm"
            InjectionRegion.BUTTOCK -> "buttock"
        }

    /** "ABOUT THEM: 30 years, male, 75 kg, 178 cm": what they entered, nothing guessed. */
    private fun aboutLine(p: Profile): String {
        val parts = listOfNotNull(
            p.ageYears?.let { "$it years" },
            p.sex?.name?.lowercase(),
            p.weightKg?.let { "${if (it % 1.0 == 0.0) it.toLong() else it} kg" },
            p.heightCm?.let { "$it cm" },
        )
        return if (parts.isEmpty()) "" else "ABOUT THEM: " + parts.joinToString(", ") + (if ((p.ageYears ?: 99) < 18) " (a child: a parent manages their settings)" else "")
    }

    /** "DOSE SETTINGS: …" while beta is on; empty numbers say so, so the model doesn't invent them. */
    private fun doseLine(dose: DoseSettings): String {
        if (!dose.enabled) return ""
        fun n(x: Double?) = x?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
        val ratios = MealSlot.entries.joinToString { s -> "${s.name.lowercase()} " + (n(dose.carbRatio[s])?.let { "1 u per $it g" } ?: "not set") }
        return "DOSE SETTINGS (beta): carb ratio $ratios; correction " + (n(dose.correctionFactor)?.let { "1 u lowers $it mg/dL" } ?: "not set") +
            "; target ${dose.target} mg/dL; maximum ${n(dose.maxDose)} u; pen step ${n(dose.step)} u. Meals: breakfast 04–10, lunch 11–15, dinner 16–21, late otherwise."
    }

    // Exact extremes (with times) matter: the 15-minute averages below smooth away short lows.
    private fun summary(label: String, readings: List<GlucoseReading>, time: DateTimeFormatter): String {
        if (readings.isEmpty()) return "$label: no readings"
        val lowest = readings.minBy { it.glucoseMgDl }
        val highest = readings.maxBy { it.glucoseMgDl }
        val samples = readings.map { GlucoseSample(it.timestamp, it.glucoseMgDl) }
        val tir = GlucoseMetrics.timeInRange(samples)
        val mean = GlucoseMetrics.mean(samples)
        fun pct(bracket: RangeBracket) = tir.getValue(bracket).roundToInt()
        return "$label: mean ${mean.roundToInt()} mg/dL, GMI ${String.format(Locale.US, "%.1f", GlucoseMetrics.gmiPercent(mean))}%, " +
            "CV ${GlucoseMetrics.coefficientOfVariationPercent(samples).roundToInt()}%, " +
            "very low ${pct(RangeBracket.VERY_LOW)}% / low ${pct(RangeBracket.LOW)}% / in range ${pct(RangeBracket.IN_RANGE)}% / " +
            "high ${pct(RangeBracket.HIGH)}% / very high ${pct(RangeBracket.VERY_HIGH)}%, " +
            "lowest ${lowest.glucoseMgDl} at ${time.format(lowest.timestamp)}, highest ${highest.glucoseMgDl} at ${time.format(highest.timestamp)}"
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
