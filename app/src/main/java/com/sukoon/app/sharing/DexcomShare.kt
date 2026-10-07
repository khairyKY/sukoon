package com.sukoon.app.sharing

import android.content.Context
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dexcom Share: a Dexcom wearer's readings (G6, G7, ONE) from Dexcom's servers, with that wearer's
 * Dexcom login and Share turned on in the Dexcom app. Every 5 minutes, a little behind the sensor.
 * Followed like a Sukoon follow, or used as this phone's glucose ([CloudSource]).
 *
 * The API is the one Dexcom's own apps use (as in xDrip+ and pydexcom), not a published one;
 * Dexcom can change it. ponytail: the password is kept in app-private prefs to sign in again when
 * the session ends, like LibreLinkUp's.
 */
class DexcomShare(context: Context) {

    enum class Region(val host: String) {
        US("https://share2.dexcom.com/ShareWebServices/Services"),
        OUTSIDE_US("https://shareous1.dexcom.com/ShareWebServices/Services"),
    }

    data class Account(val username: String, val region: Region, val accountId: String, val name: String)

    private val prefs = context.getSharedPreferences("sukoon_dexcom", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var session: String? = null
    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account?> = _account.asStateFlow()

    /** Signs in and checks readings come back; [name] is how they're shown here. */
    suspend fun signIn(username: String, password: String, region: Region, name: String): GlucoseReading? {
        lock.withLock {
            val accountId = unquote(post(region, "/General/AuthenticatePublisherAccount", credentials(password).put("accountName", username.trim())))
            prefs.edit().putString(KEY_USER, username.trim()).putString(KEY_PASSWORD, password).putString(KEY_REGION, region.name)
                .putString(KEY_ACCOUNT, accountId).putString(KEY_NAME, name.trim()).apply()
            _account.value = load()
            session = null
        }
        return readings(minutes = 30).lastOrNull()
    }

    fun signOut() {
        prefs.edit().clear().apply()
        session = null
        _account.value = null
    }

    /** The newest readings, oldest first (Dexcom keeps 24 hours here). */
    suspend fun readings(minutes: Int = 1440): List<GlucoseReading> {
        val account = _account.value ?: return emptyList()
        val path = "/Publisher/ReadPublisherLatestGlucoseValues?sessionId=%s&minutes=$minutes&maxCount=${minutes / 5 + 1}"
        return try {
            parseReadings(JSONArray(post(account.region, path.format(session()), JSONObject())))
        } catch (e: DexcomException) {
            if (!e.sessionEnded) throw e
            session = null // ended on Dexcom's side: sign in again once
            parseReadings(JSONArray(post(account.region, path.format(session()), JSONObject())))
        }
    }

    /** As a follow: id "dex:<account>". */
    suspend fun followed(): List<Followed> {
        val account = _account.value ?: return emptyList()
        return listOf(Followed(ID_PREFIX + account.accountId, account.name.ifBlank { account.username }, readings(minutes = 30).lastOrNull(), FollowVia.DEXCOM))
    }

    suspend fun readingsOf(sinceMillis: Long): List<GlucoseReading> {
        val minutes = ((System.currentTimeMillis() - sinceMillis) / 60_000).toInt().coerceIn(5, 1440)
        return readings(minutes).filter { it.timestamp.toEpochMilli() >= sinceMillis }
    }

    private suspend fun session(): String = lock.withLock {
        session ?: run {
            val account = _account.value ?: throw DexcomException("Not signed in to Dexcom", false)
            val password = prefs.getString(KEY_PASSWORD, null) ?: throw DexcomException("Sign in to Dexcom again", false)
            unquote(post(account.region, "/General/LoginPublisherAccountById", credentials(password).put("accountId", account.accountId)))
                .also { session = it }
        }
    }

    private fun credentials(password: String) = JSONObject().put("password", password).put("applicationId", APPLICATION_ID)

    private suspend fun post(region: Region, path: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val connection = URL(region.host + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw parseError(text)
            text
        } catch (e: DexcomException) {
            throw e
        } catch (e: Exception) {
            throw DexcomException(e.message ?: "Dexcom unreachable", false, unreachable = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun load(): Account? {
        val user = prefs.getString(KEY_USER, null) ?: return null
        return Account(
            user,
            Region.entries.firstOrNull { it.name == prefs.getString(KEY_REGION, null) } ?: Region.OUTSIDE_US,
            prefs.getString(KEY_ACCOUNT, "").orEmpty(),
            prefs.getString(KEY_NAME, "").orEmpty(),
        )
    }

    companion object {
        const val ID_PREFIX = "dex:"
        /** The id Dexcom's apps send; the API answers only to it. */
        private const val APPLICATION_ID = "d89443d2-327c-4a6f-89e5-496bbb0317db"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REGION = "region"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_NAME = "name"
        private val WHEN = Regex("""Date\((\d+)""")

        fun owns(id: String) = id.startsWith(ID_PREFIX)

        internal fun unquote(text: String) = text.trim().trim('"')

        internal fun trend(value: Any?): TrendDirection = when (value?.toString()) {
            "DoubleUp", "1" -> TrendDirection.RISING_FAST
            "SingleUp", "2" -> TrendDirection.RISING_FAST
            "FortyFiveUp", "3" -> TrendDirection.RISING
            "FortyFiveDown", "5" -> TrendDirection.FALLING
            "SingleDown", "6", "DoubleDown", "7" -> TrendDirection.FALLING_FAST
            else -> TrendDirection.STEADY
        }

        /** Dexcom's list, newest first, "WT": "Date(ms)" in UTC; returned oldest first. */
        internal fun parseReadings(rows: JSONArray): List<GlucoseReading> = (0 until rows.length()).mapNotNull { i ->
            val row = rows.getJSONObject(i)
            val ms = WHEN.find(row.optString("WT"))?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
            val value = row.optInt("Value", -1).takeIf { it in 20..600 } ?: return@mapNotNull null
            GlucoseReading(Instant.ofEpochMilli(ms), value, trend(row.opt("Trend")), SourceKind.DEXCOM_SHARE)
        }.distinctBy { it.timestamp }.sortedBy { it.timestamp }

        internal fun parseError(text: String): DexcomException {
            val code = runCatching { JSONObject(text).optString("Code") }.getOrDefault("")
            return when {
                code.startsWith("SessionNotValid") || code.startsWith("SessionIdNotFound") -> DexcomException(code, sessionEnded = true)
                code.contains("Password", ignoreCase = true) || code.contains("AccountNotFound") || code.contains("Authenticate") -> DexcomException(code, false, wrongLogin = true)
                else -> DexcomException(code.ifBlank { "Dexcom refused" }, false)
            }
        }
    }
}

class DexcomException(message: String, val sessionEnded: Boolean, val wrongLogin: Boolean = false, val unreachable: Boolean = false) : Exception(message)
