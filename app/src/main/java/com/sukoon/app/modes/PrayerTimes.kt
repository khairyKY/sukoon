package com.sukoon.app.modes

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fajr (suhoor ends) and Maghrib (iftar) for a day and place, from the sun's position (the
 * praytimes.org formulas): Fajr when the sun is [Method.fajrAngle] below the horizon before dawn,
 * Maghrib at sunset (0.833°). Within a minute or two of the published timetables.
 * ponytail: no high-latitude rules; above ~60° in summer there may be no Fajr, and [of] says null.
 */
object PrayerTimes {
    /** The authority whose Fajr angle to use. Egypt's is the default (Kai's). */
    enum class Method(val fajrAngle: Double) { EGYPT(19.5), MWL(18.0), UMM_AL_QURA(18.5) }

    data class Day(val fajr: Instant, val maghrib: Instant)

    /** Kai's cities, minutes apart: to name a place without a geocoder (where you are stays on the phone), and to pick by hand. */
    enum class City(val lat: Double, val lng: Double) { CAIRO(30.0444, 31.2357), ALEXANDRIA(31.2001, 29.9187), TANTA(30.7865, 31.0004) }

    /** The city within 25 km, if any. */
    fun nearest(lat: Double, lng: Double): City? = City.entries.minBy { km(lat, lng, it.lat, it.lng) }.takeIf { km(lat, lng, it.lat, it.lng) <= 25 }

    /** Great-circle distance (haversine). */
    fun km(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val a = sin(rad(lat2 - lat1) / 2).let { it * it } + cos(rad(lat1)) * cos(rad(lat2)) * sin(rad(lng2 - lng1) / 2).let { it * it }
        return 6371 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun fix(a: Double, b: Double) = (a - b * floor(a / b)).let { if (it < 0) it + b else it }

    /** Sun declination (degrees) and the equation of time (hours) at Julian day [jd]. */
    private fun sun(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = fix(deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15, 24.0)
        val eqt = q / 15 - ra
        return deg(asin(sin(rad(e)) * sin(rad(l)))) to (eqt - 24 * Math.round(eqt / 24).toDouble())
    }

    fun of(date: LocalDate, lat: Double, lng: Double, method: Method = Method.EGYPT): Day? {
        val jd0 = date.toEpochDay() + 2440587.5 // Julian day at 00:00 UT
        val (decl, eqt) = sun(jd0 + 0.5 - lng / 360)
        val noon = 12 - lng / 15 - eqt // UT hours
        fun span(angle: Double): Double? {
            val x = (-sin(rad(angle)) - sin(rad(lat)) * sin(rad(decl))) / (cos(rad(lat)) * cos(rad(decl)))
            return if (x < -1 || x > 1) null else deg(acos(x)) / 15
        }
        val fajr = noon - (span(method.fajrAngle) ?: return null)
        val maghrib = noon + (span(0.833) ?: return null)
        fun at(h: Double) = date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(Math.round(h * 3600))
        return Day(at(fajr), at(maghrib))
    }
}
