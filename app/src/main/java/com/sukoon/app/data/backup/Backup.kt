package com.sukoon.app.data.backup

import android.content.Context
import com.sukoon.app.data.db.AppDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything Sukoon keeps, as one zip: the database (readings, logbook), every settings file
 * (alarms, sensor pairing, account, AI key) and the meal photos. Restoring stages the files and
 * puts them in place at the next start, before anything opens them; the app then starts fresh.
 * What moves Sukoon to another phone, or across a signing-key change (which needs a reinstall).
 */
object Backup {

    private const val MARKER = "sukoon-backup.txt"
    private const val STAGED = "restore_staged"
    private val PHOTO_NAME = Regex("""\d+(-\d+)?\.jpg""") // a meal's first photo, then its others
    private val PREFS_NAME = Regex("""[A-Za-z0-9_.-]+\.xml""")

    fun write(context: Context, database: AppDatabase, out: OutputStream) {
        // Fold the write-ahead log into the database file, so that one file holds everything.
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        ZipOutputStream(out).use { zip ->
            fun add(file: File, name: String) {
                if (!file.isFile) return
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(MARKER))
            zip.write("Sukoon backup 1\n".toByteArray())
            zip.closeEntry()
            add(context.getDatabasePath(DB), "databases/$DB")
            prefsDir(context).listFiles()?.forEach { add(it, "shared_prefs/${it.name}") }
            photosDir(context).listFiles()?.forEach { add(it, "files/entry_photos/${it.name}") }
        }
    }

    /**
     * Unpacks a backup for the next start. False when it isn't a Sukoon backup. Only the files a
     * backup can hold are accepted, by name, so a crafted zip can't write anywhere else.
     */
    fun stage(context: Context, input: InputStream): Boolean = unpack(input, File(context.filesDir, STAGED))

    internal fun unpack(input: InputStream, into: File): Boolean {
        val dir = into.apply { deleteRecursively(); mkdirs() }
        var marked = false
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = when {
                    entry.name == MARKER -> { marked = true; null }
                    entry.name == "databases/$DB" -> File(dir, "databases/$DB")
                    entry.name.startsWith("shared_prefs/") && PREFS_NAME.matches(entry.name.removePrefix("shared_prefs/")) -> File(dir, entry.name)
                    entry.name.startsWith("files/entry_photos/") && PHOTO_NAME.matches(entry.name.removePrefix("files/entry_photos/")) -> File(dir, entry.name)
                    else -> null
                }
                if (target != null) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        if (!marked || !File(dir, "databases/$DB").isFile) {
            dir.deleteRecursively()
            return false
        }
        return true
    }

    /** At process start, before the database or any settings are opened: put a staged backup in place. */
    fun applyStaged(context: Context) {
        val dir = File(context.filesDir, STAGED)
        if (!File(dir, "databases/$DB").isFile) return
        val db = context.getDatabasePath(DB)
        listOf(db, File(db.path + "-wal"), File(db.path + "-shm")).forEach { it.delete() }
        db.parentFile?.mkdirs()
        File(dir, "databases/$DB").copyTo(db, overwrite = true)
        File(dir, "shared_prefs").listFiles()?.let { files ->
            prefsDir(context).listFiles()?.forEach { it.delete() }
            prefsDir(context).mkdirs()
            files.forEach { it.copyTo(File(prefsDir(context), it.name), overwrite = true) }
        }
        photosDir(context).deleteRecursively()
        File(dir, "files/entry_photos").listFiles()?.let { files ->
            photosDir(context).mkdirs()
            files.forEach { it.copyTo(File(photosDir(context), it.name), overwrite = true) }
        }
        dir.deleteRecursively()
    }

    private const val DB = "sukoon.db"
    private fun prefsDir(context: Context) = File(context.applicationInfo.dataDir, "shared_prefs")
    private fun photosDir(context: Context) = File(context.filesDir, "entry_photos")
}
