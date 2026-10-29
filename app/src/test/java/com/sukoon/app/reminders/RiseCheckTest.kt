package com.sukoon.app.reminders

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiseCheckTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-29T14:30:00Z")
    private fun ago(m: Long) = now.minusSeconds(m * 60)
    /** A reading a minute, [from] 25 min ago up to [to] now, in a straight line. */
    private fun climb(from: Int, to: Int) = (25L downTo 0L).map { m -> ago(m) to (to - (to - from) * m / 25).toInt() }
    private fun event(type: LogEventType, minutesAgo: Long, value: Double = 50.0) = EventEntity(timestampMillis = ago(minutesAgo).toEpochMilli(), type = type.name, value = value)

    @Test
    fun `a steady climb with nothing logged asks`() {
        assertTrue(RiseCheck.unexplained(climb(100, 150), emptyList(), now, zone, emptyList()))
    }

    @Test
    fun `small, flattening or low rises don't`() {
        assertFalse(RiseCheck.unexplained(climb(110, 130), emptyList(), now, zone, emptyList())) // +20
        assertFalse(RiseCheck.unexplained(climb(70, 115), emptyList(), now, zone, emptyList())) // under 120
        val peaked = climb(100, 150).map { (t, v) -> if (t.isAfter(ago(12))) t to 150 else t to v }
        assertFalse(RiseCheck.unexplained(peaked, emptyList(), now, zone, emptyList()))
    }

    @Test
    fun `food, insulin or a recent workout explain it`() {
        assertFalse(RiseCheck.unexplained(climb(100, 150), listOf(event(LogEventType.CARB, 60)), now, zone, emptyList()))
        assertFalse(RiseCheck.unexplained(climb(100, 150), listOf(event(LogEventType.INSULIN, 30, 4.0)), now, zone, emptyList()))
        assertFalse(RiseCheck.unexplained(climb(100, 150), listOf(event(LogEventType.ACTIVITY, 120, 60.0)), now, zone, emptyList())) // ended 1 h ago
        assertTrue(RiseCheck.unexplained(climb(100, 150), listOf(event(LogEventType.CARB, 200)), now, zone, emptyList()))
    }

    @Test
    fun `twice a day at most, 3 hours apart`() {
        assertFalse(RiseCheck.unexplained(climb(100, 150), emptyList(), now, zone, listOf(ago(120))))
        assertFalse(RiseCheck.unexplained(climb(100, 150), emptyList(), now, zone, listOf(ago(400), ago(300))))
        assertTrue(RiseCheck.unexplained(climb(100, 150), emptyList(), now, zone, listOf(ago(300))))
    }

    @Test
    fun `no fresh reading, nothing`() {
        assertFalse(RiseCheck.unexplained(climb(100, 150).map { (t, v) -> t.minusSeconds(900) to v }, emptyList(), now, zone, emptyList()))
    }
}
