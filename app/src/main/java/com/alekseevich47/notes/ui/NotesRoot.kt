package com.alekseevich47.notes.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alekseevich47.notes.R
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.ui.components.AppDialog
import com.alekseevich47.notes.ui.components.DateRangeModal
import com.alekseevich47.notes.ui.components.GlassBottomBar
import com.alekseevich47.notes.ui.components.ShellTab
import com.alekseevich47.notes.ui.components.ToastHost
import com.alekseevich47.notes.service.PlaybackService
import com.alekseevich47.notes.ui.notes.EditorScreen
import com.alekseevich47.notes.ui.notes.EditorViewModel
import com.alekseevich47.notes.ui.notes.NotesListScreen
import com.alekseevich47.notes.ui.notes.NotesViewModel
import com.alekseevich47.notes.ui.player.StickyPlayer
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
    openRecording: Boolean = false,
    onOpenRecordingConsumed: () -> Unit = {},
    shell: ShellViewModel = hiltViewModel(),
    notesVm: NotesViewModel = hiltViewModel(),
    editorVm: EditorViewModel = hiltViewModel(),
) {
    val ui by shell.ui.collectAsStateWithLifecycle()
    val phase by shell.session.phase.collectAsStateWithLifecycle()
    val lastNote by shell.session.lastNoteId.collectAsStateWithLifecycle()
    val toast by shell.toast.collectAsStateWithLifecycle()
    // Playback drives the sticky player, which sits outside the list.
    val playback by editorVm.playerUi.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    var stickyPx by remember { mutableIntStateOf(0) }
    val showSticky = ui.screen != Screen.EDITOR && playback.engaged && playback.noteId != null
    val insetTarget = if (showSticky) with(density) { stickyPx.toDp() } + 8.dp else 0.dp
    val listInset by animateDpAsState(insetTarget, tween(240), label = "sticky-inset")
    val hazeState = remember { HazeState() }
    var openedDone by remember { mutableStateOf<Long?>(null) }
    var explainPermissions by remember { mutableStateOf(false) }
    val micDenied = stringResource(R.string.permission_mic_denied)

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permissionState = rememberMultiplePermissionsState(permissions) { result ->
        val micGranted = result[Manifest.permission.RECORD_AUDIO] == true
        val notifications = result[Manifest.permission.POST_NOTIFICATIONS]
        val notificationText = when (notifications) {
            true -> "granted"
            false -> "denied"
            null -> "n/a"
        }
        AppLog.i("Permissions", "mic ${if (micGranted) "granted" else "denied"} notifications $notificationText")
        if (micGranted) shell.toggleRecording()
        else shell.toast(micDenied)
    }

    LaunchedEffect(pendingNoteId) {
        val id = pendingNoteId ?: return@LaunchedEffect
        shell.openEditor(id)
        onPendingConsumed()
    }
    LaunchedEffect(askForMic) {
        if (askForMic) {
            explainPermissions = true
            onMicPromptConsumed()
        }
    }
    LaunchedEffect(openRecording) {
        if (!openRecording) return@LaunchedEffect
        val live = shell.session.phase.value == MicPhase.RECORDING ||
            shell.session.phase.value == MicPhase.PROCESSING
        if (live && shell.ui.value.screen != Screen.LIST) shell.showList()
        onOpenRecordingConsumed()
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
                    Screen.LIST -> NotesListPage(
                        notesVm = notesVm,
                        hazeState = hazeState,
                        playingNoteId = playback.noteId,
                        onOpen = shell::openEditor,
                        onCreate = shell::createManual,
                        bottomInset = listInset,
                        modifier = Modifier.statusBarsPadding(),
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        onToast = shell::toast,
                        modifier = Modifier.statusBarsPadding(),
                    )
                    Screen.EDITOR -> ui.editorId?.let { id ->
                        key(id) {
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
            }
            val recording = phase == MicPhase.RECORDING || phase == MicPhase.PROCESSING
            if (recording) {
                RecordSheet(
                    visible = true,
                    phase = phase,
                    text = shell.session.liveText,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showSticky) {
                StickyPlayer(
                    state = playback,
                    onToggle = {
                        context.startService(
                            Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_TOGGLE),
                        )
                    },
                    onSeek = editorVm.player::seekFraction,
                    onDismiss = {
                        context.startService(
                            Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_STOP),
                        )
                    },
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 104.dp)
                        .onSizeChanged { stickyPx = it.height },
                )
            }
            if (ui.screen != Screen.EDITOR) {
                GlassBottomBar(
                    hazeState = hazeState,
                    tab = if (ui.screen == Screen.SETTINGS) ShellTab.SETTINGS else ShellTab.NOTES,
                    phase = phase,
                    level = shell.session.level,
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
                            explainPermissions = true
                        }
                    },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                )
            }
            ToastHost(message = toast, onDismiss = shell::dismissToast)
            if (explainPermissions) {
                AppDialog(
                    title = stringResource(R.string.permission_mic_title),
                    body = stringResource(R.string.permission_mic_rationale),
                    confirm = stringResource(R.string.permission_continue),
                    onConfirm = {
                        explainPermissions = false
                        permissionState.launchMultiplePermissionRequest()
                    },
                    onDismiss = { explainPermissions = false },
                )
            }
        }
    }
}

/**
 * List-only subscription. Search results must not recompose the editor or the mic.
 */
@Composable
private fun NotesListPage(
    notesVm: NotesViewModel,
    hazeState: HazeState,
    playingNoteId: Long?,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val notes by notesVm.notes.collectAsStateWithLifecycle()
    val query by notesVm.search.collectAsStateWithLifecycle()
    val range by notesVm.dateRange.collectAsStateWithLifecycle()
    var showCalendar by remember { mutableStateOf(false) }
    val context = LocalContext.current
    NotesListScreen(
        notes = notes,
        query = query,
        range = range,
        hazeState = hazeState,
        onQuery = notesVm::setQuery,
        onCalendar = { showCalendar = true },
        onClearRange = { notesVm.setRange(null) },
        onOpen = onOpen,
        onCreate = onCreate,
        onDeleteSelected = { ids ->
            if (playingNoteId != null && playingNoteId in ids) {
                context.startService(
                    Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_STOP),
                )
            }
            notesVm.deleteMany(ids)
        },
        bottomInset = bottomInset,
        modifier = modifier,
    )
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
