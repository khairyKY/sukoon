package com.sukoon.app.meter

import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The Bluetooth Glucose Profile (Bluetooth SIG GLS 1.0): what a blood glucose meter such as the
 * CareSens S Fit BT speaks. Pure byte work, so it's tested without a meter.
 */
object GlucoseProfile {
    private fun uuid16(short: Int): UUID = UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", short))

    val SERVICE: UUID = uuid16(0x1808)
    val MEASUREMENT: UUID = uuid16(0x2A18)
    val CONTEXT: UUID = uuid16(0x2A34)
    val RACP: UUID = uuid16(0x2A52)
    val CCCD: UUID = uuid16(0x2902)

    /** Before / after a meal, fasting, bedtime: the meter's own flag on a test (GLS "Meal" field). */
    enum class Meal { BEFORE, AFTER, FASTING, CASUAL, BEDTIME }

    /** One stored test: [at] is the meter's clock (it has no time zone), [mgDl] the result. */
    data class Measurement(val sequence: Int, val at: LocalDateTime, val mgDl: Int?, val hasContext: Boolean)

    data class Context(val sequence: Int, val meal: Meal?)

    /** Record Access Control Point: ask for every stored test, those after [sequence], or how many there are. */
    fun requestAll() = byteArrayOf(0x01, 0x01)
    fun requestAfter(sequence: Int) = byteArrayOf(0x01, 0x03, 0x01, (sequence and 0xFF).toByte(), ((sequence shr 8) and 0xFF).toByte())
    fun requestCount() = byteArrayOf(0x04, 0x01)

    sealed interface RacpReply {
        data class Count(val records: Int) : RacpReply
        /** The request finished; [ok] false with the meter's code, except "no records" (0x06), which is fine. */
        data class Done(val ok: Boolean, val code: Int) : RacpReply
    }

    fun racpReply(b: ByteArray): RacpReply? = when {
        b.size >= 4 && b[0].toInt() == 0x05 -> RacpReply.Count(u16(b, 2))
        b.size >= 4 && b[0].toInt() == 0x06 -> (b[3].toInt() and 0xFF).let { RacpReply.Done(it == 0x01 || it == 0x06, it) }
        else -> null
    }

    fun measurement(b: ByteArray): Measurement? {
        if (b.size < 10) return null
        val flags = b[0].toInt()
        val sequence = u16(b, 1)
        var i = 3
        var at = LocalDateTime.of(u16(b, i), b[i + 2].toInt().coerceIn(1, 12), b[i + 3].toInt().coerceIn(1, 31), b[i + 4].toInt() % 24, b[i + 5].toInt() % 60, b[i + 6].toInt() % 60)
        i += 7
        if (flags and 0x01 != 0) { // time offset, minutes
            if (b.size < i + 2) return null
            at = at.plusMinutes(s16(b, i).toLong())
            i += 2
        }
        var mgDl: Int? = null
        if (flags and 0x02 != 0) { // concentration + type/location
            if (b.size < i + 3) return null
            val value = sfloat(b, i)
            mgDl = if (flags and 0x04 != 0) (value * 1000 * 18.0182).roundToInt() // mol/L
            else (value * 100_000).roundToInt() // kg/L
            i += 3
        }
        return Measurement(sequence, at, mgDl?.takeIf { it in 10..1000 }, flags and 0x10 != 0)
    }

    fun context(b: ByteArray): Context? {
        if (b.size < 3) return null
        val flags = b[0].toInt()
        val sequence = u16(b, 1)
        var i = 3
        if (flags and 0x80 != 0) i += 1 // extended flags
        if (flags and 0x01 != 0) i += 3 // carbohydrate id + SFLOAT
        val meal = if (flags and 0x02 != 0 && b.size > i) when (b[i].toInt()) {
            1 -> Meal.BEFORE
            2 -> Meal.AFTER
            3 -> Meal.FASTING
            4 -> Meal.CASUAL
            5 -> Meal.BEDTIME
            else -> null
        } else null
        return Context(sequence, meal)
    }

    /** IEEE 11073 16-bit float: 4-bit signed exponent, 12-bit signed mantissa. */
    internal fun sfloat(b: ByteArray, i: Int): Double {
        val raw = u16(b, i)
        var mantissa = raw and 0x0FFF
        var exponent = raw shr 12
        if (mantissa >= 0x0800) mantissa -= 0x1000
        if (exponent >= 0x8) exponent -= 0x10
        return mantissa * 10.0.pow(exponent)
    }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun s16(b: ByteArray, i: Int) = u16(b, i).let { if (it >= 0x8000) it - 0x10000 else it }
}
