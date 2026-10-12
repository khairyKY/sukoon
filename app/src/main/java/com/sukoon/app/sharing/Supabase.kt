package com.sukoon.app.sharing

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The signed-in Sukoon account (Supabase Auth). */
data class Session(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long, val userId: String, val email: String)

class SupabaseException(val status: Int, message: String) : Exception(message)

/**
 * Just enough Supabase over plain HTTPS — Auth, PostgREST and RPC — with no SDK, like the
 * Nightscout uploader. The publishable key goes in `apikey`; the signed-in user's token in
 * Authorization, refreshed a minute before it expires. Row Level Security on the server decides
 * what each user may read or write (supabase/migrations).
 *
 * ponytail: tokens sit in app-private SharedPreferences, like the Gemini key; move to
 * Keystore-backed storage if the app is ever distributed.
 */
class Supabase(context: Context, private val url: String, private val key: String) {

    private val prefs = context.getSharedPreferences("sukoon_account", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()
    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    val configured: Boolean get() = url.isNotBlank() && key.isNotBlank()

    /**
     * Returns null when the project wants the email confirmed first (it does: docs/accounts.md). The
     * email's link lands on [AUTH_PAGE], which hands its one-time code back to this app (PKCE: the
     * verifier stays here), so tapping it on this phone signs in.
     */
    suspend fun signUp(email: String, password: String, displayName: String): Session? {
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", JSONObject().put("display_name", displayName.trim()))
            .put("code_challenge", challenge(newVerifier()))
            .put("code_challenge_method", "s256")
        val json = JSONObject(call("POST", "/auth/v1/signup?redirect_to=${encode(AUTH_PAGE)}", body.toString(), authorized = false))
        return if (json.has("access_token")) save(json) else null
    }

    /** Sends the confirmation email again. */
    suspend fun resendConfirmation(email: String) {
        val body = JSONObject().put("type", "signup").put("email", email.trim())
        call("POST", "/auth/v1/resend?redirect_to=${encode(AUTH_PAGE)}", body.toString(), authorized = false)
    }

    /** Emails a link to [AUTH_PAGE], where a new password is chosen (on any device, so no PKCE here). */
    suspend fun sendPasswordReset(email: String) {
        call("POST", "/auth/v1/recover?redirect_to=${encode(AUTH_PAGE)}", JSONObject().put("email", email.trim()).toString(), authorized = false)
    }

    /**
     * The confirmation email's link, back from [AUTH_PAGE] (sukoon://auth?code=…): its one-time [code],
     * traded with the verifier only this phone has. Another app catching the link gets nothing it can use.
     */
    suspend fun finishLink(code: String): Session {
        val verifier = prefs.getString(K_VERIFIER, null) ?: throw SupabaseException(400, "This link was made on another phone")
        val body = JSONObject().put("auth_code", code).put("code_verifier", verifier)
        return save(JSONObject(call("POST", "/auth/v1/token?grant_type=pkce", body.toString(), authorized = false))).also { prefs.edit().remove(K_VERIFIER).apply() }
    }

    private fun newVerifier(): String = verifier().also { prefs.edit().putString(K_VERIFIER, it).apply() }

    suspend fun signIn(email: String, password: String): Session {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        return save(JSONObject(call("POST", "/auth/v1/token?grant_type=password", body.toString(), authorized = false)))
    }

    fun signOut() {
        val verifier = prefs.getString(K_VERIFIER, null) // a confirmation still on its way survives signing out
        val editor = prefs.edit().clear()
        if (verifier != null) editor.putString(K_VERIFIER, verifier)
        editor.apply()
        _session.value = null
    }

    suspend fun get(path: String): String = call("GET", "/rest/v1/$path", null)

    suspend fun post(path: String, body: String, prefer: String? = null): String = call("POST", "/rest/v1/$path", body, prefer = prefer)

    suspend fun delete(path: String): String = call("DELETE", "/rest/v1/$path", null)

    suspend fun rpc(function: String, args: JSONObject): String = call("POST", "/rest/v1/rpc/$function", args.toString())

    private suspend fun call(method: String, path: String, body: String?, authorized: Boolean = true, prefer: String? = null): String {
        val token = if (authorized) validSession().accessToken else null
        return withContext(Dispatchers.IO) {
            val connection = URL(url.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("apikey", key)
                connection.setRequestProperty("Accept", "application/json")
                if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                if (prefer != null) connection.setRequestProperty("Prefer", prefer)
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw SupabaseException(code, errorMessage(text, code))
                text
            } finally {
                connection.disconnect()
            }
        }
    }

    /** The current session, refreshed when it has under a minute left. A dead refresh token signs out. */
    private suspend fun validSession(): Session = refreshLock.withLock {
        val current = _session.value ?: throw SupabaseException(401, "Not signed in")
        if (current.expiresAtMillis - 60_000 > System.currentTimeMillis()) return@withLock current
        try {
            val body = JSONObject().put("refresh_token", current.refreshToken)
            save(JSONObject(call("POST", "/auth/v1/token?grant_type=refresh_token", body.toString(), authorized = false)))
        } catch (e: SupabaseException) {
            if (e.status in 400..401) signOut()
            throw e
        }
    }

    private fun save(json: JSONObject): Session {
        val user = json.getJSONObject("user")
        val session = Session(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAtMillis = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
            userId = user.getString("id"),
            email = user.optString("email"),
        )
        prefs.edit()
            .putString(K_ACCESS, session.accessToken)
            .putString(K_REFRESH, session.refreshToken)
            .putLong(K_EXPIRES, session.expiresAtMillis)
            .putString(K_USER, session.userId)
            .putString(K_EMAIL, session.email)
            .apply()
        _session.value = session
        return session
    }

    private fun load(): Session? {
        val access = prefs.getString(K_ACCESS, null) ?: return null
        return Session(
            accessToken = access,
            refreshToken = prefs.getString(K_REFRESH, null) ?: return null,
            expiresAtMillis = prefs.getLong(K_EXPIRES, 0L),
            userId = prefs.getString(K_USER, null) ?: return null,
            email = prefs.getString(K_EMAIL, "") ?: "",
        )
    }

    companion object {
        private const val K_ACCESS = "access_token"
        private const val K_REFRESH = "refresh_token"
        private const val K_EXPIRES = "expires_at"
        private const val K_USER = "user_id"
        private const val K_EMAIL = "email"
        private const val K_VERIFIER = "pkce_verifier"

        /** Where Supabase's emails send people (web/auth.html): confirmation and choosing a new password. */
        const val AUTH_PAGE = "https://khairyky.github.io/sukoon/auth.html"

        private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

        /** A PKCE code verifier: 32 random bytes, base64url (RFC 7636). */
        internal fun verifier(): String = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })

        /** Its S256 challenge: base64url(SHA-256(verifier)). */
        internal fun challenge(verifier: String): String = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        /** The readable part of an Auth or PostgREST error body. */
        internal fun errorMessage(body: String, status: Int): String = runCatching {
            val json = JSONObject(body)
            listOf("error_description", "msg", "message", "error").firstNotNullOfOrNull { field -> json.optString(field).takeIf { it.isNotBlank() } }
        }.getOrNull() ?: "HTTP $status"
    }
}
