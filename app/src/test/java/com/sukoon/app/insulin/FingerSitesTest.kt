package com.sukoon.app.insulin

import com.sukoon.app.data.db.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FingerSitesTest {
    private fun prick(minute: Long, finger: Finger?, type: String = "FINGERSTICK") =
        EventEntity(timestampMillis = minute * 60_000L, type = type, value = 110.0, site = finger?.name)

    @Test
    fun `no history means no suggestion`() {
        assertNull(FingerSites.next(emptyList()))
        assertNull(FingerSites.next(listOf(prick(1, null))))
    }

    @Test
    fun `next is a finger never used, not the last and not a thumb`() {
        assertEquals(Finger.LEFT_MIDDLE, FingerSites.next(listOf(prick(1, Finger.RIGHT_MIDDLE))))
        assertEquals(Finger.RIGHT_MIDDLE, FingerSites.next(listOf(prick(1, Finger.LEFT_MIDDLE))))
    }

    @Test
    fun `once every finger was used, the one rested longest comes next`() {
        val all = Finger.entries.filter { it.digit != FingerDigit.THUMB }
        val history = all.mapIndexed { i, f -> prick(i + 1L, f) }
        assertEquals(all.first(), FingerSites.next(history))
    }

    @Test
    fun `an insulin site is not a finger`() {
        assertNull(prick(1, null).finger)
        assertNull(EventEntity(timestampMillis = 0, type = "INSULIN", site = Finger.LEFT_RING.name).finger)
        assertEquals(Finger.LEFT_RING, prick(1, Finger.LEFT_RING).finger)
    }
}
