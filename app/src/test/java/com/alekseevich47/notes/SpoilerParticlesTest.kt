package com.alekseevich47.notes

import com.alekseevich47.notes.ui.editor.spoilerParticleAlpha
import com.alekseevich47.notes.ui.editor.spoilerParticleCount
import com.alekseevich47.notes.ui.editor.spoilerParticlesRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoilerParticlesTest {
    @Test
    fun countIsOneParticlePerCellAndCapped() {
        assertEquals(0, spoilerParticleCount(0f, lowTier = false))
        assertEquals(0, spoilerParticleCount(-10f, lowTier = true))
        assertEquals(1, spoilerParticleCount(48f, lowTier = false))
        assertEquals(2, spoilerParticleCount(96f, lowTier = false))
        assertEquals(1, spoilerParticleCount(96f, lowTier = true))
        assertEquals(0, spoilerParticleCount(48f, lowTier = true))
        assertEquals(600, spoilerParticleCount(48f * 600f, lowTier = false))
        assertEquals(600, spoilerParticleCount(48f * 4000f, lowTier = false))
        assertEquals(600, spoilerParticleCount(96f * 4000f, lowTier = true))
    }

    @Test
    fun particlesInsideTheRevealCircleFade() {
        assertEquals(1f, spoilerParticleAlpha(distance = 10f, radius = 40f, progress = 0f), 0f)
        assertEquals(1f, spoilerParticleAlpha(distance = 50f, radius = 40f, progress = 0.5f), 0f)
        assertEquals(0.5f, spoilerParticleAlpha(distance = 10f, radius = 40f, progress = 0.5f), 0f)
        assertEquals(0f, spoilerParticleAlpha(distance = 10f, radius = 80f, progress = 1f), 0f)
    }

    @Test
    fun particlesRunOnlyWhileCoveringAResumedVisibleSpoiler() {
        assertFalse(
            spoilerParticlesRun(
                reduceMotion = true,
                resumed = true,
                onScreen = true,
                covering = true,
            ),
        )
        assertFalse(
            spoilerParticlesRun(
                reduceMotion = false,
                resumed = false,
                onScreen = true,
                covering = true,
            ),
        )
        assertFalse(
            spoilerParticlesRun(
                reduceMotion = false,
                resumed = true,
                onScreen = false,
                covering = true,
            ),
        )
        assertFalse(
            spoilerParticlesRun(
                reduceMotion = false,
                resumed = true,
                onScreen = true,
                covering = false,
            ),
        )
        assertTrue(
            spoilerParticlesRun(
                reduceMotion = false,
                resumed = true,
                onScreen = true,
                covering = true,
            ),
        )
    }
}
