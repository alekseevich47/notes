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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.alekseevich47.notes.domain.BlockCaret
import com.alekseevich47.notes.domain.BlockKind
import com.alekseevich47.notes.domain.EditKind
import com.alekseevich47.notes.domain.NoteBlock
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.player.PlaybackSnapshot
import com.alekseevich47.notes.service.PlaybackService
import com.alekseevich47.notes.ui.components.AppDialog
import com.alekseevich47.notes.ui.components.PinDialog
import com.alekseevich47.notes.ui.components.TableSizeDialog
import com.alekseevich47.notes.ui.editor.EditorFormatBar
import com.alekseevich47.notes.ui.editor.ListAutoFormat
import com.alekseevich47.notes.ui.editor.NoteEditorController
import com.alekseevich47.notes.ui.editor.NoteSurfaceBridge
import com.alekseevich47.notes.ui.editor.RichNoteEditor
import com.alekseevich47.notes.ui.editor.TableBlock
import com.alekseevich47.notes.ui.editor.observeSelectionTaps
import com.alekseevich47.notes.ui.player.MiniPlayer
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesMuted
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class HiddenPinStage { UNLOCK, CREATE, CONFIRM }

private data class CaretRequest(val token: Int = 0, val blockIndex: Int = 0, val offset: Int = 0)

@Composable
fun EditorScreen(
    noteId: Long,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val note by viewModel.note.collectAsStateWithLifecycle()
    val unreadable by viewModel.unreadable.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val player by viewModel.playerUi.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var askTable by remember { mutableStateOf(false) }
    var pinStage by remember { mutableStateOf(HiddenPinStage.UNLOCK) }
    var draftPin by remember { mutableStateOf<String?>(null) }
    val formats = remember { NoteEditorController() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()
    val bodyTap = remember { MutableInteractionSource() }
    var insertBlock by remember { mutableIntStateOf(0) }
    var insertCursor by remember { mutableIntStateOf(0) }
    var caretRequest by remember { mutableStateOf(CaretRequest()) }
    val caretJump by viewModel.caretJump.collectAsStateWithLifecycle()
    LaunchedEffect(caretJump?.token) {
        val jump = caretJump ?: return@LaunchedEffect
        caretRequest = CaretRequest(
            token = caretRequest.token + 1,
            blockIndex = jump.caret.blockIndex,
            offset = jump.caret.offset,
        )
    }
    val surface = remember { NoteSurfaceBridge() }
    var bodyCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    SideEffect {
        surface.toRoot = { local ->
            val coords = bodyCoords
            if (coords != null && coords.isAttached) coords.localToRoot(local) else null
        }
    }
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.flush()
    }
    LaunchedEffect(noteId) {
        formats.bodyFocused = false
        caretRequest = CaretRequest()
        viewModel.bind(noteId)
        askPin = false
        pinError = null
        draftPin = null
        viewModel.player.onEditorNote(noteId)
    }
    LaunchedEffect(noteId, note?.id, note?.audioPath) {
        val shown = note ?: return@LaunchedEffect
        if (shown.id != noteId) return@LaunchedEffect
        viewModel.player.followOpenNote(shown.id, shown.audioPath, shown.title)
    }
    LaunchedEffect(noteId, note?.id, unreadable) {
        if (unreadable && note?.id == noteId) onToast("Не удалось открыть заметку")
    }
    fun openPinPrompt() {
        if (askPin) return
        pinStage = if (viewModel.hasPin()) HiddenPinStage.UNLOCK else HiddenPinStage.CREATE
        draftPin = null
        pinError = null
        askPin = true
    }

    // bind() runs after the first frame, and this ViewModel still holds the note
    // that was open before. Paint nothing until the loaded note is the one requested.
    val current = note?.takeIf { it.id == noteId }
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
            IconButton(onClick = viewModel::undo, enabled = canUndo && current != null) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Отменить")
            }
            IconButton(onClick = viewModel::redo, enabled = canRedo && current != null) {
                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Вернуть")
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
            }
        }
        if (current == null) {
            Text("Открываю…", modifier = Modifier.padding(20.dp), fontFamily = NotesFont)
        } else {
            BasicTextField(
                value = current.title,
                onValueChange = viewModel::setTitle,
                readOnly = unreadable,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                ),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = NotesFont,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            val edited = lastEditedCaption(current.createdAt, current.updatedAt)
            if (edited != null) {
                Text(
                    edited,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = NotesMuted,
                    fontFamily = NotesFont,
                )
            }
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val minBody = maxHeight
                val flow = remember(current.document) { NoteDocuments.flow(current.document) }
                val structure = remember(flow) { flow.count { it.kind == BlockKind.TABLE } to flow.size }
                val lastText = remember(flow) { flow.indexOfLast { it.kind == BlockKind.TEXT } }
                val requesters = remember(current.id, structure) { List(flow.size) { FocusRequester() } }
                val noteNow = rememberUpdatedState(current)
                val viewModelNow = rememberUpdatedState(viewModel)
                val toastNow = rememberUpdatedState(onToast)
                val onNeedSelection = remember { { toastNow.value("Выделите фрагмент") } }
                val unlockNow = rememberUpdatedState<() -> Unit>(::openPinPrompt)
                val onRequestUnlock = remember { { unlockNow.value() } }
                val onAskTable = remember {
                    {
                        insertBlock = formats.tableBlock
                        insertCursor = formats.tableCursor
                        askTable = true
                    }
                }
                val onBlockText = remember {
                    {
                            index: Int,
                            text: String,
                            marks: List<TextMark>,
                            kind: EditKind,
                            before: Int,
                            after: Int,
                        ->
                        viewModelNow.value.setBlockText(
                            index,
                            text,
                            marks,
                            kind,
                            BlockCaret(index, before),
                            BlockCaret(index, after),
                        )
                    }
                }
                val onDeleteTableAbove = remember { { index: Int ->
                    val note = noteNow.value
                    val blocks = NoteDocuments.flow(note.document)
                    val above = blocks.getOrNull(index - 1)
                    val table = above?.table
                    if (above?.kind == BlockKind.TABLE && table != null) {
                        val caret = NoteDocuments.caretAfterRemovingTable(note.document, index)
                        viewModelNow.value.removeTable(table.id)
                        if (caret != null) {
                            caretRequest = CaretRequest(
                                token = caretRequest.token + 1,
                                blockIndex = caret.blockIndex,
                                offset = caret.offset,
                            )
                        }
                    }
                } }
                val updateTable = remember(viewModel) { viewModel::updateTable }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .heightIn(min = minBody)
                        .onGloballyPositioned { bodyCoords = it }
                        .observeSelectionTaps(surface)
                        .clickable(
                            interactionSource = bodyTap,
                            indication = null,
                            onClickLabel = "Ваш текст",
                            onClick = {
                                requesters.getOrNull(lastText)?.requestFocus()
                                keyboard?.show()
                            },
                        ),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        flow.forEachIndexed { index, block ->
                            key(current.id, index, structure) {
                                when (block.kind) {
                                    BlockKind.TEXT -> NoteTextBlock(
                                        noteId = current.id,
                                        text = block.text,
                                        marks = block.marks,
                                        readOnly = unreadable,
                                        placeholder = textPlaceholder(flow, index),
                                        hiddenUnlocked = unlocked,
                                        blockIndex = index,
                                        focusRequester = requesters[index],
                                        formats = formats,
                                        pinDialogVisible = askPin,
                                        surface = surface,
                                        onText = onBlockText,
                                        onNeedSelection = onNeedSelection,
                                        onRequestUnlock = onRequestUnlock,
                                        onAskTable = onAskTable,
                                        hasTableAbove = flow.getOrNull(index - 1)?.kind == BlockKind.TABLE,
                                        onDeleteTableAbove = onDeleteTableAbove,
                                        caretToken = if (caretRequest.blockIndex == index) caretRequest.token else 0,
                                        caretOffset = if (caretRequest.blockIndex == index) caretRequest.offset else 0,
                                        listStart = listStartFor(flow, index),
                                    )
                                    BlockKind.TABLE -> {
                                        val table = block.table
                                        if (table != null) {
                                            val tableId = table.id
                                            val onDelete = remember(tableId) {
                                                { viewModelNow.value.removeTable(tableId) }
                                            }
                                            TableBlock(
                                                table = table,
                                                blockIndex = index,
                                                readOnly = unreadable,
                                                hiddenUnlocked = unlocked,
                                                formats = formats,
                                                onChange = updateTable,
                                                onDelete = onDelete,
                                                onAskTable = onAskTable,
                                                onRequestUnlock = onRequestUnlock,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            if (current.audioPath != null) {
                // The bar describes the open note. A session for a different note must not leak through.
                val playback = if (player.noteId == current.id) {
                    player
                } else {
                    PlaybackSnapshot.opening(current.id, current.title)
                }
                MiniPlayer(
                    state = playback,
                    active = true,
                    onToggle = {
                        val path = current.audioPath ?: return@MiniPlayer
                        val sameSession = playback.engaged && playback.noteId == current.id
                        if (sameSession) {
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
                        if (playback.engaged && playback.noteId == current.id) {
                            viewModel.player.seekFraction(fraction)
                        }
                    },
                    onShare = {
                        val path = current.audioPath ?: return@MiniPlayer
                        viewModel.player.share(context, path) { ok ->
                            if (!ok) onToast("Не удалось подготовить запись")
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = formats.bodyFocused || formats.editingCell,
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
                viewModel.addTable(rows, cols, insertBlock, insertCursor)
                askTable = false
            },
            onDismiss = { askTable = false },
        )
    }
}

private fun textPlaceholder(flow: List<NoteBlock>, index: Int): String {
    val block = flow[index]
    val followedByTable = flow.getOrNull(index + 1)?.kind == BlockKind.TABLE
    return when {
        block.text.isEmpty() && followedByTable -> ""
        flow.any { it.kind == BlockKind.TABLE } -> "Ваш текст..."
        else -> "Начните писать…"
    }
}

@Composable
private fun NoteTextBlock(
    noteId: Long,
    text: String,
    marks: List<TextMark>,
    readOnly: Boolean = false,
    placeholder: String,
    hiddenUnlocked: Boolean,
    blockIndex: Int,
    focusRequester: FocusRequester,
    formats: NoteEditorController,
    pinDialogVisible: Boolean,
    surface: NoteSurfaceBridge,
    onText: (Int, String, List<TextMark>, EditKind, Int, Int) -> Unit,
    onNeedSelection: () -> Unit,
    onRequestUnlock: () -> Unit,
    onAskTable: () -> Unit,
    hasTableAbove: Boolean,
    onDeleteTableAbove: (Int) -> Unit,
    caretToken: Int = 0,
    caretOffset: Int = 0,
    listStart: Int = 1,
) {
    val publishText = rememberUpdatedState(onText)
    val onDocument = remember(noteId, blockIndex) {
        { nextText: String, nextMarks: List<TextMark>, kind: EditKind, before: Int, after: Int ->
            publishText.value(blockIndex, nextText, nextMarks, kind, before, after)
        }
    }
    RichNoteEditor(
        text = text,
        marks = marks,
        readOnly = readOnly,
        hiddenUnlocked = hiddenUnlocked,
        onDocument = onDocument,
        onNeedSelection = onNeedSelection,
        onRequestUnlock = onRequestUnlock,
        onAskTable = onAskTable,
        focusRequester = focusRequester,
        formats = formats,
        pinDialogVisible = pinDialogVisible,
        surface = surface,
        modifier = Modifier.padding(horizontal = 20.dp),
        blockIndex = blockIndex,
        placeholder = placeholder,
        hasTableAbove = hasTableAbove,
        onDeleteTableAbove = onDeleteTableAbove,
        caretToken = caretToken,
        caretOffset = caretOffset,
        listStart = listStart,
    )
}

/** First number under a table that sits inside a numbered list. */
private fun listStartFor(flow: List<NoteBlock>, index: Int): Int {
    if (flow.getOrNull(index - 1)?.kind != BlockKind.TABLE) return 1
    var previous = index - 1
    while (previous >= 0 && flow[previous].kind != BlockKind.TEXT) previous--
    val text = flow.getOrNull(previous)?.text ?: return 1
    return ListAutoFormat.continuationStart(text)
}

internal fun lastEditedCaption(
    createdAt: Long,
    updatedAt: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    if (updatedAt <= createdAt + 1_500L) return null
    val stamp = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .format(Instant.ofEpochMilli(updatedAt).atZone(zone))
    return "последнее изменение: $stamp"
}

/** Caret bottom inside the scroll content: field top, padding above the text, then the cursor. */
internal fun trailingCaretBottom(fieldTopPx: Int, topInsetPx: Int, cursorBottomPx: Int): Int =
    fieldTopPx + topInsetPx + cursorBottomPx

/** Caret after a local edit. A long tail rewrite (list renumber) keeps the caret at the start of the span. */
internal fun inferCaretAfterEdit(before: String, after: String): Int {
    if (after.isEmpty()) return 0
    val prefix = sharedPrefix(before, after)
    var suffix = 0
    val maxSuffix = minOf(before.length - prefix, after.length - prefix)
    while (
        suffix < maxSuffix &&
        before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
    ) {
        suffix++
    }
    val insertedStart = prefix
    val insertedEnd = (after.length - suffix).coerceAtLeast(insertedStart)
    val removedLen = before.length - prefix - suffix
    val insertedLen = insertedEnd - insertedStart
    if (removedLen > 24 && insertedLen > 24) return insertedStart.coerceIn(0, after.length)
    return insertedEnd.coerceIn(0, after.length)
}

/**
 * How far [scrollValue] must move down so [caretBottom] sits [marginPx] above
 * the bottom of [viewport]. Zero when the caret is already inside that window,
 * including when it is above the viewport.
 */
internal fun revealCaretDelta(
    scrollValue: Int,
    viewport: Int,
    caretBottom: Int,
    marginPx: Int,
): Int {
    if (viewport <= 0) return 0
    val hidden = caretBottom + marginPx - (scrollValue + viewport)
    return hidden.coerceAtLeast(0)
}

private fun sharedPrefix(left: String, right: String): Int {
    val limit = minOf(left.length, right.length)
    var index = 0
    while (index < limit && left[index] == right[index]) index++
    return index
}
