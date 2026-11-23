package com.alekseevich47.notes

import com.alekseevich47.notes.ui.editor.overflowVisibleRows
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionMenuLayoutTest {
    @Test
    fun sixRowsWhenTheWindowHasRoom() {
        assertEquals(6, overflowVisibleRows(maxHeightDp = 400f, rowDp = 48f, backBlockDp = 50f))
        // (338 - 50) / 48 = 6 exactly.
        assertEquals(6, overflowVisibleRows(maxHeightDp = 338f, rowDp = 48f, backBlockDp = 50f))
        assertEquals(6, overflowVisibleRows(maxHeightDp = 10_000f, rowDp = 48f, backBlockDp = 50f))
    }

    @Test
    fun fewerRowsWhenTheWindowIsShort() {
        // (337 - 50) / 48 = 5.97, floored to 5.
        assertEquals(5, overflowVisibleRows(maxHeightDp = 337f, rowDp = 48f, backBlockDp = 50f))
        assertEquals(5, overflowVisibleRows(maxHeightDp = 290f, rowDp = 48f, backBlockDp = 50f))
        // (289 - 50) / 48 = 4.97, floored to 4.
        assertEquals(4, overflowVisibleRows(maxHeightDp = 289f, rowDp = 48f, backBlockDp = 50f))
        assertEquals(4, overflowVisibleRows(maxHeightDp = 242f, rowDp = 48f, backBlockDp = 50f))
        // (241 - 50) / 48 = 3.97, floored to 3.
        assertEquals(3, overflowVisibleRows(maxHeightDp = 241f, rowDp = 48f, backBlockDp = 50f))
        assertEquals(3, overflowVisibleRows(maxHeightDp = 194f, rowDp = 48f, backBlockDp = 50f))
    }

    @Test
    fun neverFewerThanThreeRows() {
        assertEquals(3, overflowVisibleRows(maxHeightDp = 100f, rowDp = 48f, backBlockDp = 50f))
        assertEquals(3, overflowVisibleRows(maxHeightDp = 50f, rowDp = 48f, backBlockDp = 50f))
        assertEquals(3, overflowVisibleRows(maxHeightDp = 0f, rowDp = 48f, backBlockDp = 50f))
    }
}
