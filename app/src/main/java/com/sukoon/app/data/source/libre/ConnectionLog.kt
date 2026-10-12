package com.sukoon.app.data.source.libre

import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * The sensor link's history (You → Sensor → Connection): each time it came up, and each time it went
 * down after being up, with Android's reason. Failed attempts to connect aren't drops and aren't kept.
 * A small text file on the phone, newest [MAX] lines; nothing leaves the phone.
 */
class ConnectionLog(private val file: File) {

    enum class Kind { UP, DOWN }

    data class Event(val at: Instant, val kind: Kind, val status: Int = 0, val connectedSeconds: Long = 0)

    /** A drop: when, how long the link had lasted, Android's status, and how soon it came back (null: not yet). */
    data class Drop(val at: Instant, val lastedSeconds: Long, val status: Int, val backAfterSeconds: Long?)

    @Synchronized
    fun add(event: Event) {
        runCatching {
            val lines = if (file.exists()) file.readLines() else emptyList()
            file.writeText((lines + format(event)).takeLast(MAX).joinToString("\n", postfix = "\n"))
        }
    }

    @Synchronized
    fun events(): List<Event> = runCatching { if (file.exists()) file.readLines().mapNotNull(::parse) else emptyList() }.getOrDefault(emptyList())

    fun dropsSince(since: Instant): List<Drop> = drops(events()).filter { it.at >= since }

    companion object {
        private const val MAX = 400

        /** Sukoon's own watchdog closed a link that had stopped sending. */
        const val STATUS_WATCHDOG = -1

        internal fun format(e: Event) = "${e.at.toEpochMilli()}\t${e.kind}\t${e.status}\t${e.connectedSeconds}"

        internal fun parse(line: String): Event? {
            val p = line.split('\t')
            if (p.size < 4) return null
            return runCatching { Event(Instant.ofEpochMilli(p[0].toLong()), Kind.valueOf(p[1]), p[2].toInt(), p[3].toLong()) }.getOrNull()
        }

        /** Each DOWN, with the next UP after it as when it came back. Newest first. */
        fun drops(events: List<Event>): List<Drop> {
            val sorted = events.sortedBy { it.at }
            return sorted.mapIndexedNotNull { i, e ->
                if (e.kind != Kind.DOWN) return@mapIndexedNotNull null
                val back = sorted.drop(i + 1).firstOrNull { it.kind == Kind.UP }
                Drop(e.at, e.connectedSeconds, e.status, back?.let { Duration.between(e.at, it.at).seconds })
            }.reversed()
        }
    }
}
