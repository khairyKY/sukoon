package com.sukoon.app.sharing

import android.content.Context
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.net.URLEncoder
import java.security.SecureRandom
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class Person(val id: String, val name: String)

/** Where a followed person's readings come from. */
enum class FollowVia { SUKOON, LIBRE_LINK_UP, DEXCOM }

/** [alarms]: the alarms they let followers get (all, for LibreLinkUp, Dexcom and people who haven't chosen). */
data class Followed(
    val id: String,
    val name: String,
    val latest: GlucoseReading?,
    val via: FollowVia = FollowVia.SUKOON,
    val alarms: Set<AlarmType> = Sharing.ALL_ALARMS,
)

/**
 * Followers (A6) on Supabase. Sharing: this phone uploads its real-sensor readings, and anyone who
 * redeems one of its invite codes can read them. Following: reading someone else's. Read-only both
 * ways; either side can end a follow at any time. Demo readings never leave the phone.
 */
class Sharing(
    context: Context,
    val supabase: Supabase,
    private val glucose: GlucoseRepository,
    private val scope: CoroutineScope,
    /** People followed through LibreLinkUp or Dexcom Share join the Sukoon follows everywhere (Home, widget, alarms). */
    val libreLinkUp: LibreLinkUp,
    val dexcom: DexcomShare,
) {

    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    var shareOn: Boolean
        get() = prefs.getBoolean(KEY_SHARE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHARE, value).apply()

    private val me: String get() = supabase.session.value?.userId ?: throw SupabaseException(401, "Not signed in")

    /** The alarms followers' phones sound for this person (You → Sharing). Urgent low always. */
    var sharedAlarms: Set<AlarmType>
        get() = prefs.getStringSet(KEY_SHARED_ALARMS, null)?.let(::alarmsFrom) ?: ALL_ALARMS
        set(value) = prefs.edit().putStringSet(KEY_SHARED_ALARMS, alarmsFrom(value.map { it.name }).map { it.name }.toSet()).apply()

    /** Puts [sharedAlarms] on this account's profile, where followers' phones read it. */
    suspend fun publishSharedAlarms() {
        val body = JSONObject().put("id", me).put("shared_alarms", JSONArray(sharedAlarms.map { it.name }))
        supabase.post("profiles?on_conflict=id", body.toString(), prefer = "resolution=merge-duplicates,return=minimal")
    }

    /**
     * Which alarms each of [ids] shares. Anyone who hasn't chosen, or a project where the 2026-10-12
     * migration hasn't run yet (the query fails), shares them all: never fewer alerts by accident.
     */
    private suspend fun sharedAlarmsOf(ids: List<String>): Map<String, Set<AlarmType>> = runCatching {
        val rows = JSONArray(supabase.get("profiles?id=in.(${ids.joinToString(",")})&select=id,shared_alarms"))
        (0 until rows.length()).associate { i ->
            val row = rows.getJSONObject(i)
            row.getString("id") to (row.optJSONArray("shared_alarms")?.let { a -> alarmsFrom((0 until a.length()).map(a::getString)) } ?: ALL_ALARMS)
        }
    }.getOrDefault(emptyMap())

    fun start() {
        val retry = flow {
            while (true) {
                emit(Unit)
                delay(TimeUnit.MINUTES.toMillis(5))
            }
        }
        // On every sign-in (and start): followers' phones learn which alarms to sound.
        scope.launch { supabase.session.filterNotNull().collect { runCatching { publishSharedAlarms() } } }
        scope.launch {
            // New reading → upload; plus a 5-minute retry tick. Failures wait for the next one.
            merge(glucose.latestReading.map { }, retry).conflate().collect { runCatching { upload() } }
        }
    }

    /** Uploads saved real-sensor readings newer than this account's cursor (the first time: 2 days back). */
    suspend fun upload(): Int {
        val session = supabase.session.value ?: return 0
        if (!shareOn) return 0
        val cursorKey = KEY_CURSOR + session.userId
        val since = maxOf(prefs.getLong(cursorKey, 0L), System.currentTimeMillis() - BACKFILL_MS)
        val readings = glucose.readingsSince(since + 1).first().filter { it.source != SourceKind.SIMULATED }.take(MAX_BATCH)
        if (readings.isEmpty()) return 0
        supabase.post("readings", JSONArray(readings.map(::readingJson)).toString(), prefer = "resolution=ignore-duplicates,return=minimal")
        prefs.edit().putLong(cursorKey, readings.last().timestamp.toEpochMilli()).apply()
        return readings.size
    }

    /** A one-time code, valid for 24 h, that lets one person follow you. */
    suspend fun createInvite(): String {
        val code = (1..InviteCode.LENGTH).map { InviteCode.ALPHABET[random.nextInt(InviteCode.ALPHABET.length)] }.joinToString("")
        supabase.post("invites", JSONObject().put("code", code).toString(), prefer = "return=minimal")
        return code
    }

    /** My invites nobody has used yet, newest first, with when each runs out. */
    suspend fun openInvites(): List<Pair<String, Instant>> {
        val now = URLEncoder.encode(Instant.now().toString(), "UTF-8")
        val rows = JSONArray(supabase.get("invites?owner=eq.$me&used_by=is.null&expires_at=gt.$now&order=created_at.desc&select=code,expires_at"))
        return (0 until rows.length()).map { rows.getJSONObject(it).let { r -> r.getString("code") to java.time.OffsetDateTime.parse(r.getString("expires_at")).toInstant() } }
    }

    suspend fun cancelInvite(code: String) {
        supabase.delete("invites?code=eq.$code&owner=eq.$me")
    }

    /** Follows the owner of [code]; returns their name. */
    suspend fun redeem(code: String): String {
        val owner = supabase.rpc("redeem_invite", JSONObject().put("p_code", code)).trim().trim('"')
        return names(listOf(owner))[owner].orEmpty()
    }

    suspend fun followers(): List<Person> {
        val ids = column(supabase.get("follows?owner=eq.$me&select=follower"), "follower")
        val names = names(ids)
        return ids.map { Person(it, names[it].orEmpty()) }
    }

    /** Everyone this phone follows: through Sukoon, LibreLinkUp and Dexcom Share, each when signed in. */
    suspend fun following(): List<Followed> {
        val mine = if (supabase.session.value == null) emptyList() else {
            val ids = column(supabase.get("follows?follower=eq.$me&select=owner"), "owner")
            val names = names(ids)
            val alarms = sharedAlarmsOf(ids)
            ids.map { Followed(it, names[it].orEmpty(), latestOf(it), alarms = alarms[it] ?: ALL_ALARMS) }
        }
        return mine + runCatching { libreLinkUp.followed() }.getOrDefault(emptyList()) + runCatching { dexcom.followed() }.getOrDefault(emptyList())
    }

    suspend fun latestOf(userId: String): GlucoseReading? =
        parseReadings(supabase.get("readings?user_id=eq.$userId&order=ts.desc&limit=1&select=ts,mg_dl,trend")).firstOrNull()

    /** Oldest first. The server returns at most 1000 rows per request, so this pages through. */
    suspend fun readingsOf(userId: String, sinceMillis: Long): List<GlucoseReading> {
        if (LibreLinkUp.owns(userId)) return libreLinkUp.readingsOf(userId, sinceMillis)
        if (DexcomShare.owns(userId)) return dexcom.readingsOf(sinceMillis)
        val since = URLEncoder.encode(Instant.ofEpochMilli(sinceMillis).toString(), "UTF-8")
        val out = mutableListOf<GlucoseReading>()
        while (true) {
            val page = parseReadings(supabase.get("readings?user_id=eq.$userId&ts=gte.$since&order=ts.asc&select=ts,mg_dl,trend&limit=$PAGE&offset=${out.size}"))
            out += page
            if (page.size < PAGE) return out
        }
    }

    suspend fun stopFollowing(owner: String) {
        supabase.delete("follows?owner=eq.$owner&follower=eq.$me")
    }

    suspend fun removeFollower(follower: String) {
        supabase.delete("follows?owner=eq.$me&follower=eq.$follower")
    }

    suspend fun myName(): String = names(listOf(me))[me].orEmpty()

    suspend fun setName(name: String) {
        val body = JSONObject().put("id", me).put("display_name", name.trim().take(60))
        supabase.post("profiles?on_conflict=id", body.toString(), prefer = "resolution=merge-duplicates,return=minimal")
    }

    private suspend fun names(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val rows = JSONArray(supabase.get("profiles?id=in.(${ids.joinToString(",")})&select=id,display_name"))
        return (0 until rows.length()).associate { rows.getJSONObject(it).let { row -> row.getString("id") to row.optString("display_name") } }
    }

    companion object {
        private const val KEY_SHARE = "share_readings_on"
        private const val KEY_CURSOR = "share_cursor_"
        private const val KEY_SHARED_ALARMS = "shared_alarms"

        val ALL_ALARMS: Set<AlarmType> = AlarmType.entries.toSet()

        /** Alarm names to types: unknown names are dropped, and urgent low is always in. */
        fun alarmsFrom(names: Collection<String>): Set<AlarmType> =
            names.mapNotNull { n -> AlarmType.entries.firstOrNull { it.name == n } }.toSet() + AlarmType.URGENT_LOW
        private val BACKFILL_MS = TimeUnit.DAYS.toMillis(2)
        private const val MAX_BATCH = 1000
        private const val PAGE = 1000

        /** The follower page (web/, on GitHub Pages): follow from any browser, iPhone included. */
        const val FOLLOW_PAGE = "https://khairyky.github.io/sukoon/"


        fun trendCode(trend: TrendDirection) = when (trend) {
            TrendDirection.FALLING_FAST -> -2
            TrendDirection.FALLING -> -1
            TrendDirection.STEADY -> 0
            TrendDirection.RISING -> 1
            TrendDirection.RISING_FAST -> 2
        }

        fun trendFrom(code: Int) = when {
            code <= -2 -> TrendDirection.FALLING_FAST
            code == -1 -> TrendDirection.FALLING
            code == 0 -> TrendDirection.STEADY
            code == 1 -> TrendDirection.RISING
            else -> TrendDirection.RISING_FAST
        }

        fun readingJson(r: GlucoseReading): JSONObject = JSONObject()
            .put("ts", r.timestamp.toString())
            .put("mg_dl", r.glucoseMgDl)
            .put("trend", trendCode(r.trend))

        /** Postgres sends timestamptz with an offset ("+00:00"), which Instant.parse doesn't take on older Androids. */
        fun parseReadings(json: String): List<GlucoseReading> {
            val rows = JSONArray(json)
            return (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                GlucoseReading(
                    timestamp = OffsetDateTime.parse(row.getString("ts")).toInstant(),
                    glucoseMgDl = row.getInt("mg_dl"),
                    trend = trendFrom(row.optInt("trend")),
                    source = SourceKind.LIBRE_BLE,
                )
            }
        }

        private fun column(json: String, name: String): List<String> {
            val rows = JSONArray(json)
            return (0 until rows.length()).map { rows.getJSONObject(it).getString(name) }
        }
    }
}
