package com.alekseevich47.notes.data.secure

object RecognizerChoice {
    /** stored: сохранённое значение или null, если ключа нет. */
    fun effective(stored: String?, toneUsable: Boolean): String = when {
        !toneUsable -> NotesPrefs.RECOGNIZER_SYSTEM
        stored == NotesPrefs.RECOGNIZER_SYSTEM -> NotesPrefs.RECOGNIZER_SYSTEM
        else -> NotesPrefs.RECOGNIZER_TONE
    }
}
