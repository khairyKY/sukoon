package com.sukoon.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** Pull down to bring in what MyFitnessPal (through Health Connect) has now; [onSync] says what came in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToSync(onSync: (suspend () -> String)?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (onSync == null) {
        Box(modifier, content = content)
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = syncing,
        onRefresh = {
            syncing = true
            scope.launch {
                val message = onSync()
                syncing = false
                context.toast(message)
            }
        },
        modifier = modifier,
        content = content,
    )
}
