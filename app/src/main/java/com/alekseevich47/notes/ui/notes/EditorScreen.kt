package com.alekseevich47.notes.ui.notes

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.alekseevich47.notes.domain.BlockKind
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.NoteBlock
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.player.PlaybackSnapshot
import com.alekseevich47.notes.service.PlaybackService
import com.alekseevich47.notes.ui.components.AppDialog
import com.alekseevich47.notes.ui.components.PinDialog
import com.alekseevich47.notes.ui.components.TableSizeDialog
import com.alekseevich47.notes.ui.editor.EditorFormatBar
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
import kotlin.math.roundToInt

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
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()
    val bodyTap = remember { MutableInteractionSource() }
    var insertBlock by remember { mutableIntStateOf(0) }
    var insertCursor by remember { mutableIntStateOf(0) }
    val surface = remember { NoteSurfaceBridge() }
    var bodyCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    SideEffect {
        surface.toRoot = { local ->
            val coords = bodyCoords
            if (coords != null && coords.isAttached) coords.localToRoot(local) else null
        }
    }
    val context = LocalContext.current

    LaunchedEffect(noteId) {
        formats.bodyFocused = false
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
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
            }
        }
        if (current == null) {
            Text("Открываю…", modifier = Modifier.padding(20.dp), fontFamily = NotesFont)
        } else {
            BasicTextField(
                value = current.title,
                onValueChange = viewModel::setTitle,
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
                val flow = NoteDocuments.flow(current.document)
                val structure = flow.count { it.kind == BlockKind.TABLE } to flow.size
                val lastText = flow.indexOfLast { it.kind == BlockKind.TEXT }
                val requesters = remember(current.id, structure) { List(flow.size) { FocusRequester() } }
                val textWidth = (maxWidth - 40.dp).coerceAtLeast(1.dp)
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
                                        placeholder = textPlaceholder(flow, index),
                                        hiddenUnlocked = unlocked,
                                        blockIndex = index,
                                        focusRequester = requesters[index],
                                        formats = formats,
                                        pinDialogVisible = askPin,
                                        surface = surface,
                                        useSharedSurface = index == 0,
                                        scroll = scroll,
                                        textWidth = textWidth,
                                        bodyCoords = bodyCoords,
                                        onText = { text, marks -> viewModel.setBlockText(index, text, marks) },
                                        onNeedSelection = { onToast("Выделите фрагмент") },
                                        onRequestUnlock = { openPinPrompt() },
                                        onAskTable = {
                                            insertBlock = formats.tableBlock
                                            insertCursor = formats.tableCursor
                                            askTable = true
                                        },
                                    )
                                    BlockKind.TABLE -> {
                                        val table = block.table
                                        if (table != null) {
                                            TableBlock(
                                                table = table,
                                                blockIndex = index,
                                                hiddenUnlocked = unlocked,
                                                formats = formats,
                                                onChange = viewModel::updateTable,
                                                onDelete = { viewModel.removeTable(table.id) },
                                                onAskTable = {
                                                    insertBlock = formats.tableBlock
                                                    insertCursor = formats.tableCursor
                                                    askTable = true
                                                },
                                                onRequestUnlock = { openPinPrompt() },
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
    placeholder: String,
    hiddenUnlocked: Boolean,
    blockIndex: Int,
    focusRequester: FocusRequester,
    formats: NoteEditorController,
    pinDialogVisible: Boolean,
    surface: NoteSurfaceBridge,
    useSharedSurface: Boolean,
    scroll: ScrollState,
    textWidth: Dp,
    bodyCoords: LayoutCoordinates?,
    onText: (String, List<TextMark>) -> Unit,
    onNeedSelection: () -> Unit,
    onRequestUnlock: () -> Unit,
    onAskTable: () -> Unit,
) {
    var focused by remember(noteId, blockIndex) { mutableStateOf(false) }
    var top by remember(noteId, blockIndex) { mutableIntStateOf(0) }
    val bridge = remember(noteId, blockIndex) { NoteSurfaceBridge() }
    KeepCaretVisible(
        noteId = noteId,
        text = text,
        marks = marks,
        hiddenLocked = !hiddenUnlocked && marks.any { it.style == MarkStyle.HIDDEN },
        focused = focused,
        textWidth = textWidth,
        scroll = scroll,
        contentTopPx = top,
        followEdits = true,
    )
    RichNoteEditor(
        document = com.alekseevich47.notes.domain.NoteDocument(
            text = text,
            marks = marks,
        ),
        hiddenUnlocked = hiddenUnlocked,
        onDocument = { slice -> onText(slice.text, slice.marks) },
        onNeedSelection = onNeedSelection,
        onRequestUnlock = onRequestUnlock,
        onAskTable = onAskTable,
        focusRequester = focusRequester,
        formats = formats,
        pinDialogVisible = pinDialogVisible,
        surface = if (useSharedSurface) surface else bridge,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .onGloballyPositioned { coords ->
                val content = bodyCoords
                if (content == null || !content.isAttached || !coords.isAttached) return@onGloballyPositioned
                val origin = content.localToRoot(Offset.Zero)
                val here = coords.localToRoot(Offset.Zero)
                top = (here.y - origin.y).roundToInt()
            },
        blockIndex = blockIndex,
        placeholder = placeholder,
        onFocused = { focused = it },
    )
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

/**
 * Scrolls a note field when typing pushes the caret below the viewport.
 * Selection changes do not move the scroll: only a text edit does, and only
 * downward. The body ignores edits while it is unfocused. The trailing field
 * passes [followEdits] so growth still scrolls if the focus callback is late.
 *
 * [contentTopPx] is the field's top inside the scroll content. Pass -1 until
 * that position is known so a tall table above the field is included.
 */
@Composable
private fun KeepCaretVisible(
    noteId: Long,
    text: String,
    marks: List<TextMark>,
    hiddenLocked: Boolean,
    focused: Boolean,
    textWidth: Dp,
    scroll: ScrollState,
    contentTopPx: Int = 0,
    topInsetPx: Int = 0,
    followEdits: Boolean = false,
) {
    val tracked = remember(noteId) { mutableStateOf(text) }
    var edited by remember(noteId) { mutableStateOf(false) }
    var caret by remember(noteId) { mutableIntStateOf(text.length) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val body = MaterialTheme.typography.bodyLarge.copy(fontFamily = NotesFont)
    val viewport = scroll.viewportSize
    LaunchedEffect(noteId, text, focused, textWidth, viewport, contentTopPx, topInsetPx) {
        if (contentTopPx < 0) return@LaunchedEffect
        val before = tracked.value
        val textChanged = before != text
        if (textChanged && (focused || followEdits)) {
            caret = inferCaretAfterEdit(before, text)
            edited = true
        } else if (!focused || !edited) {
            tracked.value = text
            return@LaunchedEffect
        }
        tracked.value = text
        val widthPx = with(density) { textWidth.roundToPx().coerceAtLeast(1) }
        val marginPx = with(density) { 16.dp.roundToPx() }
        val layout = measurer.measure(
            text = annotatedNote(text, marks),
            style = body,
            constraints = Constraints(maxWidth = widthPx),
        )
        val rect = layout.getCursorRect(caret.coerceIn(0, text.length))
        val caretBottom = trailingCaretBottom(
            fieldTopPx = contentTopPx,
            topInsetPx = topInsetPx,
            cursorBottomPx = rect.bottom.roundToInt(),
        )
        repeat(4) { frame ->
            withFrameNanos { }
            val delta = revealCaretDelta(
                scrollValue = scroll.value,
                viewport = scroll.viewportSize,
                caretBottom = caretBottom,
                marginPx = marginPx,
            )
            if (delta == 0) return@LaunchedEffect
            val max = scroll.maxValue
            val target = (scroll.value + delta).coerceAtMost(max.coerceAtLeast(0))
            if (target <= scroll.value && frame < 3) return@repeat
            if (target > scroll.value) scroll.scrollTo(target)
            return@LaunchedEffect
        }
    }
}

private fun annotatedNote(text: String, marks: List<TextMark>) = buildAnnotatedString {
    append(text)
    marks.forEach { mark ->
        val style = when (mark.style) {
            MarkStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            MarkStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            else -> null
        } ?: return@forEach
        val start = mark.start.coerceIn(0, length)
        val end = mark.end.coerceIn(0, length)
        if (end > start) addStyle(style, start, end)
    }
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
