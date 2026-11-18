package com.alekseevich47.notes.player

import java.io.File
import java.io.RandomAccessFile

/** PCM WAV header. Recordings are 16 kHz mono PCM16, but the header is what we trust. */
data class WavPcm(
    val sampleRate: Int,
    val channels: Int,
    val bits: Int,
    val dataOffset: Long,
    val dataBytes: Long,
)

object WavAudio {
    /** AAC in an MPEG-4 container. Android has no built-in MP3 encoder. */
    const val SHARE_MIME: String = "audio/mp4"

    fun shareFileName(noteId: Long?): String {
        return if (noteId != null && noteId > 0L) "note-$noteId.m4a" else "note.m4a"
    }

    fun durationMs(file: File): Long {
        val pcm = read(file) ?: return 0L
        return durationMs(pcm)
    }

    fun durationMs(pcm: WavPcm): Long {
        val bytesPerSample = pcm.bits / 8
        if (pcm.sampleRate <= 0 || pcm.channels <= 0 || bytesPerSample <= 0) return 0L
        val bytesPerSecond = pcm.sampleRate.toLong() * pcm.channels * bytesPerSample
        if (bytesPerSecond <= 0L || pcm.dataBytes <= 0L) return 0L
        return pcm.dataBytes * 1000L / bytesPerSecond
    }

    fun read(file: File): WavPcm? {
        if (!file.isFile || file.length() < 44L) return null
        return try {
            RandomAccessFile(file, "r").use { raf -> read(raf) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun read(raf: RandomAccessFile): WavPcm? {
        val riff = ByteArray(12)
        if (raf.read(riff) != 12) return null
        if (!riff.ascii(0, "RIFF") || !riff.ascii(8, "WAVE")) return null
        var sampleRate = 0
        var channels = 0
        var bits = 0
        while (raf.filePointer + 8 <= raf.length()) {
            val idBytes = ByteArray(4)
            if (raf.read(idBytes) != 4) return null
            val size = Integer.toUnsignedLong(readLeInt(raf))
            val id = String(idBytes, Charsets.US_ASCII)
            val start = raf.filePointer
            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(minOf(size, 16L).toInt())
                    if (fmt.isEmpty() || raf.read(fmt) != fmt.size) return null
                    if (fmt.size >= 16) {
                        channels = leShort(fmt, 2)
                        sampleRate = leInt(fmt, 4)
                        bits = leShort(fmt, 14)
                    }
                }
                "data" -> {
                    if (sampleRate <= 0 || channels <= 0 || bits < 8) return null
                    val available = (raf.length() - start).coerceAtLeast(0L)
                    val dataBytes = minOf(size, available)
                    return WavPcm(sampleRate, channels, bits, start, dataBytes)
                }
            }
            val aligned = size + (size and 1L)
            val next = start + aligned
            if (next <= start || next > raf.length()) return null
            raf.seek(next)
        }
        return null
    }

    private fun ByteArray.ascii(offset: Int, text: String): Boolean {
        if (offset + text.length > size) return false
        for (index in text.indices) {
            if (this[offset + index].toInt().toChar() != text[index]) return false
        }
        return true
    }

    private fun readLeInt(raf: RandomAccessFile): Int {
        val b0 = raf.read()
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) error("truncated wav")
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun leShort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun leInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }
}
