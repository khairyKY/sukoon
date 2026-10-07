package com.sukoon.app.platform

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {

    @Test
    fun `versions compare by number, not text, and a suffix doesn't count`() {
        assertTrue(Updates.newer("0.7.0", "0.6.1"))
        assertTrue(Updates.newer("0.10.0", "0.9.9"))
        assertTrue(Updates.newer("1.0", "0.99.0"))
        assertFalse(Updates.newer("0.6.0", "0.6.1"))
        assertFalse(Updates.newer("0.6.1", "0.6.1"))
        assertFalse(Updates.newer("0.3.0", "0.3.0-dev"))
    }

    @Test
    fun `the latest release gives its version, its apk and its page`() {
        val r = Updates.parseLatest(
            JSONObject(
                """{"tag_name":"v0.7.0","html_url":"https://github.com/khairyKY/sukoon/releases/tag/v0.7.0",
                "assets":[{"name":"notes.txt","browser_download_url":"x"},{"name":"sukoon-0.7.0.apk","browser_download_url":"https://github.com/khairyKY/sukoon/releases/download/v0.7.0/sukoon-0.7.0.apk","size":11568677}]}""",
            ),
        )!!
        assertEquals("0.7.0", r.version)
        assertTrue(r.apkUrl.endsWith("sukoon-0.7.0.apk"))
        assertEquals(11568677L, r.sizeBytes)
        assertNull(Updates.parseLatest(JSONObject("""{"tag_name":"v0.8.0","assets":[]}""")))
    }
}
