package com.sukoon.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Continuous BLE collection needs the OS to leave the app's process alone: Android's own
 * battery-optimization allowlist (Unrestricted). The phone maker's extra killers have no API;
 * the Guide's "Keep Sukoon running" says where to look. See docs/PLAN.md §2, §10 risk #2.
 */
object BatteryOptimization {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Standard AOSP system dialog — safe on every device, no manufacturer-specific risk. */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
