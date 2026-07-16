package com.sukoon.app.domain.units

import org.junit.Assert.assertEquals
import org.junit.Test

class GlucoseUnitTest {

    @Test
    fun `mg dL formats as a whole number`() {
        assertEquals("105", GlucoseUnit.MG_DL.format(105))
    }

    @Test
    fun `mmol L formats to one decimal place`() {
        // 105 / 18 = 5.8333... -> rounds to 5.8
        assertEquals("5.8", GlucoseUnit.MMOL_L.format(105))
    }

    @Test
    fun `mmol L always shows the decimal, even on an exact value`() {
        // 180 / 18 = 10.0 exactly — must still render as "10.0", not "10"
        assertEquals("10.0", GlucoseUnit.MMOL_L.format(180))
    }
}
