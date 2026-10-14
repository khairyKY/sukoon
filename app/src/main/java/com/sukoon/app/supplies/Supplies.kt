package com.sukoon.app.supplies

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Rapid (logged as INSULIN) or long-acting (BASAL) pens. */
enum class PenKind(val type: LogEventType) { RAPID(LogEventType.INSULIN), LONG(LogEventType.BASAL) }

/** A count of pens at [at]: [pens] unopened of [penUnits] each, and [openLeft] units in the one in use. */
data class Stock(val penUnits: Int, val pens: Int, val openLeft: Double, val at: Instant)

/** The insurance (or pharmacy) pickup: the next date, how often, and how many days before to remind. */
data class Pickup(val next: LocalDate? = null, val everyDays: Int = 30, val remindDays: Int = 3)

/**
 * Insulin left, from a count and the doses logged since. Every injection also costs a safety shot
 * (the pen leaflets' priming test: 2 u for Apidra and most pens, 3 u for Toujeo SoloStar).
 * ponytail: one 2 u figure for both; a per-pen setting if counts drift.
 */
object Supply {
    const val PRIMING = 2.0

    /** Units left now: the count, less every logged dose of [kind] (plus priming) after it. */
    fun remaining(stock: Stock, events: List<EventEntity>, kind: PenKind): Double =
        stock.pens * stock.penUnits + stock.openLeft -
            events.filter { it.logType == kind.type && Instant.ofEpochMilli(it.timestampMillis) > stock.at && (it.value ?: 0.0) > 0 }.sumOf { it.value!! + PRIMING }

    /** Units a day over the last 14 days of logged doses; null under 3 days of them. */
    fun perDay(events: List<EventEntity>, kind: PenKind, now: Instant, zone: ZoneId): Double? {
        val since = now.minus(Duration.ofDays(14))
        val doses = events.filter { it.logType == kind.type && (it.value ?: 0.0) > 0 && Instant.ofEpochMilli(it.timestampMillis).let { t -> t > since && t <= now } }
        val days = doses.map { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }.distinct().size
        if (days < 3) return null
        val first = doses.minOf { it.timestampMillis }.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val span = ChronoUnit.DAYS.between(first, now.atZone(zone).toLocalDate()).coerceAtLeast(1) + 1
        return doses.sumOf { it.value!! + PRIMING } / span
    }

    /** Whole days left at the usual rate; null without enough history. */
    fun daysLeft(remaining: Double, perDay: Double?): Int? = perDay?.takeIf { it > 0 }?.let { floor(remaining.coerceAtLeast(0.0) / it).toInt() }

    /** A new count after picking up [added] pens: what's in the open pen now carries over. */
    fun addPens(stock: Stock, remainingNow: Double, added: Int, now: Instant): Stock =
        stock.copy(pens = stock.pens + added, openLeft = (remainingNow - stock.pens * stock.penUnits).coerceIn(0.0, stock.penUnits.toDouble()), at = now)

    /** Opening a new pen: one fewer unopened, a full one in use (whatever was left in the old one is done). */
    fun openPen(stock: Stock, now: Instant): Stock = stock.copy(pens = (stock.pens - 1).coerceAtLeast(0), openLeft = stock.penUnits.toDouble(), at = now)

    /** Days until the pickup: 0 today, negative when it's passed; null without a date. */
    fun daysToPickup(p: Pickup, today: LocalDate): Long? = p.next?.let { ChronoUnit.DAYS.between(today, it) }

    /** Low enough to say so: this many days or fewer. */
    const val LOW_DAYS = 5
}

/** Stored counts and the pickup, as state the screens follow. */
class SuppliesStore(context: Context) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)

    private fun read(kind: PenKind): Stock? = prefs.getString("supply_${kind.name}", null)?.split(",")?.takeIf { it.size == 4 }?.let {
        runCatching { Stock(it[0].toInt(), it[1].toInt(), it[2].toDouble(), Instant.ofEpochMilli(it[3].toLong())) }.getOrNull()
    }

    private val _stock = MutableStateFlow(PenKind.entries.associateWith { read(it) })
    val stock: StateFlow<Map<PenKind, Stock?>> = _stock.asStateFlow()

    fun set(kind: PenKind, s: Stock?) {
        prefs.edit().apply { if (s == null) remove("supply_${kind.name}") else putString("supply_${kind.name}", "${s.penUnits},${s.pens},${s.openLeft},${s.at.toEpochMilli()}") }.apply()
        _stock.value = _stock.value + (kind to s)
    }

    private fun readPickup() = Pickup(
        next = prefs.getLong("pickup_next", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay),
        everyDays = prefs.getInt("pickup_every", 30),
        remindDays = prefs.getInt("pickup_remind", 3),
    )

    private val _pickup = MutableStateFlow(readPickup())
    val pickup: StateFlow<Pickup> = _pickup.asStateFlow()

    fun setPickup(p: Pickup) {
        prefs.edit().apply { if (p.next == null) remove("pickup_next") else putLong("pickup_next", p.next.toEpochDay()) }
            .putInt("pickup_every", p.everyDays).putInt("pickup_remind", p.remindDays).apply()
        _pickup.value = p
    }
}

/**
 * Once a day at 10:00: a reminder when the pickup is within its days (or passed), and when a kind
 * of insulin is down to [Supply.LOW_DAYS] days or fewer. One notification, both in it.
 */
class SuppliesReminder : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SukoonApp).container
        val done = goAsync()
        container.appScope.launch {
            try {
                schedule(context)
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val events = container.logbookRepository.eventsSince(now.minus(Duration.ofDays(120)).toEpochMilli()).first()
                val lines = mutableListOf<String>()
                container.supplies.stock.value.forEach { (kind, s) ->
                    s ?: return@forEach
                    val days = Supply.daysLeft(Supply.remaining(s, events, kind), Supply.perDay(events, kind, now, zone)) ?: return@forEach
                    if (days <= Supply.LOW_DAYS) lines += context.getString(if (kind == PenKind.LONG) R.string.supply_low_long else R.string.supply_low_rapid, days)
                }
                val p = container.supplies.pickup.value
                val to = Supply.daysToPickup(p, LocalDate.now(zone))
                if (to != null && to <= p.remindDays) lines.add(0, pickupLine(context, to, p.next!!))
                container.clinic.visit.value?.let { v ->
                    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(zone), v)
                    if (days in 0..2) lines.add(0, if (days == 0L) context.getString(R.string.clinic_visit_today) else context.resources.getQuantityString(R.plurals.clinic_visit_soon, days.toInt(), days.toInt()))
                }
                if (lines.isNotEmpty()) notify(context, lines)
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        private const val CHANNEL = "supplies"
        private const val ID = 3100

        fun pickupLine(context: Context, daysTo: Long, date: LocalDate): String = when {
            daysTo < 0 -> context.getString(R.string.pickup_overdue)
            daysTo == 0L -> context.getString(R.string.pickup_today)
            else -> context.getString(R.string.pickup_on, date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()))
        }

        /** The next 10:00. Safe to call any time: it replaces itself. */
        fun schedule(context: Context) {
            val now = ZonedDateTime.now()
            var at = now.with(LocalTime.of(10, 0)).withSecond(0).withNano(0)
            if (!at.isAfter(now)) at = at.plusDays(1)
            val pending = PendingIntent.getBroadcast(context, 0, Intent(context, SuppliesReminder::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val alarms = context.getSystemService(AlarmManager::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending)
            }
        }

        private fun notify(context: Context, lines: List<String>) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.supply_channel), NotificationManager.IMPORTANCE_DEFAULT))
            }
            val open = PendingIntent.getActivity(
                context, 31, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val body = lines.drop(1).joinToString("\n")
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_insulin)
                .setColor(0xFF3E7A63.toInt())
                .setContentTitle(lines.first())
                .apply { if (body.isNotEmpty()) setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)) }
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(ID, n) }
        }
    }
}
