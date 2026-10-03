package com.sukoon.app.emergency

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sukoon.app.R
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Texts, calls and WhatsApp for the emergency contacts. Texts go straight out through the phone's
 * SMS service (nobody has to tap anything — the person may be unconscious). The call needs the
 * Phone permission and starts from the background thanks to "display over other apps". WhatsApp
 * can only open a chat with the message filled in (someone must press Send), so it's offered for
 * alerts the user sends themselves, not the automatic one.
 */
class EmergencyAlerts(private val context: Context, private val settings: SettingsPrefs) {

    private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    @Volatile private var freshFix: Location? = null

    val contacts: List<EmergencyContact> get() = settings.emergency.contacts

    fun canText() = granted(Manifest.permission.SEND_SMS)

    fun canCall() = granted(Manifest.permission.CALL_PHONE)

    /** The automatic alert: whose glucose, how low, how long nobody answered, and where (if allowed). */
    fun alertText(type: AlarmType, last: GlucoseReading?, unansweredMinutes: Long): String {
        val body = if (type == AlarmType.SIGNAL_LOSS && last != null) {
            context.getString(R.string.emergency_sms_signal, displayName(), last.glucoseMgDl, time.format(last.timestamp), unansweredMinutes)
        } else {
            context.getString(R.string.emergency_sms_urgent, displayName(), last?.glucoseMgDl ?: 0, trendWord(last), unansweredMinutes)
        }
        return listOfNotNull(body, locationLine()).joinToString("\n")
    }

    /** Home → "Alert my emergency contact": the user asking for help themselves. */
    fun helpText(last: GlucoseReading?): String =
        listOfNotNull(context.getString(R.string.emergency_sms_help, displayName(), last?.glucoseMgDl ?: 0, trendWord(last)), locationLine()).joinToString("\n")

    fun recoveredText(last: GlucoseReading): String =
        context.getString(R.string.emergency_sms_recovered, displayName(), last.glucoseMgDl, time.format(last.timestamp))

    fun respondedText(last: GlucoseReading?): String = context.getString(R.string.emergency_sms_responded, displayName(), last?.glucoseMgDl ?: 0)

    fun testText(): String = context.getString(R.string.emergency_sms_test, displayName())

    /** Texts every contact; returns the names handed to the phone's SMS service (empty without the permission). */
    fun textAll(message: String): List<String> {
        if (!canText()) return emptyList()
        val sms = smsManager() ?: return emptyList()
        return contacts.mapNotNull { contact ->
            runCatching {
                sms.sendMultipartTextMessage(contact.phone, null, sms.divideMessage(message), null, null)
                contact.name
            }.onFailure { Log.w(TAG, "Text to ${contact.name} failed", it) }.getOrNull()
        }
    }

    /** Rings the first contact. Without the Phone permission it can only open the dialer. */
    fun callFirst(): Boolean {
        val first = contacts.firstOrNull() ?: return false
        val action = if (canCall()) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return runCatching { context.startActivity(Intent(action, Uri.fromParts("tel", first.phone, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w(TAG, "Call failed", it) }
            .isSuccess
    }

    /** Opens a WhatsApp chat with [contact] and [message] filled in; WhatsApp itself never sends without a tap. */
    fun openWhatsApp(contact: EmergencyContact, message: String): Boolean {
        val url = "https://wa.me/${PhoneNumbers.whatsappDigits(contact.phone)}?text=${Uri.encode(message)}"
        return runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Calling code of the SIM's country, for turning local numbers ("010…") into international ones. */
    fun callingCode(): String = PhoneNumbers.callingCodeFor(context.getSystemService(TelephonyManager::class.java)?.simCountryIso)

    fun locationAllowed() = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Asks for a fresh fix while the countdown runs (the countdown screen keeps the app in the foreground). */
    @SuppressLint("MissingPermission")
    fun refreshLocation() {
        freshFix = null
        if (!settings.emergency.shareLocation || !locationAllowed() || Build.VERSION.SDK_INT < 30) return
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        val provider = providers.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return
        runCatching { manager.getCurrentLocation(provider, null, context.mainExecutor) { fix -> if (fix != null) freshFix = fix } }
            .onFailure { Log.w(TAG, "No fresh location", it) }
    }

    /** "Location (03:05): <map link>" from the freshest fix under an hour old, when the user allowed it. */
    @SuppressLint("MissingPermission")
    private fun locationLine(): String? {
        if (!settings.emergency.shareLocation || !locationAllowed()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val known = manager.getProviders(true).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        val fix = (known + listOfNotNull(freshFix)).maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < 60 * 60_000L }
            ?: return null
        return context.getString(
            R.string.emergency_sms_location,
            time.format(Instant.ofEpochMilli(fix.time)),
            String.format(Locale.US, "%.5f,%.5f", fix.latitude, fix.longitude),
        )
    }

    private fun displayName() = settings.emergency.yourName.trim().ifBlank { context.getString(R.string.emergency_name_fallback) }

    private fun trendWord(r: GlucoseReading?): String = context.getString(
        when (r?.trend) {
            TrendDirection.FALLING_FAST -> R.string.home_trend_falling_fast
            TrendDirection.FALLING -> R.string.home_trend_falling
            TrendDirection.RISING, TrendDirection.RISING_FAST -> R.string.home_trend_rising
            else -> R.string.home_trend_steady
        },
    ).lowercase()

    private fun smsManager(): SmsManager? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "Emergency"
    }
}
