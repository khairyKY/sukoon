package com.sukoon.app.ui.home

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.insights.InsightEngine
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** A number Home can show for today (the user picks up to [MAX_HOME_STATS] of them). */
enum class HomeStat { KCAL, CARBS, PROTEIN, FAT, RAPID, LONG, TIR, AVERAGE, LOWS, STEPS, WATER }

const val MAX_HOME_STATS = 4

val DEFAULT_HOME_STATS = listOf(HomeStat.KCAL, HomeStat.CARBS, HomeStat.RAPID, HomeStat.TIR)

/** Steps and water live in Health Connect, not in Sukoon. */
val HomeStat.fromHealthConnect: Boolean get() = this == HomeStat.STEPS || this == HomeStat.WATER

/**
 * Today's numbers since local midnight, from the logbook (meals, MyFitnessPal's included, and
 * doses) and the readings. A stat with nothing to go on is left out, so Home shows a dash rather
 * than a misleading zero (except lows: none is the answer).
 */
internal fun todayStats(events: List<EventEntity>, readings: List<GlucoseReading>, now: Instant, zone: ZoneId): Map<HomeStat, Double> {
    val midnight = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    val today = events.filter { it.timestampMillis >= midnight.toEpochMilli() && it.timestampMillis <= now.toEpochMilli() }
    val meals = today.filter { it.logType == LogEventType.CARB }
    fun sum(values: List<Double?>) = values.filterNotNull().takeIf { it.isNotEmpty() }?.sum()
    val todayReadings = readings.filter { it.timestamp >= midnight && it.timestamp <= now }
    val summary = todayReadings.takeIf { it.size >= 2 }?.let { InsightEngine.summary(it, now) }
    return buildMap {
        sum(meals.map { it.kcal })?.let { put(HomeStat.KCAL, it) }
        sum(meals.map { it.value })?.let { put(HomeStat.CARBS, it) }
        sum(meals.map { it.protein })?.let { put(HomeStat.PROTEIN, it) }
        sum(meals.map { it.fat })?.let { put(HomeStat.FAT, it) }
        sum(today.filter { it.logType == LogEventType.INSULIN }.map { it.value })?.let { put(HomeStat.RAPID, it) }
        sum(today.filter { it.logType == LogEventType.BASAL }.map { it.value })?.let { put(HomeStat.LONG, it) }
        summary?.let {
            put(HomeStat.TIR, it.inRange.roundToInt().toDouble())
            put(HomeStat.AVERAGE, it.meanMgDl.toDouble())
            put(HomeStat.LOWS, InsightEngine.lowEpisodes(todayReadings).size.toDouble())
        }
    }
}
