package com.alekseevich47.notes

import com.alekseevich47.notes.data.secure.NotesPrefs
import com.alekseevich47.notes.data.secure.RecognizerChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognizerChoiceTest {
    @Test
    fun effectiveUsesSystemUntilToneIsUsable() {
        assertEquals(NotesPrefs.RECOGNIZER_SYSTEM, RecognizerChoice.effective(null, false))
        assertEquals(NotesPrefs.RECOGNIZER_SYSTEM, RecognizerChoice.effective("tone", false))
        assertEquals(NotesPrefs.RECOGNIZER_TONE, RecognizerChoice.effective(null, true))
        assertEquals(NotesPrefs.RECOGNIZER_SYSTEM, RecognizerChoice.effective("system", true))
        assertEquals(NotesPrefs.RECOGNIZER_TONE, RecognizerChoice.effective("tone", true))
    }
}
