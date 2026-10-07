package com.sukoon.app.data.repository

import java.io.File

/**
 * Photos attached to logbook entries: one JPEG per entry, named by its id, in app-private
 * storage. ponytail: the file name is the link (no database column, so no migration); add a
 * column if photos ever need to live elsewhere or several per entry.
 */
class EntryPhotos(filesDir: File) {
    private val dir = File(filesDir, "entry_photos")

    fun file(id: Long) = File(dir, "$id.jpg")

    /** Entry id → its photo, for every entry that has one. */
    fun all(): Map<Long, File> =
        dir.listFiles()?.mapNotNull { f -> f.name.removeSuffix(".jpg").toLongOrNull()?.let { it to f } }?.toMap().orEmpty()

    fun save(id: Long, jpeg: ByteArray) {
        dir.mkdirs()
        file(id).writeBytes(jpeg)
    }

    fun delete(id: Long) {
        file(id).delete()
    }
}
