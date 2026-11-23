package com.alekseevich47.notes

import com.alekseevich47.notes.data.secure.NotesPrefs
import com.alekseevich47.notes.stt.sherpaNumThreads
import org.junit.Assert.assertEquals
import org.junit.Test

class SherpaThreadsTest {
    @Test
    fun usesAtLeastTwoAndAtMostFourThreads() {
        assertEquals(2, sherpaNumThreads(1))
        assertEquals(2, sherpaNumThreads(2))
        assertEquals(3, sherpaNumThreads(3))
        assertEquals(4, sherpaNumThreads(4))
        assertEquals(4, sherpaNumThreads(8))
    }
}

class NotesPrefsTest {
    @Test
    fun recognizerDefaultsToToneUnlessSystem() {
        assertEquals(NotesPrefs.RECOGNIZER_TONE, NotesPrefs.recognizerOf(null))
        assertEquals(NotesPrefs.RECOGNIZER_TONE, NotesPrefs.recognizerOf("tone"))
        assertEquals(NotesPrefs.RECOGNIZER_TONE, NotesPrefs.recognizerOf("other"))
        assertEquals(NotesPrefs.RECOGNIZER_SYSTEM, NotesPrefs.recognizerOf("system"))
        assertEquals("notes_widget_prefs", NotesPrefs.FILE)
        assertEquals("warmup_on_boot", NotesPrefs.WARMUP_ON_BOOT)
        assertEquals("recognizer", NotesPrefs.RECOGNIZER)
        assertEquals("theme_mode", NotesPrefs.THEME_MODE)
    }
}
