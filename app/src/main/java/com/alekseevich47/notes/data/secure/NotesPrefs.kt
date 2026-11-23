package com.alekseevich47.notes.data.secure

import android.content.Context
import android.content.SharedPreferences

/**
 * Plain preferences the widget process can read without Hilt.
 * [SecureSettingsStore] mirrors the warmup switch and the recognizer choice here.
 * The theme lives here as well, so the first frame does not open the keystore.
 */
object NotesPrefs {
    const val FILE = "notes_widget_prefs"
    const val WARMUP_ON_BOOT = "warmup_on_boot"
    const val RECOGNIZER = "recognizer"
    const val RECOGNIZER_TONE = "tone"
    const val RECOGNIZER_SYSTEM = "system"
    const val SEARCH_INDEX_V2 = "search_index_v2"
    const val THEME_MODE = "theme_mode"
    const val LOG_SESSION = "log_session"
    const val LAST_EXIT_LOGGED_TS = "last_exit_logged_ts"

    fun open(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    /** Anything other than [RECOGNIZER_SYSTEM] is T-one, including a missing value. */
    fun recognizerOf(raw: String?): String {
        return if (raw == RECOGNIZER_SYSTEM) RECOGNIZER_SYSTEM else RECOGNIZER_TONE
    }
}
