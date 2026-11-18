package com.alekseevich47.notes

import com.alekseevich47.notes.stt.LiveMode
import com.alekseevich47.notes.stt.LiveTranscriptRoute
import com.alekseevich47.notes.stt.MicRouting
import com.alekseevich47.notes.stt.ToneStartup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicRoutingTest {
    @Test
    fun loadedToneNeverStartsSpeechRecognizer() {
        assertEquals(LiveTranscriptRoute.TONE_ON_RECORD, MicRouting.route(toneReady = true))
    }

    @Test
    fun withoutToneTheFileWriterStartsFirst() {
        assertEquals(LiveTranscriptRoute.PLATFORM_AFTER_RECORD, MicRouting.route(toneReady = false))
    }

    @Test
    fun missingOrDisabledToneDoesNotBlockTheMicrophone() {
        assertEquals(ToneStartup.PARALLEL, MicRouting.toneStartup(installed = true, enabled = true))
        assertEquals(ToneStartup.SKIP, MicRouting.toneStartup(installed = true, enabled = false))
        assertEquals(ToneStartup.SKIP, MicRouting.toneStartup(installed = false, enabled = true))
        assertEquals(ToneStartup.SKIP, MicRouting.toneStartup(installed = false, enabled = false))
    }

    @Test
    fun systemRecognizerNeverLoadsTone() {
        assertEquals(LiveMode.SYSTEM, MicRouting.liveMode(recognizer = "system", warmupOnBoot = true))
        assertEquals(LiveMode.SYSTEM, MicRouting.liveMode(recognizer = "system", warmupOnBoot = false))
    }

    @Test
    fun toneWithWarmupReusesOrLoads() {
        assertEquals(LiveMode.TONE_WARM, MicRouting.liveMode(recognizer = "tone", warmupOnBoot = true))
        assertEquals("речь", MicRouting.liveLine(LiveMode.TONE_WARM, "речь"))
    }

    @Test
    fun toneWithoutWarmupKeepsTheHintAndDropsSpeech() {
        assertEquals(LiveMode.TONE_COLD, MicRouting.liveMode(recognizer = "tone", warmupOnBoot = false))
        assertEquals(LiveMode.TONE_COLD, MicRouting.liveMode(recognizer = "other", warmupOnBoot = false))
        val said = "привет мир"
        val line = MicRouting.liveLine(LiveMode.TONE_COLD, said)
        assertEquals(
            "Чтобы видеть текст во время записи, включите в настройках автозагрузку моделей или системное распознавание.",
            line,
        )
        assertEquals(MicRouting.COLD_TONE_TEXT, line)
        assertFalse(line.contains(said))
        assertFalse(line.contains("привет"))
    }

    @Test
    fun disabledGigaKeepsTheLiveTranscript() {
        assertTrue(MicRouting.useGiga(installed = true, enabled = true))
        assertFalse(MicRouting.useGiga(installed = true, enabled = false))
        assertFalse(MicRouting.useGiga(installed = false, enabled = true))
    }
}
