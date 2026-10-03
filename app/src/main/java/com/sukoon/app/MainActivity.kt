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
import com.sukoon.app.data.prefs.DisclaimerPrefs
import com.sukoon.app.ui.navigation.MainScaffold
import com.sukoon.app.ui.onboarding.DisclaimerGateScreen
import com.sukoon.app.ui.theme.SukoonTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import com.sukoon.app.data.prefs.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val disclaimerPrefs = DisclaimerPrefs(applicationContext)

        val themeMode = (application as SukoonApp).container.themeMode
        setContent {
            val mode by themeMode.collectAsState()
            SukoonTheme(
                darkTheme = when (mode) {
                    ThemeMode.AUTO -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) {
                var disclaimerAccepted by remember { mutableStateOf(disclaimerPrefs.hasAccepted()) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (disclaimerAccepted) {
                        // Post-disclaimer: the 3-tab shell (Now/Trends/You). The Now tab hosts the
                        // live SimulatedSource-driven Home; Trends/You are placeholders until their
                        // screens land (A3/A4/A9/A10).
                        MainScaffold()
                    } else {
                        DisclaimerGateScreen(
                            onAccept = {
                                disclaimerPrefs.setAccepted()
                                disclaimerAccepted = true
                            },
                        )
                    }
                }
            }
        }
    }
}
