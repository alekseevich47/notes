package com.alekseevich47.notes.ui.editor

import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark

/**
 * Locked hidden spans stay in the document, but edits that would reveal or
 * change them are rejected until the PIN dialog unlocks the note.
 */
object HiddenText {
    /**
     * Blur radius as a fraction of the font size. Wide enough that locked
     * glyphs melt into a smudge instead of staying readable.
     */
    const val BLUR_EM: Float = 0.5f

    fun editTouches(marks: List<TextMark>, before: String, after: String): Boolean {
        if (before == after) return false
        val prefix = sharedPrefix(before, after)
        var suffix = 0
        while (
            suffix < before.length - prefix &&
            suffix < after.length - prefix &&
            before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
        ) {
            suffix++
        }
        val oldStart = prefix
        val oldEnd = before.length - suffix
        return marks.any { mark ->
            if (mark.style != MarkStyle.HIDDEN || mark.end <= mark.start) return@any false
            if (oldStart == oldEnd) {
                mark.start < oldStart && oldStart < mark.end
            } else {
                mark.start < oldEnd && mark.end > oldStart
            }
        }
    }

    fun cursorInside(marks: List<TextMark>, start: Int, end: Int): Boolean {
        val from = minOf(start, end)
        val to = maxOf(start, end)
        return marks.any { mark ->
            if (mark.style != MarkStyle.HIDDEN || mark.end <= mark.start) return@any false
            if (from == to) from in mark.start until mark.end else mark.start < to && mark.end > from
        }
    }

    private fun sharedPrefix(left: String, right: String): Int {
        val limit = minOf(left.length, right.length)
        var index = 0
        while (index < limit && left[index] == right[index]) index++
        return index
    }
}
