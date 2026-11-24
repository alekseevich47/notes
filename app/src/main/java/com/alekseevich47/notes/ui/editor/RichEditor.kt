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
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
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
import android.content.ClipboardManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
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
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.components.LinkDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.alekseevich47.notes.ui.theme.NotesPurple
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    documentSelectToken: Int = 0,
    listStart: Int = 1,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    val fieldNow = remember { FieldSlot(field) }
    fieldNow.current = field
    val forcedSelection = remember { RangeSlot() }
    var appliedDocumentSelect by remember { mutableIntStateOf(0) }
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
    val menuState = remember { SelectionMenuState(scope) }
    val gestures = remember { EditorGestureCallbacks() }
    val density = LocalDensity.current
    val view = LocalView.current
    val tapWait = LocalViewConfiguration.current.doubleTapTimeoutMillis.coerceAtMost(260L)
    var textCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menuOverflow by remember { mutableStateOf(false) }
    var caretHold by remember { mutableStateOf(false) }
    val caretHoldJob = remember { object { var job: Job? = null } }
    var pasteAnchor by remember { mutableStateOf<Rect?>(null) }
    val pasteGuard = remember { intArrayOf(0) }
    var linkDraft by remember { mutableStateOf<TextRange?>(null) }
    var blockMenu by remember { mutableStateOf(false) }
    val prompt = remember { HiddenPromptGuard() }
    val clipboard = LocalClipboardManager.current
    val platformClipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val platformToolbar = LocalTextToolbar.current
    lateinit var applyIncoming: (TextFieldValue) -> Unit
    val toolbar = remember { RoutingTextToolbar(platformToolbar) }
    val ink = MaterialTheme.colorScheme.onSurface
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        color = ink,
        fontFamily = NotesFont,
    )
    val spoiler = rememberSpoilerLayer(
        text = field.text,
        marks = marks,
        style = bodyStyle,
        ink = ink,
        layout = layout,
        widthPx = layout?.size?.width ?: 0,
        hiddenUnlocked = hiddenUnlocked,
    )
    val spoilerModifier = spoiler.modifier
    if (field.selection.collapsed && menuOverflow) {
        menuOverflow = false
    }
    var blockFocused by remember { mutableStateOf(false) }
    LaunchedEffect(documentSelectToken) {
        if (documentSelectToken == 0) {
            if (
                appliedDocumentSelect != 0 &&
                field.text.isNotEmpty() &&
                field.selection.min == 0 &&
                field.selection.max == field.text.length
            ) {
                val old = field.selection
                field = field.copy(selection = TextRange(old.end))
                fieldNow.current = field
                menuState.onSelectionChanged(old, field.selection)
            }
            appliedDocumentSelect = 0
            return@LaunchedEffect
        }
        if (documentSelectToken == appliedDocumentSelect || text.isEmpty()) return@LaunchedEffect
        appliedDocumentSelect = documentSelectToken
        if (field.selection.min == 0 && field.selection.max == field.text.length) return@LaunchedEffect
        val old = field.selection
        field = field.copy(selection = TextRange(0, field.text.length))
        fieldNow.current = field
        if (blockFocused) {
            menuOverflow = false
            toolbar.markSelectionShown()
        }
        menuState.onSelectionChanged(old, field.selection)
    }
    LaunchedEffect(blockMenu) {
        if (blockMenu) {
            withFrameNanos { }
            blockMenu = false
        }
    }
    LaunchedEffect(formats.scrolling) {
        menuState.onScroll(formats.scrolling)
    }
    LaunchedEffect(formats.autoScrolling) {
        menuState.setAutoScrolling(formats.autoScrolling)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) pasteAnchor = null
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (pinDialogVisible) {
        prompt.dialogOpen = true
        pasteAnchor = null
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
        menuOverflow = false
        pasteAnchor = null
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

    fun clearPendingStyles() {
        if (formats.pendingOn.isEmpty() && formats.pendingOff.isEmpty()) return
        formats.pendingOn = emptySet()
        formats.pendingOff = emptySet()
    }

    fun collapseSelection() {
        blockMenu = true
        val old = field.selection
        if (!old.collapsed) {
            field = field.copy(selection = TextRange(old.end))
            clearPendingStyles()
            menuState.onSelectionChanged(old, field.selection)
        }
        toolbar.dismissQuietly()
        hideMenu()
    }

    fun placeCaret(offset: Int) {
        val cursor = offset.coerceIn(0, field.text.length)
        blockMenu = true
        val old = field.selection
        if (old != TextRange(cursor)) clearPendingStyles()
        field = field.copy(selection = TextRange(cursor))
        if (old != field.selection) menuState.onSelectionChanged(old, field.selection)
        toolbar.dismissQuietly()
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

    fun togglePending(style: MarkStyle) {
        val on = formats.pendingOn
        val off = formats.pendingOff
        when {
            style in on -> formats.pendingOn = on - style
            style in off -> formats.pendingOff = off - style
            style in stylesTouching(marks, field.selection) -> formats.pendingOff = off + style
            else -> formats.pendingOn = on + style
        }
    }

    fun applyMark(style: MarkStyle) {
        if (readOnly) return
        val start = field.selection.min
        val end = field.selection.max
        if (start == end) {
            if (style != MarkStyle.HIDDEN) togglePending(style)
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

    fun stashCopy(source: String, start: Int, end: Int) {
        val fragment = source.substring(start, end)
        val clipped = RichClip.clip(marks, start, end)
        RichClip.put(fragment, clipped)
        writeSelectionClipboard(
            composeClipboard = clipboard,
            platformClipboard = platformClipboard,
            text = fragment,
            sensitive = RichClip.containsHidden(clipped),
        )
    }

    fun copySelection() {
        val start = field.selection.min
        val end = field.selection.max
        if (start == end || end > field.text.length) return
        if (overlapsLockedHidden()) {
            requestUnlock()
            return
        }
        stashCopy(field.text, start, end)
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
        stashCopy(previous, start, end)
        val proposed = previous.removeRange(start, end)
        publish(previous, ListAutoFormat.afterEdit(previous, proposed, start, listStart))
        hideMenu()
    }

    fun pasteSelection() {
        if (readOnly) return
        val clip = clipboard.getText()?.text ?: return
        if (clip.isEmpty()) return
        val start = field.selection.min
        val end = field.selection.max.coerceAtMost(field.text.length)
        if (start > field.text.length) return
        if (overlapsLockedHidden()) {
            requestUnlock()
            return
        }
        val proposed = field.text.substring(0, start) + clip + field.text.substring(end)
        applyIncoming(TextFieldValue(proposed, TextRange((start + clip.length).coerceAtMost(proposed.length))))
        hideMenu()
    }

    fun clearStyles() {
        if (readOnly) return
        val start = field.selection.min
        val end = field.selection.max
        if (start == end) return
        if (overlapsLockedHidden()) {
            requestUnlock()
            return
        }
        val caret = field.selection.end
        onDocument(text, MarkEdits.clear(marks, start, end), EditKind.STRUCTURE, caret, caret)
        hideMenu()
    }

    fun selectAll() {
        if (field.text.isEmpty()) {
            hideMenu()
            return
        }
        if (selectAllStep(field.selection, field.text.length) == SelectAllStep.DOCUMENT) {
            formats.documentSelectToken += 1
            return
        }
        // Primary row (cut / copy / …), not the overflow the button lives in.
        menuOverflow = false
        val old = field.selection
        toolbar.markSelectionShown()
        field = field.copy(selection = TextRange(0, field.text.length))
        menuState.onSelectionChanged(old, field.selection)
    }

    fun rectInWindow(offset: Int): Rect? {
        val result = layout ?: return null
        val coords = textCoordinates ?: return null
        if (!coords.isAttached) return null
        val length = result.layoutInput.text.length
        val rect = result.getCursorRect(offset.coerceIn(0, length))
        val topLeft = coords.localToWindow(rect.topLeft)
        val bottomRight = coords.localToWindow(rect.bottomRight)
        return Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    fun extendActiveEdge(dy: Float) {
        val result = layout ?: return
        if (!menuState.dragging) return
        val edge = if (menuState.activeIsEnd) field.selection.end else field.selection.start
        val next = extendSelectionOffset(result, edge, dy)
        val raw = if (menuState.activeIsEnd) {
            TextRange(menuState.anchor, next)
        } else {
            TextRange(next, menuState.anchor)
        }
        val updated = snapDraggedSelection(field.text, field.selection, raw)
        if (updated == field.selection) return
        val old = field.selection
        field = field.copy(selection = updated)
        menuState.onSelectionChanged(old, updated)
    }

    val clearPaste = remember { { pasteAnchor = null } }
    DisposableEffect(surface, blockIndex) {
        surface.addBodyDownListener(clearPaste)
        onDispose {
            surface.removeBodyDownListener(clearPaste)
            surface.unregister(blockIndex)
        }
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
                bounds = { root ->
                    val coords = textCoordinates
                    val result = layout
                    if (coords == null || !coords.isAttached || result == null) {
                        false
                    } else {
                        val local = root - coords.localToRoot(Offset.Zero)
                        pointInsideTextBounds(
                            local.x,
                            local.y,
                            result.size.width.toFloat(),
                            result.size.height.toFloat(),
                        )
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
            formats.dragging = menuState.dragging
            formats.caretRectInWindow = { rectInWindow(field.selection.end) }
            formats.activeEdgeRectInWindow = {
                if (!menuState.dragging) null
                else rectInWindow(if (menuState.activeIsEnd) field.selection.end else field.selection.start)
            }
            formats.activeLineHeightPx = {
                val result = layout
                if (result == null || result.lineCount == 0) 0f
                else {
                    val offset = field.selection.end.coerceIn(0, result.layoutInput.text.length)
                    val line = result.getLineForOffset(offset)
                    result.getLineBottom(line) - result.getLineTop(line)
                }
            }
            formats.extendActiveEdge = { dy -> extendActiveEdge(dy) }
        }
    }
    toolbar.onSelectionGestureEnded = {
        menuState.onHandleDragEnded()
        if (!field.selection.collapsed) menuOverflow = false
    }
    toolbar.onSelectionGestureBegan = {
        menuState.onHandleDragBegan()
    }

    BackHandler(enabled = !field.selection.collapsed || menuState.shouldCompose || pasteAnchor != null) {
        collapseSelection()
    }

    fun scheduleReveal(result: TextLayoutResult) {
        if (menuState.dragging || formats.dragging) return
        if (!pendingReveal || readOnly || !blockFocused) return
        val laidOut = result.layoutInput.text.text
        if (laidOut != field.text) return
        pendingReveal = false
        val end = field.selection.end.coerceIn(0, laidOut.length)
        val rect = result.getCursorRect(end).inflate(with(density) { 16.dp.toPx() })
        scope.launch { bring.bringIntoView(rect) }
    }

    fun holdCaret() {
        caretHold = true
        caretHoldJob.job?.cancel()
        caretHoldJob.job = scope.launch {
            delay(tapWait)
            caretHold = false
        }
    }

    gestures.onTapUp = {
        forcedSelection.range = null
        if (field.selection.collapsed) holdCaret()
        scope.launch {
            withFrameNanos { }
            if (blockFocused && !formats.editingCell) formats.caretToken++
        }
    }
    gestures.onDoubleTap = doubleTap@{ pos ->
        caretHoldJob.job?.cancel()
        caretHold = true
        val result = layout
        val range = result?.let { pressedWord(field.text, it, pos) }
        if (result == null || range == null) {
            caretHold = false
            return@doubleTap
        }
        if (!hiddenUnlocked && HiddenText.cursorInside(marks, range.start, range.end)) {
            requestUnlock()
            caretHold = false
            return@doubleTap
        }
        scope.launch {
            withFrameNanos { }
            val old = field.selection
            forcedSelection.range = range
            field = field.copy(selection = range)
            menuOverflow = false
            toolbar.markSelectionShown()
            menuState.onSelectionChanged(old, range)
            pasteAnchor = null
            caretHold = false
            withFrameNanos { }
            forcedSelection.range = null
        }
    }
    gestures.onLongPress = longPress@{ pos ->
        val result = layout ?: return@longPress
        val offset = result.getOffsetForPosition(pos)
        val onGlyph = glyphHit(result, pos, 0f)
        val word = pressedWord(field.text, result, pos)
        val hiddenWord = word != null &&
            !hiddenUnlocked &&
            HiddenText.cursorInside(marks, word.start, word.end)
        if (hiddenWord) {
            requestUnlock()
            return@longPress
        }
        if (word != null) {
            SelectionHaptics.tick(view)
            menuState.onHandleDragBegan()
            scope.launch {
                withFrameNanos { }
                val old = field.selection
                forcedSelection.range = word
                field = field.copy(selection = word)
                menuOverflow = false
                toolbar.markSelectionShown()
                menuState.onSelectionChanged(old, word)
                pasteAnchor = null
                withFrameNanos { }
                forcedSelection.range = null
            }
            return@longPress
        }
        if (
            field.selection.collapsed &&
            !readOnly &&
            !pinDialogVisible &&
            clipboard.hasText() &&
            isBlankLongPress(field.text, offset, onGlyph)
        ) {
            pasteGuard[0] = 1
            pasteAnchor = result.getCursorRect(offset.coerceIn(0, field.text.length))
            scope.launch {
                withFrameNanos { }
                pasteGuard[0] = 0
            }
        }
    }
    gestures.onGestureEnd = {
        scope.launch {
            withFrameNanos { }
            forcedSelection.range = null
            menuState.onHandleDragEnded()
        }
    }

    InterceptPlatformTextInput(interceptor = tableBackspace) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            val selectionColors = LocalTextSelectionColors.current
            val shownColors = if (caretHold) {
                TextSelectionColors(
                    handleColor = Color.Transparent,
                    backgroundColor = selectionColors.backgroundColor,
                )
            } else {
                selectionColors
            }
            CompositionLocalProvider(
                LocalTextToolbar provides toolbar,
                LocalTextSelectionColors provides shownColors,
            ) {
                applyIncoming = incoming@{ next ->
                    if (readOnly) return@incoming
                    val previous = field.text
                    val previousSelection = field.selection
                    val textChanged = next.text != previous
                    val selectionMoved = next.selection != previousSelection
                    if (pasteGuard[0] == 1 && !textChanged && next.selection.collapsed) {
                        pasteGuard[0] = 0
                    } else if (textChanged || selectionMoved) {
                        pasteAnchor = null
                    }
                    if (blockMenu && next.text == previous) {
                        field = field.copy(selection = previousSelection)
                        hideMenu()
                        return@incoming
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
                        return@incoming
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
                    val listedMarks = if (edit.text == previous) {
                        marks
                    } else {
                        MarkEdits.shift(marks, previous, edit.text)
                    }
                    val shortcut = if (
                        next.composition == null &&
                        (hiddenUnlocked || !HiddenText.cursorInside(marks, edit.cursor, edit.cursor))
                    ) {
                        MarkdownShortcuts.apply(
                            text = edit.text,
                            cursor = edit.cursor,
                            marks = listedMarks,
                            rejectLockedHidden = !hiddenUnlocked,
                        )
                    } else {
                        null
                    }
                    val resolvedText = shortcut?.text ?: edit.text
                    val resolvedCursor = (shortcut?.cursor ?: edit.cursor).coerceIn(0, resolvedText.length)
                    val forced = forcedSelection.range
                    val selection = when {
                        shortcut != null -> TextRange(resolvedCursor)
                        forced != null && resolvedText == previous -> forced
                        edit.text == next.text -> snapDraggedSelection(resolvedText, previousSelection, next.selection)
                        else -> TextRange(edit.cursor.coerceIn(0, edit.text.length))
                    }
                    if (resolvedText != previous) forcedSelection.range = null
                    field = if (resolvedText != next.text) {
                        TextFieldValue(resolvedText, selection, composition = null)
                    } else {
                        next.copy(text = resolvedText, selection = selection)
                    }
                    fieldNow.current = field
                    if (resolvedText != previous) {
                        if (blockFocused) pendingReveal = true
                    }
                    if (selection != previousSelection) {
                        menuState.onSelectionChanged(previousSelection, selection)
                    }
                    if (selection.collapsed) menuOverflow = false
                    val slice = if (shortcut == null) textInsertion(previous, resolvedText) else null
                    var nextMarks = shortcut?.marks ?: listedMarks
                    var kind = if (shortcut != null) {
                        EditKind.STRUCTURE
                    } else {
                        EditHistory.kindForTextChange(previous, next.text, resolvedText)
                    }
                    if (slice != null) {
                        val pasted = RichClip.match(resolvedText.substring(slice.start, slice.end))
                        if (pasted != null) {
                            nextMarks = RichClip.overlay(nextMarks, slice.start, pasted)
                            kind = EditKind.STRUCTURE
                        }
                    }
                    if (
                        slice != null &&
                        previousSelection.collapsed &&
                        previous.length + (slice.end - slice.start) == resolvedText.length
                    ) {
                        nextMarks = MarkEdits.applyTyping(
                            nextMarks,
                            slice.start,
                            slice.end,
                            formats.pendingOn,
                            formats.pendingOff,
                        )
                    }
                    if (resolvedText == previous && selection != previousSelection) {
                        clearPendingStyles()
                    }
                    onDocument(
                        resolvedText,
                        nextMarks,
                        kind,
                        previousSelection.end,
                        selection.end,
                    )
                    val ready = layout
                    if (ready != null) scheduleReveal(ready)
                }
                val monoPlate = MaterialTheme.colorScheme.surfaceVariant
                BasicTextField(
                    value = field,
                    readOnly = readOnly,
                    onValueChange = { applyIncoming(it) },
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
                            AppLog.i(
                                "EditorFocus",
                                if (it.isFocused) "text block focused" else "text block cleared",
                            )
                            if (!it.isFocused) pasteAnchor = null
                            if (it.isFocused) {
                                formats.bodyFocused = true
                                formats.editingCell = false
                                formats.caretToken++
                            } else {
                                formats.bodyFocused = false
                                clearPendingStyles()
                            }
                            onFocused(it.isFocused)
                        }
                        .then(spoilerModifier)
                        .followLinkTaps(
                            marks = marks,
                            menuVisible = menuState.shouldCompose,
                            layout = layout,
                            hiddenUnlocked = hiddenUnlocked,
                        )
                        .editorTapGestures(gestures),
                    textStyle = bodyStyle,
                    cursorBrush = SolidColor(if (caretHold) Color.Transparent else NotesPurple),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true,
                    ),
                    visualTransformation = remember(marks, spoiler.glyphsHidden, monoPlate) {
                        NoteVisual(
                            marks,
                            unlocked = !spoiler.glyphsHidden,
                            monoPlate = monoPlate,
                            linkInk = NotesPurple,
                        )
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
            if (shown != null && menuState.shouldCompose && blockFocused) {
                val length = shown.layoutInput.text.length
                val start = field.selection.min.coerceIn(0, length)
                val end = field.selection.max.coerceIn(0, length)
                if (start != end) {
                    SelectionActionsPopup(
                        startHandle = shown.getCursorRect(start),
                        endHandle = shown.getCursorRect(end),
                        overflow = menuOverflow,
                        activeStyles = stylesTouching(marks, field.selection),
                        onCut = { cutSelection() },
                        onCopy = { copySelection() },
                        onPaste = { pasteSelection() },
                        onMore = { menuOverflow = true },
                        onDismiss = { if (menuState.shouldCompose) collapseSelection() },
                        menuAlpha = { menuState.alpha.value },
                        onBack = { menuOverflow = false },
                        onHidden = { applyMark(MarkStyle.HIDDEN) },
                        onSelectAll = { selectAll() },
                        onBold = { applyMark(MarkStyle.BOLD) },
                        onItalic = { applyMark(MarkStyle.ITALIC) },
                        onStrike = { applyMark(MarkStyle.STRIKE) },
                        onUnderline = { applyMark(MarkStyle.UNDERLINE) },
                        onMono = { applyMark(MarkStyle.MONO) },
                        onLink = {
                            val start = field.selection.min
                            val end = field.selection.max
                            if (start != end) linkDraft = TextRange(start, end)
                        },
                        onPlain = { clearStyles() },
                    )
                }
            }
            val anchor = pasteAnchor
            if (anchor != null && field.selection.collapsed && blockFocused && clipboard.hasText()) {
                PasteActionPopup(
                    anchor = anchor,
                    onPaste = {
                        pasteSelection()
                        pasteAnchor = null
                    },
                    onDismiss = clearPaste,
                )
            }
        }
    }
    ReportLinkDialog(formats, linkDraft != null)
    val draft = linkDraft
    if (draft != null) {
        LinkDialog(
            initial = coveringLinkUrl(marks, draft.min, draft.max),
            onConfirm = { url ->
                val caret = field.selection.end
                onDocument(
                    text,
                    MarkEdits.applyLink(marks, draft.min, draft.max, url),
                    EditKind.STRUCTURE,
                    caret,
                    caret,
                )
                linkDraft = null
                hideMenu()
            },
            onDismiss = { linkDraft = null },
        )
    }
    }
}

private class FieldSlot(var current: TextFieldValue)

private class RangeSlot(var range: TextRange? = null)

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
 * Selection uses the in-app bubble. Compose hides the toolbar when a handle
 * drag starts and shows it again when the finger lifts. Paste is not opened
 * from here. The platform toolbar is never shown.
 */
internal class RoutingTextToolbar(
    private val platform: TextToolbar,
) : TextToolbar {
    private var selectionMenu = false
    var onSelectionGestureEnded: () -> Unit = {}
    var onSelectionGestureBegan: () -> Unit = {}

    fun markSelectionShown() {
        selectionMenu = true
    }

    fun dismissQuietly() {
        selectionMenu = false
        platform.hide()
    }

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
        if (!ranged) {
            selectionMenu = false
            platform.hide()
            return
        }
        val gestureEnded = !selectionMenu
        selectionMenu = true
        platform.hide()
        if (gestureEnded) onSelectionGestureEnded()
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
    private val monoPlate: Color,
    private val linkInk: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            marks.forEach { mark ->
                if (mark.style == MarkStyle.HIDDEN) return@forEach
                val start = mark.start.coerceIn(0, length)
                val end = mark.end.coerceIn(0, length)
                if (end <= start) return@forEach
                addStyle(styleFor(mark.style, monoPlate, linkInk), start, end)
            }
            if (!unlocked) {
                marks.forEach { mark ->
                    if (mark.style != MarkStyle.HIDDEN) return@forEach
                    val start = mark.start.coerceIn(0, length)
                    val end = mark.end.coerceIn(0, length)
                    if (end > start) addStyle(SpanStyle(color = Color.Transparent), start, end)
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private fun styleFor(style: MarkStyle, monoPlate: Color, linkInk: Color): SpanStyle = when (style) {
    MarkStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    MarkStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    MarkStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    MarkStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    MarkStyle.MONO -> SpanStyle(fontFamily = FontFamily.Monospace, background = monoPlate)
    MarkStyle.LINK -> SpanStyle(color = linkInk, textDecoration = TextDecoration.Underline)
    MarkStyle.HIDDEN -> SpanStyle(color = Color.Transparent)
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
