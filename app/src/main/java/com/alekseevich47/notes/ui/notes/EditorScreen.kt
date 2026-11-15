package com.alekseevich47.notes.ui.notes

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.alekseevich47.notes.service.PlaybackService
import com.alekseevich47.notes.ui.components.AppDialog
import com.alekseevich47.notes.ui.components.PinDialog
import com.alekseevich47.notes.ui.components.TableSizeDialog
import com.alekseevich47.notes.ui.editor.RichNoteEditor
import com.alekseevich47.notes.ui.editor.TableGrid
import com.alekseevich47.notes.ui.player.MiniPlayer
import com.alekseevich47.notes.ui.theme.NotesFont

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
    val context = LocalContext.current

    LaunchedEffect(noteId) { viewModel.bind(noteId) }

    val current = note
    Column(modifier.fillMaxSize().statusBarsPadding().imePadding()) {
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
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                BasicTextField(
                    value = current.title,
                    onValueChange = viewModel::setTitle,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = NotesFont,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                key(current.id) {
                    RichNoteEditor(
                        document = current.document,
                        hiddenUnlocked = unlocked,
                        onDocument = viewModel::setDocument,
                        onNeedSelection = { onToast("Выделите фрагмент") },
                        onRequestUnlock = { askPin = true },
                        onAskTable = { askTable = true },
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
            title = "PIN",
            subtitle = if (viewModel.hasPin()) "Четыре цифры, чтобы открыть скрытый текст" else "Сначала задайте PIN в настройках",
            error = pinError,
            onSubmit = { pin ->
                if (!viewModel.hasPin()) {
                    pinError = "PIN ещё не задан"
                    return@PinDialog
                }
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
                    EditorViewModel.PinResult.MISSING -> pinError = "PIN ещё не задан"
                }
            },
            onDismiss = {
                askPin = false
                pinError = null
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
