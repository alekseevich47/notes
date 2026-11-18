package com.alekseevich47.notes

import com.alekseevich47.notes.data.audio.WavWriter
import com.alekseevich47.notes.player.WavAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WavAudioTest {
    @Test
    fun durationMatchesOneSecondOfPcm() {
        val file = File.createTempFile("note", ".wav")
        try {
            WavWriter(file, 16_000).use { writer ->
                writer.write(ByteArray(16_000 * 2), 16_000 * 2)
            }
            assertEquals(1_000L, WavAudio.durationMs(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun emptyOrForeignFileHasNoDuration() {
        val empty = File.createTempFile("empty", ".wav")
        val text = File.createTempFile("text", ".wav")
        try {
            WavWriter(empty, 16_000).use { }
            assertEquals(0L, WavAudio.durationMs(empty))
            text.writeText("not a wav")
            assertNull(WavAudio.read(text))
            assertEquals(0L, WavAudio.durationMs(text))
            assertEquals(0L, WavAudio.durationMs(File(empty.parentFile, "missing-note.wav")))
        } finally {
            empty.delete()
            text.delete()
        }
    }

    @Test
    fun shareContainerIsAacInM4aNotMp3() {
        assertEquals("audio/mp4", WavAudio.SHARE_MIME)
        assertEquals("note-12.m4a", WavAudio.shareFileName(12L))
        assertEquals("note.m4a", WavAudio.shareFileName(null))
        assertTrue(WavAudio.shareFileName(12L).endsWith(".m4a"))
    }
}
