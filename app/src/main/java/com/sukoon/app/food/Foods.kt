package com.sukoon.app.food

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A food: what 100 g of it holds (carbs, fibre, sugar, protein, fat in g, then kcal; the order of
 * [Food.CARBS]..[Food.KCAL]), and the portions it comes in. One of yours made by hand ([own]) holds
 * one portion instead of 100 g, and its amounts are counted in portions.
 */
data class Food(
    val name: String,
    val brand: String? = null,
    val code: String? = null,
    val per100: List<Double>,
    val servingGrams: Double? = null,
    val packageGrams: Double? = null,
    val own: Boolean = false,
    /** Yours: how much you had last time (grams, or portions for [own]). */
    val lastAmount: Double? = null,
) {
    /** What [amount] (grams, or portions for [own]) of it holds, in [per100]'s order. */
    fun of(amount: Double): List<Double> = per100.map { it * (if (own) amount else amount / 100) }

    /** The same food remembered by its barcode, else its name and brand. */
    val key: String get() = code ?: "${name.lowercase()}|${brand.orEmpty().lowercase()}"

    companion object {
        const val CARBS = 0
        const val KCAL = 5
    }
}

/** One food on the plate, at an amount. */
data class PlateItem(val food: Food, val amount: Double) {
    val sums: List<Double> get() = food.of(amount)
}

/**
 * Open Food Facts (free, open data): search and barcodes. Fast first: the search-a-licious index,
 * only the fields used, and answers kept for the session, so a second look is instant.
 */
object OpenFoodFacts {
    private const val FIELDS = "code,product_name,product_name_en,product_name_ar,brands,nutriments,serving_quantity,product_quantity"
    private const val AGENT = "Sukoon/0.7 (Android; github.com/khairyKY/sukoon)"
    private val searches = mutableMapOf<String, List<Food>>()
    private val barcodes = mutableMapOf<String, Food?>()

    suspend fun search(query: String, egyptOnly: Boolean): List<Food> {
        val q = query.trim() + if (egyptOnly) " countries_tags:\"en:egypt\"" else ""
        searches[q]?.let { return it }
        val hits = runCatching {
            get("https://search.openfoodfacts.org/search?page_size=25&fields=$FIELDS&q=${enc(q)}").optJSONArray("hits")
        }.getOrElse {
            // ponytail: the older search (slower) when the new index is down; the Egypt filter is dropped there.
            get("https://world.openfoodfacts.org/cgi/search.pl?search_simple=1&json=1&page_size=25&fields=$FIELDS&search_terms=${enc(query.trim())}").optJSONArray("products")
        }
        return parseAll(hits).also { searches[q] = it }
    }

    /** The product with this barcode, or null when Open Food Facts doesn't have it (with carbs). */
    suspend fun barcode(code: String): Food? {
        if (code in barcodes) return barcodes[code]
        val json = get("https://world.openfoodfacts.org/api/v2/product/${enc(code)}?fields=$FIELDS")
        return (if (json.optInt("status") == 1) json.optJSONObject("product")?.let(::parse) else null).also { barcodes[code] = it }
    }

    private fun parseAll(array: JSONArray?): List<Food> =
        if (array == null) emptyList() else List(array.length()) { array.optJSONObject(it)?.let(::parse) }.filterNotNull().distinctBy { it.key }

    /** A product as a [Food]; null without a name or carbs (it couldn't be dosed from). */
    internal fun parse(p: JSONObject): Food? {
        val n = p.optJSONObject("nutriments") ?: return null
        fun num(key: String) = n.optDouble(key).takeUnless { it.isNaN() }
        val carbs = num("carbohydrates_100g") ?: return null
        val name = listOf("product_name", "product_name_en", "product_name_ar").map { p.optString(it).trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        // Brands come as "A, B" from one search and a list from the other.
        val brand = (p.optJSONArray("brands")?.optString(0) ?: p.optString("brands").substringBefore(',')).trim().ifEmpty { null }
        val kcal = num("energy-kcal_100g") ?: num("energy_100g")?.let { it / 4.184 } ?: 0.0
        return Food(
            name = name,
            brand = brand,
            code = p.optString("code").ifEmpty { null },
            per100 = listOf(carbs, num("fiber_100g") ?: 0.0, num("sugars_100g") ?: 0.0, num("proteins_100g") ?: 0.0, num("fat_100g") ?: 0.0, kcal),
            servingGrams = p.optDouble("serving_quantity").takeIf { !it.isNaN() && it > 0 },
            packageGrams = p.optDouble("product_quantity").takeIf { !it.isNaN() && it > 0 },
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6_000
        c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", AGENT)
        try {
            if (c.responseCode != 200) error("Open Food Facts: ${c.responseCode}")
            JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }
}

/** Your foods: everything you've put on a plate (and made by hand), the most used first; offline and instant. */
class MyFoods private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("sukoon_foods", Context.MODE_PRIVATE)

    fun all(): List<Food> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return List(array.length()) { i -> array.optJSONObject(i)?.let(::read) }.filterNotNull()
    }

    fun search(query: String): List<Food> = all().filter { it.name.contains(query.trim(), ignoreCase = true) || it.brand?.contains(query.trim(), ignoreCase = true) == true }

    fun byCode(code: String): Food? = all().firstOrNull { it.code == code }

    /** Put first, at the amount just had. */
    fun remember(item: PlateItem) {
        val kept = listOf(item.food.copy(lastAmount = item.amount)) + all().filter { it.key != item.food.key }
        // ponytail: the newest 200 foods; plenty for one person's usual.
        prefs.edit().putString(KEY, JSONArray(kept.take(200).map(::write)).toString()).apply()
    }

    private fun write(f: Food) = JSONObject().put("name", f.name).put("brand", f.brand).put("code", f.code).put("per100", JSONArray(f.per100))
        .put("serving", f.servingGrams).put("package", f.packageGrams).put("own", f.own).put("last", f.lastAmount)

    private fun read(o: JSONObject): Food? = runCatching {
        val per = o.getJSONArray("per100")
        fun opt(key: String) = o.optDouble(key).takeUnless { it.isNaN() }
        Food(
            name = o.getString("name"),
            brand = o.optString("brand").ifEmpty { null }.takeUnless { o.isNull("brand") },
            code = o.optString("code").ifEmpty { null }.takeUnless { o.isNull("code") },
            per100 = List(per.length()) { per.getDouble(it) },
            servingGrams = opt("serving"),
            packageGrams = opt("package"),
            own = o.optBoolean("own"),
            lastAmount = opt("last"),
        )
    }.getOrNull()

    companion object {
        private const val KEY = "foods"
        @Volatile private var instance: MyFoods? = null
        fun get(context: Context): MyFoods = instance ?: synchronized(this) { instance ?: MyFoods(context.applicationContext).also { instance = it } }
    }
}

/** The quick amounts for a food: its package parts and serving, else common gram amounts; portions for yours. */
fun portions(food: Food): List<Pair<String?, Double>> = when {
    food.own -> listOf(null to 0.5, null to 1.0, null to 1.5, null to 2.0)
    food.packageGrams != null -> listOf("¼" to food.packageGrams / 4, "½" to food.packageGrams / 2, "1" to food.packageGrams) +
        listOfNotNull(food.servingGrams?.takeIf { it < food.packageGrams / 4 || it > food.packageGrams }?.let { "serving" to it })
    food.servingGrams != null -> listOf("serving" to food.servingGrams, null to 100.0, null to 200.0)
    else -> listOf(null to 50.0, null to 100.0, null to 150.0, null to 200.0)
}
