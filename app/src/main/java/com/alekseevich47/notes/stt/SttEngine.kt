package com.alekseevich47.notes.stt

import kotlinx.coroutines.flow.Flow
import java.io.File

interface SttEngine {
    val ready: Boolean
    fun partials(): Flow<String>
    suspend fun startSession()
    suspend fun acceptPcm(samples: FloatArray, sampleRate: Int)
    suspend fun finishUtterance(): String
    suspend fun finalizeWithOffline(wavFile: File): String
    fun release()
}
