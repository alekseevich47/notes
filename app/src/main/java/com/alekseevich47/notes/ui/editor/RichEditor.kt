package com.alekseevich47.notes.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.EditHistory
import com.alekseevich47.notes.domain.EditKind
import com.alekseevich47.notes.domain.MarkEdits
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.NoteTable
import com.alekseevich47.notes.domain.NoteTables
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private val numberedLine = Regex("""^\d+\.\s?.*""")

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun RichNoteEditor(
    text: String,
    marks: List<TextMark>,
    readOnly: Boolean = false,
    hiddenUnlocked: Boolean,
    onDocument: (String, List<TextMark>, EditKind, Int, Int) -> Unit,
    onNeedSelection: () -> Unit,
    onRequestUnlock: () -> Unit,
    onAskTable: () -> Unit,
    focusRequester: FocusRequester,
    formats: NoteEditorController,
    pinDialogVisible: Boolean,
    surface: NoteSurfaceBridge,
    modifier: Modifier = Modifier,
    blockIndex: Int = 0,
    placeholder: String = "Начните писать…",
    onFocused: (Boolean) -> Unit = {},
    hasTableAbove: Boolean = false,
    onDeleteTableAbove: (Int) -> Unit = {},
    caretToken: Int = 0,
    caretOffset: Int = 0,
    listStart: Int = 1,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    val fieldNow = remember { FieldSlot(field) }
    fieldNow.current = field
    val deleteTableAbove = rememberUpdatedState(onDeleteTableAbove)
    val tableAbove = rememberUpdatedState(hasTableAbove)
    val blockIndexNow = rememberUpdatedState(blockIndex)
    val lastTableBackspace = remember { longArrayOf(0L) }
    fun backspaceAtStart(): Boolean {
        if (!tableAbove.value) return false
        val delete = deleteTableAbove.value
        val current = fieldNow.current
        if (!(current.selection.collapsed && current.selection.min == 0)) return false
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTableBackspace[0] < 150L) return true
        lastTableBackspace[0] = now
        delete(blockIndexNow.value)
        return true
    }
    val tableBackspace = remember {
        androidx.compose.ui.platform.PlatformTextInputInterceptor { request, nextHandler ->
            nextHandler.startInputMethod(
                object : PlatformTextInputMethodRequest {
                    override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                        return TableBackspaceConnection(request.createInputConnection(outAttributes)) {
                            backspaceAtStart()
                        }
                    }
                },
            )
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    var consumedCaret by remember { mutableIntStateOf(0) }
    LaunchedEffect(text, caretToken) {
        if (caretToken > 0 && caretToken != consumedCaret) {
            consumedCaret = caretToken
            val cursor = caretOffset.coerceIn(0, text.length)
            field = TextFieldValue(text, TextRange(cursor))
            fieldNow.current = field
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            return@LaunchedEffect
        }
        if (field.text != text) {
            val cursor = field.selection.end.coerceIn(0, text.length)
            field = TextFieldValue(text, TextRange(cursor))
            fieldNow.current = field
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var pendingReveal by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var textCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menuOverflow by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var blockMenu by remember { mutableStateOf(false) }
    val prompt = remember { HiddenPromptGuard() }
    val clipboard = LocalClipboardManager.current
    val platformToolbar = LocalTextToolbar.current
    val toolbar = remember { RoutingTextToolbar(platformToolbar) }
    val ink = MaterialTheme.colorScheme.onSurface
    val censorLayout = layout
    val censor = !hiddenUnlocked && marks.any { it.style == MarkStyle.HIDDEN }
    val spoiler = rememberSpoilerPaint(
        text = field.text,
        marks = marks,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = ink,
            fontFamily = NotesFont,
        ),
        ink = ink,
        widthPx = if (censor) censorLayout?.size?.width ?: 0 else 0,
    )
    if (field.selection.collapsed && (menuOverflow || menuVisible)) {
        menuOverflow = false
        menuVisible = false
    }
    var releasePlatformMenu by remember { mutableStateOf(false) }
    var blockFocused by remember { mutableStateOf(false) }
    LaunchedEffect(blockMenu) {
        if (blockMenu) {
            withFrameNanos { }
            blockMenu = false
        }
    }
    LaunchedEffect(releasePlatformMenu) {
        if (!releasePlatformMenu) return@LaunchedEffect
        withFrameNanos { }
        if (!releasePlatformMenu) return@LaunchedEffect
        toolbar.suppressPlatform = false
        releasePlatformMenu = false
    }
    if (pinDialogVisible) {
        prompt.dialogOpen = true
    } else if (prompt.dialogOpen) {
        prompt.dialogOpen = false
        prompt.armed = false
    }

    fun requestUnlock() {
        prompt.dialogOpen = true
        prompt.armed = false
        onRequestUnlock()
    }

    fun hideMenu() {
        menuVisible = false
        menuOverflow = false
    }

    fun publish(previous: String, edit: ListAutoFormat.Edit) {
        val before = field.selection.end
        field = TextFieldValue(edit.text, TextRange(edit.cursor.coerceIn(0, edit.text.length)))
        if (field.selection.collapsed) hideMenu()
        onDocument(
            edit.text,
            MarkEdits.shift(marks, previous, edit.text),
            EditKind.STRUCTURE,
            before,
            field.selection.end,
        )
    }

    fun collapseSelection() {
        blockMenu = true
        toolbar.suppressPlatform = true
        releasePlatformMenu = false
        if (!field.selection.collapsed) {
            field = field.copy(selection = TextRange(field.selection.end))
        }
        toolbar.hide()
        hideMenu()
    }

    fun placeCaret(offset: Int) {
        val cursor = offset.coerceIn(0, field.text.length)
        blockMenu = true
        toolbar.suppressPlatform = true
        releasePlatformMenu = false
        field = field.copy(selection = TextRange(cursor))
        toolbar.hide()
        hideMenu()
        if (
            !hiddenUnlocked &&
            HiddenText.cursorInside(marks, cursor, cursor) &&
            prompt.armed &&
            !prompt.dialogOpen
        ) {
            requestUnlock()
        }
    }

    fun applyMark(style: MarkStyle) {
        if (readOnly) return
        val start = field.selection.min
        val end = field.selection.max
        if (start == end) {
            onNeedSelection()
            return
        }
        val caret = field.selection.end
        onDocument(text, MarkEdits.toggle(marks, style, start, end), EditKind.STRUCTURE, caret, caret)
        hideMenu()
    }

    fun toggleList() {
        if (readOnly) return
        val previous = field.text
        publish(previous, ListAutoFormat.toggleLine(previous, field.selection.min, listStart))
    }

    fun overlapsLockedHidden(): Boolean {
        if (hiddenUnlocked) return false
        return HiddenText.cursorInside(marks, field.selection.start, field.selection.end)
    }

    fun copySelection() {
        val start = field.selection.min
        val end = field.selection.max
        if (start == end || end > field.text.length) return
        if (overlapsLockedHidden()) {
            requestUnlock()
            return
        }
        clipboard.setText(AnnotatedString(field.text.substring(start, end)))
        hideMenu()
    }

    fun cutSelection() {
        val start = field.selection.min
        val end = field.selection.max
        if (start == end || end > field.text.length) return
        if (overlapsLockedHidden()) {
            requestUnlock()
            return
        }
        val previous = field.text
        clipboard.setText(AnnotatedString(previous.substring(start, end)))
        val proposed = previous.removeRange(start, end)
        publish(previous, ListAutoFormat.afterEdit(previous, proposed, start, listStart))
        hideMenu()
    }

    fun selectAll() {
        releasePlatformMenu = false
        if (field.text.isEmpty()) {
            hideMenu()
            return
        }
        // Primary row (cut / copy / …), not the overflow the button lives in.
        menuOverflow = false
        menuVisible = true
        field = field.copy(selection = TextRange(0, field.text.length))
    }

    DisposableEffect(surface, blockIndex) {
        onDispose { surface.unregister(blockIndex) }
    }
    SideEffect {
        surface.register(
            blockIndex,
            BlockSurfaceHandlers(
                focused = blockFocused,
                readSelection = { SelectionSnapshot(field.selection.start, field.selection.end) },
                pointInside = { root, selection ->
                    val coords = textCoordinates
                    val result = layout
                    if (coords == null || !coords.isAttached || result == null) {
                        false
                    } else {
                        val local = root - coords.localToRoot(Offset.Zero)
                        val start = min(selection.start, selection.end)
                        val end = max(selection.start, selection.end)
                        pointInsideSelection(local.x, local.y, bandsFor(result, start, end))
                    }
                },
                onClear = { root, _ ->
                    val coords = textCoordinates
                    val result = layout
                    if (coords == null || !coords.isAttached || result == null) {
                        collapseSelection()
                    } else {
                        val local = root - coords.localToRoot(Offset.Zero)
                        placeCaret(result.getOffsetForPosition(local))
                    }
                },
                onGestureSettle = settle@{ cleared, collapsedAtDown, root ->
                    if (cleared || !collapsedAtDown || root == null) {
                        if (cleared) {
                            toolbar.suppressPlatform = true
                            releasePlatformMenu = false
                        }
                        return@settle
                    }
                    val coords = textCoordinates
                    val result = layout
                    if (coords == null || !coords.isAttached || result == null) return@settle
                    val local = root - coords.localToRoot(Offset.Zero)
                    if (
                        pointInsideTextBounds(
                            local.x,
                            local.y,
                            result.size.width.toFloat(),
                            result.size.height.toFloat(),
                        )
                    ) {
                        releasePlatformMenu = true
                    }
                },
            ),
        )
        if (blockFocused && !formats.editingCell) {
            formats.bold = { applyMark(MarkStyle.BOLD) }
            formats.italic = { applyMark(MarkStyle.ITALIC) }
            formats.underline = { applyMark(MarkStyle.UNDERLINE) }
            formats.strike = { applyMark(MarkStyle.STRIKE) }
            formats.hidden = { applyMark(MarkStyle.HIDDEN) }
            formats.list = { toggleList() }
            formats.table = {
                formats.tableBlock = blockIndex
                formats.tableCursor = field.selection.end
                onAskTable()
            }
            formats.activeStyles = stylesTouching(marks, field.selection)
            formats.listActive = lineNumbered(field.text, field.selection.min)
        }
    }
    toolbar.onSelectionGestureEnded = {
        if (!field.selection.collapsed) {
            menuOverflow = false
            menuVisible = true
        }
    }
    toolbar.onSelectionGestureBegan = {
        menuVisible = false
    }

    BackHandler(enabled = !field.selection.collapsed || menuVisible) {
        collapseSelection()
    }

    fun scheduleReveal(result: TextLayoutResult) {
        if (!pendingReveal || readOnly || !blockFocused) return
        val laidOut = result.layoutInput.text.text
        if (laidOut != field.text) return
        pendingReveal = false
        val end = field.selection.end.coerceIn(0, laidOut.length)
        val rect = result.getCursorRect(end).inflate(with(density) { 16.dp.toPx() })
        scope.launch { bring.bringIntoView(rect) }
    }

    InterceptPlatformTextInput(interceptor = tableBackspace) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                BasicTextField(
                    value = field,
                    readOnly = readOnly,
                    onValueChange = { next ->
                        if (readOnly) return@BasicTextField
                        val previous = field.text
                        val previousSelection = field.selection
                        if (blockMenu && next.text == previous) {
                            field = field.copy(selection = previousSelection)
                            hideMenu()
                            return@BasicTextField
                        }
                        if (blockMenu) blockMenu = false
                        val textHitsHidden = HiddenText.editTouches(marks, previous, next.text)
                        val cursorInHidden = HiddenText.cursorInside(
                            marks,
                            next.selection.start,
                            next.selection.end,
                        )
                        if (!hiddenUnlocked && textHitsHidden) {
                            requestUnlock()
                            return@BasicTextField
                        }
                        if (!cursorInHidden && !prompt.dialogOpen) prompt.armed = true
                        if (!hiddenUnlocked && cursorInHidden && prompt.armed && !prompt.dialogOpen) {
                            requestUnlock()
                        }
                        val edit = ListAutoFormat.afterEdit(
                            previous,
                            next.text,
                            next.selection.end,
                            listStart,
                            composing = next.composition != null,
                        )
                        val selection = if (edit.text == next.text) next.selection else TextRange(edit.cursor)
                        field = if (edit.text != next.text) {
                            TextFieldValue(edit.text, selection, composition = null)
                        } else {
                            next.copy(text = edit.text, selection = selection)
                        }
                        fieldNow.current = field
                        if (edit.text != previous) {
                            toolbar.suppressPlatform = false
                            releasePlatformMenu = false
                            if (blockFocused) pendingReveal = true
                        }
                        // The menu waits for finger-up. Compose asks the toolbar to show only
                        // after the selection gesture ends; showing here covers the magnifier.
                        if (selection.collapsed) hideMenu()
                        val kind = EditHistory.kindForTextChange(previous, next.text, edit.text)
                        onDocument(
                            edit.text,
                            MarkEdits.shift(marks, previous, edit.text),
                            kind,
                            previousSelection.end,
                            selection.end,
                        )
                        val ready = layout
                        if (ready != null) scheduleReveal(ready)
                    },
                    onTextLayout = { result ->
                        val previousLayout = layout
                        if (
                            previousLayout == null ||
                            previousLayout.size != result.size ||
                            previousLayout.layoutInput.text.text != result.layoutInput.text.text
                        ) {
                            layout = result
                        }
                        scheduleReveal(result)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bring)
                        .onPreviewKeyEvent { event ->
                            event.type == KeyEventType.KeyDown &&
                                event.key == Key.Backspace &&
                                backspaceAtStart()
                        }
                        .onGloballyPositioned { textCoordinates = it }
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            blockFocused = it.isFocused
                            if (it.isFocused) {
                                formats.bodyFocused = true
                                formats.editingCell = false
                            } else {
                                formats.bodyFocused = false
                            }
                            onFocused(it.isFocused)
                        }
                        .then(if (censor) Modifier.drawSpoilerShadows(spoiler, marks, ink) else Modifier),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = NotesFont,
                    ),
                    cursorBrush = SolidColor(NotesPurple),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true,
                    ),
                    visualTransformation = remember(marks, hiddenUnlocked) {
                        NoteVisual(marks, hiddenUnlocked)
                    },
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (field.text.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                                        fontFamily = NotesFont,
                                    ),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            val shown = layout?.takeIf {
                !field.selection.collapsed && it.layoutInput.text.isNotEmpty()
            }
            if (shown != null && menuVisible) {
                val length = shown.layoutInput.text.length
                val start = field.selection.min.coerceIn(0, length)
                val end = field.selection.max.coerceIn(0, length)
                if (start != end) {
                    SelectionActionsPopup(
                        startHandle = shown.getCursorRect(start),
                        endHandle = shown.getCursorRect(end),
                        overflow = menuOverflow,
                        onCut = { cutSelection() },
                        onCopy = { copySelection() },
                        onMore = { menuOverflow = true },
                        onDismiss = { if (menuVisible) collapseSelection() },
                        onBack = { menuOverflow = false },
                        onHidden = { applyMark(MarkStyle.HIDDEN) },
                        onSelectAll = { selectAll() },
                        onBold = { applyMark(MarkStyle.BOLD) },
                        onItalic = { applyMark(MarkStyle.ITALIC) },
                        onStrike = { applyMark(MarkStyle.STRIKE) },
                        onUnderline = { applyMark(MarkStyle.UNDERLINE) },
                    )
                }
            }
        }
    }
    }
}

private class FieldSlot(var current: TextFieldValue)

private class TableBackspaceConnection(
    delegate: InputConnection,
    private val onBackspaceAtStart: () -> Boolean,
) : InputConnectionWrapper(delegate, true) {
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (beforeLength > 0 && afterLength == 0 && onBackspaceAtStart()) return true
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        if (beforeLength > 0 && afterLength == 0 && onBackspaceAtStart()) return true
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    override fun sendKeyEvent(event: android.view.KeyEvent): Boolean {
        if (
            event.action == android.view.KeyEvent.ACTION_DOWN &&
            event.keyCode == android.view.KeyEvent.KEYCODE_DEL &&
            onBackspaceAtStart()
        ) {
            return true
        }
        return super.sendKeyEvent(event)
    }
}

private class HiddenPromptGuard {
    var dialogOpen: Boolean = false
    var armed: Boolean = true
}

/**
 * Selection uses the in-app bubble. Compose shows its floating toolbar when a
 * selection gesture ends and hides it while the finger is down, so those calls
 * open and close the bubble. A collapsed caret may still paste. Select all is
 * never forwarded — that is the system window after a tap on empty space.
 */
internal class RoutingTextToolbar(
    private val platform: TextToolbar,
) : TextToolbar {
    private var selectionMenu = false
    var suppressPlatform = false
    var onSelectionGestureEnded: () -> Unit = {}
    var onSelectionGestureBegan: () -> Unit = {}

    override val status: TextToolbarStatus
        get() = if (selectionMenu) TextToolbarStatus.Shown else platform.status

    override fun hide() {
        val gestureBegan = selectionMenu
        selectionMenu = false
        platform.hide()
        if (gestureBegan) onSelectionGestureBegan()
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        val ranged = onCopyRequested != null || onCutRequested != null
        if (ranged) {
            val gestureEnded = !selectionMenu
            selectionMenu = true
            platform.hide()
            if (gestureEnded) onSelectionGestureEnded()
            return
        }
        selectionMenu = false
        if (
            !shouldOfferPlatformPaste(
                hasCopy = false,
                hasCut = false,
                hasPaste = onPasteRequested != null,
                suppressPlatform = suppressPlatform,
            )
        ) {
            platform.hide()
            return
        }
        platform.showMenu(
            rect,
            onCopyRequested = null,
            onPasteRequested = onPasteRequested,
            onCutRequested = null,
            onSelectAllRequested = null,
        )
    }
}

private fun bandsFor(layout: TextLayoutResult, start: Int, end: Int): List<SelectionBand> {
    val length = layout.layoutInput.text.length
    if (length == 0 || layout.lineCount == 0) return emptyList()
    return selectionBands(
        selectionStart = start.coerceIn(0, length),
        selectionEnd = end.coerceIn(0, length),
        lineCount = layout.lineCount,
        lineStart = { layout.getLineStart(it) },
        lineEnd = { layout.getLineEnd(it, visibleEnd = false) },
        lineTop = { layout.getLineTop(it) },
        lineBottom = { layout.getLineBottom(it) },
        lineLeft = { layout.getLineLeft(it) },
        lineRight = { layout.getLineRight(it) },
        xForOffset = { offset -> layout.getHorizontalPosition(offset.coerceIn(0, length), true) },
    )
}

private fun stylesTouching(marks: List<TextMark>, selection: TextRange): Set<MarkStyle> {
    val start = selection.min
    val end = selection.max
    return marks.mapNotNull { mark ->
        if (mark.end <= mark.start) return@mapNotNull null
        val hits = if (start == end) {
            start in mark.start until mark.end
        } else {
            mark.start < end && mark.end > start
        }
        if (hits) mark.style else null
    }.toSet()
}

private fun lineNumbered(text: String, cursor: Int): Boolean {
    if (text.isEmpty()) return false
    val safe = cursor.coerceIn(0, text.length)
    // Search strictly before the caret. At offset 0, coerceAtLeast(0) would
    // look at the caret itself, so a leading '\n' made lineStart > lineEnd.
    val lineStart = if (safe == 0) {
        0
    } else {
        val index = text.lastIndexOf('\n', safe - 1)
        if (index < 0) 0 else index + 1
    }
    val lineEnd = text.indexOf('\n', safe).let { index -> if (index < 0) text.length else index }
    if (lineEnd < lineStart) return false
    return numberedLine.matches(text.substring(lineStart, lineEnd))
}

private class NoteVisual(
    private val marks: List<TextMark>,
    private val unlocked: Boolean,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            marks.forEach { mark ->
                val start = mark.start.coerceIn(0, length)
                val end = mark.end.coerceIn(0, length)
                if (end <= start) return@forEach
                val style = styleFor(mark.style, unlocked) ?: return@forEach
                addStyle(style, start, end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private fun styleFor(style: MarkStyle, unlocked: Boolean): SpanStyle? = when (style) {
    MarkStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    MarkStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    MarkStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    MarkStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    MarkStyle.HIDDEN -> if (unlocked) {
        null
    } else {
        SpanStyle(color = Color.Transparent)
    }
}


/**
 * Plain paragraph drawn under every table. Tapping the empty page below the
 * table focuses this field, so the caret sits after the table instead of in
 * the body above it. Marks stay on the body; this text is stored separately.
 */
@Composable
fun TrailingNoteField(
    text: String,
    onText: (String) -> Unit,
    focusRequest: Int,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        if (text != field.text) {
            val cursor = field.selection.end.coerceIn(0, text.length)
            field = TextFieldValue(text, TextRange(cursor))
        }
    }
    LaunchedEffect(focusRequest) {
        if (!armed) {
            armed = true
            return@LaunchedEffect
        }
        field = field.copy(selection = TextRange(field.text.length))
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboard?.show()
    }
    BasicTextField(
        value = field,
        onValueChange = { next ->
            field = next
            if (next.text != text) onText(next.text)
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = NotesFont,
        ),
        cursorBrush = SolidColor(NotesPurple),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrectEnabled = true,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(min = 120.dp)
            .focusRequester(focusRequester),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.TopStart) {
                if (field.text.isEmpty()) {
                    Text(
                        "Продолжите писать…",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            fontFamily = NotesFont,
                        ),
                    )
                }
                inner()
            }
        },
    )
}
