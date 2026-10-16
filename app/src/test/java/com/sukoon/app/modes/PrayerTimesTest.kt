package com.sukoon.app.modes

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `Alexandria is minutes off Cairo`() {
        // api.aladhan.com, 14 Oct 2026, Alexandria 31.2001 N 29.9187 E (UTC), Egyptian: Fajr 02:35, Maghrib 15:30
        val day = LocalDate.of(2026, 10, 14)
        val alex = PrayerTimes.of(day, 31.2001, 29.9187)!!
        near("2026-10-14T02:35:00Z", alex.fajr)
        near("2026-10-14T15:30:00Z", alex.maghrib)
        // A Ramadan day (20 Feb 2027), checked against NOAA's solar equations: Cairo 03:03 / 15:47, Alexandria 03:09 / 15:51.
        val ramadan = LocalDate.of(2027, 2, 20)
        val cairo = PrayerTimes.of(ramadan, 30.0444, 31.2357)!!
        val alexRamadan = PrayerTimes.of(ramadan, 31.2001, 29.9187)!!
        near("2027-02-20T03:03:00Z", cairo.fajr)
        near("2027-02-20T15:47:00Z", cairo.maghrib)
        near("2027-02-20T03:09:00Z", alexRamadan.fajr)
        near("2027-02-20T15:51:00Z", alexRamadan.maghrib)
        assertTrue(Duration.between(cairo.maghrib, alexRamadan.maghrib).toMinutes() in 3..5)
    }

    @Test
    fun `places are named by the city within 25 km`() {
        assertEquals(180.0, PrayerTimes.km(30.0444, 31.2357, 31.2001, 29.9187), 5.0)
        assertEquals(PrayerTimes.City.CAIRO, PrayerTimes.nearest(30.04, 31.24))
        assertEquals(PrayerTimes.City.TANTA, PrayerTimes.nearest(30.79, 31.0))
        assertNull(PrayerTimes.nearest(26.82, 30.8)) // the middle of Egypt: no name, just coordinates
    }

    @Test
    fun `the place moves only past 10 km`() {
        assertFalse(PrayerTimes.moved(30.04, 31.24, 30.08, 31.24)) // ~4.5 km across Cairo: stays
        assertTrue(PrayerTimes.moved(30.04, 31.24, 30.79, 31.0)) // Cairo to Tanta
        assertTrue(PrayerTimes.moved(30.92, 29.70, 31.20, 29.92)) // New Borg El Arab to Alexandria
    }

    @Test
    fun `a geocoded place is named by its town, then district, then governorate`() {
        assertEquals("Tanta", PrayerTimes.placeName("Tanta", "Tanta", "Gharbia Governorate"))
        assertEquals("Borg El Arab", PrayerTimes.placeName(" ", "Borg El Arab", "Alexandria Governorate"))
        assertEquals("محافظة الإسكندرية", PrayerTimes.placeName(null, null, "محافظة الإسكندرية"))
        assertNull(PrayerTimes.placeName(null, "", null))
    }

    @Test
    fun `no Fajr in a white night`() {
        assertNull(PrayerTimes.of(LocalDate.of(2026, 6, 21), 64.0, 25.0))
    }
}
