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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as SukoonApp).container
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
}
