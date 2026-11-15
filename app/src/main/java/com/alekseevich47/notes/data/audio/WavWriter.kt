package com.alekseevich47.notes.data.audio

import java.io.File
import java.io.RandomAccessFile

/** 16 kHz mono PCM16 WAV. Header is rewritten when the file is closed. */
class WavWriter(
    val file: File,
    private val sampleRate: Int = 16_000,
) : AutoCloseable {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        raf.setLength(0)
        writeHeader(0)
    }

    fun write(buffer: ByteArray, length: Int) {
        check(!closed)
        if (length <= 0) return
        raf.write(buffer, 0, length)
        dataBytes += length
    }

    override fun close() {
        if (closed) return
        closed = true
        raf.seek(0)
        writeHeader(dataBytes)
        raf.setLength((44 + dataBytes).toLong())
        raf.close()
    }

    private fun writeHeader(dataLen: Int) {
        val channels = 1
        val bits = 16
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        raf.writeBytes("RIFF")
        writeInt(36 + dataLen)
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        writeInt(16)
        writeShort(1)
        writeShort(channels)
        writeInt(sampleRate)
        writeInt(byteRate)
        writeShort(blockAlign)
        writeShort(bits)
        raf.writeBytes("data")
        writeInt(dataLen)
    }

    private fun writeInt(value: Int) {
        raf.write(value and 0xff)
        raf.write((value ushr 8) and 0xff)
        raf.write((value ushr 16) and 0xff)
        raf.write((value ushr 24) and 0xff)
    }

    private fun writeShort(value: Int) {
        raf.write(value and 0xff)
        raf.write((value ushr 8) and 0xff)
    }
}

object Pcm {
    fun toFloats(pcm: ByteArray, length: Int): FloatArray {
        val samples = length / 2
        val out = FloatArray(samples)
        var index = 0
        var offset = 0
        while (offset + 1 < length) {
            val lo = pcm[offset].toInt() and 0xff
            val hi = pcm[offset + 1].toInt()
            val sample = (hi shl 8) or lo
            out[index++] = sample / 32768f
            offset += 2
        }
        return out
    }

    fun level(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) sum += sample * sample
        val rms = kotlin.math.sqrt(sum / samples.size).toFloat()
        return (rms / 0.18f).coerceIn(0f, 1f)
    }
}
