package io.github.dyzydery.bahasakotak.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** The small Markdown subset grammar notes use: **bold**, *italic* and "- " bullets. */
fun simpleMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    text.lines().forEachIndexed { index, rawLine ->
        if (index > 0) append('\n')
        val line = if (rawLine.startsWith("- ")) "•  " + rawLine.removePrefix("- ") else rawLine
        appendInline(line)
    }
}

private val INLINE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*""")

private fun AnnotatedString.Builder.appendInline(line: String) {
    var last = 0
    for (match in INLINE.findAll(line)) {
        append(line.substring(last, match.range.first))
        val (bold, italic) = match.destructured
        if (bold.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        else withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        last = match.range.last + 1
    }
    append(line.substring(last))
}
