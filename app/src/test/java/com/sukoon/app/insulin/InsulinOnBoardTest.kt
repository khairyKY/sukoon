package com.sukoon.app.insulin

import com.sukoon.app.data.db.EventEntity
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsulinOnBoardTest {

    private val rapid = InsulinAction(peakMinutes = 75, durationMinutes = 300)

    @Test
    fun `a dose is all there at first, about two-thirds at the peak, gone at the end`() {
        assertEquals(1.0, InsulinOnBoard.remaining(0.0, rapid), 1e-9)
        assertEquals(0.6726, InsulinOnBoard.remaining(75.0, rapid), 1e-4)
        assertEquals(0.2682, InsulinOnBoard.remaining(150.0, rapid), 1e-3)
        assertEquals(0.0, InsulinOnBoard.remaining(300.0, rapid), 1e-9)
        val curve = (0..300 step 10).map { InsulinOnBoard.remaining(it.toDouble(), rapid) }
        assertTrue(curve.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun `rapid doses add up, basal and old doses don't count`() {
        val now = Instant.parse("2026-10-03T12:00:00Z")
        fun dose(minutesAgo: Long, units: Double, type: String = "INSULIN") =
            EventEntity(timestampMillis = now.minus(Duration.ofMinutes(minutesAgo)).toEpochMilli(), type = type, value = units)
        val events = listOf(dose(0, 4.0), dose(75, 2.0), dose(60, 18.0, "BASAL"), dose(400, 6.0))
        assertEquals(4.0 + 2.0 * 0.6726, InsulinOnBoard.total(events, now, rapid), 1e-3)
    }

    @Test
    fun `settings keep the peak where the curve works`() {
        assertEquals(InsulinAction(75, 180), InsulinAction(120, 120).sanitized())
    }
}
