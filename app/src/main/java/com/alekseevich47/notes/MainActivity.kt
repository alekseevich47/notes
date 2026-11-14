package com.alekseevich47.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.alekseevich47.notes.ui.NotesAppRoot
import com.alekseevich47.notes.ui.theme.NotesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var openNoteId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        openNoteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it > 0 }
        setContent {
            NotesTheme {
                NotesAppRoot(
                    openNoteId = openNoteId,
                    onOpenNoteConsumed = { openNoteId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNoteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it > 0 }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val ACTION_OPEN_NOTE = "com.alekseevich47.notes.OPEN_NOTE"
    }
}
