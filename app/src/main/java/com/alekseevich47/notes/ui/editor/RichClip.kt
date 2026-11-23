package com.alekseevich47.notes.ui.editor

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipboardManager as ComposeClipboard
import androidx.compose.ui.text.AnnotatedString
import com.alekseevich47.notes.domain.MarkEdits
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark

/**
 * Last formatted copy in this process. [match] succeeds only for that exact
 * text. Nothing is written to disk or to the system clipboard.
 */
internal object RichClip {
    private var stored: String? = null
    private var marks: List<TextMark> = emptyList()

    fun put(text: String, marks: List<TextMark>) {
        stored = text
        this.marks = marks.toList()
    }

    fun match(inserted: String): List<TextMark>? {
        if (stored == null || inserted != stored) return null
        return marks
    }

    /** Marks overlapping [start, end), shifted so the fragment starts at 0. */
    fun clip(marks: List<TextMark>, start: Int, end: Int): List<TextMark> {
        if (end <= start) return emptyList()
        return marks.mapNotNull { mark ->
            val from = maxOf(mark.start, start)
            val to = minOf(mark.end, end)
            if (to <= from) null else mark.copy(start = from - start, end = to - start)
        }
    }

    fun overlay(existing: List<TextMark>, origin: Int, copied: List<TextMark>): List<TextMark> {
        if (copied.isEmpty()) return existing
        val shifted = copied.map { mark ->
            mark.copy(start = mark.start + origin, end = mark.end + origin)
        }
        return MarkEdits.include(existing, shifted)
    }

    fun containsHidden(marks: List<TextMark>): Boolean = marks.any { it.style == MarkStyle.HIDDEN }
}

/** New characters in [after], from the shared prefix to the shared suffix. */
internal data class TextInsertion(val start: Int, val end: Int)

internal fun textInsertion(before: String, after: String): TextInsertion? {
    if (before == after) return null
    val prefix = sharedPrefix(before, after)
    var suffix = 0
    val limit = minOf(before.length - prefix, after.length - prefix)
    while (
        suffix < limit &&
        before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
    ) {
        suffix++
    }
    val end = after.length - suffix
    if (end <= prefix) return null
    return TextInsertion(prefix, end)
}

/**
 * Plain text always goes to [composeClipboard]. When [sensitive] and the
 * device is API 33+, the platform clipboard is set again with
 * [ClipDescription.EXTRA_IS_SENSITIVE] so the keyboard and clipboard history
 * hide it. [ComposeClipboard] cannot set that extra.
 */
internal fun writeSelectionClipboard(
    composeClipboard: ComposeClipboard,
    platformClipboard: ClipboardManager?,
    text: String,
    sensitive: Boolean,
) {
    composeClipboard.setText(AnnotatedString(text))
    if (!sensitive || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val manager = platformClipboard ?: return
    val data = ClipData.newPlainText("note", text)
    val extras = PersistableBundle()
    extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    data.description.extras = extras
    manager.setPrimaryClip(data)
}

private fun sharedPrefix(left: String, right: String): Int {
    val limit = minOf(left.length, right.length)
    var index = 0
    while (index < limit && left[index] == right[index]) index++
    return index
}
