package com.alekseevich47.notes.ui.editor

import androidx.compose.ui.text.TextRange

/**
 * Word under a tap. Offsets are UTF-16 indexes, the same ones Compose uses.
 * A separator sitting directly after a word selects that word; a gap that does
 * not touch a word selects nothing. Surrogate pairs are never split.
 */
internal object WordBounds {
    fun at(text: String, offset: Int): TextRange? {
        if (text.isEmpty() || offset < 0 || offset > text.length) return null
        val anchor = if (offset == text.length) {
            if (!inWord(text, text.offsetByCodePoints(text.length, -1))) return null
            text.offsetByCodePoints(text.length, -1)
        } else {
            val aligned = codePointStart(text, offset)
            if (inWord(text, aligned)) {
                aligned
            } else if (aligned == 0 || !inWord(text, text.offsetByCodePoints(aligned, -1))) {
                return null
            } else {
                text.offsetByCodePoints(aligned, -1)
            }
        }
        val range = expand(text, anchor)
        return range.takeIf { it.length > 0 }
    }

    /**
     * Word that contains [offset] itself. A space or punctuation after a word
     * does not count: that is how a handle decides whether to jump.
     */
    fun occupied(text: String, offset: Int): TextRange? {
        if (text.isEmpty() || offset < 0 || offset >= text.length) return null
        val start = codePointStart(text, offset)
        if (!inWord(text, start)) return null
        return expand(text, start).takeIf { it.length > 0 }
    }

    private fun codePointStart(text: String, offset: Int): Int {
        if (offset <= 0) return 0
        if (offset >= text.length) return text.length
        return if (Character.isLowSurrogate(text[offset]) && Character.isHighSurrogate(text[offset - 1])) {
            offset - 1
        } else {
            offset
        }
    }

    private fun isWord(codePoint: Int): Boolean {
        return Character.isLetterOrDigit(codePoint) || codePoint == '_'.code
    }

    private fun isConnector(codePoint: Int): Boolean {
        return codePoint == '\''.code || codePoint == '’'.code || codePoint == '-'.code || codePoint == '.'.code
    }

    /** A letter, digit, underscore, or a connector that actually joins two of those. */
    private fun inWord(text: String, index: Int): Boolean {
        if (index < 0 || index >= text.length) return false
        val start = codePointStart(text, index)
        val codePoint = text.codePointAt(start)
        if (isWord(codePoint)) return true
        if (!isConnector(codePoint) || start == 0) return false
        val previous = text.offsetByCodePoints(start, -1)
        val next = text.offsetByCodePoints(start, 1)
        if (next >= text.length) return false
        return isWord(text.codePointAt(previous)) && isWord(text.codePointAt(next))
    }

    private fun expand(text: String, index: Int): TextRange {
        var start = codePointStart(text, index)
        while (start > 0) {
            val previous = text.offsetByCodePoints(start, -1)
            if (!inWord(text, previous)) break
            start = previous
        }
        var end = start
        while (end < text.length && inWord(text, end)) {
            end = text.offsetByCodePoints(end, 1)
        }
        return TextRange(start, end)
    }
}
