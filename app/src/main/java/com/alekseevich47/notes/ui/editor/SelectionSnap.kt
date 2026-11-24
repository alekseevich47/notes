package com.alekseevich47.notes.ui.editor

import androidx.compose.ui.text.TextRange

/**
 * Where "Select all" goes next. The first press covers the current block.
 * Pressing it again, once the block is already covered, asks for the note.
 */
internal enum class SelectAllStep { BLOCK, DOCUMENT }

internal fun selectAllStep(selection: TextRange, length: Int): SelectAllStep {
    if (length > 0 && selection.min <= 0 && selection.max >= length) return SelectAllStep.DOCUMENT
    return SelectAllStep.BLOCK
}

/**
 * One handle moved. Jump to the far side of the word under the handle,
 * in the direction of the drag. A fresh selection (collapsed before) and a
 * change of both ends are left alone.
 */
internal fun snapDraggedSelection(text: String, previous: TextRange, proposed: TextRange): TextRange {
    if (proposed == previous || text.isEmpty()) return proposed
    if (previous.collapsed || proposed.collapsed) return proposed
    val startMoved = previous.start != proposed.start
    val endMoved = previous.end != proposed.end
    if (startMoved == endMoved) return proposed
    return if (endMoved) {
        TextRange(proposed.start, snapOffset(text, previous.end, proposed.end))
    } else {
        TextRange(snapOffset(text, previous.start, proposed.start), proposed.end)
    }
}

/** Pixels to scroll in one frame once the handle is inside an edge zone. */
internal fun autoScrollStep(lineHeightPx: Float, direction: Float): Float {
    if (direction == 0f || lineHeightPx <= 0f) return 0f
    val step = lineHeightPx / 2f
    return if (direction < 0f) -step else step
}

internal fun snapOffset(text: String, from: Int, to: Int): Int {
    if (to == from || text.isEmpty()) return to.coerceIn(0, text.length)
    val forward = to > from
    val probe = if (forward) (to - 1).coerceIn(0, text.lastIndex) else to.coerceIn(0, text.lastIndex)
    val word = WordBounds.occupied(text, probe) ?: return to.coerceIn(0, text.length)
    return (if (forward) word.end else word.start).coerceIn(0, text.length)
}
