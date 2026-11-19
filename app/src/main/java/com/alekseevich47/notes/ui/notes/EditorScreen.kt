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
import com.alekseevich47.notes.domain.MarkStyle
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
import com.alekseevich47.notes.ui.editor.TrailingNoteField
import com.alekseevich47.notes.ui.editor.observeSelectionTaps
import com.alekseevich47.notes.ui.editor.TableGrid
import com.alekseevich47.notes.ui.player.MiniPlayer
import com.alekseevich47.notes.ui.theme.NotesFont
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
    val bodyFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()
    val bodyTap = remember { MutableInteractionSource() }
    var bodyEpoch by remember { mutableStateOf(0) }
    var trailingFocus by remember { mutableStateOf(0) }
    var knownTableCount by remember(noteId) { mutableStateOf(-1) }
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
    LaunchedEffect(noteId, note?.id, note?.document?.tables?.size) {
        val shown = note ?: return@LaunchedEffect
        if (shown.id != noteId) return@LaunchedEffect
        val count = shown.document.tables.size
        if (knownTableCount >= 0 && count > knownTableCount) {
            trailingFocus++
            withFrameNanos { }
            withFrameNanos { }
            scroll.scrollTo(scroll.maxValue)
        }
        knownTableCount = count
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
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val minBody = maxHeight
                KeepCaretVisible(
                    noteId = current.id,
                    text = current.document.text,
                    marks = current.document.marks,
                    hiddenLocked = !unlocked && current.document.marks.any { it.style == MarkStyle.HIDDEN },
                    focused = formats.bodyFocused,
                    textWidth = (maxWidth - 40.dp).coerceAtLeast(1.dp),
                    scroll = scroll,
                )
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
                            onClickLabel = if (current.document.tables.isEmpty()) "Начните писать" else "Продолжите писать",
                            onClick = {
                                if (current.document.tables.isEmpty()) focusBody() else trailingFocus++
                            },
                        )
                        .padding(horizontal = 20.dp),
                ) {
                    val hasTables = current.document.tables.isNotEmpty()
                    Column(Modifier.fillMaxWidth()) {
                        key(current.id, bodyEpoch) {
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
                                surface = surface,
                            )
                        }
                        current.document.tables.forEach { table ->
                            key(table.id) {
                                TableGrid(
                                    table = table,
                                    onChange = viewModel::updateTable,
                                    onDelete = {
                                        val last = current.document.tables.none { it.id != table.id }
                                        if (last && current.document.trailing.isNotEmpty()) {
                                            bodyEpoch++
                                        }
                                        viewModel.removeTable(table.id)
                                    },
                                )
                            }
                        }
                        if (hasTables) {
                            key(current.id) {
                                TrailingNoteField(
                                    text = current.document.trailing,
                                    onText = viewModel::setTrailing,
                                    focusRequest = trailingFocus,
                                )
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

/**
 * Scrolls the note body when typing pushes the caret below the viewport.
 * Selection changes do not move the scroll: only a text edit while the body
 * is focused does, and only downward.
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
) {
    val tracked = remember(noteId) { mutableStateOf(text) }
    var edited by remember(noteId) { mutableStateOf(false) }
    var caret by remember(noteId) { mutableIntStateOf(text.length) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val body = MaterialTheme.typography.bodyLarge.copy(fontFamily = NotesFont)
    val labelStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = NotesFont)
    val viewport = scroll.viewportSize
    LaunchedEffect(noteId, text, focused, textWidth, viewport) {
        val before = tracked.value
        val textChanged = before != text
        if (!focused || (!textChanged && !edited)) {
            tracked.value = text
            return@LaunchedEffect
        }
        if (textChanged) {
            caret = inferCaretAfterEdit(before, text)
            edited = true
        }
        tracked.value = text
        val widthPx = with(density) { textWidth.roundToPx().coerceAtLeast(1) }
        val marginPx = with(density) { 16.dp.roundToPx() }
        val censorPx = if (!hiddenLocked) {
            0
        } else {
            measurer.measure(
                text = "Открыть скрытый текст",
                style = labelStyle,
                constraints = Constraints(maxWidth = widthPx),
            ).size.height + with(density) { 6.dp.roundToPx() }
        }
        val layout = measurer.measure(
            text = annotatedNote(text, marks),
            style = body,
            constraints = Constraints(maxWidth = widthPx),
        )
        val rect = layout.getCursorRect(caret.coerceIn(0, text.length))
        val caretBottom = censorPx + rect.bottom.roundToInt()
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
