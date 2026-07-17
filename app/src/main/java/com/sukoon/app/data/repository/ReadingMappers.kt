package com.sukoon.app.data.repository

import com.sukoon.app.data.db.ReadingEntity
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant

/**
 * Conversions between the domain [GlucoseReading] and the persisted [ReadingEntity]. Pure and
 * unit-tested (see ReadingMappersTest).
 *
 * Enum fields are stored as their `name` string. Reads are tolerant: an unrecognized value
 * (e.g. a row written by a future build that renamed an enum) falls back to a safe default
 * rather than crashing the whole readings Flow with an IllegalArgumentException — a corrupt or
 * stale row must not take the live display down.
 */
fun GlucoseReading.toEntity(): ReadingEntity = ReadingEntity(
    timestampMillis = timestamp.toEpochMilli(),
    glucoseMgDl = glucoseMgDl,
    trend = trend.name,
    source = source.name,
)

fun ReadingEntity.toGlucoseReading(): GlucoseReading = GlucoseReading(
    timestamp = Instant.ofEpochMilli(timestampMillis),
    glucoseMgDl = glucoseMgDl,
    trend = safeEnum(trend, TrendDirection.STEADY),
    source = safeEnum(source, SourceKind.SIMULATED),
)

private inline fun <reified T : Enum<T>> safeEnum(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
