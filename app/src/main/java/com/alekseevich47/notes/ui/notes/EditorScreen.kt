package com.alekseevich47.notes.ui.notes

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.alekseevich47.notes.service.PlaybackService
import com.alekseevich47.notes.ui.components.AppDialog
import com.alekseevich47.notes.ui.components.PinDialog
import com.alekseevich47.notes.ui.components.TableSizeDialog
import com.alekseevich47.notes.ui.editor.EditorFormatBar
import com.alekseevich47.notes.ui.editor.NoteEditorController
import com.alekseevich47.notes.ui.editor.RichNoteEditor
import com.alekseevich47.notes.ui.editor.TableGrid
import com.alekseevich47.notes.ui.player.MiniPlayer
import com.alekseevich47.notes.ui.theme.NotesFont

private enum class HiddenPinStage { UNLOCK, CREATE, CONFIRM }

@Composable
fun EditorScreen(
    noteId: Long,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val note by viewModel.note.collectAsState()
    val unlocked by viewModel.unlocked.collectAsState()
    val player by viewModel.playerUi.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var askTable by remember { mutableStateOf(false) }
    var pinStage by remember { mutableStateOf(HiddenPinStage.UNLOCK) }
    var draftPin by remember { mutableStateOf<String?>(null) }
    val formats = remember { NoteEditorController() }
    val bodyFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()
    val bodyTap = remember { MutableInteractionSource() }
    val context = LocalContext.current

    LaunchedEffect(noteId) {
        formats.bodyFocused = false
        viewModel.bind(noteId)
        askPin = false
        pinError = null
        draftPin = null
    }

    fun focusBody() {
        bodyFocus.requestFocus()
        keyboard?.show()
    }

    fun openPinPrompt() {
        if (askPin) return
        pinStage = if (viewModel.hasPin()) HiddenPinStage.UNLOCK else HiddenPinStage.CREATE
        draftPin = null
        pinError = null
        askPin = true
    }

    val current = note
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.statusBars
                    .union(WindowInsets.navigationBars)
                    .union(WindowInsets.ime),
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { viewModel.close(onBack) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
            }
        }
        if (current == null) {
            Text("Открываю…", modifier = Modifier.padding(20.dp), fontFamily = NotesFont)
        } else {
            BasicTextField(
                value = current.title,
                onValueChange = viewModel::setTitle,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = NotesFont,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val minBody = maxHeight
                Box(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .heightIn(min = minBody)
                        .clickable(
                            interactionSource = bodyTap,
                            indication = null,
                            onClickLabel = "Начните писать",
                            onClick = { focusBody() },
                        )
                        .padding(horizontal = 20.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        key(current.id) {
                            RichNoteEditor(
                                document = current.document,
                                hiddenUnlocked = unlocked,
                                onDocument = viewModel::setDocument,
                                onNeedSelection = { onToast("Выделите фрагмент") },
                                onRequestUnlock = { openPinPrompt() },
                                onAskTable = { askTable = true },
                                focusRequester = bodyFocus,
                                formats = formats,
                                pinDialogVisible = askPin,
                            )
                        }
                        current.document.tables.forEach { table ->
                            TableGrid(
                                table = table,
                                onChange = viewModel::updateTable,
                                onDelete = { viewModel.removeTable(table.id) },
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            if (current.audioPath != null) {
                MiniPlayer(
                    state = player,
                    active = true,
                    onToggle = {
                        val path = current.audioPath ?: return@MiniPlayer
                        if (player.noteId == current.id) {
                            val intent = Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_TOGGLE)
                            context.startService(intent)
                        } else {
                            val intent = Intent(context, PlaybackService::class.java)
                                .setAction(PlaybackService.ACTION_PLAY)
                                .putExtra(PlaybackService.EXTRA_NOTE_ID, current.id)
                                .putExtra(PlaybackService.EXTRA_PATH, path)
                                .putExtra(PlaybackService.EXTRA_TITLE, current.title)
                            ContextCompat.startForegroundService(context, intent)
                        }
                    },
                    onSeek = { fraction ->
                        if (player.noteId == current.id) {
                            viewModel.player.seekFraction(fraction)
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = formats.bodyFocused,
                enter = slideInVertically(animationSpec = tween(180)) { it } + fadeIn(tween(160)),
                exit = slideOutVertically(animationSpec = tween(140)) { it } + fadeOut(tween(120)),
            ) {
                EditorFormatBar(formats)
            }
        }
    }
    if (confirmDelete) {
        AppDialog(
            title = "Удалить заметку?",
            body = "Аудио тоже будет удалено с устройства.",
            confirm = "Удалить",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (askPin) {
        PinDialog(
            title = when (pinStage) {
                HiddenPinStage.UNLOCK -> "PIN"
                HiddenPinStage.CREATE -> "Новый PIN"
                HiddenPinStage.CONFIRM -> "Повторите PIN"
            },
            subtitle = when (pinStage) {
                HiddenPinStage.UNLOCK -> "Четыре цифры, чтобы открыть скрытый текст"
                HiddenPinStage.CREATE -> "PIN не задан. Придумайте 4 цифры — они откроют скрытый текст"
                HiddenPinStage.CONFIRM -> "Введите те же 4 цифры ещё раз"
            },
            error = pinError,
            onSubmit = { pin ->
                when (pinStage) {
                    HiddenPinStage.CREATE -> {
                        draftPin = pin
                        pinStage = HiddenPinStage.CONFIRM
                        pinError = null
                    }
                    HiddenPinStage.CONFIRM -> {
                        when (viewModel.saveCreatedPin(draftPin.orEmpty(), pin)) {
                            EditorViewModel.PinSetup.SAVED -> {
                                askPin = false
                                pinError = null
                                draftPin = null
                                onToast("PIN сохранён")
                            }
                            EditorViewModel.PinSetup.MISMATCH -> {
                                draftPin = null
                                pinStage = HiddenPinStage.CREATE
                                pinError = "Не совпало, начните снова"
                            }
                            EditorViewModel.PinSetup.RESET -> {
                                askPin = false
                                pinError = null
                                draftPin = null
                                onToast("PIN сброшен")
                            }
                        }
                    }
                    HiddenPinStage.UNLOCK -> {
                        when (viewModel.unlock(pin)) {
                            EditorViewModel.PinResult.OK -> {
                                askPin = false
                                pinError = null
                            }
                            EditorViewModel.PinResult.RESET -> {
                                askPin = false
                                pinError = null
                                onToast("PIN сброшен")
                            }
                            EditorViewModel.PinResult.WRONG -> pinError = "Неверный PIN"
                            EditorViewModel.PinResult.MISSING -> {
                                draftPin = null
                                pinStage = HiddenPinStage.CREATE
                                pinError = null
                            }
                        }
                    }
                }
            },
            onDismiss = {
                askPin = false
                pinError = null
                draftPin = null
            },
        )
    }
    if (askTable) {
        TableSizeDialog(
            onCreate = { rows, cols ->
                viewModel.addTable(rows, cols)
                askTable = false
            },
            onDismiss = { askTable = false },
        )
    }
}
