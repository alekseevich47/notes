package com.alekseevich47.notes

import com.alekseevich47.notes.ui.editor.autoScrollRamp
import com.alekseevich47.notes.ui.editor.autoScrollVelocity
import com.alekseevich47.notes.ui.editor.shiftedEdgeY
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionAutoScrollTest {
    @Test
    fun velocityIsZeroOutsideTheZonesAndSignedAtTheEdges() {
        assertEquals(0f, velocity(edgeY = 400f, held = 900L), 0.01f)
        assertEquals(true, velocity(edgeY = 20f, held = 900L) < 0f)
        assertEquals(true, velocity(edgeY = 780f, held = 900L) > 0f)
    }

    @Test
    fun speedGrowsWithHoldTimeAndClampsPastTheKeyboard() {
        val early = velocity(edgeY = 900f, held = 0L)
        val late = velocity(edgeY = 900f, held = 900L)
        assertEquals(80f + (900f - 80f) * autoScrollRamp(0L), early, 0.01f)
        assertEquals(900f, late, 0.01f)
        assertEquals(true, late > early)
        assertEquals(12f, shiftedEdgeY(10f, 2f), 0.01f)
    }

    private fun velocity(edgeY: Float, held: Long): Float {
        return autoScrollVelocity(
            edgeY = edgeY,
            viewportTop = 0f,
            viewportBottom = 800f,
            zonePx = 56f,
            minPxPerSec = 80f,
            maxPxPerSec = 900f,
            heldMillis = held,
        )
    }
}
