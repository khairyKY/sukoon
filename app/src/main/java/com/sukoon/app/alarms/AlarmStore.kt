package com.sukoon.app.alarms

import android.content.Context
import com.sukoon.app.emergency.EscalationPhase
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * The alarms' memory across a restart (Android stopping Sukoon, an update): which alarms are on and
 * since when, the last alert, snoozes, answers, "treated at", and where an emergency stands. Without
 * it a restart forgot snoozes (alarms back sooner), the 15-minute countdown after a low, and that
 * contacts were texted (no "back to normal" text). Written only when something changed.
 */
class AlarmStore(context: Context) {

    data class Snapshot(val state: AlarmState, val treatedAt: Instant?, val phase: EscalationPhase)

    private val prefs = context.getSharedPreferences("sukoon_alarm_state", Context.MODE_PRIVATE)
    private var last: String? = null

    fun load(): Snapshot = prefs.getString(KEY, null)?.let { decode(it) } ?: Snapshot(AlarmState(), null, EscalationPhase.Idle)

    fun save(snapshot: Snapshot) {
        val text = encode(snapshot)
        if (text == last) return
        last = text
        prefs.edit().putString(KEY, text).apply()
    }

    internal companion object {
        private const val KEY = "snapshot"

        private fun times(map: Map<AlarmType, Instant>) = JSONObject().apply { map.forEach { (k, v) -> put(k.name, v.toEpochMilli()) } }

        private fun times(json: JSONObject?): Map<AlarmType, Instant> =
            json?.keys()?.asSequence()?.mapNotNull { k -> AlarmType.entries.firstOrNull { it.name == k }?.let { it to Instant.ofEpochMilli(json.getLong(k)) } }?.toMap().orEmpty()

        fun encode(s: Snapshot): String = JSONObject()
            .put("active", times(s.state.activeSince))
            .put("last", times(s.state.lastAlertAt))
            .put("snoozed", times(s.state.snoozedUntil))
            .put("acked", times(s.state.acknowledgedAt))
            .put("treated", s.treatedAt?.toEpochMilli() ?: JSONObject.NULL)
            .put(
                "phase",
                when (val p = s.phase) {
                    EscalationPhase.Idle -> JSONObject.NULL
                    is EscalationPhase.Countdown -> JSONObject().put("kind", "countdown").put("type", p.type.name).put("at", p.startedAt.toEpochMilli())
                    is EscalationPhase.Sent -> JSONObject().put("kind", "sent").put("type", p.type.name).put("at", p.at.toEpochMilli()).put("to", JSONArray(p.to))
                },
            )
            .toString()

        /** A countdown cut off by the restart comes back as Idle: if still unanswered, a fresh 60 s starts (with its screen and sound). */
        fun decode(text: String): Snapshot? = runCatching {
            val json = JSONObject(text)
            val phase = json.optJSONObject("phase")?.takeIf { it.optString("kind") == "sent" }?.let { p ->
                val to = p.optJSONArray("to") ?: JSONArray()
                EscalationPhase.Sent(AlarmType.valueOf(p.getString("type")), Instant.ofEpochMilli(p.getLong("at")), (0 until to.length()).map { to.getString(it) })
            } ?: EscalationPhase.Idle
            Snapshot(
                AlarmState(times(json.optJSONObject("active")), times(json.optJSONObject("last")), times(json.optJSONObject("snoozed")), times(json.optJSONObject("acked"))),
                json.optLong("treated", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                phase,
            )
        }.getOrNull()
    }
}
