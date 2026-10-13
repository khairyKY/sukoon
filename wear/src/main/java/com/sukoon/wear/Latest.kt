package com.sukoon.wear

import android.content.Context

/**
 * The newest reading the phone sent (path [PATH] on the Data Layer): what the tile and the
 * complication show. [high] is the top of the user's target range on the phone, so the watch colours
 * a number the same way the phone does.
 */
data class Latest(val mgDl: Int, val trend: String, val timestampMillis: Long, val high: Int = 180) {

    fun minutesAgo(now: Long): Int = ((now - timestampMillis) / 60_000).toInt().coerceAtLeast(0)

    /** PLAN §5: a reading older than 10 minutes is greyed and never shown as current. */
    fun stale(now: Long): Boolean = now - timestampMillis > STALE_MS

    /** The phone's arrows, one per trend bucket (TrendDirection names); nothing for one it doesn't know. */
    val arrow: String
        get() = when (trend) {
            "FALLING_FAST" -> "↓"
            "FALLING" -> "↘"
            "STEADY" -> "→"
            "RISING" -> "↗"
            "RISING_FAST" -> "↑"
            else -> ""
        }

    /** Fixed glucose colours, the same in every theme: low, high, in range, or grey once stale. */
    fun color(now: Long): Int = when {
        stale(now) -> STALE
        mgDl < 70 -> LOW
        mgDl > high -> HIGH
        else -> IN_RANGE
    }

    fun save(context: Context) {
        prefs(context).edit()
            .putInt(KEY_MGDL, mgDl).putString(KEY_TREND, trend).putLong(KEY_TIME, timestampMillis).putInt(KEY_HIGH, high)
            .apply()
    }

    companion object {
        const val PATH = "/sukoon/latest"
        const val KEY_MGDL = "mgDl"
        const val KEY_TREND = "trend"
        const val KEY_TIME = "timestampMillis"
        const val KEY_HIGH = "high"

        const val STALE_MS = 10 * 60_000L
        // The in-range green's light shade (#82BBA0): the watch is always dark, and #3E7A63 is the light-theme one.
        const val IN_RANGE = 0xFF82BBA0.toInt()
        const val HIGH = 0xFFC88A3E.toInt()
        const val LOW = 0xFFC9564B.toInt()
        const val STALE = 0xFF8A918D.toInt()

        /** Null until the phone has sent a reading. */
        fun read(context: Context): Latest? {
            val p = prefs(context)
            if (!p.contains(KEY_TIME)) return null
            return Latest(p.getInt(KEY_MGDL, 0), p.getString(KEY_TREND, "") ?: "", p.getLong(KEY_TIME, 0), p.getInt(KEY_HIGH, 180))
        }

        private fun prefs(context: Context) = context.getSharedPreferences("sukoon_wear", Context.MODE_PRIVATE)
    }
}
