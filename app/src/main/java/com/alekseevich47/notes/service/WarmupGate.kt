package com.alekseevich47.notes.service

/**
 * Models are hundreds of megabytes. Below this class, or on a low-RAM device,
 * keeping them resident makes the system kill the UI.
 */
internal object WarmupGate {
    const val MIN_MEMORY_CLASS_MB = 192

    fun available(lowRamDevice: Boolean, memoryClassMb: Int): Boolean {
        return !lowRamDevice && memoryClassMb >= MIN_MEMORY_CLASS_MB
    }
}
