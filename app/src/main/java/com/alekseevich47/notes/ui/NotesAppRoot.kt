package com.alekseevich47.notes.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.domain.MicVisualState
import com.alekseevich47.notes.domain.NoteBody
import com.alekseevich47.notes.service.RecordingService
import com.alekseevich47.notes.stt.RecordingController
import com.alekseevich47.notes.ui.components.LiquidGlassPlate
import com.alekseevich47.notes.ui.components.MicButton
import com.alekseevich47.notes.ui.components.NotesDialog
import com.alekseevich47.notes.ui.components.PinDialog
import com.alekseevich47.notes.ui.editor.RichNoteEditor
import com.alekseevich47.notes.ui.notes.NoteDetailRoute
import com.alekseevich47.notes.ui.notes.NotesRoute
import com.alekseevich47.notes.ui.record.RecordSheet
import com.alekseevich47.notes.ui.record.RecordViewModel
import com.alekseevich47.notes.ui.settings.SettingsRoute
import com.alekseevich47.notes.ui.settings.SettingsViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import javax.inject.Inject
import kotlinx.coroutines.launch

object Routes {
    const val NOTES = "notes"
    const val SETTINGS = "settings"
    const val NOTE = "note/{id}"
    const val CREATE = "create"
    fun note(id: Long) = "note/$id"
}

@HiltViewModel
class RootViewModel @Inject constructor(
    val recording: RecordingController,
    private val repository: NotesRepository,
) : ViewModel() {
    fun createManual(title: String, body: NoteBody, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createNote(title, body)
            onCreated(id)
        }
    }

    fun verifyPin(pin: String) = PinHasher.matchesPin(pin, repository.settings.value.pinHash)
    val settings = repository.settings
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotesAppRoot(
    openNoteId: Long?,
    onOpenNoteConsumed: () -> Unit,
    rootViewModel: RootViewModel = hiltViewModel(),
    recordViewModel: RecordViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val hazeState = remember { HazeState() }
    val context = LocalContext.current
    val recState by rootViewModel.recording.state.collectAsStateWithLifecycle()
    val settings by rootViewModel.settings.collectAsStateWithLifecycle()
    var showRecord by remember { mutableStateOf(false) }
    var showMicHelp by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }

    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val notifPermission = if (Build.VERSION.SDK_INT >= 33) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    LaunchedEffect(openNoteId) {
        val id = openNoteId ?: return@LaunchedEffect
        navController.navigate(Routes.note(id))
        onOpenNoteConsumed()
    }

    LaunchedEffect(recState.active, recState.processing, recState.done) {
        if (recState.active || recState.processing || recState.done) {
            showRecord = true
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val micVisual = when {
        recState.processing -> MicVisualState.PROCESSING
        recState.done && showRecord -> MicVisualState.DONE
        recState.active -> MicVisualState.RECORDING
        else -> MicVisualState.IDLE
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            navController = navController,
            startDestination = Routes.NOTES,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            enterTransition = {
                fadeIn(tween(160)) + slideInHorizontally(tween(160)) { it / 12 }
            },
            exitTransition = {
                fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 12 }
            },
            popEnterTransition = {
                fadeIn(tween(160)) + slideInHorizontally(tween(160)) { -it / 12 }
            },
            popExitTransition = {
                fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 12 }
            },
        ) {
            composable(Routes.NOTES) {
                NotesRoute(
                    hazeState = hazeState,
                    onOpenNote = { navController.navigate(Routes.note(it)) },
                    onCreateNote = { navController.navigate(Routes.CREATE) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(viewModel = settingsViewModel)
            }
            composable(
                Routes.NOTE,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                NoteDetailRoute(
                    noteId = id,
                    onBack = { navController.popBackStack() },
                    onNeedPinSetup = { showPinSetup = true },
                )
            }
            composable(Routes.CREATE) {
                ManualCreateRoute(
                    onBack = { navController.popBackStack() },
                    onCreated = { id ->
                        navController.popBackStack()
                        navController.navigate(Routes.note(id))
                    },
                    onNeedPinSetup = { showPinSetup = true },
                    rootViewModel = rootViewModel,
                )
            }
        }

        RecordSheet(
            visible = showRecord,
            onClose = { showRecord = false },
            onNeedModels = {
                showRecord = false
                navController.navigate(Routes.SETTINGS)
            },
            onNeedPinSetup = { showPinSetup = true },
            viewModel = recordViewModel,
        )

        // Bottom glass menu
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .zIndex(4f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            LiquidGlassPlate(
                hazeState = hazeState,
                shape = RoundedCornerShape(28.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = {
                        showRecord = false
                        navController.navigate(Routes.NOTES) {
                            popUpTo(Routes.NOTES) { inclusive = false }
                            launchSingleTop = true
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Notes,
                            contentDescription = "Заметки",
                            tint = if (route == Routes.NOTES) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    MicButton(
                        state = micVisual,
                        amplitude = recState.amplitude,
                        size = 58.dp,
                        onClick = {
                            if (!micPermission.status.isGranted) {
                                micPermission.launchPermissionRequest()
                                return@MicButton
                            }
                            notifPermission?.let {
                                if (!it.status.isGranted) it.launchPermissionRequest()
                            }
                            if (!rootViewModel.recording.canRecord() && !recState.active) {
                                showMicHelp = true
                                return@MicButton
                            }
                            showRecord = true
                            if (!recState.active && !recState.processing) {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, RecordingService::class.java)
                                        .setAction(RecordingService.ACTION_START),
                                )
                            } else if (recState.active) {
                                context.startService(
                                    Intent(context, RecordingService::class.java)
                                        .setAction(RecordingService.ACTION_STOP),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        showRecord = false
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Настройки",
                            tint = if (route == Routes.SETTINGS) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    if (showMicHelp) {
        NotesDialog(
            title = "Нужны модели или доступ к распознаванию",
            onDismiss = { showMicHelp = false },
            confirmLabel = "В настройки",
            onConfirm = {
                showMicHelp = false
                navController.navigate(Routes.SETTINGS)
            },
        ) {
            Text(
                "Скачайте T-one / GigaAM в настройках для офлайн-режима. " +
                    "Пока моделей нет, на многих телефонах доступен быстрый онлайн-распознаватель Google.",
            )
        }
    }

    if (showPinSetup) {
        PinDialog(
            title = "Задайте PIN (4 цифры)",
            onDismiss = { showPinSetup = false },
            onSubmit = {
                settingsViewModel.setPin(it)
                showPinSetup = false
            },
        )
    }
}

@Composable
private fun ManualCreateRoute(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    onNeedPinSetup: () -> Unit,
    rootViewModel: RootViewModel,
) {
    val settings by rootViewModel.settings.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf(NoteBody()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .padding(bottom = 110.dp),
    ) {
        Text("Новая заметка", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        RichNoteEditor(
            title = title,
            onTitleChange = { title = it },
            body = body,
            onBodyChange = { body = it },
            pinHash = settings.pinHash,
            onNeedPinSetup = onNeedPinSetup,
            verifyPin = rootViewModel::verifyPin,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                rootViewModel.createManual(title.ifBlank { "Заметка" }, body, onCreated)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Сохранить") }
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Отмена") }
    }
}
