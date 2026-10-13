package com.sukoon.app.sharing

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONObject

/**
 * What a wearer is doing, for their followers' Now (profiles.modes): a workout, the night after one,
 * a fast, a sick day with the last ketones, alarms paused. Times are the wearer's; [at] is when it
 * was sent, so a follower can tell a fast from yesterday from today's.
 */
data class SharedModes(
    val workoutFrom: Instant? = null,
    val workoutUntil: Instant? = null,
    val nightLow: Boolean = false,
    val suhoorEnds: LocalTime? = null,
    val iftar: LocalTime? = null,
    val sickSince: Instant? = null,
    val ketones: Double? = null,
    val ketonesAt: Instant? = null,
    val pausedUntil: Instant? = null,
    val at: Instant? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        workoutFrom?.let { put("workoutFrom", it.toString()) }
        workoutUntil?.let { put("workoutUntil", it.toString()) }
        if (nightLow) put("nightLow", true)
        suhoorEnds?.let { put("suhoorEnds", it.toString()) }
        iftar?.let { put("iftar", it.toString()) }
        sickSince?.let { put("sickSince", it.toString()) }
        ketones?.let { put("ketones", it) }
        ketonesAt?.let { put("ketonesAt", it.toString()) }
        pausedUntil?.let { put("pausedUntil", it.toString()) }
        at?.let { put("at", it.toString()) }
    }

    /** In a workout now. */
    fun working(now: Instant) = workoutUntil != null && workoutUntil > now

    /** Inside today's fast now (only a fast sent today counts). */
    fun fasting(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val s = suhoorEnds ?: return false
        val i = iftar ?: return false
        if (at?.atZone(zone)?.toLocalDate() != LocalDate.now(zone)) return false
        val t = now.atZone(zone).toLocalTime()
        return t >= s && t < i
    }

    fun paused(now: Instant) = pausedUntil != null && pausedUntil > now

    companion object {
        fun fromJson(o: JSONObject): SharedModes {
            fun i(k: String) = o.optString(k).takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
            fun t(k: String) = o.optString(k).takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            return SharedModes(
                workoutFrom = i("workoutFrom"), workoutUntil = i("workoutUntil"), nightLow = o.optBoolean("nightLow"),
                suhoorEnds = t("suhoorEnds"), iftar = t("iftar"), sickSince = i("sickSince"),
                ketones = if (o.has("ketones")) o.optDouble("ketones") else null, ketonesAt = i("ketonesAt"),
                pausedUntil = i("pausedUntil"), at = i("at"),
            )
        }
    }
}
