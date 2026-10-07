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
        assertEquals(HomeUiState.SensorEnded, ended)
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
}
