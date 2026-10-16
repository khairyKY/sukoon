package com.sukoon.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView

/**
 * Every bottom sheet (motion spec "Bottom sheet"): the backdrop dims to 42 % and a soft tick lands as it presents.
 * ponytail: Material3 1.3 owns the rise itself (its fixed ~300 ms tween, no duration or easing hook), so present and
 * dismiss aren't the spec's 360 / 240 ms; with "Remove animations" on, Compose already shows it without travel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SukoonSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    /** False: only the sheet's own buttons close it (give it a state that refuses Hidden, see [answerOnlySheetState]). */
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val view = LocalView.current
    LaunchedEffect(Unit) { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = Color.Black.copy(alpha = 0.42f),
        properties = androidx.compose.material3.ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
        content = content,
    )
}

/** A sheet state that won't be swiped or tapped away: for questions that need an answer. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun answerOnlySheetState(): SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden })
