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
    /** The product's photo (Open Food Facts), shown beside it. */
    val imageUrl: String? = null,
    /** A home dish's usual portion ("bowl", "طبق"), [servingGrams] of it. */
    val servingLabel: String? = null,
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
 * USDA FoodData Central (public domain): generic foods ("eggs, scrambled", "rice, cooked") with
 * protein, fat and kcal, where Open Food Facts mostly has packaged products. Foundation, SR Legacy
 * and the survey foods (FNDDS, which come with household portions).
 */
object UsdaFoods {
    private val searches = mutableMapOf<String, List<Food>>()

    suspend fun search(query: String): List<Food> {
        val q = query.trim()
        searches[q]?.let { return it }
        val url = "https://api.nal.usda.gov/fdc/v1/foods/search?pageSize=12&dataType=${OpenFoodFacts.enc("Foundation,SR Legacy,Survey (FNDDS)")}&query=${OpenFoodFacts.enc(q)}&api_key=${com.sukoon.app.BuildConfig.USDA_KEY}"
        val foods = OpenFoodFacts.get(url).optJSONArray("foods")
        val out = if (foods == null) emptyList() else List(foods.length()) { foods.optJSONObject(it)?.let(::parse) }.filterNotNull().distinctBy { it.name.lowercase() }
        return out.also { searches[q] = it }
    }

    /** USDA's nutrient numbers: carbs 205, fibre 291, sugar 269, protein 203, fat 204, kcal 208. */
    internal fun parse(f: JSONObject): Food? {
        val name = f.optString("description").trim().ifEmpty { return null }
        val nutrients = f.optJSONArray("foodNutrients") ?: return null
        val by = (0 until nutrients.length()).mapNotNull { nutrients.optJSONObject(it) }.associate { it.optString("nutrientNumber") to it.optDouble("value") }
        val carbs = by["205"]?.takeIf { !it.isNaN() } ?: return null
        fun v(k: String) = by[k]?.takeIf { !it.isNaN() } ?: 0.0
        val measure = f.optJSONArray("foodMeasures")?.optJSONObject(0)
        return Food(
            name = name.lowercase().replaceFirstChar { it.uppercase() },
            brand = "USDA",
            per100 = listOf(carbs, v("291"), v("269"), v("203"), v("204"), v("208")),
            servingGrams = measure?.optDouble("gramWeight")?.takeIf { !it.isNaN() && it > 0 },
            servingLabel = measure?.optString("disseminationText")?.takeIf { it.isNotBlank() && it != "Quantity not specified" },
        )
    }
}

/**
 * Open Food Facts (free, open data): search and barcodes. Fast first: the search-a-licious index,
 * only the fields used, and answers kept for the session, so a second look is instant.
 */
object OpenFoodFacts {
    private const val FIELDS = "code,product_name,product_name_en,product_name_ar,brands,nutriments,serving_quantity,product_quantity,image_front_small_url"
    private const val AGENT = "Sukoon/0.7 (Android; github.com/khairyKY/sukoon)"
    private val searches = mutableMapOf<String, List<Food>>()
    private val lookups = mutableMapOf<String, Lookup>()

    suspend fun search(query: String, egyptOnly: Boolean): List<Food> {
        // Made in Egypt (barcodes 622…) counts too: about half of those aren't tagged with the country.
        val q = query.trim() + if (egyptOnly) " (countries_tags:\"en:egypt\" OR code:622*)" else ""
        searches[q]?.let { return it }
        // Arabic and English names alike: "فول" finds as much as "foul". The older search is mostly down, so a hiccup gets one retry instead.
        val url = "https://search.openfoodfacts.org/search?page_size=25&langs=ar,en&fields=$FIELDS&q=${enc(q)}"
        val hits = runCatching { get(url) }.getOrElse { kotlinx.coroutines.delay(800); get(url) }.optJSONArray("hits")
        return parseAll(hits).also { searches[q] = it }
    }

    /** What Open Food Facts has for a barcode: the food (with carbs), or only what it's called and looks like, or nothing. */
    data class Lookup(val food: Food?, val name: String? = null, val brand: String? = null, val imageUrl: String? = null)

    suspend fun lookup(code: String): Lookup {
        lookups[code]?.let { return it }
        val json = get("https://world.openfoodfacts.org/api/v2/product/${enc(code)}?fields=$FIELDS")
        val p = json.optJSONObject("product")?.takeIf { json.optInt("status") == 1 }
        val found = if (p == null) Lookup(null) else Lookup(
            food = parse(p),
            name = listOf("product_name", "product_name_ar", "product_name_en").map { p.optString(it).trim() }.firstOrNull { it.isNotEmpty() },
            brand = p.optString("brands").substringBefore(',').trim().ifEmpty { null },
            imageUrl = p.optString("image_front_small_url").ifEmpty { null },
        )
        return found.also { lookups[code] = it }
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
            imageUrl = p.optString("image_front_small_url").ifEmpty { null },
        )
    }

    internal fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    internal suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6_000
        c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", AGENT)
        try {
            if (c.responseCode != 200) error("${URL(url).host}: ${c.responseCode}")
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
        .put("image", f.imageUrl).put("servingLabel", f.servingLabel)

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
            imageUrl = o.optString("image").ifEmpty { null }.takeUnless { o.isNull("image") },
            servingLabel = o.optString("servingLabel").ifEmpty { null }.takeUnless { o.isNull("servingLabel") },
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
    food.servingLabel != null && food.servingGrams != null -> listOf(0.5, 1.0, 1.5, 2.0).map { "dish" to food.servingGrams * it }
    food.packageGrams != null -> listOf("¼" to food.packageGrams / 4, "½" to food.packageGrams / 2, "1" to food.packageGrams) +
        listOfNotNull(food.servingGrams?.takeIf { it < food.packageGrams / 4 || it > food.packageGrams }?.let { "serving" to it })
    food.servingGrams != null -> listOf("serving" to food.servingGrams, null to 100.0, null to 200.0)
    else -> listOf(null to 50.0, null to 100.0, null to 150.0, null to 200.0)
}

/**
 * Home dishes and staples (Egyptian and wider Middle-Eastern), in Arabic and English, with sources:
 * assets/dishes.json. Offline and instant: what Open Food Facts (packaged foods) doesn't have.
 */
object Dishes {
    private var all: List<Pair<Food, String>>? = null // the food, and every name it answers to

    fun search(context: Context, query: String, arabic: Boolean): List<Food> {
        val q = normalize(query)
        if (q.length < 2) return emptyList()
        // Every word somewhere in its names: "ملوخية و ارانب" finds molokhia and rabbit both.
        val words = q.split(' ').filter { it.length >= 2 && it != "و" && it != "and" && it != "with" && it != "ب" }
        return load(context, arabic).filter { (_, names) -> words.any { it in names } }.map { it.first }.take(15)
    }

    private fun load(context: Context, arabic: Boolean): List<Pair<Food, String>> = all ?: runCatching {
        val array = JSONArray(context.assets.open("dishes.json").bufferedReader().readText())
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val per = o.getJSONArray("per100")
            val portion = o.optJSONObject("portion")
            val names = (listOf(o.optString("en"), o.optString("ar")) + o.optJSONArray("aliases").let { a -> if (a == null) emptyList() else List(a.length()) { a.getString(it) } })
            Food(
                name = if (arabic) o.optString("ar").ifEmpty { o.getString("en") } else o.getString("en"),
                brand = null,
                per100 = List(6) { per.optDouble(it, 0.0) },
                servingGrams = portion?.optDouble("grams")?.takeIf { !it.isNaN() && it > 0 },
                servingLabel = portion?.optString(if (arabic) "ar" else "en")?.ifEmpty { null }?.removePrefix("1 ")?.trim(),
            ) to normalize(names.joinToString(" | "))
        }
    }.getOrDefault(emptyList()).also { all = it }

    /** Lower case, no Arabic marks, and its look-alike letters as one (ة/ه, أ/إ/آ/ا, ى/ي). */
    internal fun normalize(s: String): String = s.lowercase()
        .replace(Regex("[\u064B-\u0652\u0640]"), "")
        .replace('ة', 'ه').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
        .replace(Regex("\\s+"), " ").trim()
}
