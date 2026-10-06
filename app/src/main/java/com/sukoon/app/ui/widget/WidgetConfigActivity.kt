package com.sukoon.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sukoon.app.ui.theme.SukoonTheme


/**
 * Per-widget settings: graph range (or none), details, background. Shown when a widget is added
 * and, on Android 12+, from the launcher's "reconfigure" — each placed widget has its own options.
 * Size isn't a setting: the user drags the widget to any size and the layout follows.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result) // backing out of the first configure cancels adding the widget

        // Exported for the launcher, so only accept ids that are actually our widgets.
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.packageName != packageName) {
            finish()
            return
        }
        setContent {
            SukoonTheme {
                WidgetMaker(editId = appWidgetId) {
                    setResult(RESULT_OK, result)
                    finish()
                }
            }
        }
    }
}
