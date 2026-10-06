package com.sukoon.app.alarms

import android.content.Context
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the alarms actually did: each one that went off (and whether it sounded, took the screen and
 * posted its notification), each answer, and each one that ended. You → Alarms shows it, so "did it
 * go off?" has an answer on the phone itself.
 */
class AlarmLog(context: Context) {

    enum class Kind { FIRED, TREATED, SNOOZED, DISMISSED, RESOLVED }

    data class Entry(
        val at: Instant,
        val type: AlarmType,
        val kind: Kind,
        val mgDl: Int? = null,
        /** Snooze length for [Kind.SNOOZED] / [Kind.TREATED]. */
        val minutes: Int = 0,
        // For FIRED: what got through to the person.
        val sounded: Boolean = true,
        val screen: Boolean = true,
        val posted: Boolean = true,
        val test: Boolean = false,
        /** Someone this phone follows (their name); null = this phone's own alarm. */
        val who: String? = null,
    )

    private val prefs = context.getSharedPreferences("sukoon_alarm_log", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(prefs.getString(KEY, null).orEmpty().lines().mapNotNull(::decode))

    /** Oldest first, at most [KEEP]. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Synchronized
    fun add(entry: Entry) {
        _entries.value = (_entries.value + entry).takeLast(KEEP)
        prefs.edit().putString(KEY, _entries.value.joinToString("\n", transform = ::encode)).apply()
    }

    internal companion object {
        private const val KEY = "entries"
        const val KEEP = 60

        fun encode(e: Entry) = listOf(
            e.at.toEpochMilli(), e.type.name, e.kind.name, e.mgDl ?: "", e.minutes,
            flags(e.sounded, e.screen, e.posted, e.test), e.who.orEmpty().replace('|', '/').replace('\n', ' '),
        ).joinToString("|")

        /** null for anything unreadable (an older format, a hand-edited line): the log never breaks the app. */
        fun decode(line: String): Entry? = runCatching {
            val p = line.split('|')
            Entry(
                at = Instant.ofEpochMilli(p[0].toLong()),
                type = AlarmType.valueOf(p[1]),
                kind = Kind.valueOf(p[2]),
                mgDl = p[3].toIntOrNull(),
                minutes = p[4].toInt(),
                sounded = 's' in p[5], screen = 'v' in p[5], posted = 'n' in p[5], test = 't' in p[5],
                who = p[6].ifBlank { null },
            )
        }.getOrNull()

        private fun flags(sounded: Boolean, screen: Boolean, posted: Boolean, test: Boolean) =
            (if (sounded) "s" else "") + (if (screen) "v" else "") + (if (posted) "n" else "") + (if (test) "t" else "")
    }
}
