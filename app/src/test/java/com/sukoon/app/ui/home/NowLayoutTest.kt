package com.sukoon.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowLayoutTest {
    @Test
    fun `nothing saved is Sukoon's order with everything on`() {
        assertEquals(NowLayout(), NowLayout.decode(null))
        assertEquals(NowLayout.MOVABLE, NowLayout.decode("").order)
    }

    @Test
    fun `order and switches survive a save`() {
        val l = NowLayout(listOf(NowPart.GRAPH, NowPart.DOSE, NowPart.NEXT, NowPart.STATS), setOf(NowPart.STATS, NowPart.CHIPS))
        assertEquals(l, NowLayout.decode(l.encode()))
        assertFalse(NowLayout.decode(l.encode()).shows(NowPart.CHIPS))
        assertTrue(NowLayout.decode(l.encode()).shows(NowPart.SENSOR_ENDING))
    }

    @Test
    fun `a part missing from an old save shows at the end, and unknown names are skipped`() {
        val l = NowLayout.decode("GRAPH,-NEXT,OLD_THING;")
        assertEquals(listOf(NowPart.GRAPH, NowPart.NEXT, NowPart.STATS, NowPart.DOSE), l.order)
        assertEquals(setOf(NowPart.NEXT), l.hidden)
    }
}
