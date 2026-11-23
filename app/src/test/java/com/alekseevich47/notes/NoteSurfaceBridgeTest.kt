package com.alekseevich47.notes

import androidx.compose.ui.geometry.Offset
import com.alekseevich47.notes.ui.editor.BlockSurfaceHandlers
import com.alekseevich47.notes.ui.editor.NoteSurfaceBridge
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSurfaceBridgeTest {
    @Test
    fun upCallsEmptyTapOnlyForAShortTapOutsideBlockBounds() {
        val bridge = NoteSurfaceBridge()
        bridge.toRoot = { it }
        var taps = 0
        bridge.onEmptyTap = { taps += 1 }
        bridge.register(
            0,
            BlockSurfaceHandlers(
                bounds = { root -> root.x in 0f..100f && root.y in 0f..40f },
            ),
        )
        fingerUp(bridge, Offset(200f, 200f), drag = 1f, elapsed = 40L)
        assertEquals(1, taps)
        fingerUp(bridge, Offset(10f, 10f), drag = 1f, elapsed = 40L)
        assertEquals(1, taps)
        fingerUp(bridge, Offset(200f, 200f), drag = 20f, elapsed = 40L)
        assertEquals(1, taps)
        fingerUp(bridge, Offset(200f, 200f), drag = 1f, elapsed = 500L)
        assertEquals(1, taps)
    }

    @Test
    fun emptyTapStillRunsWhenNothingIsFocused() {
        val bridge = NoteSurfaceBridge()
        bridge.toRoot = { it }
        var taps = 0
        bridge.onEmptyTap = { taps += 1 }
        fingerUp(bridge, Offset(4f, 4f), drag = 0f, elapsed = 30L)
        assertEquals(1, taps)
    }

    private fun fingerUp(bridge: NoteSurfaceBridge, at: Offset, drag: Float, elapsed: Long) {
        bridge.down()
        bridge.up(
            local = at,
            dragDistance = drag,
            elapsedMillis = elapsed,
            touchSlop = 16f,
            longPressTimeoutMillis = 400L,
        )
    }
}
