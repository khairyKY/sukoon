package com.sukoon.app.sharing

import android.content.Context
import android.util.Log
import com.sukoon.app.R
import com.sukoon.app.alarms.AlarmEngine
import com.sukoon.app.alarms.AlarmNotifier
import com.sukoon.app.alarms.AlarmState
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.platform.FollowService
import com.sukoon.app.ui.widget.arrow
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Alerts about the people this phone follows. Every minute their last 30 minutes of readings go
 * through the same [AlarmEngine] as your own (your alarm levels and sounds); the alerts carry
 * their name. Runs while signed in, following someone, and alerts are on — with a small ongoing
 * notification (their latest values) that keeps it alive in the background.
 *
 * ponytail: polls once a minute (a few small requests); Supabase Realtime would make it instant
 * if a minute ever matters.
 */
class FollowerWatch(
    private val context: Context,
    private val sharing: Sharing,
    private val settings: SettingsPrefs,
    private val notifier: AlarmNotifier,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val states = mutableMapOf<String, AlarmState>()
    private val _active = MutableStateFlow<Map<String, Set<AlarmType>>>(emptyMap())

    /** Each followed person's alarms going on right now: their alert screen closes itself once one is over. */
    val active: StateFlow<Map<String, Set<AlarmType>>> = _active.asStateFlow()

    private fun publish() {
        _active.value = states.mapValues { it.value.activeSince.keys }
    }

    var alertsOn: Boolean
        get() = prefs.getBoolean(KEY_ALERTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ALERTS, value).apply()
            refreshNow()
        }

    fun start() {
        scope.launch {
            while (true) {
                mutex.withLock { runCatching { checkLocked() }.onFailure { Log.w(TAG, "Follow check failed", it) } }
                delay(60_000)
            }
        }
    }

    /** After following/unfollowing or signing in/out: check now (also starts the service while the app is on screen). */
    fun refreshNow() {
        scope.launch { mutex.withLock { runCatching { checkLocked() }.onFailure { Log.w(TAG, "Follow check failed", it) } } }
    }

    /** A follower alert's button or swipe-away: silence it and, if [minutes] > 0, snooze it for that person. */
    suspend fun acknowledge(person: String, type: AlarmType, minutes: Int) {
        notifier.stopSound()
        mutex.withLock {
            val now = Instant.now()
            var state = AlarmEngine.acknowledge(states[person] ?: AlarmState(), type, now)
            if (minutes > 0) state = AlarmEngine.snooze(state, type, minutes, now)
            states[person] = state
        }
        if (minutes > 0) notifier.cancel(type, person)
    }

    private suspend fun checkLocked() {
        val following = if (sharing.supabase.session.value != null && alertsOn) sharing.following() else emptyList()
        if (following.isEmpty()) {
            states.forEach { (id, state) -> state.activeSince.keys.forEach { notifier.cancel(it, id) } }
            states.clear()
            publish()
            FollowService.stop(context)
            return
        }
        FollowService.start(context)
        val now = Instant.now()
        val alarmSettings = settings.alarmSettings
        for (person in following) {
            val recent = (sharing.readingsOf(person.id, now.minus(WINDOW).toEpochMilli()) + listOfNotNull(person.latest)).distinctBy { it.timestamp }
            val result = AlarmEngine.evaluate(recent, now, alarmSettings, states[person.id] ?: AlarmState())
            states[person.id] = result.state
            publish() // before showing: the alert screen checks it as it opens
            result.cleared.forEach { notifier.cancel(it, person.id) }
            result.fire.forEach { notifier.show(it, alarmSettings, who = person.name.ifBlank { "…" }, person = person.id) }
        }
        val followedIds = following.map { it.id }.toSet()
        (states.keys - followedIds).forEach { id -> states.remove(id)?.activeSince?.keys?.forEach { notifier.cancel(it, id) } }
        publish()
        FollowService.update(context, following.joinToString("\n") { line(it) })
    }

    private fun line(person: Followed): String {
        val name = person.name.ifBlank { "…" }
        val r = person.latest ?: return "$name · ${context.getString(R.string.sharing_no_readings)}"
        val minutes = Duration.between(r.timestamp, Instant.now()).toMinutes().coerceAtLeast(0)
        val age = if (minutes < 1) context.getString(R.string.graph_just_now) else context.getString(R.string.graph_min_ago, minutes.toInt())
        return "$name  ${String.format(Locale.getDefault(), "%d", r.glucoseMgDl)} ${r.trend.arrow} · $age"
    }

    private companion object {
        const val TAG = "FollowerWatch"
        const val KEY_ALERTS = "follow_alerts_on"
        val WINDOW: Duration = Duration.ofMinutes(30)
    }
}
