package com.sukoon.app.ui.logbook

import com.sukoon.app.data.db.EventEntity

/** Logged events for the current window (see [LogbookViewModel.WINDOW_MILLIS]), newest first. Empty list is the empty state. */
data class LogbookUiState(val events: List<EventEntity> = emptyList())
