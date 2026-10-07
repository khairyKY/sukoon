package com.sukoon.app.ui.logbook

import com.sukoon.app.insulin.InjectionSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyZonesTest {

    @Test
    fun `a tap picks the zone it lands on, or the nearest one within reach`() {
        assertEquals(InjectionSite.ABDOMEN_LEFT, BodyZones.hit(47f, 89f, front = true))
        assertEquals(InjectionSite.THIGH_RIGHT, BodyZones.hit(72f, 140f, front = true))
        assertEquals(InjectionSite.ARM_LEFT, BodyZones.hit(10f, 60f, front = false)) // beside the narrow arm
        assertEquals(InjectionSite.BUTTOCK_RIGHT, BodyZones.hit(70f, 110f, front = false))
        assertNull(BodyZones.hit(60f, 18f, front = true)) // the head
        assertNull(BodyZones.hit(47f, 80f, front = false)) // the back, between the arms and above the buttocks
    }

    @Test
    fun `your left is on the left in both figures`() {
        InjectionSite.entries.filter { it.left }.forEach { left ->
            assertTrue(left.name, BodyZones.zones.getValue(left).x < BodyZones.zones.getValue(left.other).x)
        }
    }
}
