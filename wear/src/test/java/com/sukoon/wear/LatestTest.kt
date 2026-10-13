package com.sukoon.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class LatestTest {
    private val now = 1_000_000_000L

    private fun at(mgDl: Int, minutesAgo: Long = 1, high: Int = 180) = Latest(mgDl, "RISING", now - minutesAgo * 60_000, high)

    @Test
    fun coloursByStateAndGreysOnceStale() {
        assertEquals(Latest.LOW, at(69).color(now))
        assertEquals(Latest.IN_RANGE, at(70).color(now))
        assertEquals(Latest.IN_RANGE, at(180).color(now))
        assertEquals(Latest.HIGH, at(181).color(now))
        assertEquals(Latest.HIGH, at(150, high = 140).color(now)) // the phone's own target range
        assertEquals(Latest.IN_RANGE, at(100, minutesAgo = 10).color(now))
        assertEquals(Latest.STALE, at(100, minutesAgo = 11).color(now))
        assertEquals("↗", at(100).arrow)
        assertEquals(1, at(100).minutesAgo(now))
    }
}
