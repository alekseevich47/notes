package com.alekseevich47.notes.ui.editor

import com.alekseevich47.notes.domain.MarkEdits
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark

/**
 * Telegram-style markers typed immediately before the caret.
 * The inside is non-empty, does not start or end with a space, and has no newline.
 * Markers are removed and the other marks move with [MarkEdits.shift].
 */
object MarkdownShortcuts {
    data class Edit(
        val text: String,
        val marks: List<TextMark>,
        val cursor: Int,
    )

    private val markers = listOf(
        "**" to MarkStyle.BOLD,
        "__" to MarkStyle.ITALIC,
        "~~" to MarkStyle.STRIKE,
        "||" to MarkStyle.HIDDEN,
    )

    fun apply(
        text: String,
        cursor: Int,
        marks: List<TextMark>,
        rejectLockedHidden: Boolean = false,
    ): Edit? {
        val end = cursor.coerceIn(0, text.length)
        for ((token, style) in markers) {
            if (end < token.length * 2 + 1) continue
            val closeStart = end - token.length
            if (!text.regionMatches(closeStart, token, 0, token.length)) continue
            val open = findOpener(text, token, closeStart)
            if (open < 0) continue
            val insideStart = open + token.length
            val insideEnd = closeStart
            if (insideStart >= insideEnd) continue
            val inside = text.substring(insideStart, insideEnd)
            if (inside.startsWith(' ') || inside.endsWith(' ') || '\n' in inside) continue
            if (rejectLockedHidden && overlapsHidden(marks, open, end)) continue
            val withoutClose = text.removeRange(closeStart, end)
            val afterClose = MarkEdits.shift(marks, text, withoutClose)
            val withoutOpen = withoutClose.removeRange(open, open + token.length)
            val shifted = MarkEdits.shift(afterClose, withoutClose, withoutOpen)
            val styledStart = open
            val styledEnd = closeStart - token.length
            val styled = MarkEdits.include(shifted, listOf(TextMark(styledStart, styledEnd, style)))
            return Edit(withoutOpen, styled, styledEnd)
        }
        return null
    }

    private fun findOpener(text: String, token: String, closeStart: Int): Int {
        var from = closeStart - token.length
        while (from >= 0) {
            val index = text.lastIndexOf(token, from)
            if (index < 0) return -1
            if (index + token.length <= closeStart) return index
            from = index - 1
        }
        return -1
    }

    private fun overlapsHidden(marks: List<TextMark>, start: Int, end: Int): Boolean {
        return marks.any { mark ->
            mark.style == MarkStyle.HIDDEN &&
                mark.end > mark.start &&
                mark.start < end &&
                mark.end > start
        }
    }
}
