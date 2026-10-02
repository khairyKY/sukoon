package com.sukoon.app.data.prefs

import android.content.Context
import com.sukoon.app.data.source.SourceKind

/** Which glucose source is active, plus its connection details (You tab → Data source). */
data class SourceConfig(
    val kind: SourceKind = SourceKind.SIMULATED,
    val nightscoutUrl: String = "",
    val nightscoutToken: String = "",
)

/**
 * User settings, in the same prefs file as [DisclaimerPrefs]. ponytail: plain private
 * SharedPreferences for the Gemini key too — app-private storage, a personal-use key; move to
 * Keystore-backed storage if the app is ever distributed.
 */
class SettingsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)

    fun loadSourceConfig() = SourceConfig(
        kind = SourceKind.entries.firstOrNull { it.name == prefs.getString(KEY_SOURCE, null) } ?: SourceKind.SIMULATED,
        nightscoutUrl = prefs.getString(KEY_NS_URL, "") ?: "",
        nightscoutToken = prefs.getString(KEY_NS_TOKEN, "") ?: "",
    )

    fun saveSourceConfig(config: SourceConfig) {
        prefs.edit()
            .putString(KEY_SOURCE, config.kind.name)
            .putString(KEY_NS_URL, config.nightscoutUrl)
            .putString(KEY_NS_TOKEN, config.nightscoutToken)
            .apply()
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI, value.trim()).apply()

    private companion object {
        const val KEY_SOURCE = "source_kind"
        const val KEY_NS_URL = "nightscout_url"
        const val KEY_NS_TOKEN = "nightscout_token"
        const val KEY_GEMINI = "gemini_api_key"
    }
}
