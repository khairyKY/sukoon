package com.sukoon.app.ui.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.sukoon.app.R
import com.sukoon.app.SukoonApp

private var shown: Toast? = null

/**
 * One-line feedback for a user action (success or failure). Safe from any thread. While Sukoon is
 * on screen it's Sukoon's own toast (the mark on the canvas colour, res/layout/sukoon_toast.xml): a
 * system toast, so it shows over sheets and dialogs too. Android only shows custom toasts in front,
 * so from the background it's the plain one. A newer toast replaces the one showing.
 */
fun Context.toast(message: String, long: Boolean = false) {
    val app = applicationContext
    Handler(Looper.getMainLooper()).post {
        val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        val toast = if ((app as? SukoonApp)?.inFront == true) branded(app, message, duration) else Toast.makeText(app, message, duration)
        shown?.cancel()
        shown = toast
        toast.show()
    }
}

@Suppress("DEPRECATION") // custom toast views still show while the app is in front, which is the only time this is used
private fun branded(context: Context, message: String, duration: Int): Toast {
    val view = LayoutInflater.from(context).inflate(R.layout.sukoon_toast, null)
    view.findViewById<TextView>(R.id.toast_text).text = message
    view.contentDescription = message
    return Toast(context).apply {
        this.duration = duration
        this.view = view
        setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, (context.resources.displayMetrics.density * 112).toInt())
    }
}
