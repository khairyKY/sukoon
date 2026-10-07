package com.sukoon.app.alarms

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundPackTest {

    @Test
    fun `every pack has a sound for every role`() {
        // Unit tests run from the app module, so res/raw is right here.
        SoundPack.entries.forEach { pack ->
            SoundRole.entries.forEach { role ->
                val file = File("src/main/res/raw/${SukoonSounds.raw(pack, role)}.wav")
                assertTrue("$pack $role: ${file.path}", file.isFile && file.length() > 1000)
            }
        }
    }
}
