package com.sukoon.app.ui.ai

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

private val HEADING = Regex("""^#{1,6}\s+""")
private val BULLET = Regex("""^[-*•]\s+""")
private val NUMBER = Regex("""^(\d{1,3})[.)]\s+""")
private val INLINE = Regex("""\*\*(.+?)\*\*|(?<![*\w])\*(?![\s*])(.+?)(?<![\s*])\*(?![*\w])""")

/**
 * The little markdown Gemini writes anyway (**bold**, *italic*, bullets, numbered lists, # headings) as
 * styled text, one paragraph per line so lists hang. Safe mid-stream: a ** not closed yet just doesn't show.
 */
fun markdown(text: String): AnnotatedString = buildAnnotatedString {
    var blank = false
    text.lines().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) {
            // A gap between paragraphs: half a line, once, and never before the first one.
            if (!blank && length > 0) withStyle(ParagraphStyle(lineHeight = 8.sp)) { append(" ") }
            blank = true
            return@forEach
        }
        blank = false
        val heading = HEADING.find(line)
        val bullet = BULLET.find(line)
        val number = NUMBER.find(line)
        when {
            heading != null -> withStyle(ParagraphStyle()) { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { inline(line.substring(heading.range.last + 1)) } }
            bullet != null -> withStyle(ParagraphStyle(textIndent = TextIndent(restLine = 14.sp))) { append("•\u2002"); inline(line.substring(bullet.range.last + 1)) }
            number != null -> withStyle(ParagraphStyle(textIndent = TextIndent(restLine = 20.sp))) { append(number.groupValues[1] + ".\u2002"); inline(line.substring(number.range.last + 1)) }
            else -> withStyle(ParagraphStyle()) { inline(line) }
        }
    }
}

private fun AnnotatedString.Builder.inline(s: String) {
    var at = 0
    INLINE.findAll(s).forEach { m ->
        append(s.substring(at, m.range.first).replace("**", ""))
        val bold = m.groups[1]
        if (bold != null) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold.value) }
        else withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[2]) }
        at = m.range.last + 1
    }
    append(s.substring(at).replace("**", ""))
}
