package com.alekseevich47.notes.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.ui.components.DateRangeModal
import com.alekseevich47.notes.ui.components.GlassBottomBar
import com.alekseevich47.notes.ui.components.ShellTab
import com.alekseevich47.notes.ui.components.ToastHost
import com.alekseevich47.notes.ui.notes.EditorScreen
import com.alekseevich47.notes.ui.notes.EditorViewModel
import com.alekseevich47.notes.ui.notes.NotesListScreen
import com.alekseevich47.notes.ui.notes.NotesViewModel
import com.alekseevich47.notes.ui.record.RecordSheet
import com.alekseevich47.notes.ui.settings.SettingsScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotesRoot(
    pendingNoteId: Long?,
    askForMic: Boolean,
    onPendingConsumed: () -> Unit,
    onMicPromptConsumed: () -> Unit,
    shell: ShellViewModel = hiltViewModel(),
    notesVm: NotesViewModel = hiltViewModel(),
    editorVm: EditorViewModel = hiltViewModel(),
) {
    val ui by shell.ui.collectAsState()
    val phase by shell.session.phase.collectAsState()
    val level by shell.session.level.collectAsState()
    val live by shell.session.liveText.collectAsState()
    val lastNote by shell.session.lastNoteId.collectAsState()
    val toast by shell.toast.collectAsState()
    val notes by notesVm.notes.collectAsState()
    val query by notesVm.search.collectAsState()
    val range by notesVm.dateRange.collectAsState()
    val hazeState = remember { HazeState() }
    var showCalendar by remember { mutableStateOf(false) }
    var openedDone by remember { mutableStateOf<Long?>(null) }

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permissionState = rememberMultiplePermissionsState(permissions) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) shell.toggleRecording()
        else shell.toast("Нужен доступ к микрофону")
    }

    LaunchedEffect(pendingNoteId) {
        val id = pendingNoteId ?: return@LaunchedEffect
        shell.openEditor(id)
        onPendingConsumed()
    }
    LaunchedEffect(askForMic) {
        if (askForMic) {
            shell.toast("Разрешите микрофон, чтобы записать заметку с виджета")
            permissionState.launchMultiplePermissionRequest()
            onMicPromptConsumed()
        }
    }
    LaunchedEffect(phase, lastNote) {
        val readyId = lastNote
        if (phase == MicPhase.DONE && readyId != null && openedDone != readyId) {
            openedDone = readyId
            shell.openEditor(readyId)
        }
    }

    BackHandler(enabled = ui.screen == Screen.EDITOR || phase == MicPhase.RECORDING || phase == MicPhase.PROCESSING) {
        when {
            phase == MicPhase.RECORDING -> shell.toggleRecording()
            ui.screen == Screen.EDITOR -> editorVm.close { shell.showList() }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                when (ui.screen) {
                    Screen.LIST -> NotesListScreen(
                        notes = notes,
                        query = query,
                        range = range,
                        hazeState = hazeState,
                        onQuery = notesVm::setQuery,
                        onCalendar = { showCalendar = true },
                        onClearRange = { notesVm.setRange(null) },
                        onOpen = shell::openEditor,
                        onCreate = shell::createManual,
                        modifier = Modifier.statusBarsPadding(),
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        onToast = shell::toast,
                        modifier = Modifier.statusBarsPadding(),
                    )
                    Screen.EDITOR -> ui.editorId?.let { id ->
                        EditorScreen(
                            noteId = id,
                            onBack = {
                                shell.showList()
                            },
                            onToast = shell::toast,
                        )
                    }
                }
            }
            val recording = phase == MicPhase.RECORDING || phase == MicPhase.PROCESSING
            if (recording) {
                RecordSheet(
                    visible = true,
                    phase = phase,
                    text = live,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (ui.screen != Screen.EDITOR) {
                GlassBottomBar(
                    hazeState = hazeState,
                    tab = if (ui.screen == Screen.SETTINGS) ShellTab.SETTINGS else ShellTab.NOTES,
                    phase = phase,
                    level = level,
                    onTab = { tab ->
                        when (tab) {
                            ShellTab.NOTES -> shell.showList()
                            ShellTab.SETTINGS -> shell.showSettings()
                        }
                    },
                    onMic = {
                        val micGranted = permissionState.permissions.any { permission ->
                            permission.permission == Manifest.permission.RECORD_AUDIO && permission.status.isGranted
                        }
                        if (phase == MicPhase.RECORDING || micGranted) {
                            shell.toggleRecording()
                        } else {
                            permissionState.launchMultiplePermissionRequest()
                        }
                    },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                )
            }
            ToastHost(message = toast, onDismiss = shell::dismissToast)
            if (showCalendar) {
                DateRangeModal(
                    initial = range,
                    onConfirm = {
                        notesVm.setRange(it)
                        showCalendar = false
                    },
                    onClear = {
                        notesVm.setRange(null)
                        showCalendar = false
                    },
                    onDismiss = { showCalendar = false },
                )
            }
        }
    }
}
