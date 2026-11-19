package com.alekseevich47.notes

import com.alekseevich47.notes.ui.editor.pointInsideSelection
import com.alekseevich47.notes.ui.editor.pointInsideTextBounds
import com.alekseevich47.notes.ui.editor.selectionBands
import com.alekseevich47.notes.ui.editor.shouldClearSelectionOnUp
import com.alekseevich47.notes.ui.editor.shouldOfferPlatformPaste
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTapTest {
    private val lineStarts = intArrayOf(0, 6, 12)
    private val lineEnds = intArrayOf(6, 12, 18)

    private fun bands() = selectionBands(
        selectionStart = 4,
        selectionEnd = 10,
        lineCount = 3,
        lineStart = { lineStarts[it] },
        lineEnd = { lineEnds[it] },
        lineTop = { it * 20f },
        lineBottom = { it * 20f + 18f },
        lineLeft = { 0f },
        lineRight = { (lineEnds[it] - lineStarts[it]) * 10f },
        xForOffset = { offset ->
            val line = when {
                offset < lineEnds[0] -> 0
                offset < lineEnds[1] -> 1
                else -> 2
            }
            (offset - lineStarts[line]) * 10f
        },
    )

    @Test
    fun tapOutsideTheHighlightClearsAndADragDoesNot() {
        assertTrue(
            shouldClearSelectionOnUp(
                collapsedAtDown = false,
                dragDistance = 4f,
                touchSlop = 16f,
                elapsedMillis = 90,
                longPressTimeoutMillis = 400,
                insideSelection = false,
            ),
        )
        assertFalse(
            shouldClearSelectionOnUp(
                collapsedAtDown = false,
                dragDistance = 16.1f,
                touchSlop = 16f,
                elapsedMillis = 90,
                longPressTimeoutMillis = 400,
                insideSelection = false,
            ),
        )
        assertFalse(
            shouldClearSelectionOnUp(
                collapsedAtDown = false,
                dragDistance = 2f,
                touchSlop = 16f,
                elapsedMillis = 400,
                longPressTimeoutMillis = 400,
                insideSelection = false,
            ),
        )
        assertFalse(
            shouldClearSelectionOnUp(
                collapsedAtDown = false,
                dragDistance = 2f,
                touchSlop = 16f,
                elapsedMillis = 80,
                longPressTimeoutMillis = 400,
                insideSelection = true,
            ),
        )
        assertFalse(
            shouldClearSelectionOnUp(
                collapsedAtDown = true,
                dragDistance = 0f,
                touchSlop = 16f,
                elapsedMillis = 40,
                longPressTimeoutMillis = 400,
                insideSelection = false,
            ),
        )
    }

    @Test
    fun otherLinesAndSpaceBelowTheTextAreOutsideTheSelection() {
        val bands = bands()
        assertTrue(pointInsideSelection(45f, 10f, bands))
        assertFalse(pointInsideSelection(10f, 10f, bands))
        assertFalse(pointInsideSelection(80f, 10f, bands))
        assertTrue(pointInsideSelection(20f, 30f, bands))
        assertTrue(pointInsideSelection(10f, 30f, bands))
        assertFalse(pointInsideSelection(50f, 30f, bands))
        assertFalse(pointInsideSelection(10f, 50f, bands))
        assertFalse(pointInsideSelection(10f, 80f, bands))
        assertFalse(pointInsideSelection(10f, -4f, bands))
    }

    @Test
    fun textBoundsIncludeTheLayoutAndExcludeTheSpaceBelow() {
        assertTrue(pointInsideTextBounds(0f, 0f, 100f, 40f))
        assertTrue(pointInsideTextBounds(100f, 40f, 100f, 40f))
        assertFalse(pointInsideTextBounds(10f, 41f, 100f, 40f))
        assertFalse(pointInsideTextBounds(-1f, 10f, 100f, 40f))
        assertFalse(pointInsideTextBounds(10f, 10f, 0f, 40f))
    }

    @Test
    fun platformPasteStaysCollapsedAndSelectAllNeverForwards() {
        assertFalse(
            shouldOfferPlatformPaste(
                hasCopy = true,
                hasCut = true,
                hasPaste = true,
                suppressPlatform = false,
            ),
        )
        assertFalse(
            shouldOfferPlatformPaste(
                hasCopy = false,
                hasCut = false,
                hasPaste = false,
                suppressPlatform = false,
            ),
        )
        assertFalse(
            shouldOfferPlatformPaste(
                hasCopy = false,
                hasCut = false,
                hasPaste = true,
                suppressPlatform = true,
            ),
        )
        assertTrue(
            shouldOfferPlatformPaste(
                hasCopy = false,
                hasCut = false,
                hasPaste = true,
                suppressPlatform = false,
            ),
        )
    }
}
