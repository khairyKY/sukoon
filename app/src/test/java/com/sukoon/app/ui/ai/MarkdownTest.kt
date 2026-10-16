package com.sukoon.app.ui.ai

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun `bold, italic, bullets, numbers and headings lose their marks`() {
        val out = markdown("## Your night\nLows at **03:10**, *twice*.\n\n- one\n* two\n2) three")
        assertEquals("Your nightLows at 03:10, twice. •\u2002one•\u2002two2.\u2002three", out.text)
        val bold = out.spanStyles.filter { it.item.fontWeight == FontWeight.SemiBold }.map { out.text.substring(it.start, it.end) }
        assertEquals(listOf("Your night", "03:10"), bold)
        assertEquals(listOf("twice"), out.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }.map { out.text.substring(it.start, it.end) })
        // One paragraph per line (and one for the gap), so each list line hangs on its own.
        assertEquals(6, out.paragraphStyles.size)
    }

    @Test
    fun `half-streamed bold shows plain, never its stars`() {
        assertEquals("Your average is 142", markdown("Your average is **142").text)
        assertTrue(markdown("Your average is **142").spanStyles.isEmpty())
        assertEquals("5 * 3 stays", markdown("5 * 3 stays").text)
    }
}
