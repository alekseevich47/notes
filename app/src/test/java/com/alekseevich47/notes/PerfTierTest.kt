package com.alekseevich47.notes

import com.alekseevich47.notes.ui.theme.PerfTier
import com.alekseevich47.notes.ui.theme.perfTierFor
import org.junit.Assert.assertEquals
import org.junit.Test

class PerfTierTest {
    @Test
    fun normalDeviceStaysNormal() {
        assertEquals(
            PerfTier.NORMAL,
            perfTierFor(lowRam = false, sdkInt = 31, processors = 8, memoryClass = 256),
        )
    }

    @Test
    fun anyLowSignalClassifiesTheDeviceAsLow() {
        assertEquals(
            PerfTier.LOW,
            perfTierFor(lowRam = true, sdkInt = 34, processors = 8, memoryClass = 256),
        )
        assertEquals(
            PerfTier.LOW,
            perfTierFor(lowRam = false, sdkInt = 27, processors = 8, memoryClass = 256),
        )
        assertEquals(
            PerfTier.NORMAL,
            perfTierFor(lowRam = false, sdkInt = 28, processors = 8, memoryClass = 256),
        )
        assertEquals(
            PerfTier.LOW,
            perfTierFor(lowRam = false, sdkInt = 33, processors = 4, memoryClass = 256),
        )
        assertEquals(
            PerfTier.NORMAL,
            perfTierFor(lowRam = false, sdkInt = 33, processors = 5, memoryClass = 256),
        )
        assertEquals(
            PerfTier.LOW,
            perfTierFor(lowRam = false, sdkInt = 33, processors = 8, memoryClass = 191),
        )
        assertEquals(
            PerfTier.NORMAL,
            perfTierFor(lowRam = false, sdkInt = 33, processors = 8, memoryClass = 192),
        )
    }
}
