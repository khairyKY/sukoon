package com.sukoon.app.insulin

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import java.time.Instant

enum class FingerDigit { MIDDLE, RING, PINKY, INDEX, THUMB }

/**
 * A fingertip, by your own hand: what's logged with a finger-prick (in the event's `site`, next to
 * the insulin spots; the names never overlap). Declared in the order a fresh rotation suggests:
 * middle, ring and pinky first, alternating hands; index and thumb last, as they are the most used.
 */
enum class Finger(val digit: FingerDigit, val left: Boolean) {
    LEFT_MIDDLE(FingerDigit.MIDDLE, true),
    RIGHT_MIDDLE(FingerDigit.MIDDLE, false),
    LEFT_RING(FingerDigit.RING, true),
    RIGHT_RING(FingerDigit.RING, false),
    LEFT_PINKY(FingerDigit.PINKY, true),
    RIGHT_PINKY(FingerDigit.PINKY, false),
    LEFT_INDEX(FingerDigit.INDEX, true),
    RIGHT_INDEX(FingerDigit.INDEX, false),
    LEFT_THUMB(FingerDigit.THUMB, true),
    RIGHT_THUMB(FingerDigit.THUMB, false),
}

/** Which finger a finger-prick used, if it was logged (tolerant of an unknown name). */
val EventEntity.finger: Finger? get() = if (logType == LogEventType.FINGERSTICK) site?.let { name -> Finger.entries.firstOrNull { it.name == name } } else null

/** Rotation for finger-pricks: the same fingertip again and again gets sore and callused. Pure. */
object FingerSites {

    private fun pricks(history: List<EventEntity>) = history.filter { it.finger != null }.sortedBy { it.timestampMillis }

    /** The next fingertip: of the fingers but the thumbs, the one rested longest (never the last used). Null with no history. */
    fun next(history: List<EventEntity>): Finger? {
        val pricks = pricks(history)
        val last = pricks.lastOrNull()?.finger ?: return null
        val lastUse = pricks.mapNotNull { e -> e.finger?.let { it to e.timestampMillis } }.toMap()
        return Finger.entries.filter { it.digit != FingerDigit.THUMB && it != last }.minBy { lastUse[it] ?: Long.MIN_VALUE }
    }

    /** When [finger] was last pricked, if it was. */
    fun lastUsed(history: List<EventEntity>, finger: Finger): Instant? =
        pricks(history).lastOrNull { it.finger == finger }?.let { Instant.ofEpochMilli(it.timestampMillis) }
}
