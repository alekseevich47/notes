package com.alekseevich47.notes

import com.alekseevich47.notes.ui.editor.TapClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorGesturesTest {
    @Test
    fun doubleTapRequiresAShortGapAndAShortReach() {
        assertTrue(
            TapClassifier.isDoubleTap(
                previousUp = 100L,
                nextDown = 280L,
                previousX = 10f,
                previousY = 20f,
                nextX = 14f,
                nextY = 22f,
                gapLimit = 300L,
                reach = 16f,
            ),
        )
        assertFalse(
            TapClassifier.isDoubleTap(
                previousUp = 100L,
                nextDown = 500L,
                previousX = 10f,
                previousY = 20f,
                nextX = 12f,
                nextY = 20f,
                gapLimit = 300L,
                reach = 16f,
            ),
        )
        assertFalse(
            TapClassifier.isDoubleTap(
                previousUp = 100L,
                nextDown = 180L,
                previousX = 0f,
                previousY = 0f,
                nextX = 40f,
                nextY = 0f,
                gapLimit = 300L,
                reach = 16f,
            ),
        )
    }

    @Test
    fun longPressIsNotATapAndSlopCancelsTheGesture() {
        assertFalse(TapClassifier.countsAsShortTap(releasedBeforeLongPress = false, withinSlop = true))
        assertFalse(TapClassifier.countsAsShortTap(releasedBeforeLongPress = true, withinSlop = false))
        assertTrue(TapClassifier.countsAsShortTap(releasedBeforeLongPress = true, withinSlop = true))
        assertFalse(TapClassifier.withinSlop(dx = 9f, dy = 0f, slop = 8f))
        assertTrue(TapClassifier.withinSlop(dx = 3f, dy = 4f, slop = 5f))
    }
}
