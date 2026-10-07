package com.sukoon.app.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z -> entries.forEach { (name, body) -> z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry() } }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    @Test
    fun `only the files a backup can hold are unpacked, nowhere else`() {
        val root = Files.createTempDirectory("sukoon").toFile()
        val dir = File(root, "staged")
        val ok = Backup.unpack(
            zip(
                "sukoon-backup.txt" to "Sukoon backup 1",
                "databases/sukoon.db" to "db",
                "shared_prefs/sukoon_prefs.xml" to "<map/>",
                "files/entry_photos/12.jpg" to "jpg",
                "shared_prefs/../../escape.xml" to "x",
                "../outside.txt" to "x",
                "files/entry_photos/notes.txt" to "x",
            ),
            dir,
        )
        assertTrue(ok)
        assertEquals("db", File(dir, "databases/sukoon.db").readText())
        assertTrue(File(dir, "shared_prefs/sukoon_prefs.xml").isFile)
        assertTrue(File(dir, "files/entry_photos/12.jpg").isFile)
        assertFalse(File(root, "escape.xml").exists())
        assertFalse(File(root, "outside.txt").exists())
        assertFalse(File(dir, "files/entry_photos/notes.txt").exists())
        root.deleteRecursively()
    }

    @Test
    fun `a zip that isn't a Sukoon backup is refused and leaves nothing`() {
        val dir = File(Files.createTempDirectory("sukoon").toFile(), "staged")
        assertFalse(Backup.unpack(zip("databases/sukoon.db" to "db"), dir)) // no marker
        assertFalse(dir.exists())
    }
}
