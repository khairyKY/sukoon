package com.sukoon.app.data.prefs

import android.content.Context

/** Persists whether the first-launch safety disclaimer (docs/PLAN.md §8) has been accepted. */
class DisclaimerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)

    fun hasAccepted(): Boolean = prefs.getBoolean(KEY_ACCEPTED, false)

    fun setAccepted() {
        prefs.edit().putBoolean(KEY_ACCEPTED, true).apply()
    }

    private companion object {
        const val KEY_ACCEPTED = "disclaimer_accepted"
    }
}
