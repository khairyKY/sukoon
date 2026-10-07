package com.sukoon.app.domain.metrics

/**
 * Your target range: 70 to [high]. Only the top moves (120–180, e.g. 70–140 for "time in tight range"):
 * under 70 is a low everywhere, for the alarms and the treat-it flow alike. The everyday screens (Home,
 * graph, widgets, the status bar) use it; Insights and the doctor report keep the international 70–180.
 */
object TargetRange {
    const val LOW = 70
    const val DEFAULT_HIGH = 180
    val HIGH_RANGE = 120..180

    /**
     * ponytail: process-wide, set from settings at start and whenever it changes; screens pick it up
     * on their next reading. A StateFlow through every screen would only matter if a minute's lag did.
     */
    @Volatile var high: Int = DEFAULT_HIGH
}
