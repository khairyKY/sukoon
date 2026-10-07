package com.sukoon.app.data.prefs

import com.sukoon.app.domain.metrics.TargetRange
import android.content.Context
import com.sukoon.app.alarms.AlarmSettings
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.alarms.AlarmSound
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.alarms.SoundPack
import com.sukoon.app.emergency.EmergencySettings
import com.sukoon.app.insulin.InsulinAction
import com.sukoon.app.insulin.DoseSettings
import com.sukoon.app.insulin.Profile
import com.sukoon.app.insulin.Sex
import com.sukoon.app.insights.MealSlot
import com.sukoon.app.ui.home.HomeStat
import com.sukoon.app.ui.home.DEFAULT_HOME_STATS
import com.sukoon.app.ui.home.MAX_HOME_STATS
import com.sukoon.app.reminders.BasalReminderSettings

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
            soundPack = SoundPack.entries.firstOrNull { it.name == prefs.getString(KEY_SOUND_PACK, null) } ?: AlarmSettings().soundPack,
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
                .putString(KEY_SOUND_PACK, value.soundPack.name)
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

    /** You → Insulin: the long-acting reminder. */
    var basalReminder: BasalReminderSettings
        get() = BasalReminderSettings(prefs.getBoolean(KEY_BASAL_REMINDER, false), prefs.getInt(KEY_BASAL_MINUTE, 22 * 60).coerceIn(0, 1439))
        set(value) = prefs.edit().putBoolean(KEY_BASAL_REMINDER, value.enabled).putInt(KEY_BASAL_MINUTE, value.minuteOfDay).apply()

    /** The top of your target range (70 to this); also sets [TargetRange.high], which the everyday screens read. */
    var targetHigh: Int
        get() = prefs.getInt(KEY_TARGET_HIGH, TargetRange.DEFAULT_HIGH).coerceIn(TargetRange.HIGH_RANGE)
        set(value) {
            val high = value.coerceIn(TargetRange.HIGH_RANGE)
            prefs.edit().putInt(KEY_TARGET_HIGH, high).apply()
            TargetRange.high = high
        }

    /** The parent lock's PIN as "salt:hash" ([com.sukoon.app.platform.ParentLock]); null = no lock. */
    var parentPin: String?
        get() = prefs.getString(KEY_PARENT_PIN, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_PARENT_PIN) else putString(KEY_PARENT_PIN, value) }.apply()

    /** The learning suggestion Home was told "Not now" about ([com.sukoon.app.insulin.RatioLearner.Nudge.key]). */
    var nudgeDismissed: String?
        get() = prefs.getString(KEY_NUDGE_DISMISSED, null)
        set(value) = prefs.edit().putString(KEY_NUDGE_DISMISSED, value).apply()

    /** About you, for the dose setup: each part optional, stored as absent when empty. */
    var profile: Profile
        get() = Profile(
            weightKg = if (prefs.contains(KEY_WEIGHT)) prefs.getFloat(KEY_WEIGHT, 0f).toDouble() else null,
            heightCm = prefs.getInt(KEY_HEIGHT, 0).takeIf { it > 0 },
            ageYears = prefs.getInt(KEY_AGE, 0).takeIf { it > 0 },
            sex = Sex.entries.firstOrNull { it.name == prefs.getString(KEY_SEX, null) },
        )
        set(value) = prefs.edit().apply {
            if (value.weightKg == null) remove(KEY_WEIGHT) else putFloat(KEY_WEIGHT, value.weightKg.toFloat())
            putInt(KEY_HEIGHT, value.heightCm ?: 0)
            putInt(KEY_AGE, value.ageYears ?: 0)
            if (value.sex == null) remove(KEY_SEX) else putString(KEY_SEX, value.sex.name)
        }.apply()

    /** You → Insulin: beta dose suggestions. An empty number is stored as absent. */
    var doseSettings: DoseSettings
        get() {
            fun number(key: String) = if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else null
            return DoseSettings(
                enabled = prefs.getBoolean(KEY_DOSE_ON, false),
                carbRatio = MealSlot.entries.mapNotNull { slot -> number(KEY_DOSE_RATIO + slot.name)?.let { slot to it } }.toMap(),
                correctionFactor = number(KEY_DOSE_FACTOR),
                target = prefs.getInt(KEY_DOSE_TARGET, 110).coerceIn(80, 180),
                maxDose = number(KEY_DOSE_MAX) ?: 10.0,
                step = number(KEY_DOSE_STEP) ?: 1.0,
            )
        }
        set(value) = prefs.edit().apply {
            fun number(key: String, v: Double?) { if (v == null) remove(key) else putFloat(key, v.toFloat()) }
            putBoolean(KEY_DOSE_ON, value.enabled)
            MealSlot.entries.forEach { number(KEY_DOSE_RATIO + it.name, value.carbRatio[it]) }
            number(KEY_DOSE_FACTOR, value.correctionFactor)
            putInt(KEY_DOSE_TARGET, value.target)
            number(KEY_DOSE_MAX, value.maxDose)
            number(KEY_DOSE_STEP, value.step)
        }.apply()

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
        private const val KEY_TARGET_HIGH = "target_high"
        private const val KEY_PARENT_PIN = "parent_pin"
        private const val KEY_NUDGE_DISMISSED = "nudge_dismissed"
        private const val KEY_WEIGHT = "profile_weight_kg"
        private const val KEY_HEIGHT = "profile_height_cm"
        private const val KEY_AGE = "profile_age"
        private const val KEY_SEX = "profile_sex"
        private const val KEY_DOSE_ON = "dose_beta"
        private const val KEY_DOSE_RATIO = "dose_ratio_" // + MealSlot name
        private const val KEY_DOSE_FACTOR = "dose_factor"
        private const val KEY_DOSE_TARGET = "dose_target"
        private const val KEY_DOSE_MAX = "dose_max"
        private const val KEY_DOSE_STEP = "dose_step"
        private const val KEY_BASAL_REMINDER = "basal_reminder_on"
        private const val KEY_BASAL_MINUTE = "basal_reminder_minute"
        private const val KEY_START_DISMISSED = "getting_started_dismissed"
        private const val KEY_QUIET_FROM = "alarm_quiet_highs_from"
        private const val KEY_QUIET_TO = "alarm_quiet_highs_to"
        private const val KEY_SOUND_PACK = "alarm_sound_pack"
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
