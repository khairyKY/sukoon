package com.sukoon.app.data.prefs

import android.content.Context
import com.sukoon.app.alarms.AlarmSettings
import com.sukoon.app.data.source.SourceKind

/**
 * User settings, in the same prefs file as [DisclaimerPrefs]. ponytail: plain private
 * SharedPreferences for the Gemini key too — app-private storage, a personal-use key; move to
 * Keystore-backed storage if the app is ever distributed.
 */
class SettingsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)

    /** Unknown/retired values (e.g. the removed NIGHTSCOUT/BROADCAST sources) fall back to demo data. */
    var sourceKind: SourceKind
        get() = SourceKind.entries.firstOrNull { it.name == prefs.getString(KEY_SOURCE, null) } ?: SourceKind.SIMULATED
        set(value) = prefs.edit().putString(KEY_SOURCE, value.name).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI, value.trim()).apply()

    /** You → Readings: minimum minutes between saved readings. Alarms ignore it and see every reading. */
    var saveIntervalMinutes: Int
        get() = prefs.getInt(KEY_SAVE_INTERVAL, 1).takeIf { it in SAVE_INTERVALS } ?: 1
        set(value) = prefs.edit().putInt(KEY_SAVE_INTERVAL, value).apply()

    var alarmSettings: AlarmSettings
        get() = AlarmSettings(
            lowEnabled = prefs.getBoolean(KEY_LOW_ON, true),
            lowMgDl = prefs.getInt(KEY_LOW, 70),
            goingLowEnabled = prefs.getBoolean(KEY_GOING_LOW_ON, true),
            highEnabled = prefs.getBoolean(KEY_HIGH_ON, true),
            highMgDl = prefs.getInt(KEY_HIGH, 250),
            signalLossEnabled = prefs.getBoolean(KEY_SIGNAL_ON, true),
            signalLossMinutes = prefs.getInt(KEY_SIGNAL, 20),
            lowSnoozeMinutes = prefs.getInt(KEY_LOW_SNOOZE, 15),
            highSnoozeMinutes = prefs.getInt(KEY_HIGH_SNOOZE, 60),
        ).sanitized()
        set(value) = prefs.edit()
            .putBoolean(KEY_LOW_ON, value.lowEnabled)
            .putInt(KEY_LOW, value.lowMgDl)
            .putBoolean(KEY_GOING_LOW_ON, value.goingLowEnabled)
            .putBoolean(KEY_HIGH_ON, value.highEnabled)
            .putInt(KEY_HIGH, value.highMgDl)
            .putBoolean(KEY_SIGNAL_ON, value.signalLossEnabled)
            .putInt(KEY_SIGNAL, value.signalLossMinutes)
            .putInt(KEY_LOW_SNOOZE, value.lowSnoozeMinutes)
            .putInt(KEY_HIGH_SNOOZE, value.highSnoozeMinutes)
            .apply()

    companion object {
        val SAVE_INTERVALS = listOf(1, 2, 3, 5, 15)
        private const val KEY_SOURCE = "source_kind"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_SAVE_INTERVAL = "save_interval_minutes"
        private const val KEY_LOW_ON = "alarm_low_on"
        private const val KEY_LOW = "alarm_low"
        private const val KEY_GOING_LOW_ON = "alarm_going_low_on"
        private const val KEY_HIGH_ON = "alarm_high_on"
        private const val KEY_HIGH = "alarm_high"
        private const val KEY_SIGNAL_ON = "alarm_signal_on"
        private const val KEY_SIGNAL = "alarm_signal_minutes"
        private const val KEY_LOW_SNOOZE = "alarm_low_snooze"
        private const val KEY_HIGH_SNOOZE = "alarm_high_snooze"
    }
}
