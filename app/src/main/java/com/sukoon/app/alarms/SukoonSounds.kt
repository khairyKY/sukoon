package com.sukoon.app.alarms

import android.content.Context
import android.net.Uri
import com.sukoon.app.R

/**
 * Sukoon's own sounds, in packs (res/raw, synthesized by tools/make_sound_packs.py). Every pack has
 * the same six roles; You → Alarms picks the pack, and any alarm can still have a phone sound or file.
 */
enum class SoundPack(val key: String, val nameRes: Int, val blurbRes: Int) {
    ASTRAL("astral", R.string.pack_astral, R.string.pack_astral_body),
    ORBIT("orbit", R.string.pack_orbit, R.string.pack_orbit_body),
    GLASS("glass", R.string.pack_glass, R.string.pack_glass_body),
    CLEAR("clear", R.string.pack_clear, R.string.pack_clear_body),
    /** The first sounds (bells and wood), under their original file names so earlier choices still play. */
    BELLS("sukoon", R.string.pack_bells, R.string.pack_bells_body),
}

enum class SoundRole(val key: String, val bells: String) {
    URGENT("urgent", "sukoon_rise"),
    LOW("low", "sukoon_ripple"),
    GOING_LOW("going_low", "sukoon_drift"),
    HIGH("high", "sukoon_warm"),
    NO_READINGS("no_readings", "sukoon_knock"),
    REMINDER("reminder", "sukoon_chime"),
    ;

    companion object {
        fun of(type: AlarmType): SoundRole = when (type) {
            AlarmType.URGENT_LOW -> URGENT
            AlarmType.LOW -> LOW
            AlarmType.GOING_LOW -> GOING_LOW
            AlarmType.HIGH -> HIGH
            AlarmType.SIGNAL_LOSS -> NO_READINGS
        }
    }
}

object SukoonSounds {
    fun raw(pack: SoundPack, role: SoundRole): String = if (pack == SoundPack.BELLS) role.bells else "${pack.key}_${role.key}"

    /** By name, so it stays valid across builds (resource ids don't). */
    fun uri(context: Context, pack: SoundPack, role: SoundRole): Uri = Uri.parse("android.resource://${context.packageName}/raw/${raw(pack, role)}")
}
