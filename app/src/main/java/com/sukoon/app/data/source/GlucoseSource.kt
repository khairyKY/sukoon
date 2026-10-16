package com.sukoon.app.data.source

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * A pluggable source of glucose readings. See docs/PLAN.md §1.
 * LibreBleSource (direct Libre 2 EU BLE) is the primary/shipped implementation;
 * SimulatedSource exists only for UI development and tests, not as a shipped fallback.
 */
interface GlucoseSource {
    val readings: Flow<GlucoseReading>
    val status: StateFlow<SourceStatus>

    suspend fun connect()
    suspend fun disconnect()
}

data class GlucoseReading(
    val timestamp: Instant,
    val glucoseMgDl: Int,
    val trend: TrendDirection,
    val source: SourceKind,
    /** Taken in the sensor's first hour: shown, but never counted in reports, insights, alarms, shares or the AI. */
    val warmup: Boolean = false,
)

/** Rate-of-change buckets over a 15-min window, mg/dL/min. See docs/PLAN.md §3. */
enum class TrendDirection {
    FALLING_FAST, // < -2
    FALLING,      // -2..-1
    STEADY,       // -1..1
    RISING,       // 1..2
    RISING_FAST,  // > 2
}

enum class SourceKind {
    SIMULATED,
    LIBRE_BLE,
    /** A LibreLinkUp connection (Abbott's follow service: any Libre on Abbott's app, Libre 3 included). */
    LIBRE_LINK_UP,
    /** A Dexcom Share account (G6, G7, ONE through Dexcom's servers). */
    DEXCOM_SHARE,
    /** Nothing picked yet (a new install's default): no readings, so Now says "No sensor connected". */
    NONE,
    ;

    /** Real glucose (not demo data, not nothing): alarms sound for it, it's shared, it goes to other apps. */
    val real: Boolean get() = this != SIMULATED && this != NONE
}

/** The source while none is picked: never connects, never reads. */
class NoSource : GlucoseSource {
    override val readings: Flow<GlucoseReading> = kotlinx.coroutines.flow.emptyFlow()
    override val status: StateFlow<SourceStatus> = kotlinx.coroutines.flow.MutableStateFlow(SourceStatus.Disconnected)
    override suspend fun connect() = Unit
    override suspend fun disconnect() = Unit
}

sealed interface SourceStatus {
    data object Disconnected : SourceStatus
    data object Connecting : SourceStatus
    data object WarmingUp : SourceStatus // sensor's 60-min warm-up window
    data object Connected : SourceStatus
    // No reading for ~10 min — UI must grey out / show "---", never present as current. PLAN.md §5.
    data object Stale : SourceStatus
    data class Error(val message: String) : SourceStatus
}

/** The reading nearest [atMillis], if one is within [withinMillis]: the glucose "at" a logged moment. */
fun List<GlucoseReading>.nearestTo(atMillis: Long, withinMillis: Long = 5 * 60_000L): GlucoseReading? =
    minByOrNull { abs(it.timestamp.toEpochMilli() - atMillis) }?.takeIf { abs(it.timestamp.toEpochMilli() - atMillis) <= withinMillis }
