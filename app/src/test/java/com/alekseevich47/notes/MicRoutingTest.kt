package com.alekseevich47.notes

import com.alekseevich47.notes.stt.LiveTranscriptRoute
import com.alekseevich47.notes.stt.MicRouting
import org.junit.Assert.assertEquals
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
}
