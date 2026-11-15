package com.alekseevich47.notes.ui.editor

import kotlin.math.max
import kotlin.math.min

/**
 * One line-slice of a selection highlight, in text-layout coordinates.
 * Taps that miss every band are outside the selected text: another line,
 * the empty tail of a line, or the space under the note.
 */
internal data class SelectionBand(
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
)

internal fun selectionBands(
    selectionStart: Int,
    selectionEnd: Int,
    lineCount: Int,
    lineStart: (Int) -> Int,
    lineEnd: (Int) -> Int,
    lineTop: (Int) -> Float,
    lineBottom: (Int) -> Float,
    lineLeft: (Int) -> Float,
    lineRight: (Int) -> Float,
    xForOffset: (Int) -> Float,
): List<SelectionBand> {
    if (selectionEnd <= selectionStart || lineCount <= 0) return emptyList()
    val bands = mutableListOf<SelectionBand>()
    for (line in 0 until lineCount) {
        val start = lineStart(line)
        val end = lineEnd(line)
        val from = max(selectionStart, start)
        val to = min(selectionEnd, end)
        if (to <= from) continue
        // The exclusive line end is also the next line's first offset, so its
        // raw x is 0. Use the line's own edges there.
        val x1 = if (from <= start) lineLeft(line) else xForOffset(from)
        val x2 = if (to >= end) lineRight(line) else xForOffset(to)
        bands += SelectionBand(
            top = lineTop(line),
            bottom = lineBottom(line),
            left = min(x1, x2),
            right = max(x1, x2),
        )
    }
    return bands
}

internal fun pointInsideSelection(x: Float, y: Float, bands: List<SelectionBand>): Boolean {
    return bands.any { band ->
        y >= band.top && y <= band.bottom && x >= band.left && x <= band.right
    }
}

/**
 * A finger-up clears the selection only when it was a tap outside the
 * highlight. Drags (past touch slop) keep the selection so scrolling and
 * handle adjustments do not dismiss it. A long-press is how a selection is
 * created, so its finger-up must not immediately collapse that selection.
 */
internal fun shouldClearSelectionOnUp(
    collapsedAtDown: Boolean,
    dragDistance: Float,
    touchSlop: Float,
    elapsedMillis: Long,
    longPressTimeoutMillis: Long,
    insideSelection: Boolean,
): Boolean {
    if (collapsedAtDown) return false
    if (insideSelection) return false
    if (dragDistance > touchSlop) return false
    if (elapsedMillis >= longPressTimeoutMillis) return false
    return true
}
