package com.sukoon.app.meter

import java.time.LocalDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlucoseProfileTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `a CareSens-style test - kg per L, with a time offset and context to follow`() {
        // flags 0x13: offset, concentration (kg/L), context follows; seq 212; 2026-10-29 19:01:30; offset 0;
        // 142 mg/dL = 0.00142 kg/L = 142 x 10^-5 → mantissa 142 (0x08E), exponent -5 (0xB) → 0xB08E
        val m = GlucoseProfile.measurement(bytes(0x13, 0xD4, 0x00, 0xEA, 0x07, 10, 29, 19, 1, 30, 0x00, 0x00, 0x8E, 0xB0, 0x11))!!
        assertEquals(212, m.sequence)
        assertEquals(LocalDateTime.of(2026, 10, 29, 19, 1, 30), m.at)
        assertEquals(142, m.mgDl)
        assertEquals(true, m.hasContext)
    }

    @Test
    fun `mmol per L meters and the time offset`() {
        // flags 0x07: offset +60 min, mol/L; 7.8 mmol/L = 0.0078 mol/L = 78 x 10^-4 → 0xC04E
        val m = GlucoseProfile.measurement(bytes(0x07, 0x01, 0x00, 0xEA, 0x07, 1, 2, 3, 4, 5, 0x3C, 0x00, 0x4E, 0xC0, 0x11))!!
        assertEquals(LocalDateTime.of(2026, 1, 2, 4, 4, 5), m.at)
        assertEquals(141, m.mgDl) // 7.8 x 18.0182
    }

    @Test
    fun `context - the meal flag, past carbs and extended flags`() {
        assertEquals(GlucoseProfile.Meal.BEFORE, GlucoseProfile.context(bytes(0x02, 0xD4, 0x00, 0x01))!!.meal)
        assertEquals(GlucoseProfile.Meal.AFTER, GlucoseProfile.context(bytes(0x83, 0xD4, 0x00, 0x00, 0x01, 0x10, 0xE0, 0x02))!!.meal)
        assertNull(GlucoseProfile.context(bytes(0x00, 0xD4, 0x00))!!.meal)
    }

    @Test
    fun `record access - requests and replies`() {
        assertArrayEquals(bytes(0x01, 0x03, 0x01, 0xD4, 0x00), GlucoseProfile.requestAfter(212))
        assertEquals(GlucoseProfile.RacpReply.Count(212), GlucoseProfile.racpReply(bytes(0x05, 0x00, 0xD4, 0x00)))
        assertEquals(GlucoseProfile.RacpReply.Done(true, 1), GlucoseProfile.racpReply(bytes(0x06, 0x00, 0x01, 0x01)))
        assertEquals(GlucoseProfile.RacpReply.Done(true, 6), GlucoseProfile.racpReply(bytes(0x06, 0x00, 0x01, 0x06))) // no records: fine
        assertEquals(GlucoseProfile.RacpReply.Done(false, 2), GlucoseProfile.racpReply(bytes(0x06, 0x00, 0x01, 0x02)))
    }

    @Test
    fun `too short or nonsense values give nothing`() {
        assertNull(GlucoseProfile.measurement(bytes(0x02, 0x01, 0x00)))
        assertNull(GlucoseProfile.measurement(bytes(0x00, 0x01, 0x00, 0xEA, 0x07, 1, 2, 3, 4, 5))!!.mgDl)
    }
}
