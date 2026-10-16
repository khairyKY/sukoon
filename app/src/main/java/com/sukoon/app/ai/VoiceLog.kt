package com.sukoon.app.ai

import com.sukoon.app.data.db.LogEventType
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Speaking an entry ("had koshari, a big plate, took 6 units"): the phone's speech recognition
 * writes it down, the model turns it into entries, and a sheet shows them before anything is saved.
 * Insulin is only ever what was said: the model may estimate carbs for named food, never a dose.
 */
data class VoiceEntry(
    val type: LogEventType,
    /** Grams, units, mg/dL, mmol/L, minutes or kg; null only for a note. */
    val amount: Double?,
    /** The food, the activity kind ("STRENGTH", "WALK"…) or the note's text. */
    val name: String? = null,
    /** Carbs worked out from the food named, not said as grams. */
    val estimated: Boolean = false,
    /** Carbs said to be for a low. */
    val treatment: Boolean = false,
    /** For an activity: "EASY", "MODERATE" or "HARD". */
    val effort: String? = null,
    /** For food: protein, fat (g) and kcal, estimated like the carbs. */
    val protein: Double? = null,
    val fat: Double? = null,
    val kcal: Double? = null,
)

object VoiceLog {
    private val RULES = """
        You turn what someone with type 1 diabetes SAID (speech-to-text, maybe Egyptian Arabic, Arabic
        or English, maybe with recognition mistakes) into logbook entries. Answer with JSON only:
        {"entries": [{"type": "carbs"|"rapid"|"long"|"activity"|"fingerstick"|"ketones"|"weight"|"note",
          "amount": <number or null>, "name": "...", "estimated": true|false, "treatment": true|false,
          "protein_g": <number>, "fat_g": <number>, "kcal": <number>,
          "activity": "STRENGTH"|"WALK"|"RUN"|"BIKE"|"SWIM"|"HIIT"|"FOOTBALL"|"YOGA"|"OTHER", "effort": "EASY"|"MODERATE"|"HARD"}]}
        It is often Egyptian Arabic and English mixed in one sentence, the Arabic maybe in Latin letters and
        numbers ("kalt 2 sandwich foul w tea" = ate 2 ful sandwiches and tea; "w" = and, 3 = ع, 7 = ح,
        "etnein"/"talata" = 2/3): read it all as one account. Egyptian names in any spelling: foul/ful,
        ta3meya/ta'meya/falafel, koshari, feteer, molokhia, mahshi, aish baladi, fino, basbousa, konafa.
        Rules:
        - carbs: grams of carbohydrate. If grams were said, use them ("estimated": false). If only food was
          named, estimate grams for the portion said (one typical serving if unclear; Egyptian home food is
          common: aish baladi, ful, ta'meya, koshari, mahshi, rice, pasta, fruit, sweets) and set "estimated": true.
          "name" is the food in the speaker's language. "treatment": true when it was for a low (juice for a low, "I was low").
          For food also estimate "protein_g", "fat_g" and "kcal" (eggs, meat and cheese: little carbs, real protein).
          A food with almost no carbs (eggs, meat) is still a "carbs" entry, with its small carbs amount.
        - rapid: rapid-acting/meal/correction insulin units (Apidra, Novorapid, Humalog, Fiasp, "units" with a meal).
        - long: long-acting/basal units (Toujeo, Lantus, Tresiba, Levemir, "basal", "long-acting").
        - NEVER invent or estimate an insulin amount. If insulin was mentioned without a number, leave it out.
        - activity: "amount" is minutes; "activity" its kind (gym/weights = STRENGTH); "effort" if said.
        - fingerstick: a meter reading in mg/dL. ketones: mmol/L. weight: kg.
        - note: anything else worth keeping, as "name". Leave out filler. Return {"entries": []} if nothing fits.
    """.trimIndent()

    fun systemPrompt(): String = RULES

    /** The model's JSON as entries; anything malformed or out of range is dropped, not guessed. */
    fun parse(text: String): List<VoiceEntry> {
        val json = JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val rows = json.optJSONArray("entries") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val o = rows.optJSONObject(i) ?: return@mapNotNull null
            val amount = if (o.isNull("amount")) null else o.optDouble("amount").takeIf { !it.isNaN() }
            val name = o.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" }
            when (o.optString("type")) {
                "carbs" -> amount?.takeIf { it in 0.0..400.0 }?.let {
                    fun g(k: String) = o.optDouble(k).takeIf { v -> !v.isNaN() && v in 0.0..500.0 }
                    VoiceEntry(LogEventType.CARB, it.roundToInt().toDouble(), name, o.optBoolean("estimated"), o.optBoolean("treatment"),
                        protein = g("protein_g"), fat = g("fat_g"), kcal = o.optDouble("kcal").takeIf { v -> !v.isNaN() && v in 0.0..8000.0 })
                }
                "rapid" -> amount?.takeIf { it in 0.5..50.0 }?.let { VoiceEntry(LogEventType.INSULIN, (it * 2).roundToInt() / 2.0) }
                "long" -> amount?.takeIf { it in 1.0..150.0 }?.let { VoiceEntry(LogEventType.BASAL, (it * 2).roundToInt() / 2.0) }
                "activity" -> amount?.takeIf { it in 1.0..600.0 }?.let {
                    VoiceEntry(LogEventType.ACTIVITY, it.roundToInt().toDouble(), o.optString("activity").takeIf { a -> a.isNotEmpty() } ?: "OTHER", effort = o.optString("effort").takeIf { e -> e in setOf("EASY", "MODERATE", "HARD") })
                }
                "fingerstick" -> amount?.takeIf { it in 20.0..600.0 }?.let { VoiceEntry(LogEventType.FINGERSTICK, it.roundToInt().toDouble()) }
                "ketones" -> amount?.takeIf { it in 0.0..8.0 }?.let { VoiceEntry(LogEventType.KETONE, it) }
                "weight" -> amount?.takeIf { it in 20.0..300.0 }?.let { VoiceEntry(LogEventType.WEIGHT, it) }
                "note" -> name?.let { VoiceEntry(LogEventType.NOTE, null, it) }
                else -> null
            }
        }
    }
}
