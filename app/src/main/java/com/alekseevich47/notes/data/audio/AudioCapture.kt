package com.alekseevich47.notes.data.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class PcmChunk(val samples: FloatArray, val rms: Float)

@Singleton
class AudioCapture @Inject constructor() {
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _chunks = MutableSharedFlow<PcmChunk>(extraBufferCapacity = 8)
    val chunks: SharedFlow<PcmChunk> = _chunks.asSharedFlow()

    @Volatile private var recording = false

    suspend fun recordToWav(output: File): Long = withContext(Dispatchers.IO) {
        val sampleRate = SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBuf, sampleRate / 5) * 2
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
        )
        runCatching {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(recorder.audioSessionId)?.enabled = true
            }
        }
        output.parentFile?.mkdirs()
        RandomAccessFile(output, "rw").use { raf ->
            writeWavHeader(raf, sampleRate, 1, 16, 0)
            val shortBuf = ShortArray(bufferSize / 2)
            var totalBytes = 0
            recording = true
            recorder.startRecording()
            try {
                while (coroutineContext.isActive && recording) {
                    val read = recorder.read(shortBuf, 0, shortBuf.size)
                    if (read <= 0) continue
                    val bytes = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                    val floats = FloatArray(read)
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = shortBuf[i]
                        bytes.putShort(s)
                        val f = s / 32768f
                        floats[i] = f
                        sumSq += (f * f).toDouble()
                    }
                    raf.write(bytes.array())
                    totalBytes += read * 2
                    val rms = sqrt(sumSq / read).toFloat()
                    _amplitude.value = (rms * 4f).coerceIn(0f, 1f)
                    _chunks.tryEmit(PcmChunk(floats, rms))
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
                recording = false
                _amplitude.value = 0f
                raf.seek(0)
                writeWavHeader(raf, sampleRate, 1, 16, totalBytes)
            }
            (totalBytes / 2.0 / sampleRate * 1000).toLong()
        }
    }

    fun stop() {
        recording = false
    }

    private fun writeWavHeader(
        raf: RandomAccessFile,
        sampleRate: Int,
        channels: Int,
        bits: Int,
        dataBytes: Int,
    ) {
        val byteRate = sampleRate * channels * bits / 8
        raf.writeBytes("RIFF")
        raf.writeIntLE(36 + dataBytes)
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.writeIntLE(16)
        raf.writeShortLE(1)
        raf.writeShortLE(channels)
        raf.writeIntLE(sampleRate)
        raf.writeIntLE(byteRate)
        raf.writeShortLE(channels * bits / 8)
        raf.writeShortLE(bits)
        raf.writeBytes("data")
        raf.writeIntLE(dataBytes)
    }

    private fun RandomAccessFile.writeIntLE(v: Int) {
        write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(), ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte()))
    }

    private fun RandomAccessFile.writeShortLE(v: Int) {
        write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte()))
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
