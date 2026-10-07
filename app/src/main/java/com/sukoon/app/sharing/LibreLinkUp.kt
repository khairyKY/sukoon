package com.sukoon.app.sharing

import android.content.Context
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Why LibreLinkUp said no, worded by the UI. */
enum class LluProblem { WRONG_LOGIN, ACCEPT_TERMS, UNREACHABLE, OTHER }

class LluException(val problem: LluProblem, message: String) : Exception(message)

/**
 * LibreLinkUp, Abbott's follow service: anyone who shares from Abbott's own Libre app (Libre 2, 2+,
 * 3, 3+, on Android or iPhone) shows up here as a connection, about a minute behind. Sukoon follows
 * them like a Sukoon follow (Home, widget, alarms), and can use one connection as its own glucose
 * source ([LibreLinkUpSource]).
 *
 * The API is the one Abbott's LibreLinkUp app uses, not a published one (as in xDrip+ and
 * nightscout-librelink-up); Abbott can change it. Requests carry the app's product/version headers
 * and, since 4.12, the SHA-256 of the account id.
 *
 * ponytail: the LibreLinkUp password sits in app-private prefs to sign in again when the token
 * expires (like the Gemini key); Keystore-backed storage if Sukoon is ever distributed widely.
 */
class LibreLinkUp(context: Context) {

    data class Account(val email: String, val token: String, val expiresAtSeconds: Long, val accountId: String, val host: String)

    /** One person sharing with this LibreLinkUp account, with their newest reading. */
    data class Connection(val patientId: String, val name: String, val latest: GlucoseReading?)

    private val prefs = context.getSharedPreferences("sukoon_llu", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account?> = _account.asStateFlow()

    suspend fun signIn(email: String, password: String): List<Connection> {
        lock.withLock { login(email.trim(), password, HOST) }
        prefs.edit().putString(KEY_PASSWORD, password).apply()
        return connections()
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    /** The connection used as this phone's own glucose ([LibreLinkUpSource]); null = none chosen. */
    var ownPatientId: String?
        get() = prefs.getString(KEY_OWN, null)
        set(value) = prefs.edit().putString(KEY_OWN, value).apply()

    /** The connections, newest reading each. Empty when not signed in. */
    suspend fun connections(): List<Connection> {
        if (_account.value == null) return emptyList()
        return parseConnections(JSONObject(authorized("/llu/connections")))
    }

    /** As Sukoon follows: ids "llu:<patientId>". */
    suspend fun followed(): List<Followed> = connections().map { Followed(ID_PREFIX + it.patientId, it.name, it.latest, FollowVia.LIBRE_LINK_UP) }

    /** The last ~12 hours LibreLinkUp keeps for [id] (an [ID_PREFIX] id or a bare patient id), oldest first. */
    suspend fun readingsOf(id: String, sinceMillis: Long): List<GlucoseReading> =
        parseGraph(JSONObject(authorized("/llu/connections/${id.removePrefix(ID_PREFIX)}/graph"))).filter { it.timestamp.toEpochMilli() >= sinceMillis }

    // --- HTTP -------------------------------------------------------------------------------------

    private suspend fun login(email: String, password: String, host: String, redirects: Int = 0) {
        val json = JSONObject(call("POST", host, "/llu/auth/login", JSONObject().put("email", email).put("password", password).toString(), null))
        when (val result = parseLogin(json)) {
            is Login.Redirect -> {
                if (redirects > 1) throw LluException(LluProblem.OTHER, "LibreLinkUp kept redirecting")
                login(email, password, "https://api-${result.region}.libreview.io", redirects + 1)
            }
            is Login.Ok -> {
                val account = Account(email, result.token, result.expiresAtSeconds, sha256(result.userId), host)
                prefs.edit()
                    .putString(KEY_EMAIL, account.email).putString(KEY_TOKEN, account.token).putLong(KEY_EXPIRES, account.expiresAtSeconds)
                    .putString(KEY_ACCOUNT_ID, account.accountId).putString(KEY_HOST, account.host)
                    .apply()
                _account.value = account
            }
            is Login.Refused -> throw LluException(result.problem, result.message)
        }
    }

    /** A signed-in GET; signs in again once with the saved password when the token has run out. */
    private suspend fun authorized(path: String): String {
        var account = _account.value ?: throw LluException(LluProblem.OTHER, "Not signed in to LibreLinkUp")
        if (account.expiresAtSeconds - 60 < System.currentTimeMillis() / 1000) account = renew()
        return try {
            call("GET", account.host, path, null, account)
        } catch (e: LluException) {
            if (e.problem != LluProblem.WRONG_LOGIN) throw e
            call("GET", renew().host, path, null, _account.value)
        }
    }

    private suspend fun renew(): Account = lock.withLock {
        val email = prefs.getString(KEY_EMAIL, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        if (email == null || password == null) throw LluException(LluProblem.WRONG_LOGIN, "Sign in to LibreLinkUp again")
        login(email, password, prefs.getString(KEY_HOST, null) ?: HOST)
        _account.value!!
    }

    private suspend fun call(method: String, host: String, path: String, body: String?, account: Account?): String = withContext(Dispatchers.IO) {
        val connection = try {
            URL(host + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw LluException(LluProblem.UNREACHABLE, e.message ?: "unreachable")
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("product", "llu.android")
            connection.setRequestProperty("version", APP_VERSION)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("cache-control", "no-cache")
            if (account != null) {
                connection.setRequestProperty("Authorization", "Bearer ${account.token}")
                connection.setRequestProperty("Account-Id", account.accountId)
            }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                code == 401 -> throw LluException(LluProblem.WRONG_LOGIN, "LibreLinkUp signed this phone out")
                code !in 200..299 -> throw LluException(LluProblem.OTHER, "LibreLinkUp answered $code")
                else -> text
            }
        } catch (e: LluException) {
            throw e
        } catch (e: Exception) {
            throw LluException(LluProblem.UNREACHABLE, e.message ?: e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun load(): Account? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return Account(
            prefs.getString(KEY_EMAIL, "").orEmpty(), token, prefs.getLong(KEY_EXPIRES, 0),
            prefs.getString(KEY_ACCOUNT_ID, "").orEmpty(), prefs.getString(KEY_HOST, HOST) ?: HOST,
        )
    }

    // --- the API's JSON, pure ----------------------------------------------------------------------

    internal sealed interface Login {
        data class Ok(val token: String, val expiresAtSeconds: Long, val userId: String) : Login
        data class Redirect(val region: String) : Login
        data class Refused(val problem: LluProblem, val message: String) : Login
    }

    companion object {
        /** Followed-person ids that are LibreLinkUp connections. */
        const val ID_PREFIX = "llu:"
        private const val HOST = "https://api.libreview.io"
        /** The LibreLinkUp app version the API is told; it refuses versions it considers too old. */
        private const val APP_VERSION = "4.16.0"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TOKEN = "token"
        private const val KEY_EXPIRES = "expires"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_HOST = "host"
        private const val KEY_OWN = "own_patient"
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

        fun owns(id: String) = id.startsWith(ID_PREFIX)

        internal fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        internal fun parseLogin(json: JSONObject): Login {
            val status = json.optInt("status", -1)
            val data = json.optJSONObject("data")
            return when {
                data?.optBoolean("redirect") == true -> Login.Redirect(data.getString("region"))
                status == 0 && data?.optJSONObject("authTicket") != null -> data.getJSONObject("authTicket").let { ticket ->
                    Login.Ok(ticket.getString("token"), ticket.optLong("expires"), data.getJSONObject("user").getString("id"))
                }
                status == 2 -> Login.Refused(LluProblem.WRONG_LOGIN, json.optJSONObject("error")?.optString("message").orEmpty())
                status == 4 -> Login.Refused(LluProblem.ACCEPT_TERMS, "Open LibreLinkUp and accept its terms")
                else -> Login.Refused(LluProblem.OTHER, json.optJSONObject("error")?.optString("message")?.ifBlank { null } ?: "LibreLinkUp status $status")
            }
        }

        /** LibreLinkUp's FactoryTimestamp is UTC, US-formatted ("10/7/2026 9:52:31 AM"). */
        internal fun parseTime(text: String): Instant? = runCatching { LocalDateTime.parse(text.trim(), TIME).toInstant(ZoneOffset.UTC) }.getOrNull()

        /** TrendArrow 1–5: falling fast … rising fast (0 = none). */
        internal fun trend(arrow: Int): TrendDirection = when (arrow) {
            1 -> TrendDirection.FALLING_FAST
            2 -> TrendDirection.FALLING
            4 -> TrendDirection.RISING
            5 -> TrendDirection.RISING_FAST
            else -> TrendDirection.STEADY
        }

        internal fun measurement(m: JSONObject?): GlucoseReading? {
            if (m == null) return null
            val at = parseTime(m.optString("FactoryTimestamp")) ?: return null
            val value = m.optInt("ValueInMgPerDl", -1).takeIf { it > 0 } ?: return null
            return GlucoseReading(at, value, trend(m.optInt("TrendArrow", 3)), SourceKind.LIBRE_LINK_UP)
        }

        internal fun parseConnections(json: JSONObject): List<Connection> {
            val data = json.optJSONArray("data") ?: return emptyList()
            return (0 until data.length()).map { data.getJSONObject(it) }.map { c ->
                Connection(
                    c.getString("patientId"),
                    listOf(c.optString("firstName"), c.optString("lastName")).filter { it.isNotBlank() }.joinToString(" "),
                    measurement(c.optJSONObject("glucoseMeasurement")),
                )
            }
        }

        /** The history points plus the newest measurement, oldest first, one per timestamp. */
        internal fun parseGraph(json: JSONObject): List<GlucoseReading> {
            val data = json.optJSONObject("data") ?: return emptyList()
            val points = data.optJSONArray("graphData") ?: JSONArray()
            val history = (0 until points.length()).mapNotNull { measurement(points.getJSONObject(it)) }
            val latest = measurement(data.optJSONObject("connection")?.optJSONObject("glucoseMeasurement"))
            return (history + listOfNotNull(latest)).distinctBy { it.timestamp }.sortedBy { it.timestamp }
        }
    }
}
