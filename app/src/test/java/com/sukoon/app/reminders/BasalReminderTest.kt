package com.sukoon.app.reminders

import com.sukoon.app.data.db.EventEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BasalReminderTest {

    private val now = Instant.parse("2026-10-07T22:00:00Z")
    private fun basal(hoursAgo: Long, units: Double = 20.0) = EventEntity(timestampMillis = now.minus(Duration.ofHours(hoursAgo)).toEpochMilli(), type = "BASAL", value = units)

    @Test
    fun `the reminder goes to the next time the clock reaches it`() {
        val at = ZonedDateTime.parse("2026-10-07T21:00:00Z")
        assertEquals(ZonedDateTime.parse("2026-10-07T22:30:00Z"), BasalReminder.nextAt(at, 22 * 60 + 30))
        assertEquals(ZonedDateTime.parse("2026-10-08T20:00:00Z"), BasalReminder.nextAt(at, 20 * 60))
        assertEquals(ZonedDateTime.parse("2026-10-08T21:00:00Z"), BasalReminder.nextAt(at, 21 * 60)) // exactly now: tomorrow
    }

    @Test
    fun `a dose in the last 12 hours counts as taken, yesterday's doesn't`() {
        assertTrue(BasalReminder.taken(listOf(basal(1)), now))
        assertFalse(BasalReminder.taken(listOf(basal(24)), now))
        assertFalse(BasalReminder.taken(listOf(EventEntity(timestampMillis = now.toEpochMilli() - 60_000, type = "INSULIN", value = 4.0)), now)) // rapid isn't long-acting
    }

    @Test
    fun `took it logs the last amount, and the usual time is the median of the last doses`() {
        assertEquals(22.0, BasalReminder.lastDose(listOf(basal(48, 20.0), basal(24, 22.0)))!!, 0.0)
        assertNull(BasalReminder.lastDose(emptyList()))
        val times = listOf(21 * 60 + 40, 21 * 60 + 50, 22 * 60 + 5).mapIndexed { day, minute ->
            EventEntity(timestampMillis = Instant.parse("2026-10-0${day + 1}T00:00:00Z").plus(Duration.ofMinutes(minute.toLong())).toEpochMilli(), type = "BASAL", value = 20.0)
        }
        assertEquals(21 * 60 + 50, BasalReminder.usualMinute(times, ZoneOffset.UTC))
    }
}
