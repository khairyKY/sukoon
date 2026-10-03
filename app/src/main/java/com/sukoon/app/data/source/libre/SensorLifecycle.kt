package com.sukoon.app.data.source.libre

import java.time.Duration
import java.time.Instant

/** Where the paired sensor is in its life. */
sealed interface SensorLife {
    data class WarmingUp(val minutesLeft: Int) : SensorLife

    data class Running(val startedAt: Instant, val endsAt: Instant) : SensorLife

    data class Ended(val endedAt: Instant) : SensorLife
}

/** One-time heads-ups about a sensor, in rising order of importance. */
enum class SensorNotice { WARMED_UP, DAY_LEFT, HOUR_LEFT, ENDED }

/**
 * A Libre 2 gives no glucose for its first hour and stops at the lifetime written in its memory
 * (14 days for a Libre 2, 15 for a 2 Plus); minute 0 is the pairing's start. Pure, so the
 * countdowns and notices are unit-tested.
 */
object SensorLifecycle {
    val WARMUP: Duration = Duration.ofMinutes(60)

    /** "Sensor ready" only shortly after warm-up — not when the app first meets a week-old sensor. */
    private val WARMED_UP_WINDOW = Duration.ofHours(3)

    fun of(startMillis: Long, lifetimeMinutes: Int, now: Instant): SensorLife {
        val start = Instant.ofEpochMilli(startMillis)
        val warm = start.plus(WARMUP)
        val end = start.plus(Duration.ofMinutes(lifetimeMinutes.toLong()))
        return when {
            now.isBefore(warm) -> SensorLife.WarmingUp(((Duration.between(now, warm).toMillis() + 59_999) / 60_000).toInt())
            now.isBefore(end) -> SensorLife.Running(start, end)
            else -> SensorLife.Ended(end)
        }
    }

    /** Every notice that applies now; the caller shows the most important one not shown before. */
    fun due(life: SensorLife, now: Instant): Set<SensorNotice> = when (life) {
        is SensorLife.WarmingUp -> emptySet()
        is SensorLife.Running -> buildSet {
            if (now.isBefore(life.startedAt.plus(WARMUP).plus(WARMED_UP_WINDOW))) add(SensorNotice.WARMED_UP)
            val left = Duration.between(now, life.endsAt)
            if (left <= Duration.ofHours(24)) add(SensorNotice.DAY_LEFT)
            if (left <= Duration.ofHours(1)) add(SensorNotice.HOUR_LEFT)
        }
        is SensorLife.Ended -> setOf(SensorNotice.DAY_LEFT, SensorNotice.HOUR_LEFT, SensorNotice.ENDED)
    }
}
