package com.sukoon.app.alarms

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmLogTest {

    @Test
    fun `entries survive being written and read back`() {
        val fired = AlarmLog.Entry(Instant.ofEpochMilli(1_700_000_000_000), AlarmType.LOW, AlarmLog.Kind.FIRED, 64, sounded = false, screen = true, posted = false, who = "Mum|Dad")
        val back = AlarmLog.decode(AlarmLog.encode(fired))!!
        assertEquals(fired.copy(who = "Mum/Dad"), back) // the separator can't break the line
        val snoozed = AlarmLog.Entry(Instant.ofEpochMilli(1_700_000_060_000), AlarmType.HIGH, AlarmLog.Kind.SNOOZED, minutes = 60)
        assertEquals(snoozed, AlarmLog.decode(AlarmLog.encode(snoozed)))
    }

    @Test
    fun `an unreadable line is skipped, not a crash`() {
        assertNull(AlarmLog.decode("garbage"))
        assertNull(AlarmLog.decode("1|NOT_A_TYPE|FIRED||0|s|"))
    }
}
