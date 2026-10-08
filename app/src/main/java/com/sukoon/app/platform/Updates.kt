package com.sukoon.app.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sukoon.app.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Updates from Sukoon's GitHub releases: a check at most once a day (and on demand), a dismissable
 * notice on Home, and Update: download the APK, check it's signed like this install, then hand it to
 * Android's installer. A release signed with a different key can't update this install (Android
 * refuses); that's said plainly, with the way through (back up, reinstall, restore).
 */
class Updates(private val context: Context, private val scope: CoroutineScope) {

    data class Release(val version: String, val apkUrl: String, val pageUrl: String, val sizeBytes: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release, val file: File) : State
        /** Signed with another key: it can't update this install (see docs/signing.md). */
        data class NeedsReinstall(val release: Release) : State
        data class Failed(val message: String) : State
    }

    private val prefs = context.getSharedPreferences("sukoon_updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _dismissed = MutableStateFlow(prefs.getString(KEY_DISMISSED, null))
    /** The version whose Home notice was closed: it stays closed until a newer one. */
    val dismissed: StateFlow<String?> = _dismissed.asStateFlow()

    fun dismiss(release: Release) {
        prefs.edit().putString(KEY_DISMISSED, release.version).apply()
        _dismissed.value = release.version
    }

    /** At start: a quiet check when the last was over a day ago. */
    fun checkIfDue() {
        if (!com.sukoon.app.BuildConfig.SELF_UPDATE) return // the Play build is updated by Play
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) < TimeUnit.HOURS.toMillis(20)) return
        scope.launch { check(quiet = true) }
    }

    /** [quiet]: a failure stays Idle (the daily check shouldn't show errors). */
    suspend fun check(quiet: Boolean = false) {
        if (_state.value is State.Downloading) return
        _state.value = State.Checking
        _state.value = try {
            val release = withContext(Dispatchers.IO) { parseLatest(JSONObject(get(LATEST))) }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            if (release != null && newer(release.version, BuildConfig.VERSION_NAME)) State.Available(release) else State.UpToDate
        } catch (e: Exception) {
            if (quiet) State.Idle else State.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Downloads [release], checks its signature against this install, then opens the installer. */
    fun update(release: Release) {
        if (_state.value is State.Downloading) return
        scope.launch {
            _state.value = State.Downloading(release, 0f)
            try {
                val file = withContext(Dispatchers.IO) { download(release) }
                if (!sameSigner(file)) {
                    file.delete()
                    _state.value = State.NeedsReinstall(release)
                    return@launch
                }
                _state.value = State.Ready(release, file)
                install(file)
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Android's installer for [file]; first, where needed, the switch that lets Sukoon install updates. */
    fun install(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openPage(release: Release) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun download(release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "sukoon-${release.version}.apk")
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Sukoon/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode !in 200..299) error("GitHub answered ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            connection.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) _state.value = State.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return file
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(apk: File): Boolean {
        val pm = context.packageManager
        fun hashes(signatures: Array<android.content.pm.Signature>?) = signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        return if (Build.VERSION.SDK_INT >= 28) {
            val mine = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            val theirs = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo?.apkContentsSigners
            theirs != null && hashes(mine) == hashes(theirs)
        } else {
            val mine = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            val theirs = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)?.signatures
            theirs != null && hashes(mine) == hashes(theirs)
        }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Sukoon/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode !in 200..299) error("GitHub answered ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val LATEST = "https://api.github.com/repos/khairyKY/sukoon/releases/latest"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_DISMISSED = "dismissed"

        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** The latest release with an APK attached, or null. */
        internal fun parseLatest(json: JSONObject): Release? {
            val assets = json.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
            return Release(
                version = json.optString("tag_name").removePrefix("v"),
                apkUrl = apk.getString("browser_download_url"),
                pageUrl = json.optString("html_url"),
                sizeBytes = apk.optLong("size"),
            )
        }

        /** "0.7.0" > "0.6.1"; a suffix ("-dev") doesn't count; missing parts are 0. */
        internal fun newer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
