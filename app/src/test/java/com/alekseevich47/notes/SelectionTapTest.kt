package com.alekseevich47.notes

import androidx.compose.ui.geometry.Offset
import com.alekseevich47.notes.ui.editor.BlockSurfaceHandlers
import com.alekseevich47.notes.ui.editor.NoteSurfaceBridge
import com.alekseevich47.notes.ui.editor.SelectionSnapshot
import com.alekseevich47.notes.ui.editor.pointInsideSelection
import com.alekseevich47.notes.ui.editor.pointInsideTextBounds
import com.alekseevich47.notes.ui.editor.selectionBands
import com.alekseevich47.notes.ui.editor.shouldClearSelectionOnUp
import com.alekseevich47.notes.ui.editor.shouldOfferPlatformPaste
import org.junit.Assert.assertEquals
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
    fun selectionTapRoutesToTheBlockThatOwnsIt() {
        val idle = NoteSurfaceBridge()
        idle.up(Offset.Zero, dragDistance = 0f, elapsedMillis = 10L, touchSlop = 16f, longPressTimeoutMillis = 400L)

        val selected = NoteSurfaceBridge()
        selected.toRoot = { Offset(8f, 8f) }
        var firstCleared = 0
        var secondCleared = 0
        selected.register(0, tapHandlers(onClear = { _, _ -> firstCleared++ }))
        selected.register(
            1,
            tapHandlers(
                selection = SelectionSnapshot(2, 6),
                onClear = { _, _ -> secondCleared++ },
            ),
        )
        tapOutside(selected)
        assertEquals(0, firstCleared)
        assertEquals(1, secondCleared)

        val focused = NoteSurfaceBridge()
        focused.toRoot = { Offset(8f, 8f) }
        var focusedCleared = 0
        var otherCleared = 0
        focused.register(
            0,
            tapHandlers(
                focused = true,
                selection = SelectionSnapshot(1, 4),
                onClear = { _, _ -> focusedCleared++ },
            ),
        )
        focused.register(
            1,
            tapHandlers(
                selection = SelectionSnapshot(2, 8),
                onClear = { _, _ -> otherCleared++ },
            ),
        )
        tapOutside(focused)
        assertEquals(1, focusedCleared)
        assertEquals(0, otherCleared)
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

    private fun tapHandlers(
        focused: Boolean = false,
        selection: SelectionSnapshot = SelectionSnapshot.Collapsed,
        onClear: (Offset, SelectionSnapshot) -> Unit = { _, _ -> },
    ) = BlockSurfaceHandlers(
        focused = focused,
        readSelection = { selection },
        pointInside = { _, _ -> false },
        onClear = onClear,
    )

    private fun tapOutside(bridge: NoteSurfaceBridge) {
        bridge.down()
        bridge.up(
            local = Offset.Zero,
            dragDistance = 2f,
            elapsedMillis = 40L,
            touchSlop = 16f,
            longPressTimeoutMillis = 400L,
        )
    }
}
