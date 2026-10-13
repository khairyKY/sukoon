package com.sukoon.app.alarms

import android.os.VibrationEffect
import androidx.annotation.StringRes
import com.sukoon.app.R

/**
 * How an alarm feels. Sukoon vibrates itself (not the notification channel, whose pattern Android
 * fixes forever once created), so each alarm can have its own pattern, chosen in You → Alarms, and
 * a phone in a pocket says which alarm it is without being looked at. [steps]: (ms, strength 0–255),
 * alternating on and off, repeated for as long as the alarm sounds.
 */
enum class HapticPattern(@StringRes val nameRes: Int, private val steps: List<Pair<Long, Int>>) {
    /** · · · — — — · · ·  : unmistakable, for urgent low. */
    SOS(R.string.haptic_sos, dots() + dashes() + dots(pause = 1000)),
    /** Even, steady pulses. */
    PULSE(R.string.haptic_pulse, listOf(600L to 200, 400L to 0)),
    /** Two quick beats, then rest. */
    HEARTBEAT(R.string.haptic_heartbeat, listOf(90L to 255, 110L to 0, 140L to 180, 1000L to 0)),
    /** Four buzzes, each stronger (equal on phones that can't vary strength). */
    RISING(R.string.haptic_rising, listOf(220L to 60, 110L to 0, 220L to 120, 110L to 0, 220L to 190, 110L to 0, 220L to 255, 800L to 0)),
    /** One light tap every two seconds. */
    TAP(R.string.haptic_tap, listOf(60L to 180, 1940L to 0)),
    OFF(R.string.haptic_off, emptyList());

    /** The repeating waveform; null for [OFF]. Without strength control the on/off timings carry it. */
    fun effect(strengthControl: Boolean): VibrationEffect? {
        if (steps.isEmpty()) return null
        val timings = steps.map { it.first }.toLongArray()
        return if (strengthControl) {
            VibrationEffect.createWaveform(timings, steps.map { it.second }.toIntArray(), 0)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0) + timings, 0) // starts with an "off" slot
        }
    }

    /** Alternates on, off, on, off… with every duration positive: what [effect] relies on. */
    internal val wellFormed: Boolean
        get() = steps.withIndex().all { (i, s) -> s.first > 0 && (s.second > 0) == (i % 2 == 0) } && steps.size % 2 == 0

    companion object {
        /** A different feel per alarm, so they can be told apart without looking. */
        fun defaultFor(type: AlarmType): HapticPattern = when (type) {
            AlarmType.URGENT_LOW -> SOS
            AlarmType.LOW -> PULSE
            AlarmType.GOING_LOW -> RISING
            AlarmType.HIGH -> HEARTBEAT
            AlarmType.SIGNAL_LOSS -> TAP
        }

        /** What each alarm may be set to: urgent low always vibrates. */
        fun choicesFor(type: AlarmType): List<HapticPattern> = if (type == AlarmType.URGENT_LOW) entries - OFF else entries
    }
}

private fun dots(pause: Long = 300) = listOf(150L to 255, 120L to 0, 150L to 255, 120L to 0, 150L to 255, pause to 0)
private fun dashes() = listOf(450L to 255, 150L to 0, 450L to 255, 150L to 0, 450L to 255, 300L to 0)
