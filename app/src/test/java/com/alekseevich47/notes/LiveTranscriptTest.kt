package com.alekseevich47.notes

import com.alekseevich47.notes.stt.LiveTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTranscriptTest {
    @Test
    fun repeatedPartialUpdatesDoNotGrowExponentially() {
        val phrase = "ну это бывал вот"
        var state = LiveTranscript()
        repeat(8) {
            state = state.onPartial(phrase)
            assertEquals(phrase, state.display)
            assertEquals("", state.committed)
            assertEquals(phrase.length, state.display.length)
        }

        var buggy = ""
        repeat(8) { buggy = LiveTranscript.join(buggy, phrase) }
        assertTrue(buggy.length > phrase.length * 4)
    }

    @Test
    fun growingHypothesisReplacesTheOpenSegment() {
        val steps = listOf(
            "н",
            "ну",
            "ну э",
            "ну это",
            "ну это бывал",
            "ну это бывал вот",
            "ну это бывал вот так",
            "ну это бывал вот так вот",
        )
        var state = LiveTranscript()
        steps.forEach { step ->
            state = state.onPartial(step)
            assertEquals(step, state.display)
            assertEquals("", state.committed)
        }
        assertEquals(steps.last().length, state.display.length)
    }

    @Test
    fun endpointCommitsOnceAndTheNextPartialDoesNotRepeatIt() {
        var state = LiveTranscript()
        listOf("ну", "ну это", "ну это бывал вот").forEach { step ->
            state = state.onPartial(step)
        }
        state = state.onEndpoint()
        assertEquals("ну это бывал вот", state.committed)
        assertEquals("", state.partial)
        assertEquals("ну это бывал вот", state.display)

        listOf("так", "вот так", "вот так вот").forEach { step ->
            state = state.onPartial(step)
            assertEquals("ну это бывал вот $step", state.display)
            assertEquals("ну это бывал вот", state.committed)
        }
    }

    @Test
    fun finishFlushesTheOpenPartialOnce() {
        val open = LiveTranscript(committed = "ну это бывал вот", partial = "вот так вот")
        val finished = open.onFinish()
        assertEquals("ну это бывал вот вот так вот", finished.committed)
        assertEquals("", finished.partial)
        assertEquals(finished.display, finished.onFinish().display)
        assertEquals(finished.committed, finished.onFinish().committed)
    }

    @Test
    fun repeatingAPhraseAfterEndpointKeepsBothCopies() {
        val said = LiveTranscript(partial = "ну это").onEndpoint()
        val again = said.onPartial("ну это").onEndpoint()
        assertEquals("ну это ну это", again.committed)
        assertEquals("", again.partial)
    }

    @Test
    fun preferKeepsAnUnflushedPartialAndRejectsAShorterDisplay() {
        assertEquals(
            "ну это бывал вот",
            LiveTranscript.prefer("ну это", "ну это бывал вот"),
        )
        assertEquals("ну это бывал вот", LiveTranscript.prefer("ну это бывал вот", "ну"))
        assertEquals("ну это бывал вот", LiveTranscript.prefer("ну это бывал вот", "ну это бывал вот"))
    }

    @Test
    fun blankPartialDoesNotAppendASpace() {
        val state = LiveTranscript(committed = "готово").onPartial("   ")
        assertEquals("готово", state.display)
        assertEquals("готово", state.onEndpoint().committed)
        assertEquals("", LiveTranscript().onPartial("  ").display)
    }
}
