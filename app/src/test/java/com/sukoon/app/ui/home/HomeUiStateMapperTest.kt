package com.sukoon.app.ui.home

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.SourceStatus
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sukoon.app.data.source.libre.SensorLife
import com.sukoon.app.domain.metrics.FingerPricks

class HomeUiStateMapperTest {

    private val now: Instant = Instant.parse("2026-07-16T10:00:00Z")
    private fun reading(mgDl: Int, at: Instant = now) =
        GlucoseReading(at, mgDl, TrendDirection.STEADY, SourceKind.SIMULATED)

    private val recent = listOf(100, 105, 110, 108, 112).mapIndexed { i, v -> reading(v, now.minusSeconds((4 - i) * 60L)) }

    @Test
    fun `the paired sensor's life wins - real warm-up minutes, and ended`() {
        val warming = HomeUiStateMapper.map(SourceStatus.Connecting, null, emptyList(), now, SensorLife.WarmingUp(42))
        assertEquals(HomeUiState.WarmingUp(42), warming)
        val ended = HomeUiStateMapper.map(SourceStatus.Error("Sensor has expired"), reading(140, now.minusSeconds(3600)), recent, now, SensorLife.Ended(now))
        assertEquals(HomeUiState.FingerPrick(HomeUiState.FingerPrick.Why.SENSOR_ENDED, null, emptyList()), ended)
    }

    @Test
    fun `warm-up shows the sensor's first-hour value while it is fresh, and nothing once it is old`() {
        fun warm(mgDl: Int, minutesAgo: Long) = GlucoseReading(now.minusSeconds(minutesAgo * 60), mgDl, TrendDirection.STEADY, SourceKind.LIBRE_BLE, warmup = true)
        val steady = listOf(warm(226, 3), warm(228, 2), warm(230, 1))
        assertEquals(HomeUiState.WarmingUp(30, provisionalMgDl = 230), HomeUiStateMapper.map(SourceStatus.WarmingUp, steady.last(), steady, now, SensorLife.WarmingUp(30)))
        // The sensor's opening noise (Kai's: 1103, 528, 358) never shows as a number.
        val noise = listOf(warm(528, 3), warm(358, 2), warm(378, 1))
        assertEquals(HomeUiState.WarmingUp(30, provisionalMgDl = null), HomeUiStateMapper.map(SourceStatus.WarmingUp, noise.last(), noise, now, SensorLife.WarmingUp(30)))
        val old = listOf(warm(226, 63), warm(228, 62), warm(230, 61))
        assertEquals(HomeUiState.WarmingUp(30, provisionalMgDl = null), HomeUiStateMapper.map(SourceStatus.WarmingUp, old.last(), old, now, SensorLife.WarmingUp(30)))
    }

    @Test
    fun `disconnected maps to NoSensor`() {
        assertEquals(HomeUiState.NoSensor, HomeUiStateMapper.map(SourceStatus.Disconnected, reading(110), recent, now))
    }

    @Test
    fun `error with no reading maps to NoSensor`() {
        assertEquals(HomeUiState.NoSensor, HomeUiStateMapper.map(SourceStatus.Error("boom"), null, emptyList(), now))
    }

    @Test
    fun `error with a fresh reading still shows it (one failed poll must not blank the screen)`() {
        assertTrue(HomeUiStateMapper.map(SourceStatus.Error("timeout"), reading(110, now.minusSeconds(120)), recent, now) is HomeUiState.InRange)
    }

    @Test
    fun `connected but older than 10 min is Stale, never shown as current`() {
        val state = HomeUiStateMapper.map(SourceStatus.Connected, reading(60, now.minusSeconds(11 * 60)), recent, now)
        assertTrue(state is HomeUiState.Stale)
        assertEquals(11, (state as HomeUiState.Stale).minutesAgo)
        // ...and exactly 10 min old is still current.
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(110, now.minusSeconds(10 * 60)), recent, now) is HomeUiState.InRange)
    }

    @Test
    fun `connected with no reading yet maps to NoSensor`() {
        assertEquals(HomeUiState.NoSensor, HomeUiStateMapper.map(SourceStatus.Connected, null, emptyList(), now))
    }

    @Test
    fun `very low maps to Urgent`() {
        val state = HomeUiStateMapper.map(SourceStatus.Connected, reading(47), recent, now)
        assertTrue(state is HomeUiState.Urgent)
        assertEquals(47, (state as HomeUiState.Urgent).glucoseMgDl)
    }

    @Test
    fun `low band maps to Low`() {
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(63), recent, now) is HomeUiState.Low)
    }

    @Test
    fun `in range maps to InRange and carries the recent window`() {
        val state = HomeUiStateMapper.map(SourceStatus.Connected, reading(112), recent, now)
        assertTrue(state is HomeUiState.InRange)
        assertEquals(recent, (state as HomeUiState.InRange).recentReadings)
    }

    @Test
    fun `high maps to High`() {
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(243), recent, now) is HomeUiState.High)
    }

    @Test
    fun `very high also maps to High (no separate very-high Home state)`() {
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(260), recent, now) is HomeUiState.High)
    }

    @Test
    fun `bracket boundaries land on the expected Home states`() {
        // Urgent follows the urgent-low alarm (under 55); the rest mirrors GlucoseMetrics.bracketFor.
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(54), recent, now) is HomeUiState.Urgent)
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(55), recent, now) is HomeUiState.Low)
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(69), recent, now) is HomeUiState.Low)
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(70), recent, now) is HomeUiState.InRange)
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(180), recent, now) is HomeUiState.InRange)
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(181), recent, now) is HomeUiState.High)
    }

    @Test
    fun `stale computes minutesAgo from the last reading timestamp`() {
        val lastReading = reading(104, at = now.minusSeconds(12 * 60))
        val state = HomeUiStateMapper.map(SourceStatus.Stale, lastReading, recent, now)
        assertTrue(state is HomeUiState.Stale)
        state as HomeUiState.Stale
        assertEquals(104, state.lastGlucoseMgDl)
        assertEquals(12, state.minutesAgo)
    }

    @Test
    fun `a first-hour reading never shows as a real one, even when the countdown says running`() {
        val warm = (3L downTo 1L).map { GlucoseReading(now.minusSeconds(it * 60), 64 - it.toInt(), TrendDirection.FALLING, SourceKind.LIBRE_BLE, warmup = true) }
        val state = HomeUiStateMapper.map(SourceStatus.Connected, warm.last(), warm, now, SensorLife.Running(now.minusSeconds(7200), now.plusSeconds(86_400)))
        assertEquals(HomeUiState.WarmingUp(0, provisionalMgDl = 63), state)
    }

    private val zone = java.time.ZoneOffset.UTC
    private fun prick(minutesAgo: Long, mgDl: Int) = FingerPricks.Prick(now.minusSeconds(minutesAgo * 60), mgDl)

    @Test
    fun `finger-prick mode - on when the sensor ended, with the last prick and today's`() {
        val pricks = listOf(prick(14 * 60, 90), prick(120, 171), prick(20, 142)) // the first is yesterday (UTC)
        val state = HomeUiStateMapper.map(SourceStatus.Error("Sensor has expired"), null, emptyList(), now, SensorLife.Ended(now), pricks, zone)
        assertEquals(HomeUiState.FingerPrick(HomeUiState.FingerPrick.Why.SENSOR_ENDED, pricks.last(), pricks.drop(1)), state)
    }

    @Test
    fun `finger-prick mode - after 30 min without readings, not before`() {
        val at29 = HomeUiStateMapper.map(SourceStatus.Stale, reading(104, now.minusSeconds(29 * 60)), recent, now, null, emptyList(), zone)
        assertTrue(at29 is HomeUiState.Stale)
        val at30 = HomeUiStateMapper.map(SourceStatus.Stale, reading(104, now.minusSeconds(30 * 60)), recent, now, null, emptyList(), zone)
        assertEquals(HomeUiState.FingerPrick(HomeUiState.FingerPrick.Why.NO_READINGS, null, emptyList(), 30), at30)
    }

    @Test
    fun `finger-prick mode - no sensor only once you've pricked in the last day, and off when readings return`() {
        assertEquals(HomeUiState.NoSensor, HomeUiStateMapper.map(SourceStatus.Disconnected, null, emptyList(), now, null, listOf(prick(25 * 60, 120)), zone))
        assertTrue(HomeUiStateMapper.map(SourceStatus.Disconnected, null, emptyList(), now, null, listOf(prick(60, 120)), zone) is HomeUiState.FingerPrick)
        assertTrue(HomeUiStateMapper.map(SourceStatus.Connected, reading(120), recent, now, null, listOf(prick(5, 120)), zone) is HomeUiState.InRange)
    }
}
