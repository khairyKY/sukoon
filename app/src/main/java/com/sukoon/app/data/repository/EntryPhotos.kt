package com.sukoon.app.data.repository

import java.io.File

/**
 * Photos attached to logbook entries, as many as you like: "<id>.jpg", then "<id>-1.jpg",
 * "<id>-2.jpg"… in app-private storage. ponytail: the file name is the link (no database column,
 * so no migration).
 */
class EntryPhotos(filesDir: File) {
    private val dir = File(filesDir, "entry_photos")

    private fun idOf(f: File): Long? = f.name.removeSuffix(".jpg").substringBefore('-').toLongOrNull()

    private fun order(f: File): Int = f.name.removeSuffix(".jpg").substringAfter('-', "0").toIntOrNull() ?: 0

    fun files(id: Long): List<File> = all()[id].orEmpty()

    /** Entry id → its photos in the order taken, for every entry that has any. */
    fun all(): Map<Long, List<File>> =
        dir.listFiles()?.mapNotNull { f -> idOf(f)?.let { it to f } }?.groupBy({ it.first }, { it.second })?.mapValues { (_, fs) -> fs.sortedBy(::order) }.orEmpty()

    /** [jpegs] become the entry's photos (none: it has none). */
    fun save(id: Long, jpegs: List<ByteArray>) {
        delete(id)
        if (jpegs.isEmpty()) return
        dir.mkdirs()
        jpegs.forEachIndexed { i, bytes -> File(dir, if (i == 0) "$id.jpg" else "$id-$i.jpg").writeBytes(bytes) }
    }

    fun delete(id: Long) {
        dir.listFiles()?.filter { idOf(it) == id }?.forEach { it.delete() }
    }
}
