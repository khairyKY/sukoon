package com.sukoon.app.insulin

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InjectionSitesTest {

    private val now = Instant.parse("2026-10-07T22:00:00Z")
    private fun dose(daysAgo: Long, site: InjectionSite?, type: String = "BASAL") =
        EventEntity(timestampMillis = now.minus(Duration.ofDays(daysAgo)).toEpochMilli(), type = type, value = 20.0, site = site?.name)

    @Test
    fun `next is the spot rested longest, never the last one, and rapid rotates on its own`() {
        val history = listOf(
            dose(6, InjectionSite.THIGH_RIGHT), dose(5, InjectionSite.BUTTOCK_LEFT), dose(4, InjectionSite.THIGH_LEFT),
            dose(1, InjectionSite.BUTTOCK_LEFT), dose(0, InjectionSite.THIGH_LEFT),
            dose(0, InjectionSite.ABDOMEN_RIGHT, "INSULIN"),
        )
        assertEquals(InjectionSite.THIGH_RIGHT, InjectionSites.next(history, LogEventType.BASAL))
        assertEquals(InjectionSite.ABDOMEN_LEFT, InjectionSites.next(history, LogEventType.INSULIN)) // one spot so far: the other side
        assertNull(InjectionSites.next(listOf(dose(0, null)), LogEventType.BASAL)) // nothing logged with a site
        assertEquals(now.minus(Duration.ofDays(1)), InjectionSites.lastUsed(history, LogEventType.BASAL, InjectionSite.BUTTOCK_LEFT))
    }

    @Test
    fun `one spot taking half of ten or more doses is crowded`() {
        val crowded = (0L until 6).map { dose(it, InjectionSite.ABDOMEN_RIGHT, "INSULIN") } + (0L until 5).map { dose(it, InjectionSite.ARM_LEFT, "INSULIN") }
        val c = InjectionSites.crowded(crowded, now)!!
        assertEquals(InjectionSite.ABDOMEN_RIGHT, c.site)
        assertEquals(6, c.count)
        assertEquals(11, c.total)
        val spread = InjectionSite.entries.flatMap { s -> listOf(dose(1, s, "INSULIN"), dose(2, s, "INSULIN")) }
        assertNull(InjectionSites.crowded(spread, now))
    }
}
