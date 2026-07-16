package com.sukoon.app.data.source

import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dev/test-only glucose source (docs/PLAN.md §1) — not shipped as a fallback. Used for UI
 * iteration without a paired sensor, and as a fixture for tests. Takes an external
 * [CoroutineScope] rather than owning one, so callers (and tests, via `backgroundScope`)
 * control its lifecycle.
 *
 * ponytail: skips simulating the sensor warm-up state machine (goes straight
 * Connecting -> Connected) since this harness's job is generating a readings stream, not
 * reproducing onboarding fidelity. Force SourceStatus.WarmingUp directly in a test/preview
 * if that screen state needs exercising.
 */
class SimulatedSource(
    private val scope: CoroutineScope,
    private val tickIntervalMillis: Long = 2_000L,
    private val random: Random = Random.Default,
    startingGlucoseMgDl: Int = 110,
) : GlucoseSource {

    private var job: Job? = null
    private var currentMgDl = startingGlucoseMgDl

    private val _readings = MutableSharedFlow<GlucoseReading>(replay = 1)
    override val readings: Flow<GlucoseReading> = _readings.asSharedFlow()

    private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Disconnected)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    override suspend fun connect() {
        if (job?.isActive == true) return
        _status.value = SourceStatus.Connecting
        job = scope.launch {
            _status.value = SourceStatus.Connected
            while (isActive) {
                _readings.emit(nextReading())
                delay(tickIntervalMillis)
            }
        }
    }

    override suspend fun disconnect() {
        job?.cancel()
        job = null
        _status.value = SourceStatus.Disconnected
    }

    private fun nextReading(): GlucoseReading {
        val delta = random.nextInt(-4, 5) // small random walk each tick
        val swing = if (random.nextInt(0, 20) == 0) random.nextInt(-15, 16) else 0 // rare bigger swing
        val step = delta + swing
        currentMgDl = (currentMgDl + step).coerceIn(40, 400)
        return GlucoseReading(
            timestamp = Instant.now(),
            glucoseMgDl = currentMgDl,
            trend = trendFor(step),
            source = SourceKind.SIMULATED,
        )
    }

    // Ticks aren't 1 real minute apart, so this is illustrative only — the real ROC math
    // lives in GlucoseMetrics.trendFor, exercised against timestamped readings.
    private fun trendFor(deltaPerTick: Int): TrendDirection = when {
        deltaPerTick <= -6 -> TrendDirection.FALLING_FAST
        deltaPerTick <= -2 -> TrendDirection.FALLING
        deltaPerTick < 2 -> TrendDirection.STEADY
        deltaPerTick < 6 -> TrendDirection.RISING
        else -> TrendDirection.RISING_FAST
    }
}
