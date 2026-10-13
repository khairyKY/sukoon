package com.sukoon.app.ui.help

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideContentTest {

    private val dir = listOf("src/main/assets/guide", "app/src/main/assets/guide").map(::File).first { it.exists() }
    private fun guide(name: String) = GuideContent.parse(File(dir, name).readText())

    /** Every link the app routes (MainScaffold.openLink). */
    private val links = setOf(
        "now", "sensor", "sensor_start", "alarms", "people", "fitness", "insulin", "learning", "apps", "reports", "appearance", "help",
        "trends_graph", "trends_logbook", "trends_insights", "trends_report", "trends_ask",
        "entry_carb", "entry_insulin", "entry_basal", "entry_fingerstick",
    )

    @Test
    fun `all three languages have the same topics, steps and links, and every link goes somewhere`() {
        val en = guide("guide-en.json")
        listOf("guide-ar.json", "guide-ar-EG.json").map(::guide).forEach { ar ->
            assertEquals(en.topics.map { it.id }, ar.topics.map { it.id })
            assertEquals(en.topics.map { t -> t.sections.map { s -> s.steps.map { it.link } } }, ar.topics.map { t -> t.sections.map { s -> s.steps.map { it.link } } })
            assertEquals(en.start.map { it.link }, ar.start.map { it.link })
        }
        val used = en.topics.flatMap { t -> t.sections.flatMap { s -> s.steps.mapNotNull { it.link } } } + en.start.map { it.link }
        assertTrue("unknown links: ${used.toSet() - links}", links.containsAll(used))
        val ids = en.topics.map { it.id }.toSet()
        en.topics.forEach { t -> assertTrue("${t.id} relates to a missing topic", ids.containsAll(t.related)) }
        assertEquals(23, en.topics.size)
    }

    @Test
    fun `Egyptian for Arabic in Egypt, Standard Arabic elsewhere, English otherwise`() {
        assertEquals("guide/guide-ar-EG.json", GuideContent.fileFor(Locale("ar", "EG")))
        assertEquals("guide/guide-ar.json", GuideContent.fileFor(Locale("ar", "SA")))
        assertEquals("guide/guide-en.json", GuideContent.fileFor(Locale.UK))
    }

    @Test
    fun `search finds a topic by any of its words`() {
        val en = guide("guide-en.json")
        assertEquals("sensor", en.search("start sensor").first().id)
        assertTrue(en.search("zzz").isEmpty())
    }
}
