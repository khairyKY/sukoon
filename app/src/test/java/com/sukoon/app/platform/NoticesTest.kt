package com.sukoon.app.platform

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticesTest {

    @Test
    fun `reads the file, in both languages, with a link and a last day`() {
        val notices = Notices.parse(
            """
            [
              {"id": "2026-10-12-start", "title": "Start sensors in Sukoon", "body": "You → Sensor → Start a new sensor.",
               "title_ar": "شغّل الحساس من سكون", "body_ar": "إنت ← الحساس ← شغّل حساس جديد.",
               "link": "https://github.com/khairyKY/sukoon/releases", "until": "2026-10-20"}
            ]
            """,
        )
        assertEquals(1, notices.size)
        with(notices.single()) {
            assertEquals("2026-10-12-start", id)
            assertEquals("شغّل الحساس من سكون", titleAr)
            assertEquals("https://github.com/khairyKY/sukoon/releases", link)
            assertEquals(LocalDate.of(2026, 10, 20), until)
        }
    }

    @Test
    fun `skips broken entries and anything that isn't an https link or a real date`() {
        val notices = Notices.parse(
            """
            [
              {"title": "no id", "body": "x"},
              {"id": "a", "body": "no title"},
              "not an object",
              {"id": "b", "title": "ok", "body": "ok", "link": "http://insecure.example", "until": "next week"}
            ]
            """,
        )
        assertEquals(listOf("b"), notices.map { it.id })
        assertNull(notices.single().link)
        assertNull(notices.single().until)
    }
}
