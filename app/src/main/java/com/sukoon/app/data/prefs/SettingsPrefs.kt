package com.sukoon.app.data.prefs

import android.content.Context
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

    private companion object {
        const val KEY_SOURCE = "source_kind"
        const val KEY_GEMINI = "gemini_api_key"
    }
}
