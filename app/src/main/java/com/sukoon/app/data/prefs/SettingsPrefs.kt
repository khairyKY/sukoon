package com.sukoon.app.data.prefs

import android.content.Context
import com.sukoon.app.alarms.AlarmSettings
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.alarms.AlarmSound
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.emergency.EmergencySettings
import com.sukoon.app.insulin.InsulinAction
import com.sukoon.app.ui.home.HomeStat
import com.sukoon.app.ui.home.DEFAULT_HOME_STATS
import com.sukoon.app.ui.home.MAX_HOME_STATS

/**
 * User settings, in the app's prefs file (sukoon_prefs). ponytail: plain private
 * SharedPreferences for the Gemini key too — app-private storage, a personal-use key; move to
 * Keystore-backed storage if the app is ever distributed.
 */
/** Light/dark: follow the phone, or always one. */
enum class ThemeMode { AUTO, LIGHT, DARK }

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
        get() = prefs.getInt(KEY_SAVE_INTERVAL, 1).coerceIn(SAVE_INTERVAL_RANGE)
        set(value) = prefs.edit().putInt(KEY_SAVE_INTERVAL, value).apply()

    var alarmSettings: AlarmSettings
        get() = AlarmSettings(
            lowEnabled = prefs.getBoolean(KEY_LOW_ON, true),
            lowMgDl = prefs.getInt(KEY_LOW, 70),
            goingLowEnabled = prefs.getBoolean(KEY_GOING_LOW_ON, true),
            highEnabled = prefs.getBoolean(KEY_HIGH_ON, true),
            // ponytail: a new key so the old 250 default (written by any alarm change) becomes 180; a value chosen by hand carries over.
            highMgDl = prefs.getInt(KEY_HIGH, prefs.getInt(KEY_HIGH_OLD, 250).takeIf { it != 250 } ?: AlarmSettings().highMgDl),
            signalLossEnabled = prefs.getBoolean(KEY_SIGNAL_ON, true),
            signalLossMinutes = prefs.getInt(KEY_SIGNAL, 20),
            lowSnoozeMinutes = prefs.getInt(KEY_LOW_SNOOZE, 15),
            highSnoozeMinutes = prefs.getInt(KEY_HIGH_SNOOZE, 60),
            quietHighsFrom = prefs.getInt(KEY_QUIET_FROM, -1),
            quietHighsTo = prefs.getInt(KEY_QUIET_TO, -1),
            sounds = AlarmType.entries.mapNotNull { type ->
                prefs.getString(KEY_SOUND + type.name, null)?.let { uri -> type to AlarmSound(uri, prefs.getString(KEY_SOUND_NAME + type.name, null).orEmpty()) }
            }.toMap(),
        ).sanitized()
        set(value) {
            val edit = prefs.edit()
                .putBoolean(KEY_LOW_ON, value.lowEnabled)
                .putInt(KEY_LOW, value.lowMgDl)
                .putBoolean(KEY_GOING_LOW_ON, value.goingLowEnabled)
                .putBoolean(KEY_HIGH_ON, value.highEnabled)
                .putInt(KEY_HIGH, value.highMgDl)
                .putBoolean(KEY_SIGNAL_ON, value.signalLossEnabled)
                .putInt(KEY_SIGNAL, value.signalLossMinutes)
                .putInt(KEY_LOW_SNOOZE, value.lowSnoozeMinutes)
                .putInt(KEY_HIGH_SNOOZE, value.highSnoozeMinutes)
                .putInt(KEY_QUIET_FROM, value.quietHighsFrom)
                .putInt(KEY_QUIET_TO, value.quietHighsTo)
            AlarmType.entries.forEach { type ->
                val sound = value.sounds[type]
                if (sound == null) {
                    edit.remove(KEY_SOUND + type.name).remove(KEY_SOUND_NAME + type.name)
                } else {
                    edit.putString(KEY_SOUND + type.name, sound.uri).putString(KEY_SOUND_NAME + type.name, sound.name)
                }
            }
            edit.apply()
        }

    /** You → Emergency contacts. Contacts are a small JSON list; a corrupt value reads as none. */
    var emergency: EmergencySettings
        get() = EmergencySettings(
            contacts = EmergencySettings.contactsFromJson(prefs.getString(KEY_EMERGENCY_CONTACTS, null)),
            yourName = prefs.getString(KEY_EMERGENCY_NAME, "") ?: "",
            afterMinutes = prefs.getInt(KEY_EMERGENCY_AFTER, 10),
            shareLocation = prefs.getBoolean(KEY_EMERGENCY_LOCATION, false),
        ).sanitized()
        set(value) = prefs.edit()
            .putString(KEY_EMERGENCY_CONTACTS, EmergencySettings.contactsToJson(value.contacts))
            .putString(KEY_EMERGENCY_NAME, value.yourName)
            .putInt(KEY_EMERGENCY_AFTER, value.afterMinutes)
            .putBoolean(KEY_EMERGENCY_LOCATION, value.shareLocation)
            .apply()

    /** You → Insulin: the rapid insulin's activity curve, for "active insulin". */
    var insulinAction: InsulinAction
        get() = InsulinAction(prefs.getInt(KEY_INSULIN_PEAK, 75), prefs.getInt(KEY_INSULIN_DURATION, 300)).sanitized()
        set(value) = prefs.edit().putInt(KEY_INSULIN_PEAK, value.peakMinutes).putInt(KEY_INSULIN_DURATION, value.durationMinutes).apply()

    /** You → Appearance. */
    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.AUTO
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** What this person uses Sukoon for, chosen at sign-up (onboarding's role step). */
    var role: UserRole
        get() = UserRole.entries.firstOrNull { it.name == prefs.getString(KEY_ROLE, null) } ?: UserRole.WEARER
        set(value) = prefs.edit().putString(KEY_ROLE, value.name).apply()

    /** Onboarding is done. Anyone who accepted the old disclaimer gate had set up already, so never sees it. */
    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, prefs.getBoolean(KEY_OLD_DISCLAIMER, false))
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).putBoolean(KEY_OLD_DISCLAIMER, value).apply()

    /** A followed person's phone number, kept on this phone only (for Call and Message on their Home). */
    fun followedPhone(personId: String): String? = prefs.getString(KEY_FOLLOWED_PHONE + personId, null)

    fun setFollowedPhone(personId: String, phone: String?) {
        prefs.edit().apply { if (phone.isNullOrBlank()) remove(KEY_FOLLOWED_PHONE + personId) else putString(KEY_FOLLOWED_PHONE + personId, phone) }.apply()
    }

    /** The numbers Home shows for today, in order (at most four). */
    var homeStats: List<HomeStat>
        get() = prefs.getString(KEY_HOME_STATS, null)?.let { saved ->
            saved.split(',').mapNotNull { name -> HomeStat.entries.firstOrNull { it.name == name } }
        } ?: DEFAULT_HOME_STATS
        set(value) = prefs.edit().putString(KEY_HOME_STATS, value.take(MAX_HOME_STATS).joinToString(",") { it.name }).apply()

    /** Home's getting-started card, once the user has said "Got it". */
    var gettingStartedDismissed: Boolean
        get() = prefs.getBoolean(KEY_START_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_START_DISMISSED, value).apply()

    /** Trends → Insights stays locked until the user has read and accepted what it is (and isn't). */
    var insightsAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_INSIGHTS_ACK, false)
        set(value) = prefs.edit().putBoolean(KEY_INSIGHTS_ACK, value).apply()

    companion object {
        val SAVE_INTERVALS = listOf(1, 2, 3, 5, 15)
        val SAVE_INTERVAL_RANGE = 1..60
        private const val KEY_SOURCE = "source_kind"
        private const val KEY_ROLE = "user_role"
        private const val KEY_HOME_STATS = "home_stats"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_OLD_DISCLAIMER = "disclaimer_accepted" // the disclaimer gate's key, from before onboarding
        private const val KEY_FOLLOWED_PHONE = "followed_phone_"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_SAVE_INTERVAL = "save_interval_minutes"
        private const val KEY_INSIGHTS_ACK = "insights_acknowledged"
        private const val KEY_LOW_ON = "alarm_low_on"
        private const val KEY_LOW = "alarm_low"
        private const val KEY_GOING_LOW_ON = "alarm_going_low_on"
        private const val KEY_HIGH_ON = "alarm_high_on"
        private const val KEY_HIGH = "alarm_high2"
        private const val KEY_HIGH_OLD = "alarm_high"
        private const val KEY_SIGNAL_ON = "alarm_signal_on"
        private const val KEY_SIGNAL = "alarm_signal_minutes"
        private const val KEY_LOW_SNOOZE = "alarm_low_snooze"
        private const val KEY_HIGH_SNOOZE = "alarm_high_snooze"
        private const val KEY_SOUND = "alarm_sound_"
        private const val KEY_SOUND_NAME = "alarm_sound_name_"
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
        private const val KEY_INSULIN_PEAK = "insulin_peak_minutes"
        private const val KEY_START_DISMISSED = "getting_started_dismissed"
        private const val KEY_QUIET_FROM = "alarm_quiet_highs_from"
        private const val KEY_QUIET_TO = "alarm_quiet_highs_to"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_INSULIN_DURATION = "insulin_duration_minutes"
        private const val KEY_EMERGENCY_NAME = "emergency_your_name"
        private const val KEY_EMERGENCY_AFTER = "emergency_after_minutes"
        private const val KEY_EMERGENCY_LOCATION = "emergency_share_location"
    }
}

/** Chosen at sign-up: wearing a sensor, following someone who does, or both. */
enum class UserRole {
    WEARER, FOLLOWER, BOTH;

    val wears: Boolean get() = this != FOLLOWER
    val follows: Boolean get() = this != WEARER
}
