package com.alekseevich47.notes

import com.alekseevich47.notes.ui.widgets.WidgetMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMotionTest {
    @Test
    fun framesStayInsideTheWidget() {
        for (frame in 0 until 18) {
            assertTrue(WidgetMotion.dotFrame(frame) in 0 until WidgetMotion.FRAME_COUNT)
            assertTrue(WidgetMotion.recordingScale(frame, 0.4f) in 0.9f..1.2f)
            assertTrue(WidgetMotion.recordingAlpha(frame, 0.2f) in 0.7f..1f)
            assertTrue(WidgetMotion.processingScale(frame) in 0.9f..1.15f)
            assertTrue(WidgetMotion.doneScale(frame) in 0.65f..1.15f)
        }
    }

    @Test
    fun newestTranscriptLineFadesIn() {
        val first = WidgetMotion.transcriptAlpha(line = 3, count = 4, fadeFrame = 0)
        val last = WidgetMotion.transcriptAlpha(line = 3, count = 4, fadeFrame = 5)
        assertTrue(last > first)
        assertEquals(1f, last, 0.001f)
    }

    @Test
    fun olderLinesStayDimmer() {
        val fade = 5
        val alphas = (0 until 4).map { WidgetMotion.transcriptAlpha(it, 4, fade) }
        assertTrue(alphas.zipWithNext().all { (older, newer) -> older < newer })
    }

    @Test
    fun fadeResetsWhenALineRollsUp() {
        val before = listOf("", "", "один", "два")
        val grew = listOf("", "", "один", "два три")
        val rolled = listOf("", "один", "два три", "четыре")
        assertFalse(WidgetMotion.shouldResetFade(before, grew))
        assertFalse(WidgetMotion.shouldResetFade(before, before))
        assertTrue(WidgetMotion.shouldResetFade(before, rolled))
        assertTrue(WidgetMotion.shouldResetFade(emptyList(), rolled))
    }

    @Test
    fun colorAlphaKeepsRgb() {
        assertEquals(0xFF1B1430.toInt(), WidgetMotion.colorWithAlpha(0xFF1B1430.toInt(), 1f))
        assertEquals(0x001B1430, WidgetMotion.colorWithAlpha(0xFF1B1430.toInt(), 0f))
    }
}
