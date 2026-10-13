package com.sukoon.app.supplies

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.sharing.SharedModes
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupplyTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private fun basal(daysAgo: Long, u: Double) = EventEntity(timestampMillis = now.minus(Duration.ofDays(daysAgo)).toEpochMilli(), type = LogEventType.BASAL.name, value = u)

    @Test
    fun `units left count every dose and its priming since the count`() {
        val stock = Stock(penUnits = 450, pens = 1, openLeft = 100.0, at = now.minus(Duration.ofDays(3)).minusSeconds(60))
        val doses = (0L..2L).map { basal(it, 20.0) } + basal(5, 20.0) // the one 5 days ago was before the count
        assertEquals(450 + 100 - 3 * 22.0, Supply.remaining(stock, doses, PenKind.LONG), 1e-9)
        assertEquals(0.0, Supply.remaining(stock, doses, PenKind.RAPID) - 550, 1e-9) // rapid doses: none
    }

    @Test
    fun `days left from the usual rate, and none without 3 days of doses`() {
        val doses = (0L..6L).map { basal(it, 20.0) }
        val perDay = Supply.perDay(doses, PenKind.LONG, now, ZoneOffset.UTC)!!
        assertEquals(22.0, perDay, 1e-9)
        assertEquals(4, Supply.daysLeft(100.0, perDay))
        assertNull(Supply.perDay(doses.take(2), PenKind.LONG, now, ZoneOffset.UTC))
    }

    @Test
    fun `picking up keeps the open pen, opening one starts it full`() {
        val s = Stock(300, 2, 300.0, now.minus(Duration.ofDays(1)))
        val picked = Supply.addPens(s, remainingNow = 2 * 300 + 120.0, added = 3, now = now)
        assertEquals(5, picked.pens)
        assertEquals(120.0, picked.openLeft, 1e-9)
        assertEquals(300.0, Supply.openPen(picked, now).openLeft, 1e-9)
        assertEquals(4, Supply.openPen(picked, now).pens)
        assertEquals(3L, Supply.daysToPickup(Pickup(next = LocalDate.of(2026, 10, 17)), LocalDate.of(2026, 10, 14)))
    }

    @Test
    fun `shared modes survive the trip through JSON`() {
        val m = SharedModes(workoutUntil = now, nightLow = true, suhoorEnds = LocalTime.of(4, 41), iftar = LocalTime.of(17, 34), ketones = 0.8, ketonesAt = now, at = now)
        assertEquals(m, SharedModes.fromJson(m.toJson()))
    }
}
