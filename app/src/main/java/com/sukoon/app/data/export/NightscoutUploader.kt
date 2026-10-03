package com.sukoon.app.data.export

import android.content.Context
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class NightscoutConfig(val enabled: Boolean = false, val url: String = "", val secret: String = "")

sealed interface UploadStatus {
    data object Off : UploadStatus
    data class Ok(val at: Instant, val count: Int) : UploadStatus
    data class Failed(val at: Instant, val message: String) : UploadStatus
}

/**
 * Sukoon → Nightscout (replaces DiaBox's uploader, so Nightscout-fed tools like the desktop widget
 * keep working). Pushes saved readings to /api/v1/entries and logbook entries to
 * /api/v1/treatments, authenticated with the API secret's SHA-1 (Nightscout's `api-secret`
 * header). A cursor per stream means any outage is caught up on the next success; Nightscout
 * dedupes entries by date+type, so a resend after a lost response is harmless.
 */
class NightscoutUploader(
    context: Context,
    private val glucose: GlucoseRepository,
    private val logbook: LogbookRepository,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow<UploadStatus>(UploadStatus.Off)
    val status: StateFlow<UploadStatus> = _status.asStateFlow()

    var config: NightscoutConfig
        get() = NightscoutConfig(prefs.getBoolean(KEY_ON, false), prefs.getString(KEY_URL, "") ?: "", prefs.getString(KEY_SECRET, "") ?: "")
        set(value) {
            prefs.edit().putBoolean(KEY_ON, value.enabled).putString(KEY_URL, value.url.trim().trimEnd('/')).putString(KEY_SECRET, value.secret.trim()).apply()
            scope.launch { uploadNow() }
        }

    fun start() {
        val retry = flow {
            while (true) {
                emit(Unit)
                delay(TimeUnit.MINUTES.toMillis(5))
            }
        }
        scope.launch {
            // New reading → upload; plus a 5-minute retry tick. conflate drops ticks that pile up mid-upload.
            merge(glucose.latestReading.map { }, retry).conflate().collect { uploadNow() }
        }
    }

    suspend fun uploadNow() {
        val c = config
        if (!c.enabled || c.url.isBlank() || c.secret.isBlank()) {
            _status.value = UploadStatus.Off
            return
        }
        val now = System.currentTimeMillis()
        try {
            val readingsSince = maxOf(prefs.getLong(KEY_ENTRIES_CURSOR, 0L), now - BACKFILL_MS)
            val readings = glucose.readingsSince(readingsSince + 1).first()
                .filter { it.source != SourceKind.SIMULATED } // demo data never leaves the phone
                .take(MAX_BATCH)
            if (readings.isNotEmpty()) {
                post(c, "entries", JSONArray(readings.map(::entryJson)))
                prefs.edit().putLong(KEY_ENTRIES_CURSOR, readings.last().timestamp.toEpochMilli()).apply()
            }
            val eventsSince = maxOf(prefs.getLong(KEY_TREATMENTS_CURSOR, 0L), now - BACKFILL_MS)
            val events = logbook.eventsSince(eventsSince + 1).first().take(MAX_BATCH)
            if (events.isNotEmpty()) {
                post(c, "treatments", JSONArray(events.map(::treatmentJson)))
                prefs.edit().putLong(KEY_TREATMENTS_CURSOR, events.maxOf { it.timestampMillis }).apply()
            }
            _status.value = UploadStatus.Ok(Instant.now(), readings.size + events.size)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _status.value = UploadStatus.Failed(Instant.now(), e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun post(c: NightscoutConfig, path: String, body: JSONArray) = withContext(Dispatchers.IO) {
        val connection = URL("${c.url}/api/v1/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("api-secret", sha1(c.secret))
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) error(if (code == 401) "Nightscout rejected the API secret (401)" else "Nightscout HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val KEY_ON = "ns_upload_on"
        private const val KEY_URL = "ns_upload_url"
        private const val KEY_SECRET = "ns_upload_secret"
        private const val KEY_ENTRIES_CURSOR = "ns_upload_entries_cursor"
        private const val KEY_TREATMENTS_CURSOR = "ns_upload_treatments_cursor"
        private val BACKFILL_MS = TimeUnit.DAYS.toMillis(2) // first enable sends up to 2 days back
        private const val MAX_BATCH = 500

        fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Nightscout's direction names for our trend buckets (FALLING_FAST is < −2 mg/dL/min ≈ SingleDown). */
        fun direction(trend: TrendDirection) = when (trend) {
            TrendDirection.FALLING_FAST -> "SingleDown"
            TrendDirection.FALLING -> "FortyFiveDown"
            TrendDirection.STEADY -> "Flat"
            TrendDirection.RISING -> "FortyFiveUp"
            TrendDirection.RISING_FAST -> "SingleUp"
        }

        fun entryJson(r: GlucoseReading): JSONObject = JSONObject()
            .put("type", "sgv")
            .put("sgv", r.glucoseMgDl)
            .put("date", r.timestamp.toEpochMilli())
            .put("dateString", r.timestamp.toString())
            .put("direction", direction(r.trend))
            .put("device", "Sukoon")

        /** Logbook → Nightscout Careportal treatment. Basal shows as a temp-basal-free "Note" with units. */
        fun treatmentJson(e: EventEntity): JSONObject {
            val json = JSONObject()
                .put("created_at", Instant.ofEpochMilli(e.timestampMillis).toString())
                .put("enteredBy", "Sukoon")
            e.note?.let { json.put("notes", it) }
            when (e.logType) {
                LogEventType.CARB -> json.put("eventType", "Carb Correction").put("carbs", e.value ?: 0.0)
                LogEventType.INSULIN -> json.put("eventType", "Correction Bolus").put("insulin", e.value ?: 0.0)
                LogEventType.BASAL -> json.put("eventType", "Note").put("notes", listOfNotNull("Basal ${e.value ?: 0.0} U", e.note).joinToString(" · "))
                LogEventType.ACTIVITY -> json.put("eventType", "Exercise").put("duration", e.value ?: 0.0)
                LogEventType.NOTE -> json.put("eventType", "Note")
            }
            return json
        }
    }
}
