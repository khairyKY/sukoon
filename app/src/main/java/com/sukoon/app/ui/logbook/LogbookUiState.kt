package com.sukoon.app.ui.logbook

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.insights.MeterCheck

/** Logged events for the current window (see [LogbookViewModel.WINDOW_MILLIS]), newest first. Empty list is the empty state. */
data class LogbookUiState(
    val events: List<EventEntity> = emptyList(),
    /** Finger-prick event id → what the sensor said at that moment (absent when no reading was within 5 min). */
    val meterChecks: Map<Long, MeterCheck> = emptyMap(),
)
