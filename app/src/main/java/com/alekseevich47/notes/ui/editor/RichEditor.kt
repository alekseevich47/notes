package com.alekseevich47.notes.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.MarkEdits
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteTable
import com.alekseevich47.notes.domain.NoteTables
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple
import kotlin.math.max
import kotlin.math.min

private const val TABLE_MAX_ROWS = NoteTables.MAX_ROWS
private const val TABLE_MAX_COLS = NoteTables.MAX_COLS

private val FrostPlateLight = Color(0xFFE6E0F2)
private val FrostPlateDark = Color(0xFF3C3850)
private val numberedLine = Regex("""^\d+\.\s?.*""")

@Composable
internal fun RichNoteEditor(
    document: NoteDocument,
    hiddenUnlocked: Boolean,
    onDocument: (NoteDocument) -> Unit,
    onNeedSelection: () -> Unit,
    onRequestUnlock: () -> Unit,
    onAskTable: () -> Unit,
    focusRequester: FocusRequester,
    formats: NoteEditorController,
    pinDialogVisible: Boolean,
    surface: NoteSurfaceBridge,
    modifier: Modifier = Modifier,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(document.text, TextRange(document.text.length)))
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menuOverflow by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var blockMenu by remember { mutableStateOf(false) }
    val prompt = remember { HiddenPromptGuard() }
    val clipboard = LocalClipboardManager.current
    val platformToolbar = LocalTextToolbar.current
    val toolbar = remember { RoutingTextToolbar(platformToolbar) }
    val background = MaterialTheme.colorScheme.background
    val dark = (0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue) < 0.4f
    val censorLayout = layout
    val censor = !hiddenUnlocked && document.marks.any { it.style == MarkStyle.HIDDEN }
    if (field.selection.collapsed && (menuOverflow || menuVisible)) {
        menuOverflow = false
        menuVisible = false
    }
    LaunchedEffect(blockMenu) {
        if (blockMenu) {
            withFrameNanos { }
            blockMenu = false
        }
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
        field = TextFieldValue(edit.text, TextRange(edit.cursor.coerceIn(0, edit.text.length)))
        if (field.selection.collapsed) hideMenu()
        onDocument(
            document.copy(
                text = edit.text,
                marks = MarkEdits.shift(document.marks, previous, edit.text),
            ),
        )
    }

    fun collapseSelection() {
        blockMenu = true
        if (!field.selection.collapsed) {
            field = field.copy(selection = TextRange(field.selection.end))
        }
        hideMenu()
    }

    fun placeCaret(offset: Int) {
        val cursor = offset.coerceIn(0, field.text.length)
        blockMenu = true
        field = field.copy(selection = TextRange(cursor))
        hideMenu()
        if (
            !hiddenUnlocked &&
            HiddenText.cursorInside(document.marks, cursor, cursor) &&
            prompt.armed &&
            !prompt.dialogOpen
        ) {
            requestUnlock()
        }
    }

    fun applyMark(style: MarkStyle) {
        val start = field.selection.min
        val end = field.selection.max
        if (start == end) {
            onNeedSelection()
            return
        }
        onDocument(document.copy(marks = MarkEdits.toggle(document.marks, style, start, end)))
        hideMenu()
    }

    fun toggleList() {
        val previous = field.text
        publish(previous, ListAutoFormat.toggleLine(previous, field.selection.min))
    }

    fun overlapsLockedHidden(): Boolean {
        if (hiddenUnlocked) return false
        return HiddenText.cursorInside(document.marks, field.selection.start, field.selection.end)
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
        publish(previous, ListAutoFormat.afterEdit(previous, proposed, start))
        hideMenu()
    }

    fun selectAll() {
        field = field.copy(selection = TextRange(0, field.text.length))
        hideMenu()
    }

    SideEffect {
        surface.readSelection = { SelectionSnapshot(field.selection.start, field.selection.end) }
        surface.pointInside = { root, selection ->
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
        }
        surface.onClear = { root, _ ->
            val coords = textCoordinates
            val result = layout
            if (coords == null || !coords.isAttached || result == null) {
                collapseSelection()
            } else {
                val local = root - coords.localToRoot(Offset.Zero)
                placeCaret(result.getOffsetForPosition(local))
            }
        }
        formats.bold = { applyMark(MarkStyle.BOLD) }
        formats.italic = { applyMark(MarkStyle.ITALIC) }
        formats.underline = { applyMark(MarkStyle.UNDERLINE) }
        formats.strike = { applyMark(MarkStyle.STRIKE) }
        formats.hidden = { applyMark(MarkStyle.HIDDEN) }
        formats.list = { toggleList() }
        formats.table = onAskTable
        formats.activeStyles = stylesTouching(document.marks, field.selection)
        formats.listActive = lineNumbered(field.text, field.selection.min)
    }

    BackHandler(enabled = !field.selection.collapsed || menuVisible) {
        collapseSelection()
    }

    Column(modifier.fillMaxWidth()) {
        if (censor) {
            Text(
                "Открыть скрытый текст",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = { requestUnlock() })
                    .padding(bottom = 6.dp),
                color = NotesPurple,
                fontFamily = NotesFont,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                BasicTextField(
                    value = field,
                    onValueChange = { next ->
                        val previous = field.text
                        val previousSelection = field.selection
                        if (blockMenu && next.text == previous) {
                            field = field.copy(selection = previousSelection)
                            hideMenu()
                            return@BasicTextField
                        }
                        if (blockMenu) blockMenu = false
                        val textHitsHidden = HiddenText.editTouches(document.marks, previous, next.text)
                        val cursorInHidden = HiddenText.cursorInside(
                            document.marks,
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
                        val edit = ListAutoFormat.afterEdit(previous, next.text, next.selection.end)
                        val selection = if (edit.text == next.text) next.selection else TextRange(edit.cursor)
                        field = next.copy(text = edit.text, selection = selection)
                        if (selection.collapsed) {
                            hideMenu()
                        } else if (selection != previousSelection) {
                            menuVisible = true
                        }
                        onDocument(
                            document.copy(
                                text = edit.text,
                                marks = MarkEdits.shift(document.marks, previous, edit.text),
                            ),
                        )
                    },
                    onTextLayout = { result ->
                        val previous = layout
                        if (
                            previous == null ||
                            previous.size != result.size ||
                            previous.layoutInput.text.text != result.layoutInput.text.text
                        ) {
                            layout = result
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { textCoordinates = it }
                        .focusRequester(focusRequester)
                        .onFocusChanged { formats.bodyFocused = it.isFocused }
                        .drawWithContent {
                            drawContent()
                            if (censor && censorLayout != null) {
                                drawHiddenFrost(censorLayout, document.marks, dark)
                            }
                        },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = NotesFont,
                    ),
                    cursorBrush = SolidColor(NotesPurple),
                    visualTransformation = remember(document.marks, hiddenUnlocked, dark) {
                        NoteVisual(document.marks, hiddenUnlocked, dark)
                    },
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (field.text.isEmpty()) {
                                Text(
                                    "Начните писать…",
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

private class HiddenPromptGuard {
    var dialogOpen: Boolean = false
    var armed: Boolean = true
}

/**
 * Selection uses the in-app bubble, so the platform toolbar is hidden while
 * text is selected. A collapsed cursor still gets the platform menu (paste).
 */
private class RoutingTextToolbar(
    private val platform: TextToolbar,
) : TextToolbar {
    private var selectionMenu = false

    override val status: TextToolbarStatus
        get() = if (selectionMenu) TextToolbarStatus.Shown else platform.status

    override fun hide() {
        selectionMenu = false
        platform.hide()
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        val selected = onCopyRequested != null || onCutRequested != null
        if (selected) {
            selectionMenu = true
            platform.hide()
        } else {
            selectionMenu = false
            platform.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
        }
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
    val lineStart = text.lastIndexOf('\n', (safe - 1).coerceAtLeast(0)).let { index ->
        if (index < 0) 0 else index + 1
    }
    val lineEnd = text.indexOf('\n', safe).let { index -> if (index < 0) text.length else index }
    return numberedLine.matches(text.substring(lineStart, lineEnd))
}

private class NoteVisual(
    private val marks: List<TextMark>,
    private val unlocked: Boolean,
    private val dark: Boolean,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            marks.forEach { mark ->
                val start = mark.start.coerceIn(0, length)
                val end = mark.end.coerceIn(0, length)
                if (end <= start) return@forEach
                addStyle(styleFor(mark.style, unlocked, dark), start, end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private fun styleFor(style: MarkStyle, unlocked: Boolean, dark: Boolean): SpanStyle = when (style) {
    MarkStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    MarkStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    MarkStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    MarkStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    MarkStyle.HIDDEN -> if (unlocked) {
        SpanStyle(background = NotesPurple.copy(alpha = 0.18f), textDecoration = TextDecoration.Underline)
    } else {
        val plate = if (dark) FrostPlateDark else FrostPlateLight
        SpanStyle(color = plate, background = plate)
    }
}

/**
 * Locked hidden text is painted the same colour as the plate (glyphs never show)
 * and covered with layered soft streaks. No AGSL and no RenderEffect — those break
 * compositing on Tecno.
 */
private fun DrawScope.drawHiddenFrost(layout: TextLayoutResult, marks: List<TextMark>, dark: Boolean) {
    val textLength = layout.layoutInput.text.length
    if (textLength == 0) return
    val radius = 6.dp.toPx()
    val padX = 1.5.dp.toPx()
    val padY = 1.dp.toPx()
    val tile = 3.dp.toPx()
    val streak = 2.5.dp.toPx()
    marks.filter { it.style == MarkStyle.HIDDEN }.forEach { mark ->
        val start = mark.start.coerceIn(0, textLength)
        val end = mark.end.coerceIn(0, textLength)
        if (end <= start) return@forEach
        val last = (end - 1).coerceAtMost(textLength - 1)
        val startLine = layout.getLineForOffset(start.coerceAtMost(last))
        val endLine = layout.getLineForOffset(last)
        for (line in startLine..endLine) {
            val from = max(start, layout.getLineStart(line))
            val to = min(end, layout.getLineEnd(line, visibleEnd = true))
            if (to <= from) continue
            val left = layout.getHorizontalPosition(from, true)
            val right = layout.getHorizontalPosition(to, true)
            val barLeft = min(left, right) - padX
            val barRight = max(left, right) + padX
            if (barRight - barLeft < 1f) continue
            val top = layout.getLineTop(line) - padY
            val bottom = layout.getLineBottom(line) + padY
            if (bottom - top < 1f) continue
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = barLeft,
                        top = top,
                        right = barRight,
                        bottom = bottom,
                        cornerRadius = CornerRadius(radius, radius),
                    ),
                )
            }
            clipPath(path) {
                drawRect(
                    color = if (dark) FrostPlateDark else FrostPlateLight,
                    topLeft = Offset(barLeft, top),
                    size = Size(barRight - barLeft, bottom - top),
                )
                var y = top
                var row = 0
                while (y < bottom - 0.4f) {
                    val height = min(streak, bottom - y)
                    drawRect(
                        color = frostStreak(row, line, dark),
                        topLeft = Offset(barLeft, y),
                        size = Size(barRight - barLeft, height),
                    )
                    y += streak
                    row++
                }
                y = top
                row = 0
                while (y < bottom - 0.4f) {
                    val height = min(tile, bottom - y)
                    var x = barLeft
                    var column = 0
                    while (x < barRight - 0.4f) {
                        val width = min(tile, barRight - x)
                        drawRect(
                            color = frostSpeck(row, column, line, dark),
                            topLeft = Offset(x, y),
                            size = Size(width, height),
                        )
                        x += tile
                        column++
                    }
                    y += tile
                    row++
                }
                val band = bottom - top
                drawRect(
                    color = Color.White.copy(alpha = if (dark) 0.10f else 0.34f),
                    topLeft = Offset(barLeft, top),
                    size = Size(barRight - barLeft, band * 0.42f),
                )
                drawRect(
                    color = (if (dark) Color(0xFF120E1C) else Color(0xFF6A6280)).copy(alpha = if (dark) 0.16f else 0.07f),
                    topLeft = Offset(barLeft, top + band * 0.58f),
                    size = Size(barRight - barLeft, band * 0.42f),
                )
            }
        }
    }
}

private fun frostStreak(row: Int, line: Int, dark: Boolean): Color {
    val light = arrayOf(
        Color(0x88F7F4FC),
        Color(0x66D5CEE6),
        Color(0x55F1ECF8),
        Color(0x77C8C0D8),
    )
    val night = arrayOf(
        Color(0x88524C68),
        Color(0x66342F44),
        Color(0x775A5470),
        Color(0x552C2838),
    )
    val palette = if (dark) night else light
    val mixed = (row * 3 + line * 5) and 0x7fffffff
    return palette[mixed % palette.size]
}

private fun frostSpeck(row: Int, column: Int, line: Int, dark: Boolean): Color {
    val light = arrayOf(
        Color(0x55FFFFFF),
        Color(0x38C8C0D6),
        Color(0x22F8F5FC),
        Color(0x40B5ADC6),
        Color(0x30FFFFFF),
    )
    val night = arrayOf(
        Color(0x44706888),
        Color(0x33241F34),
        Color(0x285E5874),
        Color(0x447C7592),
        Color(0x22161022),
    )
    val palette = if (dark) night else light
    val mixed = (row * 13 + column * 7 + line * 3 + (row xor column)) and 0x7fffffff
    return palette[mixed % palette.size]
}

@Composable
fun TableGrid(
    table: NoteTable,
    onChange: (NoteTable) -> Unit,
    onDelete: () -> Unit,
) {
    val rows = NoteTables.normalized(table.cells)
    val width = rows.firstOrNull()?.size ?: 0
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, border, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (width == 0) "Таблица" else "Таблица  ${rows.size} × $width",
                fontFamily = NotesFont,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Удалить",
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                fontFamily = NotesFont,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (rows.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val minCol = 112.dp
                val colWidth = if (minCol * width <= maxWidth) maxWidth / width else minCol
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(colWidth * width)) {
                        rows.forEachIndexed { rowIndex, row ->
                            val header = rowIndex == 0
                            Row(Modifier.fillMaxWidth()) {
                                row.forEachIndexed { colIndex, cell ->
                                    key(rowIndex to colIndex) {
                                        TableCellField(
                                            value = cell,
                                            header = header,
                                            border = border,
                                            headerBackground = headerBg,
                                            onValue = { value ->
                                                val next = rows.map { it.toMutableList() }
                                                next[rowIndex][colIndex] = value
                                                onChange(table.copy(cells = next.map { it.toList() }))
                                            },
                                            modifier = Modifier.width(colWidth),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TableAction("+ Строка", enabled = rows.size < TABLE_MAX_ROWS) {
                    onChange(table.copy(cells = NoteTables.addRow(table.cells)))
                }
                TableAction("- Строка", enabled = rows.size > 1) {
                    onChange(table.copy(cells = NoteTables.removeRow(table.cells)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TableAction("+ Столбец", enabled = width < TABLE_MAX_COLS) {
                    onChange(table.copy(cells = NoteTables.addColumn(table.cells)))
                }
                TableAction("- Столбец", enabled = width > 1) {
                    onChange(table.copy(cells = NoteTables.removeColumn(table.cells)))
                }
            }
        }
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

@Composable
private fun TableCellField(
    value: String,
    header: Boolean,
    border: Color,
    headerBackground: Color,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != field.text) {
            field = TextFieldValue(value, TextRange(value.length))
        }
    }
    BasicTextField(
        value = field,
        onValueChange = { next ->
            val cleaned = next.text.replace('\n', ' ')
            val selection = if (cleaned.length == next.text.length) {
                next.selection
            } else {
                TextRange(cleaned.length.coerceAtMost(next.selection.end))
            }
            field = next.copy(text = cleaned, selection = selection)
            if (cleaned != value) onValue(cleaned)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = NotesFont,
            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(NotesPurple),
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(if (header) headerBackground else Color.Transparent)
            .border(0.5.dp, border),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && header) {
                    Text(
                        "Заголовок",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontFamily = NotesFont,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun TableAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        fontFamily = NotesFont,
        color = if (enabled) NotesPurple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
    )
}
