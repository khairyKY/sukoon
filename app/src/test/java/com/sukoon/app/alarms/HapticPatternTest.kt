package com.sukoon.app.alarms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternTest {

    @Test
    fun `every pattern alternates on and off with real durations`() {
        HapticPattern.entries.forEach { assertTrue(it.name, it.wellFormed) }
    }

    @Test
    fun `each alarm feels different, and urgent low always vibrates`() {
        assertEquals(AlarmType.entries.size, AlarmType.entries.map(HapticPattern::defaultFor).toSet().size)
        assertFalse(HapticPattern.OFF in HapticPattern.choicesFor(AlarmType.URGENT_LOW))
        val s = AlarmSettings(haptics = mapOf(AlarmType.URGENT_LOW to HapticPattern.OFF, AlarmType.HIGH to HapticPattern.OFF))
        assertEquals(HapticPattern.SOS, s.hapticFor(AlarmType.URGENT_LOW)) // a stored "off" can't silence it
        assertEquals(HapticPattern.OFF, s.hapticFor(AlarmType.HIGH))
        assertEquals(HapticPattern.TAP, s.hapticFor(AlarmType.SIGNAL_LOSS)) // not chosen: its own default
    }
}
