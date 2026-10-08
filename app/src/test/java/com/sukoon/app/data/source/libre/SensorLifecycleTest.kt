package com.sukoon.app.data.source.libre

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorLifecycleTest {

    private val t0 = Instant.parse("2026-10-01T08:00:00Z")
    private val lifetime = 15 * 24 * 60 // a Libre 2 Plus
    private val end = t0.plus(Duration.ofMinutes(lifetime.toLong()))
    private fun life(at: Instant) = SensorLifecycle.of(t0.toEpochMilli(), lifetime, at)

    @Test
    fun `first hour is warm-up, then running until the lifetime, then ended`() {
        assertEquals(SensorLife.WarmingUp(31), life(t0.plusSeconds(29 * 60 + 30)))
        assertEquals(SensorLife.Running(t0, end), life(t0.plus(Duration.ofMinutes(60))))
        assertEquals(SensorLife.Ended(end), life(end))
    }

    @Test
    fun `notices come at warm-up's end, a day before, an hour before and at the end`() {
        fun due(at: Instant) = SensorLifecycle.due(life(at), at)
        assertEquals(setOf(SensorNotice.WARMED_UP), due(t0.plus(Duration.ofMinutes(70))))
        assertEquals(emptySet<SensorNotice>(), due(t0.plus(Duration.ofDays(3))))
        assertEquals(setOf(SensorNotice.DAYS_LEFT), due(end.minus(Duration.ofDays(2))))
        assertEquals(setOf(SensorNotice.DAYS_LEFT, SensorNotice.DAY_LEFT), due(end.minus(Duration.ofHours(23))))
        assertEquals(setOf(SensorNotice.DAYS_LEFT, SensorNotice.DAY_LEFT, SensorNotice.HOUR_LEFT), due(end.minus(Duration.ofMinutes(30))))
        assertEquals(setOf(SensorNotice.DAYS_LEFT, SensorNotice.DAY_LEFT, SensorNotice.HOUR_LEFT, SensorNotice.ENDED), due(end.plusSeconds(60)))
    }
}
