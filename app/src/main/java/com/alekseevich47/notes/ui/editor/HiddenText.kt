package com.alekseevich47.notes.ui.editor

import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark

/**
 * Locked hidden spans stay in the document, but edits that would reveal or
 * change them are rejected until the PIN dialog unlocks the note.
 */
object HiddenText {
    /**
     * Fake text-shadow samples, in dp, around the glyphs. The real fill stays
     * transparent; these low-alpha copies stand in for `text-shadow: 0 0 20px`.
     */
    val shadowOffsetsDp: List<Pair<Float, Float>> = listOf(
        0f to 0f,
        2.4f to 0f,
        -2.4f to 0f,
        0f to 2.4f,
        0f to -2.4f,
        1.8f to 1.8f,
        -1.8f to 1.8f,
        1.8f to -1.8f,
        -1.8f to -1.8f,
        4.6f to 0.4f,
        -4.6f to -0.4f,
        0.4f to 4.6f,
        -0.8f to -4.6f,
    )

    val shadowAlphas: List<Float> = listOf(
        0.22f,
        0.14f,
        0.14f,
        0.14f,
        0.14f,
        0.12f,
        0.12f,
        0.12f,
        0.12f,
        0.08f,
        0.08f,
        0.08f,
        0.08f,
    )

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
