package com.sukoon.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Continuous BLE collection needs the OS to leave the app's process alone. Two separate
 * fights: (1) Android's own battery-optimization allowlist (standard AOSP API, always
 * reliable), and (2) OEM-specific "app killers" on top of it (Xiaomi/Huawei/Samsung/etc. —
 * undocumented, version-fragile component names, cataloged at https://dontkillmyapp.com).
 * See docs/PLAN.md §2, §10 risk #2.
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

    /**
     * Best-effort deep link into the OEM's own background-kill settings. Component names are
     * widely documented (dontkillmyapp.com) but not an OS-guaranteed API — a ROM update can
     * break any of them, so every attempt is guarded and we always end up somewhere useful:
     * falls back to the general app-details page rather than crashing or doing nothing.
     */
    fun openOemBackgroundSettings(context: Context) {
        for (candidate in candidatesFor(Build.MANUFACTURER)) {
            if (tryLaunch(context, candidate)) return
        }
        openAppDetailsSettings(context)
    }

    internal data class ComponentIntent(val packageName: String, val className: String)

    /**
     * Pure decision function — no Context/Intent side effects — so the manufacturer-to-component
     * mapping is unit-testable without Robolectric. The actual launching (which can't be
     * meaningfully unit-tested) stays in [tryLaunch].
     */
    internal fun candidatesFor(manufacturer: String): List<ComponentIntent> {
        val m = manufacturer.lowercase()
        return when {
            m.contains("xiaomi") -> listOf(
                ComponentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            m.contains("huawei") || m.contains("honor") -> listOf(
                ComponentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )
            m.contains("samsung") -> listOf(
                ComponentIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            )
            m.contains("oppo") || m.contains("realme") -> listOf(
                ComponentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            )
            m.contains("vivo") -> listOf(
                ComponentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            )
            else -> emptyList()
        }
    }

    private fun tryLaunch(context: Context, target: ComponentIntent): Boolean = try {
        val intent = Intent().apply {
            setClassName(target.packageName, target.className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
