package com.sukoon.app.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sukoon.app.BuildConfig
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Notices: a message from Sukoon's maker to everyone who has the app (a known problem, "please
 * update", a heads-up). No push server: every phone fetches one small file from Sukoon's GitHub
 * Pages site (web/notices.json, published on every push to main) when the app starts and then every
 * [CHECK_EVERY]. Each notice it hasn't shown before comes as a notification, and the newest one
 * still running sits on Now as a slim banner (opening into a sheet) until "Don't show again". docs/notices.md says how to send one.
 *
 * ponytail: polling a static file, so a notice reaches phones within [CHECK_EVERY], not instantly;
 * a push service (FCM) if that ever matters more than having no server and no Google SDK.
 */
class Notices(private val context: Context, private val scope: CoroutineScope) {

    /** Set by AppContainer: a release notice arrived, so the updater checks now and announces it. */
    var releaseNoticed: () -> Unit = {}

    data class Notice(
        val id: String,
        val title: String,
        val body: String,
        val titleAr: String? = null,
        val bodyAr: String? = null,
        /** An https page opened from the notification or the card (else the app opens). */
        val link: String? = null,
        /** The last day it is shown, on the phone's calendar; null = no end. */
        val until: LocalDate? = null,
        /** The day it was sent ("From Sukoon · today"); null when the file doesn't say. */
        val date: LocalDate? = null,
        val kind: Kind = Kind.NOTICE,
        /** A release notice ("below" a version, linking to the releases): the updater says it, with its Update button and bar. */
        val release: Boolean = false,
    ) {
        /**
         * NOTICE: a notification and the banner. MUST_READ: also full screen when Sukoon is in front,
         * until "Got it". UPDATE: full screen, no way to close it, on phones older than its "below"
         * version (only those get it): "Update Sukoon". Readings and alarms keep working underneath.
         */
        enum class Kind { NOTICE, MUST_READ, UPDATE }

        /** Still running on [today]: shown up to and including [until]. */
        fun liveOn(today: LocalDate) = until == null || !today.isAfter(until)

        fun titleIn(arabic: Boolean) = titleAr?.takeIf { arabic } ?: title

        fun bodyIn(arabic: Boolean) = bodyAr?.takeIf { arabic } ?: body
    }

    private val prefs = context.getSharedPreferences("sukoon_notices", Context.MODE_PRIVATE)

    private val _current = MutableStateFlow(newest(cached(), dismissed(), LocalDate.now()))

    /** The newest notice still running that this phone hasn't closed: the card on Now. */
    val current: StateFlow<Notice?> = _current.asStateFlow()

    private val _urgent = MutableStateFlow(urgent(cached(), acked(), LocalDate.now()))

    /** A must-read not yet acknowledged here, or a forced update: shown full screen over everything. */
    val urgent: StateFlow<Notice?> = _urgent.asStateFlow()

    /** "Got it" on a must-read. */
    fun acknowledge(id: String) {
        prefs.edit().putStringSet(KEY_ACKED, acked() + id).apply()
        markRead(id)
        _urgent.value = urgent(cached(), acked(), LocalDate.now())
    }

    private fun acked(): Set<String> = prefs.getStringSet(KEY_ACKED, null).orEmpty()

    private val _read = MutableStateFlow(prefs.getStringSet(KEY_READ, null).orEmpty())

    /** Notices opened on this phone: the banner's unread dot goes once its id is here. */
    val read: StateFlow<Set<String>> = _read.asStateFlow()

    /** Opening the notice's sheet. */
    fun markRead(id: String) {
        if (id in _read.value) return
        _read.value = _read.value + id
        prefs.edit().putStringSet(KEY_READ, _read.value).apply()
    }

    /** "Don't show again": that notice stays closed on this phone. */
    fun dismiss(id: String) {
        prefs.edit().putStringSet(KEY_DISMISSED, dismissed() + id).apply()
        _current.value = newest(cached(), dismissed(), LocalDate.now())
    }

    fun start() {
        scope.launch {
            while (true) {
                runCatching { check() }.onFailure { Log.w(TAG, "Notice check failed", it) }
                delay(CHECK_EVERY.toMillis())
            }
        }
    }

    /**
     * Fetches the file, updates the card, and notifies what's new. The file is kept, so the card
     * survives a restart with no internet; nothing is marked shown while notifications are off.
     */
    suspend fun check() {
        val text = withContext(Dispatchers.IO) { get(URL_NOTICES) }
        val all = parse(text)
        prefs.edit().putString(KEY_FILE, text).apply()
        _current.value = newest(all, dismissed(), LocalDate.now())
        _urgent.value = urgent(all, acked(), LocalDate.now())

        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        val seen = prefs.getStringSet(KEY_SEEN, null).orEmpty()
        val today = LocalDate.now()
        val fresh = toShow(all, seen, today)
        if (fresh.isEmpty()) return
        // A release notice on a self-updating build: the updater announces it (Update button, download bar) instead.
        fresh.forEach { if (it.release && com.sukoon.app.BuildConfig.SELF_UPDATE) releaseNoticed() else notify(manager, it) }
        prefs.edit().putStringSet(KEY_SEEN, seen + all.filter { it.liveOn(today) }.map { it.id }).apply()
    }

    private fun cached(): List<Notice> = prefs.getString(KEY_FILE, null)?.let { runCatching { parse(it) }.getOrNull() }.orEmpty()

    private fun dismissed(): Set<String> = prefs.getStringSet(KEY_DISMISSED, null).orEmpty()

    private fun notify(manager: NotificationManager, notice: Notice) {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notice_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val arabic = context.resources.configuration.locales[0].language == "ar"
        val open = notice.link?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) } ?: Intent(context, MainActivity::class.java)
        val tap = PendingIntent.getActivity(context, notice.id.hashCode(), open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val body = notice.bodyIn(arabic)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sukoon)
            .setContentTitle(notice.titleIn(arabic))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_BASE + (notice.id.hashCode() and 0xFFF), notification)
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Sukoon/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode !in 200..299) error("Notices answered ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "Notices"
        private const val URL_NOTICES = "https://khairyky.github.io/sukoon/notices.json"
        private const val CHANNEL = "notices"
        private const val KEY_SEEN = "seen"
        private const val KEY_DISMISSED = "dismissed"
        private const val KEY_FILE = "file"
        private const val KEY_READ = "read"
        private const val KEY_ACKED = "acked"
        private const val NOTIFICATION_BASE = 5000
        /** How often a running Sukoon looks for notices (and a new release, which it asks GitHub for at most daily). */
        /** Often enough that an announcement lands within about 15 minutes (GitHub Pages caches 10). */
        val CHECK_EVERY: Duration = Duration.ofMinutes(15)

        /**
         * The file: a JSON array of notices, newest last. An entry without an id, title or body is
         * skipped, and so is a link that isn't https (a notice can't open anything else) or a date that
         * doesn't parse.
         */
        internal fun parse(json: String): List<Notice> {
            val rows = JSONArray(json)
            return (0 until rows.length()).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                val id = row.text("id") ?: return@mapNotNull null
                // "below": only for phones older than that version (a release notice skips the updated ones).
                row.text("below")?.let { if (!Updates.newer(it, com.sukoon.app.BuildConfig.VERSION_NAME)) return@mapNotNull null }
                Notice(
                    id = id,
                    title = row.text("title") ?: return@mapNotNull null,
                    body = row.text("body") ?: return@mapNotNull null,
                    titleAr = row.text("title_ar"),
                    bodyAr = row.text("body_ar"),
                    link = row.text("link")?.takeIf { it.startsWith("https://") },
                    until = row.text("until")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    date = row.text("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    release = row.text("below") != null && row.text("link")?.contains("/releases") == true,
                    kind = when (row.text("kind")) {
                        "must_read" -> Notice.Kind.MUST_READ
                        // A forced update needs "below": without it there's no telling who is behind.
                        "update" -> if (row.text("below") != null) Notice.Kind.UPDATE else Notice.Kind.NOTICE
                        else -> Notice.Kind.NOTICE
                    },
                )
            }
        }

        private fun JSONObject.text(name: String): String? = optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }

        /** The full-screen one: a forced update first, else the newest must-read not acknowledged. */
        internal fun urgent(all: List<Notice>, acked: Set<String>, today: LocalDate): Notice? =
            all.lastOrNull { it.kind == Notice.Kind.UPDATE && it.liveOn(today) }
                ?: all.lastOrNull { it.kind == Notice.Kind.MUST_READ && it.id !in acked && it.liveOn(today) }

        /** The card's notice: the last one in the file that is still running and wasn't closed here. */
        internal fun newest(all: List<Notice>, dismissed: Set<String>, today: LocalDate): Notice? =
            all.lastOrNull { it.id !in dismissed && it.liveOn(today) }

        /**
         * Which of [all] (in the file's order, newest last) this phone notifies now. [seen]: the ids it
         * has already notified, so a notice never comes twice. [today]: the phone's date
         * ([Notice.liveOn] says whether a notice is still running).
         *
         * At most one at a time, the newest running one not yet seen: a phone that was off for a week,
         * or a new install, gets one notification, not a pile. [check] marks the older running ones
         * seen with it, so they don't trickle in on later checks; the card on Now still shows the newest.
         */
        internal fun toShow(all: List<Notice>, seen: Set<String>, today: LocalDate): List<Notice> =
            listOfNotNull(all.lastOrNull { it.id !in seen && it.liveOn(today) })
    }
}
