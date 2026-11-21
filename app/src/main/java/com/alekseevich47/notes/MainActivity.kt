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
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.service.ModelWarmupService
import com.alekseevich47.notes.ui.NotesRoot
import com.alekseevich47.notes.ui.theme.NotesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SecureSettingsStore

    private var openNoteId by mutableLongStateOf(-1L)
    private var askMic by mutableStateOf(false)
    private var openRecording by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate restored=${savedInstanceState != null}")
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            val theme by settings.themeMode.collectAsState()
            NotesTheme(themeMode = theme) {
                NotesRoot(
                    pendingNoteId = openNoteId.takeIf { it > 0 },
                    askForMic = askMic,
                    openRecording = openRecording,
                    onPendingConsumed = { openNoteId = -1L },
                    onMicPromptConsumed = { askMic = false },
                    onOpenRecordingConsumed = { openRecording = false },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLog.i(TAG, "onStart")
        ModelWarmupService.start(this)
    }

    override fun onResume() {
        super.onResume()
        AppLog.i(TAG, "onResume")
    }

    override fun onPause() {
        AppLog.i(TAG, "onPause")
        super.onPause()
    }

    override fun onStop() {
        AppLog.i(TAG, "onStop")
        super.onStop()
    }

    override fun onDestroy() {
        AppLog.i(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        AppLog.i(TAG, "onNewIntent")
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_NOTE_ID, -1L) ?: -1L
        val needMic = intent?.getBooleanExtra(EXTRA_NEED_MIC, false) == true
        val openRec = intent?.getBooleanExtra(EXTRA_OPEN_RECORDING, false) == true
        AppLog.i(TAG, "intent hasNote=${id > 0} needMic=$needMic openRecording=$openRec")
        if (id > 0) openNoteId = id
        if (needMic) askMic = true
        if (openRec) openRecording = true
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_NEED_MIC = "need_mic"
        const val EXTRA_OPEN_RECORDING = "open_recording"
    }
}
