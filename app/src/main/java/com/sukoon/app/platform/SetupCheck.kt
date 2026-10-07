package com.sukoon.app.platform

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sukoon.app.R
import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.alarms.AlarmNotifier

/**
 * Everything Sukoon needs from the OS to be a reliable CGM app, with how to check and how to fix
 * each. Runtime permissions are requested in-app; the rest are system settings pages.
 */
enum class SetupItem(val titleRes: Int, val whyRes: Int) {
    NOTIFICATIONS(R.string.setup_notifications, R.string.setup_notifications_why),
    BLUETOOTH(R.string.setup_bluetooth, R.string.setup_bluetooth_why),
    BATTERY(R.string.setup_battery, R.string.setup_battery_why),
    FULL_SCREEN(R.string.setup_full_screen, R.string.setup_full_screen_why),
    OVERLAY(R.string.setup_overlay, R.string.setup_overlay_why),
    DND(R.string.setup_dnd, R.string.setup_dnd_why),
    EMERGENCY(R.string.setup_emergency, R.string.setup_emergency_why),
}

object SetupCheck {

    /** Items that apply here: full-screen intent only needs granting on 14+; emergency texts once there's a contact. */
    fun applicable(context: Context): List<SetupItem> = SetupItem.entries.filter {
        when (it) {
            SetupItem.FULL_SCREEN -> Build.VERSION.SDK_INT >= 34
            SetupItem.EMERGENCY -> SettingsPrefs(context).emergency.contacts.isNotEmpty()
            else -> true
        }
    }

    fun missing(context: Context): List<SetupItem> = applicable(context).filterNot { isDone(context, it) }

    fun isDone(context: Context, item: SetupItem): Boolean {
        val notifications = context.getSystemService(NotificationManager::class.java)
        return when (item) {
            SetupItem.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled() && !AlarmNotifier.alarmChannelBlocked(context)
            SetupItem.BLUETOOTH -> bluetoothPermissions().all { granted(context, it) }
            SetupItem.BATTERY -> BatteryOptimization.isIgnoringBatteryOptimizations(context)
            SetupItem.FULL_SCREEN -> Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent()
            SetupItem.OVERLAY -> Settings.canDrawOverlays(context)
            SetupItem.DND -> notifications.isNotificationPolicyAccessGranted
            SetupItem.EMERGENCY -> EMERGENCY_PERMISSIONS.all { granted(context, it) }
        }
    }

    /** Runtime permissions to request in-app for [item], or empty when it's a settings page instead. */
    fun runtimePermissions(item: SetupItem): List<String> = when (item) {
        SetupItem.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        SetupItem.BLUETOOTH -> bluetoothPermissions()
        SetupItem.EMERGENCY -> EMERGENCY_PERMISSIONS
        else -> emptyList()
    }

    /** The system page that grants [item] (also the fallback once a runtime permission was denied for good). */
    fun settingsIntent(context: Context, item: SetupItem): Intent {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (item) {
            SetupItem.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            SetupItem.BLUETOOTH -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            SetupItem.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            SetupItem.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            SetupItem.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            SetupItem.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            SetupItem.EMERGENCY -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Samsung/Xiaomi/… kill background apps beyond Android's own rules; we can't read that state, only open the page. */
    fun hasOemBackgroundSettings(): Boolean = BatteryOptimization.candidatesFor(Build.MANUFACTURER).isNotEmpty()

    private val EMERGENCY_PERMISSIONS = listOf(Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE)

    private fun bluetoothPermissions() = if (Build.VERSION.SDK_INT >= 31) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
