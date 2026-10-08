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
    EXACT_ALARMS(R.string.setup_exact, R.string.setup_exact_why),
}

/** Android's three battery settings for an app: only Unrestricted lets Sukoon keep the sensor and alarms going. */
enum class BatteryState { UNRESTRICTED, OPTIMIZED, RESTRICTED }

object SetupCheck {

    /**
     * Items that apply to this person on this phone: Bluetooth only to someone wearing a sensor read over
     * Bluetooth (not to a follower, nor to a LibreLinkUp source); emergency texts only to the wearer with a
     * contact; full-screen alarms on 14+; exact alarms where Android makes the app ask (12+, when not granted).
     */
    fun applicable(context: Context): List<SetupItem> {
        val settings = SettingsPrefs(context)
        val wears = settings.role.wears
        return SetupItem.entries.filter {
            when (it) {
                SetupItem.BLUETOOTH -> wears && settings.sourceKind == com.sukoon.app.data.source.SourceKind.LIBRE_BLE
                SetupItem.EMERGENCY -> wears && settings.emergency.contacts.isNotEmpty()
                SetupItem.FULL_SCREEN -> Build.VERSION.SDK_INT >= 34
                SetupItem.EXACT_ALARMS -> Build.VERSION.SDK_INT >= 31
                else -> true
            }
        }
    }

    /** Unrestricted (exempt from battery optimization), Optimized, or Restricted (stopped in the background). */
    fun battery(context: Context): BatteryState = when {
        Build.VERSION.SDK_INT >= 28 && context.getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted -> BatteryState.RESTRICTED
        BatteryOptimization.isIgnoringBatteryOptimizations(context) -> BatteryState.UNRESTRICTED
        else -> BatteryState.OPTIMIZED
    }

    fun missing(context: Context): List<SetupItem> = applicable(context).filterNot { isDone(context, it) }

    fun isDone(context: Context, item: SetupItem): Boolean {
        val notifications = context.getSystemService(NotificationManager::class.java)
        return when (item) {
            SetupItem.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled() && !AlarmNotifier.alarmChannelBlocked(context)
            SetupItem.BLUETOOTH -> bluetoothPermissions().all { granted(context, it) }
            SetupItem.BATTERY -> battery(context) == BatteryState.UNRESTRICTED
            SetupItem.FULL_SCREEN -> Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent()
            SetupItem.OVERLAY -> Settings.canDrawOverlays(context)
            SetupItem.DND -> notifications.isNotificationPolicyAccessGranted
            SetupItem.EMERGENCY -> EMERGENCY_PERMISSIONS.all { granted(context, it) }
            SetupItem.EXACT_ALARMS -> Build.VERSION.SDK_INT < 31 || context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
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
            // Restricted is undone on the app's own page (Battery → Unrestricted); Optimized by Android's ask.
            SetupItem.BATTERY -> if (battery(context) == BatteryState.RESTRICTED) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            else Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            SetupItem.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
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
