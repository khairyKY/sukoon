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
import org.json.JSONObject

/**
 * Health Connect, both ways. In: meals other apps log there (MyFitnessPal writes each meal's
 * totals) become Logbook carb entries, kept in step as the meal changes or is deleted. Out:
 * Sukoon's sensor readings become blood-glucose records other health apps can use.
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
    val writeGlucose = HealthPermission.getWritePermission(BloodGlucoseRecord::class)

    var importMeals: Boolean
        get() = prefs.getBoolean(KEY_IMPORT, true)
        set(value) = prefs.edit().putBoolean(KEY_IMPORT, value).apply()

    var shareGlucose: Boolean
        get() = prefs.getBoolean(KEY_EXPORT, true)
        set(value) = prefs.edit().putBoolean(KEY_EXPORT, value).apply()

    /** [HealthConnectClient.SDK_AVAILABLE], unavailable, or the provider needs an update. */
    fun status(): Int = HealthConnectClient.getSdkStatus(context)

    val available: Boolean get() = status() == HealthConnectClient.SDK_AVAILABLE

    /** What to ask for: meals in, glucose out, and background reading where the phone has it. */
    fun wantedPermissions(): Set<String> = buildSet {
        add(readMeals)
        add(writeGlucose)
        if (client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
            add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        }
    }

    suspend fun granted(): Set<String> = if (available) client.permissionController.getGrantedPermissions() else emptySet()

    fun start() {
        scope.launch {
            while (true) {
                runCatching { sync() }.onFailure { Log.w(TAG, "Background sync skipped", it) }
                delay(TimeUnit.MINUTES.toMillis(15))
            }
        }
    }

    /** One pass, both ways, as far as the grants allow. */
    suspend fun sync(): Result = mutex.withLock {
        if (!available) return@withLock Result(0, 0, 0)
        val granted = granted()
        val (added, updated) = if (importMeals && readMeals in granted) importNutrition() else 0 to 0
        val shared = if (shareGlucose && writeGlucose in granted) exportReadings() else 0
        Result(added, updated, shared)
    }

    /** Meals via Health Connect's change log: everything from 7 days back the first time, then only what changed. */
    private suspend fun importNutrition(): Pair<Int, Int> {
        var added = 0
        var updated = 0
        val ids = mealIds()
        suspend fun importOne(record: NutritionRecord) {
            when (upsertMeal(record, ids)) {
                true -> added++
                false -> updated++
                null -> Unit
            }
        }
        var token = prefs.getString(KEY_TOKEN, null)
        if (token == null) {
            // Take the token first so nothing written meanwhile is missed.
            token = client.getChangesToken(ChangesTokenRequest(setOf(NutritionRecord::class)))
            client.readRecords(ReadRecordsRequest(NutritionRecord::class, TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(7))))).records.forEach { importOne(it) }
        } else {
            while (true) {
                val response = client.getChanges(token!!)
                if (response.changesTokenExpired) {
                    // Tokens expire after ~30 days unused: start over from a fresh one.
                    prefs.edit().remove(KEY_TOKEN).apply()
                    saveMealIds(ids)
                    return importNutrition().let { (a, u) -> (a + added) to (u + updated) }
                }
                for (change in response.changes) {
                    when (change) {
                        is UpsertionChange -> (change.record as? NutritionRecord)?.let { importOne(it) }
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

    /** true = new logbook entry, false = updated, null = skipped (ours, or no carbs). */
    private suspend fun upsertMeal(record: NutritionRecord, ids: MutableMap<String, Long>): Boolean? {
        if (record.metadata.dataOrigin.packageName == context.packageName) return null
        val existing = ids[record.metadata.id]
        val carbs = record.totalCarbohydrate?.inGrams?.takeIf { it > 0 }
        if (carbs == null) {
            // A meal emptied out: drop the entry it made.
            existing?.let { logbook.deleteById(it); ids.remove(record.metadata.id) }
            return null
        }
        val note = listOfNotNull(record.name?.takeIf { it.isNotBlank() }, mealName(record.mealType), appName(record.metadata.dataOrigin.packageName))
            .joinToString(" · ")
        val event = EventEntity(
            id = existing ?: 0,
            timestampMillis = record.startTime.toEpochMilli(),
            type = LogEventType.CARB.name,
            value = (carbs * 10).roundToInt() / 10.0,
            note = note,
        )
        return if (existing != null) {
            logbook.update(event)
            false
        } else {
            ids[record.metadata.id] = logbook.insert(event)
            true
        }
    }

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

    private fun mealName(type: Int): String? = when (type) {
        MealType.MEAL_TYPE_BREAKFAST -> context.getString(R.string.hc_meal_breakfast)
        MealType.MEAL_TYPE_LUNCH -> context.getString(R.string.hc_meal_lunch)
        MealType.MEAL_TYPE_DINNER -> context.getString(R.string.hc_meal_dinner)
        MealType.MEAL_TYPE_SNACK -> context.getString(R.string.hc_meal_snack)
        else -> null
    }

    private fun appName(packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull() ?: KNOWN_APPS[packageName] ?: packageName

    /** Health Connect record id → Logbook event id, so later changes land on the same entry. */
    private fun mealIds(): MutableMap<String, Long> {
        val json = runCatching { JSONObject(prefs.getString(KEY_MEAL_IDS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        return json.keys().asSequence().associateWith { json.getLong(it) }.toMutableMap()
    }

    private fun saveMealIds(ids: Map<String, Long>) {
        // ponytail: keeps the newest 2000 mappings; older meals stop following edits made in the other app.
        val kept = ids.entries.sortedByDescending { it.value }.take(2000)
        prefs.edit().putString(KEY_MEAL_IDS, JSONObject(kept.associate { it.key to it.value }).toString()).apply()
    }

    private companion object {
        const val TAG = "HealthConnect"
        const val KEY_IMPORT = "hc_import_meals"
        const val KEY_EXPORT = "hc_share_glucose"
        const val KEY_TOKEN = "hc_nutrition_token"
        const val KEY_MEAL_IDS = "hc_meal_ids"
        const val KEY_EXPORT_CURSOR = "hc_export_cursor"
        const val MAX_BATCH = 1000
        val KNOWN_APPS = mapOf(
            "com.myfitnesspal.android" to "MyFitnessPal",
            "com.samsung.android.app.health" to "Samsung Health",
            "com.google.android.apps.fitness" to "Google Fit",
        )
    }
}
