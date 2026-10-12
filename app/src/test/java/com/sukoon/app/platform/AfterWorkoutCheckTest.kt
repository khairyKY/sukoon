package com.sukoon.app.platform

import com.sukoon.app.data.db.EventEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AfterWorkoutCheckTest {

    private val now = Instant.parse("2026-10-12T18:00:00Z")
    private fun workout(id: Long, startMinutesAgo: Long, minutes: Double, type: String = "ACTIVITY") =
        EventEntity(id = id, timestampMillis = now.minusSeconds(startMinutesAgo * 60).toEpochMilli(), type = type, value = minutes)

    @Test
    fun `a check 30 to 90 minutes after a workout ends, once`() {
        val events = listOf(
            workout(1, startMinutesAgo = 75, minutes = 40.0), // ended 35 min ago: due
            workout(2, startMinutesAgo = 50, minutes = 40.0), // ended 10 min ago: not yet
            workout(3, startMinutesAgo = 200, minutes = 60.0), // ended 140 min ago: too late
            workout(4, startMinutesAgo = 75, minutes = 40.0, type = "CARB"), // not a workout
        )
        assertEquals(listOf(1L), AfterWorkoutCheck.dueWorkouts(events, now, emptySet()).map { it.id })
        assertEquals(emptyList<Long>(), AfterWorkoutCheck.dueWorkouts(events, now, setOf("1")).map { it.id })
    }
}
