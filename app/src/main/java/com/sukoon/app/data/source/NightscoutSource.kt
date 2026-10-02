package com.sukoon.app.data.source

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Reads glucose another app already uploads to a Nightscout site (DiaBox → Kai's Nightscout over
 * Tailscale). Polls `/api/v1/entries/sgv.json`; the first poll backfills the last 24h, later polls
 * only ask for entries newer than the last one seen. Duplicates across restarts are dropped by the
 * readings table's unique timestamp index, so the cursor can live in memory.
 *
 * [readings] is cold: polling runs only while the repository collects it, and stops when the user
 * switches source (the collector is cancelled) — so connect/disconnect have nothing to do.
 *
 * ponytail: read token only (the `?token=` reader auth Nightscout widgets use), no API_SECRET.
 */
class NightscoutSource(
    private val baseUrl: String,
    private val token: String,
    private val pollMillis: Long = TimeUnit.MINUTES.toMillis(1),
) : GlucoseSource {

    private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Connecting)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    override val readings: Flow<GlucoseReading> = flow {
        var sinceMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        while (true) {
            try {
                val batch = withContext(Dispatchers.IO) { parseEntries(get(sinceMillis)) }
                _status.value = SourceStatus.Connected
                for (reading in batch) {
                    emit(reading)
                    sinceMillis = maxOf(sinceMillis, reading.timestamp.toEpochMilli())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = SourceStatus.Error(e.message ?: e.javaClass.simpleName)
            }
            delay(pollMillis)
        }
    }

    override suspend fun connect() = Unit
    override suspend fun disconnect() = Unit

    private fun get(sinceMillis: Long): String {
        val query = buildString {
            append("count=1500&find%5Bdate%5D%5B%24gt%5D=").append(sinceMillis)
            if (token.isNotBlank()) append("&token=").append(URLEncoder.encode(token.trim(), "UTF-8"))
        }
        val connection = URL("${baseUrl.trim().trimEnd('/')}/api/v1/entries/sgv.json?$query").openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        try {
            val code = connection.responseCode
            if (code != 200) error(if (code == 401) "Nightscout rejected the token (401)" else "Nightscout HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Nightscout `entries` JSON → readings, oldest first. Non-sgv or implausible rows are skipped. */
        fun parseEntries(json: String): List<GlucoseReading> {
            val array = JSONArray(json)
            return (0 until array.length()).mapNotNull { i ->
                val entry = array.optJSONObject(i) ?: return@mapNotNull null
                val sgv = entry.optInt("sgv", -1)
                val date = entry.optLong("date", 0L)
                if (sgv !in PLAUSIBLE_MG_DL || date <= 0L) return@mapNotNull null
                GlucoseReading(Instant.ofEpochMilli(date), sgv, trendFromDirection(entry.optString("direction")), SourceKind.NIGHTSCOUT)
            }.sortedBy { it.timestamp }
        }
    }
}

/** Libre/CGM apps report 40..500; anything outside is a sentinel or junk, never a real reading. */
internal val PLAUSIBLE_MG_DL = 20..600

/**
 * Nightscout / xDrip+ direction names → our trend buckets (GlucoseSource.TrendDirection). Their
 * Single/Double arrows are both >2 mg/dL/min, i.e. our RISING_FAST; unknown/NONE reads as steady.
 */
internal fun trendFromDirection(direction: String?): TrendDirection = when (direction) {
    "DoubleUp", "SingleUp" -> TrendDirection.RISING_FAST
    "FortyFiveUp" -> TrendDirection.RISING
    "FortyFiveDown" -> TrendDirection.FALLING
    "SingleDown", "DoubleDown" -> TrendDirection.FALLING_FAST
    else -> TrendDirection.STEADY
}
