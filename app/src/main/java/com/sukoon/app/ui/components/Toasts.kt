package com.sukoon.app.ui.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** One-line feedback for a user action (success or failure). Safe from any thread. */
fun Context.toast(message: String, long: Boolean = false) {
    val app = applicationContext
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(app, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
