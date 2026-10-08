package com.sukoon.app.platform

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The clock times are shown in (You → Appearance): the phone's, 12-hour or 24-hour. Compose state,
 * so every time on screen follows a change at once.
 */
object TimeFormat {
    enum class Mode { PHONE, H12, H24 }

    var mode by mutableStateOf(Mode.PHONE)
    private var phone12 = false

    fun init(context: Context, chosen: Mode) {
        phone12 = !DateFormat.is24HourFormat(context)
        mode = chosen
    }

    val twelve: Boolean get() = when (mode) {
        Mode.PHONE -> phone12
        Mode.H12 -> true
        Mode.H24 -> false
    }

    /** [pattern] with its "HH:mm" in the chosen clock ("1:50 PM" for 12-hour). */
    fun of(pattern: String = "HH:mm", zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (twelve) pattern.replace("HH:mm", "h:mm a") else pattern).withZone(zone)
}
