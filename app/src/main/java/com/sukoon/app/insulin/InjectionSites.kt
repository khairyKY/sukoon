package com.sukoon.app.insulin

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import java.time.Duration
import java.time.Instant

/** The four places insulin goes (belly, side of the thigh, back of the upper arm, upper outer buttock). */
enum class InjectionRegion { ABDOMEN, THIGH, ARM, BUTTOCK }

/** A region and its side: what's logged with a dose. Your own left and right. */
enum class InjectionSite(val region: InjectionRegion, val left: Boolean) {
    ABDOMEN_LEFT(InjectionRegion.ABDOMEN, true),
    ABDOMEN_RIGHT(InjectionRegion.ABDOMEN, false),
    THIGH_LEFT(InjectionRegion.THIGH, true),
    THIGH_RIGHT(InjectionRegion.THIGH, false),
    ARM_LEFT(InjectionRegion.ARM, true),
    ARM_RIGHT(InjectionRegion.ARM, false),
    BUTTOCK_LEFT(InjectionRegion.BUTTOCK, true),
    BUTTOCK_RIGHT(InjectionRegion.BUTTOCK, false),
    ;

    /** The same region on the other side. */
    val other: InjectionSite get() = entries.first { it.region == region && it.left != left }
}

/** Where a dose went, if it was logged (tolerant of an unknown name). */
val EventEntity.injectionSite: InjectionSite? get() = site?.let { name -> InjectionSite.entries.firstOrNull { it.name == name } }

/**
 * Rotation, from the doses logged with a site. Moving around matters: the same spot again and
 * again can form lumps (lipohypertrophy) that take up insulin unevenly (Frid et al., Mayo Clin Proc 2016).
 * Pure and clock-injected.
 */
object InjectionSites {

    /** Rapid and long-acting rotate separately: they usually go to different places. */
    private fun dosesOf(history: List<EventEntity>, type: LogEventType) =
        history.filter { it.logType == type && it.injectionSite != null }.sortedBy { it.timestampMillis }

    /**
     * Where [type] should go next: of the spots used for it, the one rested longest (never the last
     * one). With only one spot used so far, the same place on the other side. Null with no history.
     */
    fun next(history: List<EventEntity>, type: LogEventType): InjectionSite? {
        val doses = dosesOf(history, type)
        val last = doses.lastOrNull()?.injectionSite ?: return null
        val lastUse = doses.mapNotNull { e -> e.injectionSite?.let { it to e.timestampMillis } }.toMap() // later doses overwrite: the last use of each
        val used = lastUse.keys - last
        if (used.isEmpty()) return last.other
        return used.minBy { lastUse.getValue(it) }
    }

    /** When [site] was last used for [type], if it was. */
    fun lastUsed(history: List<EventEntity>, type: LogEventType, site: InjectionSite): Instant? =
        dosesOf(history, type).lastOrNull { it.injectionSite == site }?.let { Instant.ofEpochMilli(it.timestampMillis) }

    /** Doses per spot for [type] since [since]. */
    fun counts(history: List<EventEntity>, type: LogEventType, since: Instant): Map<InjectionSite, Int> =
        dosesOf(history, type).filter { it.timestampMillis >= since.toEpochMilli() }.groupingBy { it.injectionSite!! }.eachCount()

    data class Crowded(val site: InjectionSite, val count: Int, val total: Int, val type: LogEventType)

    /** One spot taking half or more of 10+ doses of a kind over [days]: time to spread them out. */
    fun crowded(history: List<EventEntity>, now: Instant, days: Long = 14): Crowded? =
        listOf(LogEventType.INSULIN, LogEventType.BASAL).firstNotNullOfOrNull { type ->
            val counts = counts(history, type, now.minus(Duration.ofDays(days)))
            val total = counts.values.sum()
            val top = counts.maxByOrNull { it.value } ?: return@firstNotNullOfOrNull null
            if (total >= 10 && top.value * 2 >= total) Crowded(top.key, top.value, total, type) else null
        }
}
