package com.sukoon.app.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import com.sukoon.app.R
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.data.source.SourceKind
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.request.AggregateRequest
import java.time.LocalDate
import kotlin.reflect.KClass
import com.sukoon.app.data.db.logType
import androidx.health.connect.client.records.metadata.DataOrigin

/**
 * Health Connect, both ways. In: meals other apps log there (MyFitnessPal writes each meal's
 * totals, with every nutrient) become Logbook meals, and workouts of 10 minutes or more become
 * Activity entries, both kept in step as they change or are deleted; today's steps and water are
 * read for Home. Out: Sukoon's sensor readings become blood-glucose records other apps can use.
 *
 * Reading in the background needs Health Connect's background-read permission (asked for when
 * the phone supports it); without it, syncing happens whenever Sukoon is open.
 */
class HealthConnectSync(
    private val context: Context,
    private val glucose: GlucoseRepository,
    private val logbook: LogbookRepository,
    private val scope: CoroutineScope,
) {
    data class Result(val mealsAdded: Int, val mealsUpdated: Int, val readingsShared: Int)

    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    val readMeals = HealthPermission.getReadPermission(NutritionRecord::class)
    val readActivity = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    val readWater = HealthPermission.getReadPermission(HydrationRecord::class)
    val readSteps = HealthPermission.getReadPermission(StepsRecord::class)
    val writeGlucose = HealthPermission.getWritePermission(BloodGlucoseRecord::class)

    /** Steps and water so far today (null: not allowed, or nothing logged). */
    data class Today(val steps: Long?, val waterLiters: Double?)

    var importMeals: Boolean
        get() = prefs.getBoolean(KEY_IMPORT, true)
        set(value) = prefs.edit().putBoolean(KEY_IMPORT, value).apply()

    var importActivity: Boolean
        get() = prefs.getBoolean(KEY_IMPORT_ACTIVITY, true)
        set(value) = prefs.edit().putBoolean(KEY_IMPORT_ACTIVITY, value).apply()

    var shareGlucose: Boolean
        get() = prefs.getBoolean(KEY_EXPORT, true)
        set(value) = prefs.edit().putBoolean(KEY_EXPORT, value).apply()

    /** [HealthConnectClient.SDK_AVAILABLE], unavailable, or the provider needs an update. */
    fun status(): Int = HealthConnectClient.getSdkStatus(context)

    val available: Boolean get() = status() == HealthConnectClient.SDK_AVAILABLE

    /** What to ask for: meals, workouts, steps and water in, glucose out, and background reading where the phone has it. */
    fun wantedPermissions(): Set<String> = buildSet {
        add(readMeals)
        add(readActivity)
        add(readSteps)
        add(readWater)
        add(writeGlucose)
        if (client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
            add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        }
    }

    suspend fun granted(): Set<String> = if (available) client.permissionController.getGrantedPermissions() else emptySet()

    /** What a resync found: MyFitnessPal's meals in Health Connect over the last 7 days, and what changed. */
    data class Resync(val mfpMeals: Int, val result: Result)

    /**
     * You → Apps & data → Resync: read the last 7 days again from scratch (onto the same entries),
     * and count MyFitnessPal's meals there, so "nothing came in" can say whose side it's on.
     */
    suspend fun resync(): Resync {
        mutex.withLock { prefs.edit().remove(KEY_TOKEN).apply() }
        val mfp = if (canReadMeals()) {
            client.readRecords(
                ReadRecordsRequest(NutritionRecord::class, TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(7))), dataOriginFilter = setOf(DataOrigin(MFP))),
            ).records.size
        } else {
            0
        }
        return Resync(mfp, sync())
    }

    /** Whether meals can come in at all: Health Connect is here and Sukoon may read nutrition. */
    suspend fun canReadMeals(): Boolean = available && readMeals in granted()

    /** Whether this phone's Health Connect can let Sukoon read while it's closed. */
    fun backgroundSupported(): Boolean = available &&
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    val readInBackground = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    /** When the newest MyFitnessPal meal in the logbook was eaten (null: none in the last 30 days). */
    suspend fun lastMfpMeal(): Instant? = logbook.eventsSince(System.currentTimeMillis() - Duration.ofDays(30).toMillis()).first()
        .filter { it.source == MFP && it.logType == LogEventType.CARB }
        .maxOfOrNull { it.timestampMillis }
        ?.let(Instant::ofEpochMilli)

    fun start() {
        scope.launch {
            while (true) {
                runCatching { sync() }.onFailure { Log.w(TAG, "Background sync skipped", it) }
                delay(TimeUnit.MINUTES.toMillis(15))
            }
        }
    }

    /** The last sync, whatever started it (the 15-minute loop, opening the app, a pull, Resync): shown under MyFitnessPal. */
    data class LastSync(val at: Instant, val added: Int, val updated: Int, val error: String?)

    val lastSync: LastSync?
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L).takeIf { it > 0 }?.let {
            LastSync(Instant.ofEpochMilli(it), prefs.getInt(KEY_LAST_SYNC_ADDED, 0), prefs.getInt(KEY_LAST_SYNC_UPDATED, 0), prefs.getString(KEY_LAST_SYNC_ERROR, null))
        }

    /** What Health Connect holds from MyFitnessPal over the last 7 days: how many meals, and the newest one's time. */
    suspend fun mfpInHealthConnect(): Pair<Int, Instant?> {
        if (!canReadMeals()) return 0 to null
        val records = client.readRecords(
            ReadRecordsRequest(NutritionRecord::class, TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(7))), dataOriginFilter = setOf(DataOrigin(MFP))),
        ).records
        return records.size to records.maxOfOrNull { it.startTime }
    }

    /** One pass, both ways, as far as the grants allow. Its outcome, or its error, is kept for [lastSync]. */
    suspend fun sync(): Result = try {
        syncOnce().also {
            Log.i(TAG, "Synced: ${it.mealsAdded} new, ${it.mealsUpdated} updated, ${it.readingsShared} readings shared")
            recordSync(it.mealsAdded, it.mealsUpdated, null)
        }
    } catch (e: Exception) {
        recordSync(0, 0, e.message ?: e.javaClass.simpleName)
        throw e
    }

    private fun recordSync(added: Int, updated: Int, error: String?) {
        prefs.edit().putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis()).putInt(KEY_LAST_SYNC_ADDED, added).putInt(KEY_LAST_SYNC_UPDATED, updated)
            .putString(KEY_LAST_SYNC_ERROR, error).apply()
    }

    private suspend fun syncOnce(): Result = mutex.withLock {
        if (!available) return@withLock Result(0, 0, 0)
        val granted = granted()
        val types = buildSet {
            if (importMeals && readMeals in granted) add(NutritionRecord::class)
            if (importActivity && readActivity in granted) add(ExerciseSessionRecord::class)
        }
        val (added, updated) = if (types.isNotEmpty()) importRecords(types) else 0 to 0
        val shared = if (shareGlucose && writeGlucose in granted) exportReadings() else 0
        Result(added, updated, shared)
    }

    /** Steps and water since midnight, as far as they're allowed. */
    suspend fun today(): Today {
        if (!available) return Today(null, null)
        val granted = granted()
        val metrics = buildSet<AggregateMetric<*>> {
            if (readSteps in granted) add(StepsRecord.COUNT_TOTAL)
            if (readWater in granted) add(HydrationRecord.VOLUME_TOTAL)
        }
        if (metrics.isEmpty()) return Today(null, null)
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val result = client.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(midnight, Instant.now())))
        return Today(result[StepsRecord.COUNT_TOTAL], result[HydrationRecord.VOLUME_TOTAL]?.inLiters)
    }

    /**
     * Meals and workouts via Health Connect's change log: everything from 7 days back the first time
     * (or when what's imported changes), then only what changed.
     */
    private suspend fun importRecords(types: Set<KClass<out Record>>): Pair<Int, Int> {
        // A new kind of record, or entries imported before nutrients were kept: read the last 7 days again, onto the same entries.
        val kinds = types.mapNotNull { it.simpleName }.sorted().joinToString(",")
        if (prefs.getInt(KEY_IMPORT_VERSION, 1) < 6) {
            // Before 6, each of MyFitnessPal's day totals was kept as one meal: those go, and its meals start from here.
            val gone = logbook.eventsSince(0).first().filter { it.source == MFP && it.logType == LogEventType.CARB }.map { it.id }.toSet()
            gone.forEach { logbook.deleteById(it) }
            saveMealIds(mealIds().filterValues { it !in gone })
            prefs.edit().remove(KEY_DAY_TOTALS).apply()
        }
        if (prefs.getInt(KEY_IMPORT_VERSION, 1) < IMPORT_VERSION || prefs.getString(KEY_TOKEN_TYPES, null) != kinds) {
            prefs.edit().remove(KEY_TOKEN).putInt(KEY_IMPORT_VERSION, IMPORT_VERSION).putString(KEY_TOKEN_TYPES, kinds).apply()
        }
        var added = 0
        var updated = 0
        val ids = mealIds()
        suspend fun importOne(record: Record) {
            val outcome = when (record) {
                is NutritionRecord -> upsertMeal(record, ids)
                is ExerciseSessionRecord -> upsertActivity(record, ids)
                else -> null
            }
            when (outcome) {
                true -> added++
                false -> updated++
                null -> Unit
            }
        }
        var token = prefs.getString(KEY_TOKEN, null)
        if (token == null) {
            // Take the token first so nothing written meanwhile is missed.
            token = client.getChangesToken(ChangesTokenRequest(types))
            val since = TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(7)))
            // Oldest change first, so where one record replaced another the newer one lands last.
            if (NutritionRecord::class in types) client.readRecords(ReadRecordsRequest(NutritionRecord::class, since)).records.sortedBy { it.metadata.lastModifiedTime }.forEach { importOne(it) }
            if (ExerciseSessionRecord::class in types) client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, since)).records.forEach { importOne(it) }
        } else {
            while (true) {
                val response = client.getChanges(token!!)
                if (response.changesTokenExpired) {
                    // Tokens expire after ~30 days unused: start over from a fresh one.
                    prefs.edit().remove(KEY_TOKEN).apply()
                    saveMealIds(ids)
                    return importRecords(types).let { (a, u) -> (a + added) to (u + updated) }
                }
                for (change in response.changes) {
                    when (change) {
                        is UpsertionChange -> importOne(change.record)
                        is DeletionChange -> ids.remove(change.recordId)?.let { logbook.deleteById(it) }
                    }
                }
                token = response.nextChangesToken
                if (!response.hasMore) break
            }
        }
        prefs.edit().putString(KEY_TOKEN, token).apply()
        saveMealIds(ids)
        return added to updated
    }

    /** true = new logbook entry, false = updated, null = skipped (ours, a day's total, a copy, or nothing in it). */
    private suspend fun upsertMeal(record: NutritionRecord, ids: MutableMap<String, Long>): Boolean? {
        val origin = record.metadata.dataOrigin.packageName
        if (origin == context.packageName) return null
        val existing = ids[record.metadata.id]
        fun round1(x: Double?) = x?.takeIf { it > 0 }?.let { (it * 10).roundToInt() / 10.0 }
        val carbs = round1(record.totalCarbohydrate?.inGrams)
        // What each record is, for working out an odd import (times and totals; never the food names).
        Log.i(TAG, "Meal record ${record.metadata.id} from $origin: ${record.startTime}..${record.endTime}, changed ${record.metadata.lastModifiedTime}, type ${record.mealType}, named ${record.name != null}, ${carbs ?: 0} g, ${round1(record.energy?.inKilocalories) ?: 0} kcal")
        // A day-long record is a day's running total (MyFitnessPal's, 10:00–22:00): its rises are the meals.
        // ponytail: other apps' day-long records are left out (copies or summaries); split them too if one turns out to be a diary.
        if (Duration.between(record.startTime, record.endTime) >= DAY_TOTAL) return if (origin == MFP) splitDayTotal(record) else null
        // The same meal posted again by another app (Samsung Health passes MyFitnessPal's on): keep MyFitnessPal's.
        if (origin != MFP && existing == null && carbs != null && isCopyOfMfp(record.startTime, carbs)) return null
        // A meal with no carbs (eggs and coffee) still counts: its calories, protein and fat.
        if (carbs == null && listOf(record.energy?.inKilocalories, record.protein?.inGrams, record.totalFat?.inGrams).all { (it ?: 0.0) <= 0 }) {
            // A meal emptied out: drop the entry it made.
            existing?.let { logbook.deleteById(it); ids.remove(record.metadata.id) }
            return null
        }
        // The time you said you ate it beats the one the app sends (MyFitnessPal's can be hours off).
        val yours = existing?.let { logbook.byId(it) }?.takeIf { it.timeSet }
        val event = EventEntity(
            id = existing ?: 0,
            timestampMillis = yours?.timestampMillis ?: record.startTime.toEpochMilli(),
            timeSet = yours != null,
            type = LogEventType.CARB.name,
            value = carbs ?: 0.0,
            note = record.name?.takeIf { it.isNotBlank() }, // the foods, as the other app names them
            source = record.metadata.dataOrigin.packageName,
            mealType = record.mealType.takeIf { it != MealType.MEAL_TYPE_UNKNOWN },
            fiber = round1(record.dietaryFiber?.inGrams),
            sugar = round1(record.sugar?.inGrams),
            protein = round1(record.protein?.inGrams),
            fat = round1(record.totalFat?.inGrams),
            kcal = round1(record.energy?.inKilocalories),
        )
        // An entry deleted in the logbook meanwhile comes back as new rather than silently not at all.
        return if (existing != null && logbook.update(event) > 0) {
            false
        } else {
            ids[record.metadata.id] = logbook.insert(event.copy(id = 0))
            true
        }
    }

    /** A rise in the day's total becomes a meal, when you logged it; a fall comes off the newest of that day's meals. */
    private suspend fun splitDayTotal(record: NutritionRecord): Boolean? {
        val totals = dayTotals()
        val key = record.metadata.id
        fun g(m: androidx.health.connect.client.units.Mass?) = m?.inGrams ?: 0.0
        val now = listOf(g(record.totalCarbohydrate), g(record.dietaryFiber), g(record.sugar), g(record.protein), g(record.totalFat), record.energy?.inKilocalories ?: 0.0)
        val prior = totals.optJSONObject(key)
        if (prior == null) {
            // First seen: what's in it already stays out (it can't be told apart into meals any more).
            totals.put(key, JSONObject().put("seen", JSONArray(now)).put("meals", JSONArray()))
            saveDayTotals(totals)
            return null
        }
        val seen = prior.getJSONArray("seen").let { a -> List(a.length()) { a.getDouble(it) } }
        val ids = prior.getJSONArray("meals").let { a -> List(a.length()) { a.getLong(it) } }
        val step = DayTotals.step(seen, now)
        var meals = ids.mapNotNull { logbook.byId(it) }
        var outcome: Boolean? = null
        step.removed?.let { removed ->
            DayTotals.takeOff(meals.map { it.sums() }, removed).zip(meals).forEach { (left, meal) ->
                if (left == null) logbook.deleteById(meal.id) else if (left != meal.sums()) logbook.update(meal.withSums(left))
            }
            meals = meals.filter { m -> logbook.byId(m.id) != null }
            outcome = false
        }
        step.meal?.let { added ->
            val zone = ZoneId.systemDefault()
            // When it was logged; a past day filled in later goes at that day's end instead of now.
            val at = record.metadata.lastModifiedTime.takeIf { it.atZone(zone).toLocalDate() == record.startTime.atZone(zone).toLocalDate() } ?: record.endTime
            val meal = EventEntity(timestampMillis = minOf(at, Instant.now()).toEpochMilli(), type = LogEventType.CARB.name, source = MFP).withSums(added)
            meals = meals + meal.copy(id = logbook.insert(meal))
            outcome = true
        }
        totals.put(key, JSONObject().put("seen", JSONArray(step.seen)).put("meals", JSONArray(meals.map { it.id })))
        saveDayTotals(totals)
        return outcome
    }

    private fun EventEntity.sums() = listOf(value ?: 0.0, fiber ?: 0.0, sugar ?: 0.0, protein ?: 0.0, fat ?: 0.0, kcal ?: 0.0)

    private fun EventEntity.withSums(s: List<Double>): EventEntity {
        fun r(x: Double) = x.takeIf { it > 0 }?.let { (it * 10).roundToInt() / 10.0 }
        return copy(value = r(s[0]) ?: 0.0, fiber = r(s[1]), sugar = r(s[2]), protein = r(s[3]), fat = r(s[4]), kcal = r(s[5]))
    }

    private fun dayTotals(): JSONObject = runCatching { JSONObject(prefs.getString(KEY_DAY_TOTALS, "{}") ?: "{}") }.getOrDefault(JSONObject())

    private fun saveDayTotals(totals: JSONObject) {
        // ponytail: only the last 14 days' totals are followed; older days stop taking edits made in MyFitnessPal.
        if (totals.length() > 14) totals.keys().asSequence().toList().dropLast(14).forEach { totals.remove(it) }
        prefs.edit().putString(KEY_DAY_TOTALS, totals.toString()).apply()
    }

    /** An MyFitnessPal meal already in the logbook within 10 minutes of [at] with the same carbs (to 1 g). */
    private suspend fun isCopyOfMfp(at: Instant, carbs: Double): Boolean =
        logbook.eventsSince(at.minus(COPY_WINDOW).toEpochMilli()).first().any {
            it.source == MFP && it.logType == LogEventType.CARB &&
                kotlin.math.abs(it.timestampMillis - at.toEpochMilli()) <= COPY_WINDOW.toMillis() &&
                kotlin.math.abs((it.value ?: 0.0) - carbs) <= 1.0
        }

    /** Everything Health Connect holds for an imported meal, read when its detail opens: its times and every nutrient sent. */
    data class MealRecord(val start: Instant, val end: Instant, val name: String?, val mealType: Int, val origin: String, val nutrients: List<Pair<Nutrient, Double>>)

    /** The nutrients beyond those kept on the entry; [milligrams] ones are shown in mg. */
    enum class Nutrient(val labelRes: Int, val milligrams: Boolean, val of: (NutritionRecord) -> androidx.health.connect.client.units.Mass?) {
        SATURATED_FAT(R.string.nutrient_saturated, false, { it.saturatedFat }),
        TRANS_FAT(R.string.nutrient_trans, false, { it.transFat }),
        MONO_FAT(R.string.nutrient_mono, false, { it.monounsaturatedFat }),
        POLY_FAT(R.string.nutrient_poly, false, { it.polyunsaturatedFat }),
        CHOLESTEROL(R.string.nutrient_cholesterol, true, { it.cholesterol }),
        SODIUM(R.string.nutrient_sodium, true, { it.sodium }),
        POTASSIUM(R.string.nutrient_potassium, true, { it.potassium }),
        CALCIUM(R.string.nutrient_calcium, true, { it.calcium }),
        IRON(R.string.nutrient_iron, true, { it.iron }),
        VITAMIN_C(R.string.nutrient_vitamin_c, true, { it.vitaminC }),
        CAFFEINE(R.string.nutrient_caffeine, true, { it.caffeine }),
    }

    /** The Health Connect record behind logbook entry [eventId] (null: not imported, gone, or not allowed). */
    suspend fun mealRecord(eventId: Long): MealRecord? = runCatching {
        val recordId = mealIds().entries.firstOrNull { it.value == eventId }?.key ?: return null
        val r = client.readRecord(NutritionRecord::class, recordId).record
        MealRecord(
            start = r.startTime,
            end = r.endTime,
            name = r.name?.takeIf { it.isNotBlank() },
            mealType = r.mealType,
            origin = r.metadata.dataOrigin.packageName,
            nutrients = Nutrient.entries.mapNotNull { n -> n.of(r)?.let { m -> (if (n.milligrams) m.inMilligrams else m.inGrams).takeIf { it > 0 }?.let { n to it } } },
        )
    }.getOrNull()

    /** true = new logbook entry, false = updated, null = skipped (ours, or under 10 minutes). */
    private suspend fun upsertActivity(record: ExerciseSessionRecord, ids: MutableMap<String, Long>): Boolean? {
        if (record.metadata.dataOrigin.packageName == context.packageName) return null
        val existing = ids[record.metadata.id]
        val minutes = Duration.between(record.startTime, record.endTime).toMinutes()
        if (minutes < MIN_WORKOUT_MINUTES) {
            // ponytail: short sessions skipped so a phone's auto-detected strolls don't flood the logbook.
            existing?.let { logbook.deleteById(it); ids.remove(record.metadata.id) }
            return null
        }
        val event = EventEntity(
            id = existing ?: 0,
            timestampMillis = record.startTime.toEpochMilli(),
            type = LogEventType.ACTIVITY.name,
            value = minutes.toDouble(),
            note = record.title?.takeIf { it.isNotBlank() } ?: workoutName(record.exerciseType),
            source = record.metadata.dataOrigin.packageName,
        )
        return if (existing != null && logbook.update(event) > 0) {
            false
        } else {
            ids[record.metadata.id] = logbook.insert(event.copy(id = 0))
            true
        }
    }

    private fun workoutName(type: Int): String = context.getString(
        when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> R.string.workout_walk
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> R.string.workout_run
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> R.string.workout_bike
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> R.string.workout_swim
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> R.string.workout_strength
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> R.string.workout_yoga
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> R.string.workout_hike
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> R.string.workout_hiit
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> R.string.workout_football
            else -> R.string.workout_other
        },
    )

    /** Saved real-sensor readings newer than the cursor (2 days back the first time), as interstitial blood glucose. */
    private suspend fun exportReadings(): Int {
        val since = maxOf(prefs.getLong(KEY_EXPORT_CURSOR, 0L), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        val readings = glucose.readingsSince(since + 1).first().filter { it.source != SourceKind.SIMULATED }.take(MAX_BATCH)
        if (readings.isEmpty()) return 0
        val zone = ZoneId.systemDefault()
        val device = Device(type = Device.TYPE_UNKNOWN, manufacturer = "Abbott", model = "FreeStyle Libre 2")
        client.insertRecords(
            readings.map { r ->
                BloodGlucoseRecord(
                    time = r.timestamp,
                    zoneOffset = zone.rules.getOffset(r.timestamp),
                    // A stable client id makes a resend update the same record instead of duplicating it.
                    metadata = Metadata.autoRecorded(device, clientRecordId = "sukoon-${r.timestamp.toEpochMilli()}"),
                    level = BloodGlucose.milligramsPerDeciliter(r.glucoseMgDl.toDouble()),
                    specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
                    relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL,
                )
            },
        )
        prefs.edit().putLong(KEY_EXPORT_CURSOR, readings.last().timestamp.toEpochMilli()).apply()
        return readings.size
    }

    /** Health Connect record id (meals and workouts) → Logbook event id, so later changes land on the same entry. */
    private fun mealIds(): MutableMap<String, Long> {
        val json = runCatching { JSONObject(prefs.getString(KEY_MEAL_IDS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        return json.keys().asSequence().associateWith { json.getLong(it) }.toMutableMap()
    }

    private fun saveMealIds(ids: Map<String, Long>) {
        // ponytail: keeps the newest 2000 mappings; older meals stop following edits made in the other app.
        val kept = ids.entries.sortedByDescending { it.value }.take(2000)
        prefs.edit().putString(KEY_MEAL_IDS, JSONObject(kept.associate { it.key to it.value }).toString()).apply()
    }

    companion object {
        /** MyFitnessPal's package: its meals' source, and the app You → Apps & data opens. */
        const val MFP = "com.myfitnesspal.android"
        private const val KEY_LAST_SYNC_AT = "hc_last_sync_at"
        private const val KEY_LAST_SYNC_ADDED = "hc_last_sync_added"
        private const val KEY_LAST_SYNC_UPDATED = "hc_last_sync_updated"
        private const val KEY_LAST_SYNC_ERROR = "hc_last_sync_error"
        private const val TAG = "HealthConnect"
        private const val KEY_IMPORT = "hc_import_meals"
        private const val KEY_EXPORT = "hc_share_glucose"
        private const val KEY_TOKEN = "hc_nutrition_token"
        private const val KEY_MEAL_IDS = "hc_meal_ids"
        private const val KEY_IMPORT_VERSION = "hc_import_version"
        private const val IMPORT_VERSION = 6 // 2: nutrients, meal type and source app; 3: workouts; 4: copies from other apps left out; 5: your own meal times kept; 6: MyFitnessPal's day totals split into meals
        private val COPY_WINDOW: Duration = Duration.ofMinutes(10)
        private val DAY_TOTAL: Duration = Duration.ofHours(6)
        private const val KEY_DAY_TOTALS = "hc_day_totals"
        private const val KEY_IMPORT_ACTIVITY = "hc_import_activity"
        private const val KEY_TOKEN_TYPES = "hc_token_types"
        private const val MIN_WORKOUT_MINUTES = 10
        private const val KEY_EXPORT_CURSOR = "hc_export_cursor"
        private const val MAX_BATCH = 1000
    }
}
