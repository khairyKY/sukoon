package com.sukoon.app.reminders

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealReminderTest {
    private val now = Instant.parse("2026-10-21T13:30:00Z")
    private fun at(minutesAgo: Long) = now.minusSeconds(minutesAgo * 60).toEpochMilli()
    private val meal = EventEntity(id = 7, timestampMillis = at(25), type = LogEventType.CARB.name, value = 60.0)

    @Test
    fun `a meal with no insulin needs one`() {
        assertTrue(MealReminder.needsInsulin(meal, listOf(meal), now))
    }

    @Test
    fun `insulin from an hour before, after, or linked to it counts`() {
        fun dose(minutesAgo: Long, mealId: Long? = null) = EventEntity(timestampMillis = at(minutesAgo), type = LogEventType.INSULIN.name, value = 5.0, mealId = mealId)
        assertFalse(MealReminder.needsInsulin(meal, listOf(meal, dose(70)), now)) // 45 min before eating
        assertFalse(MealReminder.needsInsulin(meal, listOf(meal, dose(5)), now)) // after it
        assertFalse(MealReminder.needsInsulin(meal, listOf(meal, dose(200, mealId = 7)), now))
        assertTrue(MealReminder.needsInsulin(meal, listOf(meal, dose(120)), now)) // an earlier dose, for something else
    }

    @Test
    fun `not for small snacks, low treatments, catch-ups or long-past meals`() {
        assertFalse(MealReminder.needsInsulin(meal.copy(value = 8.0), emptyList(), now))
        assertFalse(MealReminder.needsInsulin(meal.copy(treatment = true), emptyList(), now))
        assertFalse(MealReminder.needsInsulin(meal.copy(summary = true), emptyList(), now))
        assertFalse(MealReminder.needsInsulin(meal.copy(timestampMillis = at(200)), emptyList(), now))
    }
}
