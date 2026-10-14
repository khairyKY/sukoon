package com.sukoon.app.clinic

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.insights.Insight
import com.sukoon.app.ui.logbook.usualMeals
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicTest {
    private val today = LocalDate.of(2026, 10, 14)

    @Test
    fun `labs are due by their interval`() {
        val labs = listOf(LabResult(LabKind.HBA1C, 7.1, today.minusDays(100)), LabResult(LabKind.HBA1C, 6.9, today.minusDays(30)), LabResult(LabKind.EGFR, 110.0, today.minusDays(400)))
        assertFalse(Clinic.due(LabKind.HBA1C, labs, today)) // the newest is 30 days old
        assertTrue(Clinic.due(LabKind.EGFR, labs, today))
        assertTrue(Clinic.due(LabKind.LDL, labs, today)) // never done
        assertEquals(6.9, Clinic.latest(labs).getValue(LabKind.HBA1C).value, 1e-9)
    }

    @Test
    fun `questions come from insights worth a word and from labs due`() {
        val insights = listOf(Insight.BasalDrift(nights = 5, drifting = 4, medianDrift = 42), Insight.BasalDrift(nights = 5, drifting = 0, medianDrift = 5), Insight.DawnRise(4, 6, 35))
        val labs = listOf(LabResult(LabKind.HBA1C, 7.0, today), LabResult(LabKind.EGFR, 110.0, today), LabResult(LabKind.UACR, 10.0, today), LabResult(LabKind.LDL, 90.0, today))
        val q = Clinic.questions(insights, labs, today)
        assertEquals(listOf(AutoQuestion(AutoQuestion.Kind.BASAL_UP, 42), AutoQuestion(AutoQuestion.Kind.DAWN, 35)), q) // a steady night asks nothing; no lab due
        assertTrue(AutoQuestion(AutoQuestion.Kind.HBA1C_DUE) in Clinic.questions(emptyList(), emptyList(), today))
    }

    @Test
    fun `usual meals need a name twice, with the dose from the last time`() {
        val now = Instant.parse("2026-10-14T12:00:00Z")
        fun e(type: LogEventType, hoursAgo: Long, v: Double, note: String? = null) =
            EventEntity(timestampMillis = now.minus(Duration.ofHours(hoursAgo)).toEpochMilli(), type = type.name, value = v, note = note)
        val events = listOf(e(LogEventType.CARB, 2, 80.0, "Koshari"), e(LogEventType.INSULIN, 2, 6.0), e(LogEventType.CARB, 30, 90.0, "koshari "), e(LogEventType.CARB, 50, 70.0, "Koshari"), e(LogEventType.CARB, 5, 40.0, "Oats"))
        val usual = usualMeals(events, now)
        assertEquals(1, usual.size)
        assertEquals("Koshari", usual[0].name)
        assertEquals(80.0, usual[0].carbs, 1e-9) // the median
        assertEquals(6.0, usual[0].lastDose!!, 1e-9)
    }
}
