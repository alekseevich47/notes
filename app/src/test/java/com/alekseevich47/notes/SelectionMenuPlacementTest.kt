package com.alekseevich47.notes

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.alekseevich47.notes.ui.editor.placeSelectionMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionMenuPlacementTest {
    private val shadow = 12
    private val gap = 10
    private val margin = 8

    @Test
    fun plateSizeChangeKeepsCenterAndBottomWhenAbove() {
        val reserve = IntSize(200, 300)
        val wide = place(plate = IntSize(200, 300), reserve = reserve, top = 800f, bottom = 820f, left = 100f, right = 300f)
        val narrow = place(plate = IntSize(80, 40), reserve = reserve, top = 800f, bottom = 820f, left = 100f, right = 300f)
        assertEquals(centerX(wide, 200), centerX(narrow, 80))
        assertEquals(bottomEdge(wide, 300), bottomEdge(narrow, 40))
        assertEquals(790, bottomEdge(wide, 300))
        assertEquals(200, centerX(wide, 200))
    }

    @Test
    fun aboveOrBelowDependsOnlyOnTheReserve() {
        val selectionTop = 40f
        val selectionBottom = 60f
        val tallReserve = place(
            plate = IntSize(40, 20),
            reserve = IntSize(80, 200),
            top = selectionTop,
            bottom = selectionBottom,
            left = 100f,
            right = 180f,
        )
        val shortPlateWouldFitAbove = anchorTop + selectionTop - gap - 20 >= margin
        assertTrue(shortPlateWouldFitAbove)
        assertEquals(selectionBottom.toInt() + gap + 18, topEdge(tallReserve))

        val shortReserve = place(
            plate = IntSize(40, 400),
            reserve = IntSize(80, 40),
            top = 400f,
            bottom = 420f,
            left = 100f,
            right = 180f,
        )
        val tallPlateWouldNotFitAbove = anchorTop + 400f - gap - 400 >= margin
        assertFalse(tallPlateWouldNotFitAbove)
        assertEquals(400 - gap, bottomEdge(shortReserve, 400))
    }

    @Test
    fun edgesAndAWindowNarrowerThanTheReserve() {
        val reserve = IntSize(100, 80)
        val left = place(
            plate = IntSize(60, 40),
            reserve = reserve,
            top = 400f,
            bottom = 420f,
            left = 0f,
            right = 0f,
            window = IntSize(400, 800),
        )
        assertEquals(margin + reserve.width / 2, centerX(left, 60))

        val right = place(
            plate = IntSize(60, 40),
            reserve = reserve,
            top = 400f,
            bottom = 420f,
            left = 400f,
            right = 400f,
            window = IntSize(400, 800),
        )
        assertEquals(400 - margin - reserve.width / 2, centerX(right, 60))

        val topOfScreen = place(
            plate = IntSize(60, 40),
            reserve = reserve,
            top = 0f,
            bottom = 20f,
            left = 120f,
            right = 200f,
            window = IntSize(400, 800),
        )
        assertEquals(20 + gap + 18, topEdge(topOfScreen))

        val narrowWindow = IntSize(100, 800)
        val squeezed = place(
            plate = IntSize(40, 30),
            reserve = IntSize(400, 80),
            top = 400f,
            bottom = 420f,
            left = 0f,
            right = 0f,
            window = narrowWindow,
        )
        assertEquals(0, centerX(squeezed, 40))
        val squeezedRight = place(
            plate = IntSize(40, 30),
            reserve = IntSize(400, 80),
            top = 400f,
            bottom = 420f,
            left = 100f,
            right = 100f,
            window = narrowWindow,
        )
        assertEquals(narrowWindow.width, centerX(squeezedRight, 40))

        val shifted = place(
            plate = IntSize(80, 40),
            reserve = IntSize(80, 40),
            top = 400f,
            bottom = 420f,
            left = 0f,
            right = 0f,
            anchorBounds = IntRect(200, 0, 900, 2000),
        )
        assertEquals(200, centerX(shifted, 80))
    }

    private val anchorTop = 0
    private val anchor = IntRect(0, anchorTop, 1000, 2000)

    private fun place(
        plate: IntSize,
        reserve: IntSize,
        top: Float,
        bottom: Float,
        left: Float,
        right: Float,
        window: IntSize = IntSize(1000, 2000),
        anchorBounds: IntRect = anchor,
    ): IntOffset {
        return placeSelectionMenu(
            anchorBounds = anchorBounds,
            windowSize = window,
            popupContentSize = IntSize(plate.width + shadow * 2, plate.height + shadow * 2),
            startHandle = Rect(left, top, left, bottom),
            endHandle = Rect(right, top, right, bottom),
            gapPx = gap,
            marginPx = margin,
            shadowPadPx = shadow,
            reserve = reserve,
        )
    }

    private fun centerX(offset: IntOffset, plateWidth: Int): Int {
        return offset.x + shadow + plateWidth / 2
    }

    private fun bottomEdge(offset: IntOffset, plateHeight: Int): Int {
        return offset.y + shadow + plateHeight
    }

    private fun topEdge(offset: IntOffset): Int {
        return offset.y + shadow
    }
}
