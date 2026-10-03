package com.sukoon.app.ui.logbook

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.insights.MeterCheck
import com.sukoon.app.data.source.GlucoseReading

/** Logged events for the current window (see [LogbookViewModel.WINDOW_MILLIS]), newest first. Empty list is the empty state. */
data class LogbookUiState(
    val events: List<EventEntity> = emptyList(),
    /** Finger-prick event id → what the sensor said at that moment (absent when no reading was within 5 min). */
    val meterChecks: Map<Long, MeterCheck> = emptyMap(),
    /** Event id → the sensor reading at that moment (nearest within 5 min), shown on each entry. */
    val glucoseAt: Map<Long, GlucoseReading> = emptyMap(),
    /** Rapid insulin still active right now, shown when logging another dose. */
    val insulinOnBoard: Double = 0.0,
    /** The current reading when fresh: shown on a new entry, which then carries it. */
    val glucoseNow: GlucoseReading? = null,
)
