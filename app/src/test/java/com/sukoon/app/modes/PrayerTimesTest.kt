package com.sukoon.app.modes

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerTimesTest {
    private fun near(expected: String, actual: Instant) =
        assertTrue("$actual vs $expected", Duration.between(Instant.parse(expected), actual).abs() <= Duration.ofMinutes(2))

    @Test
    fun `Cairo matches aladhan for each method`() {
        // api.aladhan.com, 14 Oct 2026, Cairo (UTC): Egyptian 02:30, MWL 02:37, Umm al-Qura 02:34; Maghrib 15:26
        val day = LocalDate.of(2026, 10, 14)
        val egypt = PrayerTimes.of(day, 30.0444, 31.2357)!!
        near("2026-10-14T02:30:00Z", egypt.fajr)
        near("2026-10-14T15:26:00Z", egypt.maghrib)
        near("2026-10-14T02:37:00Z", PrayerTimes.of(day, 30.0444, 31.2357, PrayerTimes.Method.MWL)!!.fajr)
        near("2026-10-14T02:34:00Z", PrayerTimes.of(day, 30.0444, 31.2357, PrayerTimes.Method.UMM_AL_QURA)!!.fajr)
    }

    @Test
    fun `no Fajr in a white night`() {
        assertNull(PrayerTimes.of(LocalDate.of(2026, 6, 21), 64.0, 25.0))
    }
}
