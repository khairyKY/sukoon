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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukoon.app.data.prefs.DisclaimerPrefs
import com.sukoon.app.ui.home.HomeScreen
import com.sukoon.app.ui.home.HomeViewModel
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
                        // Track A: Home is driven by SimulatedSource via HomeViewModel (live,
                        // cycling demo data). Swaps to LibreBleSource behind the same interface
                        // once real-sensor reading lands. Real nav graph arrives with Trends/You.
                        val homeViewModel: HomeViewModel = viewModel()
                        val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                        HomeScreen(state = homeState)
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
