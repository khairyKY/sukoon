package com.sukoon.app.ai

import org.json.JSONObject

/**
 * A photo of a package's nutrition facts (Arabic or English) read by Gemini into what 100 g holds:
 * for products no database has, which is most Egyptian ones (barcodes 622…).
 */
object NutritionLabel {
    /** What 100 g holds in [com.sukoon.app.food.Food.per100]'s order (carbs, fibre, sugar, protein, fat, kcal), and the serving. */
    data class Facts(val name: String?, val per100: List<Double>, val servingGrams: Double?)

    val RULES = """
        You read one photo of a food or drink package's nutrition facts table. It may be in Arabic, English or both.
        Reply with JSON only, no other text:
        {"name": "the product's name if it is visible, else null",
         "per100g": {"carbs": g, "fiber": g, "sugar": g, "protein": g, "fat": g, "kcal": kcal},
         "serving_g": serving size in grams (or ml for drinks) or null}
        Rules:
        - Use the per 100 g (or per 100 ml) column. If the label only gives per serving, divide by the serving size in grams and multiply by 100.
        - "carbs" is total carbohydrate (الكربوهيدرات), not net carbs. Arabic-Indic digits (٠١٢٣٤٥٦٧٨٩) are normal digits.
        - Energy given only in kJ: kcal = kJ / 4.184.
        - A value you can't read: null. Never guess a number that isn't on the label.
        - If the photo isn't a nutrition table, reply {"error": "not a label"}.
    """.trimIndent()

    /** The reply as [Facts]; null when it isn't a label or the carbs couldn't be read. */
    fun parse(reply: String): Facts? {
        val json = runCatching { JSONObject(reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1)) }.getOrNull() ?: return null
        if (json.has("error")) return null
        val per = json.optJSONObject("per100g") ?: return null
        fun g(key: String, max: Double) = per.optDouble(key).takeIf { !it.isNaN() && it >= 0 && it <= max }
        val carbs = g("carbs", 100.0) ?: return null
        return Facts(
            name = json.optString("name").takeIf { it.isNotBlank() && it != "null" },
            per100 = listOf(carbs, g("fiber", 100.0) ?: 0.0, g("sugar", 100.0) ?: 0.0, g("protein", 100.0) ?: 0.0, g("fat", 100.0) ?: 0.0, g("kcal", 900.0) ?: 0.0),
            servingGrams = json.optDouble("serving_g").takeIf { !it.isNaN() && it > 0 && it <= 2000 },
        )
    }
}
