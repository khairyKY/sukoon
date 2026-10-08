package com.sukoon.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.sukoon.app.data.db.AppDatabase
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Opt-in: a full backup (the same zip as You → Reports → Back up) written every day to a file you
 * chose in any cloud app on the phone (Google Drive, OneDrive, Dropbox…: whatever offers "save to"),
 * through Android's own picker. Sukoon keeps permission to that one file only, and overwrites it.
 */
class CloudBackup(private val context: Context, private val database: AppDatabase) {
    private val prefs = context.getSharedPreferences("sukoon_cloud_backup", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    data class State(val target: Uri?, val where: String?, val lastAt: Instant?, val error: String?)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun read() = State(
        target = prefs.getString(KEY_URI, null)?.let(Uri::parse),
        where = prefs.getString(KEY_WHERE, null),
        lastAt = prefs.getLong(KEY_LAST, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        error = prefs.getString(KEY_ERROR, null),
    )

    /** Starts daily backups to [uri] (a file just made with the system's "save to" picker), with one now. */
    suspend fun choose(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        prefs.edit().putString(KEY_URI, uri.toString()).putString(KEY_WHERE, describe(uri)).remove(KEY_LAST).remove(KEY_ERROR).apply()
        backUp()
    }

    fun stop() {
        state.value.target?.let { uri -> runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        prefs.edit().clear().apply()
        _state.value = read()
    }

    /** Writes the backup now; the outcome is kept for You → Reports. */
    suspend fun backUp(): Boolean = mutex.withLock {
        val uri = state.value.target ?: read().target ?: return false
        val ok = runCatching {
            withContext(Dispatchers.IO) {
                // "wt": replace the file's contents, so the cloud copy is always the latest.
                (context.contentResolver.openOutputStream(uri, "wt") ?: error("can't open the file")).use { Backup.write(context, database, it) }
            }
        }
        ok.onFailure { Log.w(TAG, "Cloud backup failed", it) }
        prefs.edit().apply {
            if (ok.isSuccess) putLong(KEY_LAST, System.currentTimeMillis()).remove(KEY_ERROR) else putString(KEY_ERROR, ok.exceptionOrNull()?.message ?: "failed")
        }.apply()
        _state.value = read()
        ok.isSuccess
    }

    /** Checks every hour while Sukoon runs (it always does, for the sensor); backs up once a day is due. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                val s = read()
                if (s.target != null && (s.lastAt == null || Duration.between(s.lastAt, Instant.now()) >= EVERY)) backUp()
                delay(Duration.ofHours(1).toMillis())
            }
        }
    }

    /** "sukoon-backup.zip · Google Drive": the file's name and the app that holds it. */
    private fun describe(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        val pm = context.packageManager
        val app = uri.authority?.let { runCatching { pm.resolveContentProvider(it, 0)?.loadLabel(pm)?.toString() }.getOrNull() }
        return listOfNotNull(name, app).joinToString(" · ")
    }

    private companion object {
        const val TAG = "CloudBackup"
        const val KEY_URI = "uri"
        const val KEY_WHERE = "where"
        const val KEY_LAST = "last"
        const val KEY_ERROR = "error"
        val EVERY: Duration = Duration.ofHours(24)
    }
}
