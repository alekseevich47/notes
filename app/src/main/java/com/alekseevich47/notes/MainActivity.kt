package com.alekseevich47.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alekseevich47.notes.data.secure.SecureSettingsStore
import com.alekseevich47.notes.ui.NotesRoot
import com.alekseevich47.notes.ui.theme.NotesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SecureSettingsStore

    private var openNoteId by mutableLongStateOf(-1L)
    private var askMic by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            val theme by settings.themeMode.collectAsState()
            NotesTheme(themeMode = theme) {
                NotesRoot(
                    pendingNoteId = openNoteId.takeIf { it > 0 },
                    askForMic = askMic,
                    onPendingConsumed = { openNoteId = -1L },
                    onMicPromptConsumed = { askMic = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_NOTE_ID, -1L) ?: -1L
        if (id > 0) openNoteId = id
        if (intent?.getBooleanExtra(EXTRA_NEED_MIC, false) == true) askMic = true
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_NEED_MIC = "need_mic"
    }
}
