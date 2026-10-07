package com.sukoon.app.alarms

import android.content.Context
import android.net.Uri
import com.sukoon.app.R

/**
 * Sukoon's own sounds (res/raw, synthesized: bell and wood tones, soft attacks, natural decays).
 * Each alarm has its own by default; any of them can be chosen for any alarm in You → Alarms.
 */
enum class SukoonSounds(val raw: String, val nameRes: Int) {
    RISE("sukoon_rise", R.string.sound_rise),
    RIPPLE("sukoon_ripple", R.string.sound_ripple),
    DRIFT("sukoon_drift", R.string.sound_drift),
    WARM("sukoon_warm", R.string.sound_warm),
    KNOCK("sukoon_knock", R.string.sound_knock),
    CHIME("sukoon_chime", R.string.sound_chime),
    ;

    companion object {
        fun defaultFor(type: AlarmType): SukoonSounds = when (type) {
            AlarmType.URGENT_LOW -> RISE
            AlarmType.LOW -> RIPPLE
            AlarmType.GOING_LOW -> DRIFT
            AlarmType.HIGH -> WARM
            AlarmType.SIGNAL_LOSS -> KNOCK
        }

        /** By name, so it stays valid across builds (resource ids don't). */
        fun uri(context: Context, sound: SukoonSounds): Uri = Uri.parse("android.resource://${context.packageName}/raw/${sound.raw}")
    }
}
