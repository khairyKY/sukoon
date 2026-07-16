package com.sukoon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sukoon.app.data.prefs.DisclaimerPrefs
import com.sukoon.app.ui.onboarding.DisclaimerGateScreen
import com.sukoon.app.ui.theme.SukoonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val disclaimerPrefs = DisclaimerPrefs(applicationContext)

        setContent {
            SukoonTheme {
                var disclaimerAccepted by remember { mutableStateOf(disclaimerPrefs.hasAccepted()) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (disclaimerAccepted) {
                        // Placeholder — real navigation graph + Home screen land in a later task.
                        Text(text = "Sukoon")
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
