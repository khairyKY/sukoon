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
               "link": "https://github.com/khairyKY/sukoon/releases", "until": "2026-10-20", "date": "2026-10-12"}
            ]
            """,
        )
        assertEquals(1, notices.size)
        with(notices.single()) {
            assertEquals("2026-10-12-start", id)
            assertEquals("شغّل الحساس من سكون", titleAr)
            assertEquals("https://github.com/khairyKY/sukoon/releases", link)
            assertEquals(LocalDate.of(2026, 10, 20), until)
            assertEquals(LocalDate.of(2026, 10, 12), date)
        }
    }

    @Test
    fun `the card shows the newest notice still running that wasn't closed`() {
        val today = LocalDate.of(2026, 10, 12)
        val old = Notices.Notice("a", "Old", "x", until = today.minusDays(1))
        val lastDay = Notices.Notice("b", "Ends today", "x", until = today)
        val closed = Notices.Notice("c", "Closed", "x")
        assertEquals("b", Notices.newest(listOf(old, lastDay, closed), dismissed = setOf("c"), today = today)?.id)
        assertNull(Notices.newest(listOf(old, closed), dismissed = setOf("c"), today = today))
        assertEquals("كلام", Notices.Notice("d", "T", "words", bodyAr = "كلام").bodyIn(arabic = true))
        assertEquals("words", Notices.Notice("d", "T", "words").bodyIn(arabic = true))
    }

    @Test
    fun `notifies one at a time, the newest running one not yet seen`() {
        val today = LocalDate.of(2026, 10, 12)
        val ended = Notices.Notice("a", "Ended", "x", until = today.minusDays(1))
        val older = Notices.Notice("b", "Older", "x")
        val newer = Notices.Notice("c", "Newer", "x")
        assertEquals(listOf("c"), Notices.toShow(listOf(ended, older, newer), seen = emptySet(), today = today).map { it.id })
        assertEquals(listOf("b"), Notices.toShow(listOf(ended, older, newer), seen = setOf("c"), today = today).map { it.id })
        assertEquals(emptyList<Notices.Notice>(), Notices.toShow(listOf(ended, older), seen = setOf("b"), today = today))
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
