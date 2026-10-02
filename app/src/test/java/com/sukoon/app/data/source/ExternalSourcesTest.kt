package com.sukoon.app.data.source

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Parsing for the two "another app decoded it" sources: Nightscout entries and the xDrip broadcast. */
class ExternalSourcesTest {

    // Shape copied from Kai's Nightscout (DiaBox uploader, device "bubble"), newest first as the API returns it.
    private val diaboxEntries = """
        [{"_id":"a","type":"sgv","date":1790573563077,"dateString":"2026-09-28T05:32:43.077Z","device":"bubble","direction":"FortyFiveUp","sgv":233,"utcOffset":180},
         {"_id":"b","type":"sgv","date":1790573505514,"device":"bubble","direction":"Flat","sgv":231},
         {"_id":"c","type":"mbg","date":1790573400000,"mbg":120},
         {"_id":"d","type":"sgv","date":1790573300000,"direction":"NOT COMPUTABLE","sgv":5}]
    """.trimIndent()

    @Test
    fun `nightscout entries parse oldest-first and drop non-sgv or implausible rows`() {
        val readings = NightscoutSource.parseEntries(diaboxEntries)
        assertEquals(listOf(231, 233), readings.map { it.glucoseMgDl })
        assertEquals(Instant.ofEpochMilli(1790573505514), readings.first().timestamp)
        assertEquals(TrendDirection.RISING, readings.last().trend)
        assertEquals(SourceKind.NIGHTSCOUT, readings.last().source)
    }

    @Test
    fun `direction names map onto our trend buckets`() {
        assertEquals(TrendDirection.RISING_FAST, trendFromDirection("DoubleUp"))
        assertEquals(TrendDirection.RISING_FAST, trendFromDirection("SingleUp"))
        assertEquals(TrendDirection.FALLING, trendFromDirection("FortyFiveDown"))
        assertEquals(TrendDirection.FALLING_FAST, trendFromDirection("SingleDown"))
        assertEquals(TrendDirection.STEADY, trendFromDirection("NOT COMPUTABLE"))
        assertEquals(TrendDirection.STEADY, trendFromDirection(null))
    }

    @Test
    fun `xdrip broadcast becomes a reading, junk does not`() {
        val now = 1_790_573_563_077L
        val reading = XDripBroadcastReceiver.readingFromXDrip(117.6, now - 60_000, "FortyFiveDown", now)!!
        assertEquals(118, reading.glucoseMgDl)
        assertEquals(TrendDirection.FALLING, reading.trend)
        assertEquals(SourceKind.BROADCAST, reading.source)

        assertNull(XDripBroadcastReceiver.readingFromXDrip(0.0, now, "Flat", now)) // "no data" sentinel
        assertNull(XDripBroadcastReceiver.readingFromXDrip(120.0, 0L, "Flat", now)) // missing time
        assertNull(XDripBroadcastReceiver.readingFromXDrip(120.0, now + 3_600_000, "Flat", now)) // from the future
    }
}
