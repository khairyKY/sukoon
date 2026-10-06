package com.sukoon.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A row's settings, opened under it: they slide straight down (not out of the corner), in a tinted
 * panel set in from the card's edges, so they read as belonging to the row above, not as more rows.
 * Opening scrolls the page so the panel is in view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandedPanel(open: Boolean, inset: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    val view = remember { BringIntoViewRequester() }
    LaunchedEffect(open) {
        if (open) {
            delay(260) // after the panel has grown
            view.bringIntoView()
        }
    }
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(view)
                .padding(start = inset, end = inset, bottom = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
