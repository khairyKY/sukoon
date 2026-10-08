package com.sukoon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sukoon.app.ui.navigation.MainScaffold
import com.sukoon.app.ui.onboarding.Onboarding
import com.sukoon.app.ui.theme.SukoonTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import com.sukoon.app.data.prefs.ThemeMode
import android.content.Intent
import com.sukoon.app.data.db.LogEventType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Health Connect's "why this app wants access" link: Sukoon's privacy policy, which says exactly that.
        if (intent?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" || intent?.action == Intent.ACTION_VIEW_PERMISSION_USAGE) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_URL))) }
            finish()
            return
        }
        val container = (application as SukoonApp).container
        takeEntry(intent)
        val themeMode = container.themeMode
        setContent {
            val mode by themeMode.collectAsState()
            SukoonTheme(
                darkTheme = when (mode) {
                    ThemeMode.AUTO -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) {
                var onboarded by remember { mutableStateOf(container.settings.onboarded) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    // First run: welcome (with the disclaimer), role, account, then that role's setup.
                    if (onboarded) MainScaffold() else Onboarding(container) { onboarded = true }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeEntry(intent)
    }

    /** A notification asked for a new entry (the long-acting reminder's "Log it"): the Logbook opens it. */
    private fun takeEntry(intent: Intent) {
        val type = intent.getStringExtra(EXTRA_ENTRY)?.let { name -> LogEventType.entries.firstOrNull { it.name == name } } ?: return
        intent.removeExtra(EXTRA_ENTRY)
        (application as SukoonApp).container.requestedEntry.value = type
    }

    companion object {
        const val EXTRA_ENTRY = "entry"
    }
}

/** The privacy policy (web/privacy.html on GitHub Pages): You → Help, Health Connect's rationale, Play. */
const val PRIVACY_URL = "https://khairyky.github.io/sukoon/privacy.html"
