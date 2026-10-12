package com.sukoon.app.ui.logbook

import com.sukoon.app.insulin.Finger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandZonesTest {
    @Test
    fun `a tap on a fingertip picks it, or the nearest one within reach`() {
        Finger.entries.forEach { f ->
            val c = HandZones.center(f)
            assertEquals(f.name, f, HandZones.hit(c.x, c.y))
        }
        assertNull(HandZones.hit(209f, 250f)) // between the hands
    }

    @Test
    fun `your left is on the left and the thumbs point out`() {
        Finger.entries.filter { it.left }.forEach { left ->
            val right = Finger.entries.first { it.digit == left.digit && !it.left }
            assertTrue(left.name, HandZones.center(left).x < HandZones.center(right).x)
        }
        assertTrue(HandZones.center(Finger.LEFT_THUMB).x < HandZones.center(Finger.LEFT_INDEX).x)
        assertTrue(HandZones.center(Finger.RIGHT_THUMB).x > HandZones.center(Finger.RIGHT_INDEX).x)
    }
}
