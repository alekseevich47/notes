package com.alekseevich47.notes

import com.alekseevich47.notes.service.WarmupGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmupGateTest {
    @Test
    fun lowRamDeviceSkipsWarmup() {
        assertFalse(WarmupGate.available(lowRamDevice = true, memoryClassMb = 256))
    }

    @Test
    fun smallMemoryClassSkipsWarmup() {
        assertFalse(WarmupGate.available(lowRamDevice = false, memoryClassMb = 191))
        assertFalse(WarmupGate.available(lowRamDevice = false, memoryClassMb = 128))
    }

    @Test
    fun normalDeviceCanWarmUp() {
        assertTrue(WarmupGate.available(lowRamDevice = false, memoryClassMb = 192))
        assertTrue(WarmupGate.available(lowRamDevice = false, memoryClassMb = 256))
    }
}
