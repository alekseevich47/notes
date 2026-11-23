package com.alekseevich47.notes.ui.editor

import android.view.ActionMode
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.MarkEdits
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.NoteTable
import com.alekseevich47.notes.domain.NoteTables
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

private val TableBlue = Color(0xFF2F80ED)
private val FillLight = Color(0xFFE7E5EC)
private val FillDark = Color(0xFF2A2833)
private val TableCorner = 10.dp

private val AlignHorizontal = listOf(
    Icons.Outlined.FormatAlignLeft,
    Icons.Outlined.FormatAlignCenter,
    Icons.Outlined.FormatAlignRight,
)
private val AlignVertical = listOf(
    Icons.Outlined.VerticalAlignTop,
    Icons.Outlined.VerticalAlignCenter,
    Icons.Outlined.VerticalAlignBottom,
)

private sealed interface TableMenu {
    data class Row(val index: Int) : TableMenu
    data class Column(val index: Int) : TableMenu
    data class Cell(val row: Int, val col: Int) : TableMenu
}

/**
 * A long-press opens the cell table menu unless it lands on glyphs.
 * Focus does not matter: another cell being edited still gets the cell menu.
 * Glyph presses stay with the text selection popup.
 */
internal fun cellLongPressOpensCellMenu(onGlyph: Boolean): Boolean = !onGlyph

internal data class CellSpan(
    val row: Int,
    val col: Int,
    val colSpan: Int,
    val rowSpan: Int,
)

/** Anchors only. Covered cells are omitted so they are not drawn. */
internal fun anchorSpans(
    rowCount: Int,
    colCount: Int,
    covered: (row: Int, col: Int) -> Boolean,
    colSpan: (row: Int, col: Int) -> Int,
    rowSpan: (row: Int, col: Int) -> Int,
): List<CellSpan> {
    if (rowCount <= 0 || colCount <= 0) return emptyList()
    val spans = mutableListOf<CellSpan>()
    for (row in 0 until rowCount) {
        var col = 0
        while (col < colCount) {
            if (!covered(row, col)) {
                val columns = colSpan(row, col).coerceIn(1, colCount - col)
                val rows = rowSpan(row, col).coerceIn(1, rowCount - row)
                spans += CellSpan(row, col, columns, rows)
                col += columns
            } else {
                col++
            }
        }
    }
    return spans
}

/**
 * Row heights from single-row cells, then any extra a vertical merge needs
 * so the anchor's box is the sum of the rows it covers.
 */
internal fun rowHeightsForSpans(
    rowCount: Int,
    spans: List<CellSpan>,
    contentHeights: FloatArray,
    minRowPx: Float,
): FloatArray {
    val heights = FloatArray(rowCount) { minRowPx.coerceAtLeast(0f) }
    spans.forEachIndexed { index, span ->
        if (span.rowSpan == 1 && span.row in heights.indices) {
            heights[span.row] = max(heights[span.row], contentHeights.getOrElse(index) { 0f })
        }
    }
    spans.forEachIndexed { index, span ->
        if (span.rowSpan <= 1) return@forEachIndexed
        val last = (span.row + span.rowSpan - 1).coerceIn(0, heights.lastIndex)
        var sum = 0f
        for (row in span.row until span.row + span.rowSpan) {
            if (row in heights.indices) sum += heights[row]
        }
        val extra = contentHeights.getOrElse(index) { 0f } - sum
        if (extra > 0f) heights[last] += extra
    }
    return heights
}

internal fun spanOrigin(span: CellSpan, colWidths: FloatArray, rowHeights: FloatArray): Pair<Float, Float> {
    var x = 0f
    for (col in 0 until span.col) x += colWidths.getOrElse(col) { 0f }
    var y = 0f
    for (row in 0 until span.row) y += rowHeights.getOrElse(row) { 0f }
    return x to y
}

internal fun spanSize(span: CellSpan, colWidths: FloatArray, rowHeights: FloatArray): Pair<Float, Float> {
    var width = 0f
    for (col in span.col until span.col + span.colSpan) width += colWidths.getOrElse(col) { 0f }
    var height = 0f
    for (row in span.row until span.row + span.rowSpan) height += rowHeights.getOrElse(row) { 0f }
    return width to height
}

internal fun growingColumnWidths(
    longestChars: IntArray,
    availablePx: Float,
    minPx: Float,
    maxPx: Float,
): FloatArray {
    if (longestChars.isEmpty()) return FloatArray(0)
    val preferred = FloatArray(longestChars.size) { index ->
        (longestChars[index] * 11f + 36f).coerceIn(minPx, maxPx)
    }
    val sum = preferred.sum()
    if (sum < availablePx && preferred.isNotEmpty()) {
        val extra = (availablePx - sum) / preferred.size
        for (index in preferred.indices) preferred[index] += extra
    }
    return preferred
}

@Composable
fun TableBlock(
    table: NoteTable,
    blockIndex: Int,
    hiddenUnlocked: Boolean,
    formats: NoteEditorController,
    onChange: (NoteTable) -> Unit,
    onDelete: () -> Unit,
    onAskTable: () -> Unit,
    onRequestUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = NoteTables.normalized(table.cells)
    val width = rows.firstOrNull()?.size ?: 0
    var active by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var menu by remember { mutableStateOf<TableMenu?>(null) }
    val rowHeights = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    var headerPx by remember { mutableStateOf(0f) }
    val rowBounds = remember(rowHeights.toList()) {
        var top = 0f
        rowHeights.map { height ->
            val span = top to (top + height)
            top += height
            span
        }
    }
    val editing = active != null
    val background = MaterialTheme.colorScheme.background
    val dark = (0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue) < 0.4f
    val fillColor = if (dark) FillDark else FillLight
    val gridLine = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val ink = MaterialTheme.colorScheme.onSurface
    BackHandler(enabled = menu != null) { menu = null }

    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp)
                .onGloballyPositioned { headerPx = it.size.height.toFloat() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var caption by remember(table.id) {
                mutableStateOf(TextFieldValue(table.caption, TextRange(table.caption.length)))
            }
            LaunchedEffect(table.caption) {
                if (caption.text != table.caption) {
                    caption = TextFieldValue(table.caption, TextRange(table.caption.length))
                }
            }
            BasicTextField(
                value = caption,
                onValueChange = { next ->
                    val cleaned = next.text.replace('\n', ' ')
                    val value = if (cleaned == next.text) next else next.copy(text = cleaned, selection = TextRange(cleaned.length))
                    caption = value
                    if (value.text != table.caption) onChange(NoteTables.withCaption(table, value.text))
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = NotesFont,
                    color = ink,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(NotesPurple),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                ),
                modifier = Modifier.weight(1f).padding(top = 6.dp, bottom = 6.dp, end = 8.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (table.caption.isEmpty()) {
                            Text(
                                "Заголовок...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                fontFamily = NotesFont,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        inner()
                    }
                },
            )
            AnimatedVisibility(
                visible = editing,
                enter = fadeIn(tween(160)) + slideInHorizontally(tween(180)) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutHorizontally(tween(140)) { it / 2 },
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Удалить таблицу",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .clickable(onClick = onDelete)
                        .padding(8.dp),
                )
            }
        }
        if (rows.isEmpty() || width == 0) return@Column
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val gutter = 32.dp
            val available = with(density) { (maxWidth - gutter - 12.dp).toPx() }.coerceAtLeast(1f)
            val minPx = with(density) { 72.dp.toPx() }
            val maxPx = with(density) { 240.dp.toPx() }
            val longest = IntArray(width) { col ->
                rows.maxOf { row ->
                    row.getOrElse(col) { "" }.lineSequence().maxOfOrNull { it.length } ?: 0
                }
            }
            val colWidths = growingColumnWidths(longest, available, minPx, maxPx)
            val colDp = colWidths.map { with(density) { it.toDp() } }
            val activeRow = active?.first
            val activeCol = active?.second
            val tableScroll = rememberScrollState()
            Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp)) {
                val gridHeight = rowBounds.lastOrNull()?.second ?: 0f
                Box(Modifier.width(gutter).height(with(density) { gridHeight.toDp() })) {
                    val focus = active
                    if (focus != null && menu == null && NoteTables.canInsertRow(table)) {
                        val bounds = rowBounds.getOrNull(focus.first)
                        if (bounds != null) {
                            JunctionPlus(
                                stemEnd = true,
                                onClick = { onChange(NoteTables.insertRow(table, focus.first)) },
                                modifier = Modifier.offset {
                                    IntOffset(0, (bounds.first - 11.dp.toPx()).roundToInt())
                                },
                            )
                            JunctionPlus(
                                stemEnd = true,
                                onClick = { onChange(NoteTables.insertRow(table, focus.first + 1)) },
                                modifier = Modifier.offset {
                                    IntOffset(0, (bounds.second - 11.dp.toPx()).roundToInt())
                                },
                            )
                        }
                    }
                    if (focus != null) {
                        val bounds = rowBounds.getOrNull(focus.first)
                        if (bounds != null) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "Строка",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .offset {
                                        val mid = (bounds.first + bounds.second) / 2f
                                        IntOffset(4, (mid - 12.dp.toPx()).roundToInt())
                                    }
                                    .clickable {
                                        menu = if (menu is TableMenu.Row && (menu as TableMenu.Row).index == focus.first) {
                                            null
                                        } else {
                                            TableMenu.Row(focus.first)
                                        }
                                    }
                                    .padding(2.dp),
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f).horizontalScroll(tableScroll)) {
                    val spans = anchorSpans(
                        rowCount = rows.size,
                        colCount = width,
                        covered = { row, col -> NoteTables.covered(table, row, col) },
                        colSpan = { row, col -> NoteTables.colSpan(table, row, col) },
                        rowSpan = { row, col -> NoteTables.rowSpan(table, row, col) },
                    )
                    val minRowPx = with(density) { 48.dp.toPx() }
                    MergedCellGrid(
                        spans = spans,
                        colWidthsPx = colWidths,
                        minRowPx = minRowPx,
                        onRowHeights = { heights ->
                            val changed = heights.size != rowHeights.size ||
                                heights.indices.any { rowHeights[it] != heights[it] }
                            if (changed) {
                                rowHeights.clear()
                                heights.forEach { rowHeights.add(it) }
                            }
                        },
                    ) {
                        spans.forEach { span ->
                            key(span.row to span.col) {
                                val open = menu
                                val highlighted = when (open) {
                                    is TableMenu.Row -> open.index == span.row
                                    is TableMenu.Column -> open.index == span.col
                                    is TableMenu.Cell -> open.row == span.row && open.col == span.col
                                    null -> false
                                }
                                val spanWidthPx = spanSize(span, colWidths, FloatArray(0)).first
                                val shape = cellCornerShape(
                                    topStart = span.row == 0 && span.col == 0,
                                    topEnd = span.row == 0 && span.col + span.colSpan >= width,
                                    bottomStart = span.row + span.rowSpan >= rows.size && span.col == 0,
                                    bottomEnd = span.row + span.rowSpan >= rows.size &&
                                        span.col + span.colSpan >= width,
                                )
                                TableCellEditor(
                                    table = table,
                                    row = span.row,
                                    col = span.col,
                                    width = with(density) { spanWidthPx.toDp() },
                                    shape = shape,
                                    fillColor = fillColor,
                                    gridLine = gridLine,
                                    ink = ink,
                                    hiddenUnlocked = hiddenUnlocked,
                                    highlighted = highlighted,
                                    focused = active == (span.row to span.col),
                                    formats = formats,
                                    blockIndex = blockIndex,
                                    onChange = onChange,
                                    onAskTable = onAskTable,
                                    onRequestUnlock = onRequestUnlock,
                                    onFocused = { focused ->
                                        if (focused) {
                                            active = span.row to span.col
                                            formats.editingCell = true
                                            if (menu !is TableMenu.Cell) menu = null
                                        } else if (active == (span.row to span.col)) {
                                            active = null
                                            formats.editingCell = false
                                        }
                                    },
                                    onCellMenu = { menu = TableMenu.Cell(span.row, span.col) },
                                )
                            }
                        }
                    }
                    Box(Modifier.height(36.dp).fillMaxWidth()) {
                        val col = activeCol
                        if (col != null) {
                            val left = colDp.take(col).fold(0.dp) { acc, dp -> acc + dp }
                            val cellW = colDp.getOrElse(col) { 0.dp }
                            if (menu == null && NoteTables.canInsertColumn(table)) {
                                // Column 0's left "+" sits 11.dp outside the scroll
                                // and gets clipped. It is drawn on the overlay below.
                                if (col != 0) {
                                    JunctionPlus(
                                        stemEnd = false,
                                        onClick = { onChange(NoteTables.insertColumn(table, col)) },
                                        modifier = Modifier.offset(x = left - 11.dp, y = 4.dp),
                                    )
                                }
                                JunctionPlus(
                                    stemEnd = false,
                                    onClick = { onChange(NoteTables.insertColumn(table, col + 1)) },
                                    modifier = Modifier.offset(x = left + cellW - 11.dp, y = 4.dp),
                                )
                            }
                            Icon(
                                Icons.Outlined.MoreHoriz,
                                contentDescription = "Столбец",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .offset(x = left + cellW / 2 - 12.dp, y = 6.dp)
                                    .clickable {
                                        menu = if (menu is TableMenu.Column && (menu as TableMenu.Column).index == col) {
                                            null
                                        } else {
                                            TableMenu.Column(col)
                                        }
                                    },
                            )
                        }
                    }
                    }
                }
            if (activeCol == 0 && menu == null && NoteTables.canInsertColumn(table)) {
                val shift = with(density) { tableScroll.value.toDp() }
                JunctionPlus(
                    stemEnd = false,
                    onClick = { onChange(NoteTables.insertColumn(table, 0)) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 4.dp + gutter - 11.dp - shift, y = (-4).dp),
                )
            }
            }
        }
        val open = menu
        if (open != null) {
            val lines = menuLines(table, open, onChange) { menu = null }
            val (h, v) = menuAlign(table, open)
            val top = when (open) {
                is TableMenu.Row -> rowBounds.getOrNull(open.index)?.first ?: 0f
                is TableMenu.Cell -> rowBounds.getOrNull(open.row)?.first ?: 0f
                is TableMenu.Column -> rowBounds.lastOrNull()?.second ?: 0f
            }
            TableActionsPopup(
                horizontal = h,
                vertical = v,
                onHorizontal = { value ->
                    onChange(applyAlign(table, open, horizontal = value, vertical = null))
                },
                onVertical = { value ->
                    onChange(applyAlign(table, open, horizontal = null, vertical = value))
                },
                lines = lines,
                onDismiss = { menu = null },
                offset = IntOffset(24, (headerPx + top).roundToInt()),
                horizontalIcons = AlignHorizontal,
                verticalIcons = AlignVertical,
            )
        }
    }
}

@Composable
private fun MergedCellGrid(
    spans: List<CellSpan>,
    colWidthsPx: FloatArray,
    minRowPx: Float,
    onRowHeights: (FloatArray) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val seen = remember { object { var heights = floatArrayOf() } }
    Layout(content = content, modifier = modifier) { measurables, _ ->
        val widths = FloatArray(spans.size)
        spans.forEachIndexed { index, span ->
            widths[index] = spanSize(span, colWidthsPx, FloatArray(0)).first
        }
        // Probe content height without measure(): Compose forbids measuring the
        // same Measurable twice in one pass (crash on insert).
        val contentHeights = FloatArray(measurables.size) { index ->
            val widthPx = widths[index].roundToInt().coerceAtLeast(0)
            measurables[index].minIntrinsicHeight(widthPx).toFloat()
        }
        val rowCount = spans.maxOfOrNull { it.row + it.rowSpan } ?: 0
        val rowHeights = rowHeightsForSpans(rowCount, spans, contentHeights, minRowPx)
        if (!seen.heights.contentEquals(rowHeights)) {
            val snapshot = rowHeights.copyOf()
            seen.heights = snapshot
            scope.launch { onRowHeights(snapshot) }
        }
        val placed = measurables.mapIndexed { index, measurable ->
            val (widthPx, heightPx) = spanSize(spans[index], colWidthsPx, rowHeights)
            measurable.measure(
                Constraints(
                    minWidth = widthPx.roundToInt().coerceAtLeast(0),
                    maxWidth = widthPx.roundToInt().coerceAtLeast(0),
                    minHeight = heightPx.roundToInt().coerceAtLeast(0),
                    maxHeight = heightPx.roundToInt().coerceAtLeast(0),
                ),
            )
        }
        val totalWidth = colWidthsPx.sum().roundToInt().coerceAtLeast(0)
        val totalHeight = rowHeights.sum().roundToInt().coerceAtLeast(0)
        layout(totalWidth, totalHeight) {
            placed.forEachIndexed { index, placeable ->
                val (x, y) = spanOrigin(spans[index], colWidthsPx, rowHeights)
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

private fun menuAlign(table: NoteTable, menu: TableMenu): Pair<Int, Int> {
    val (row, col) = when (menu) {
        is TableMenu.Row -> menu.index to 0
        is TableMenu.Column -> 0 to menu.index
        is TableMenu.Cell -> menu.row to menu.col
    }
    return NoteTables.alignH(table, row, col) to NoteTables.alignV(table, row, col)
}

private fun applyAlign(table: NoteTable, menu: TableMenu, horizontal: Int?, vertical: Int?): NoteTable {
    val (row, col) = when (menu) {
        is TableMenu.Row -> menu.index to 0
        is TableMenu.Column -> 0 to menu.index
        is TableMenu.Cell -> menu.row to menu.col
    }
    val h = horizontal ?: NoteTables.alignH(table, row, col)
    val v = vertical ?: NoteTables.alignV(table, row, col)
    return when (menu) {
        is TableMenu.Row -> NoteTables.alignRow(table, menu.index, h, v)
        is TableMenu.Column -> NoteTables.alignColumn(table, menu.index, h, v)
        is TableMenu.Cell -> NoteTables.alignCell(table, menu.row, menu.col, h, v)
    }
}

@Composable
private fun menuLines(
    table: NoteTable,
    menu: TableMenu,
    onChange: (NoteTable) -> Unit,
    close: () -> Unit,
): List<TableMenuLine> {
    fun run(block: () -> NoteTable) {
        onChange(block())
        close()
    }
    return when (menu) {
        is TableMenu.Cell -> listOf(fillLine(NoteTables.filled(table, menu.row, menu.col), "Залить ячейку") {
            run { NoteTables.fillCell(table, menu.row, menu.col, !NoteTables.filled(table, menu.row, menu.col)) }
        })
        is TableMenu.Row -> listOf(
            fillLine(NoteTables.rowFilled(table, menu.index), "Залить строку") {
                val filled = NoteTables.rowFilled(table, menu.index)
                run { NoteTables.fillRow(table, menu.index, !filled) }
            },
            TableMenuLine("Объединить", Icons.Outlined.CallMerge) {
                run { NoteTables.mergeRow(table, menu.index) }
            },
            TableMenuLine(
                "Вставить сверху",
                Icons.Outlined.KeyboardArrowUp,
                enabled = NoteTables.canInsertRow(table),
            ) { run { NoteTables.insertRow(table, menu.index) } },
            TableMenuLine(
                "Вставить снизу",
                Icons.Outlined.KeyboardArrowDown,
                enabled = NoteTables.canInsertRow(table),
            ) { run { NoteTables.insertRow(table, menu.index + 1) } },
            TableMenuLine(
                "Удалить строку",
                Icons.Outlined.Delete,
                destructive = true,
                enabled = NoteTables.canDeleteRow(table),
            ) { run { NoteTables.deleteRow(table, menu.index) } },
        )
        is TableMenu.Column -> listOf(
            fillLine(NoteTables.columnFilled(table, menu.index), "Залить столбец") {
                val filled = NoteTables.columnFilled(table, menu.index)
                run { NoteTables.fillColumn(table, menu.index, !filled) }
            },
            TableMenuLine("Объединить", Icons.Outlined.CallMerge) {
                run { NoteTables.mergeColumn(table, menu.index) }
            },
            TableMenuLine(
                "Вставить слева",
                Icons.Outlined.KeyboardArrowLeft,
                enabled = NoteTables.canInsertColumn(table),
            ) { run { NoteTables.insertColumn(table, menu.index) } },
            TableMenuLine(
                "Вставить справа",
                Icons.Outlined.KeyboardArrowRight,
                enabled = NoteTables.canInsertColumn(table),
            ) { run { NoteTables.insertColumn(table, menu.index + 1) } },
            TableMenuLine(
                "Удалить столбец",
                Icons.Outlined.Delete,
                destructive = true,
                enabled = NoteTables.canDeleteColumn(table),
            ) { run { NoteTables.deleteColumn(table, menu.index) } },
        )
    }
}

private fun fillLine(filled: Boolean, fillLabel: String, onClick: () -> Unit): TableMenuLine {
    return if (filled) {
        TableMenuLine("Убрать заливку", Icons.Outlined.FormatColorReset, onClick = onClick)
    } else {
        TableMenuLine(fillLabel, Icons.Outlined.FormatColorFill, onClick = onClick)
    }
}

@Composable
private fun JunctionPlus(stemEnd: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Box(
        modifier
            .width(if (stemEnd) 28.dp else 22.dp)
            .height(if (stemEnd) 22.dp else 28.dp)
            .drawBehind {
                val stroke = 1.1.dp.toPx()
                val radius = 8.dp.toPx()
                val center = if (stemEnd) {
                    Offset(radius + stroke, size.height / 2f)
                } else {
                    Offset(size.width / 2f, size.height - radius - stroke)
                }
                drawCircle(color, radius, center, style = Stroke(stroke))
                val arm = 3.5.dp.toPx()
                drawLine(color, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), stroke)
                drawLine(color, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), stroke)
                val stem = if (stemEnd) {
                    Offset(center.x + radius, center.y) to Offset(size.width, center.y)
                } else {
                    Offset(center.x, center.y - radius) to Offset(center.x, 0f)
                }
                drawLine(color, stem.first, stem.second, stroke)
            }
            .clickable(onClick = onClick),
    )
}

@Composable
private fun cellCornerShape(
    topStart: Boolean,
    topEnd: Boolean,
    bottomEnd: Boolean,
    bottomStart: Boolean,
): RoundedCornerShape {
    return RoundedCornerShape(
        topStart = if (topStart) TableCorner else 0.dp,
        topEnd = if (topEnd) TableCorner else 0.dp,
        bottomEnd = if (bottomEnd) TableCorner else 0.dp,
        bottomStart = if (bottomStart) TableCorner else 0.dp,
    )
}

@Composable
private fun TableCellEditor(
    table: NoteTable,
    row: Int,
    col: Int,
    width: Dp,
    shape: RoundedCornerShape,
    fillColor: Color,
    gridLine: Color,
    ink: Color,
    hiddenUnlocked: Boolean,
    highlighted: Boolean,
    focused: Boolean,
    formats: NoteEditorController,
    blockIndex: Int,
    onChange: (NoteTable) -> Unit,
    onAskTable: () -> Unit,
    onRequestUnlock: () -> Unit,
    onFocused: (Boolean) -> Unit,
    onCellMenu: () -> Unit,
) {
    val value = table.cells.getOrNull(row)?.getOrNull(col).orEmpty()
    val marks = NoteTables.cellMarks(table, row, col)
    var field by remember(table.id, row, col) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var menuOverflow by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val platformToolbar = LocalTextToolbar.current
    val toolbar = remember { RoutingTextToolbar(platformToolbar) }
    val prompt = remember { CellPrompt() }
    val h = NoteTables.alignH(table, row, col)
    val v = NoteTables.alignV(table, row, col)
    val filled = NoteTables.filled(table, row, col)
    val borderColor by animateColorAsState(if (highlighted) TableBlue else gridLine, tween(180), label = "cell-border")
    val borderWidth by androidx.compose.animation.core.animateDpAsState(if (highlighted) 1.6.dp else 0.6.dp, label = "cell-stroke")
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length.coerceAtMost(field.selection.end)))
    }
    if (field.selection.collapsed && (menuVisible || menuOverflow)) {
        menuVisible = false
        menuOverflow = false
    }
    fun publish(text: String, nextMarks: List<TextMark>, selection: TextRange) {
        field = TextFieldValue(text, selection)
        var next = NoteTables.withCell(table, row, col, text)
        next = NoteTables.withCellMarks(next, row, col, nextMarks)
        onChange(next)
    }
    fun apply(style: MarkStyle) {
        val start = field.selection.min
        val end = field.selection.max
        if (start == end) return
        onChange(NoteTables.withCellMarks(table, row, col, MarkEdits.toggle(marks, style, start, end)))
        menuVisible = false
    }
    SideEffect {
        if (!focused) return@SideEffect
        formats.editingCell = true
        formats.bodyFocused = false
        formats.bold = { apply(MarkStyle.BOLD) }
        formats.italic = { apply(MarkStyle.ITALIC) }
        formats.underline = { apply(MarkStyle.UNDERLINE) }
        formats.strike = { apply(MarkStyle.STRIKE) }
        formats.hidden = { apply(MarkStyle.HIDDEN) }
        formats.list = {}
        formats.listActive = false
        formats.table = {
            formats.tableBlock = blockIndex
            formats.tableCursor = -1
            onAskTable()
        }
        formats.activeStyles = cellStyles(marks, field.selection)
    }
    toolbar.onSelectionGestureEnded = {
        if (!field.selection.collapsed) {
            menuOverflow = false
            menuVisible = true
        }
    }
    toolbar.onSelectionGestureBegan = { menuVisible = false }
    val cellStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = NotesFont,
        color = ink,
        textAlign = textAlign(h),
    )
    val spoiler = rememberSpoilerPaint(
        text = field.text,
        marks = marks,
        style = cellStyle,
        ink = ink,
        widthPx = if (!hiddenUnlocked && marks.any { it.style == MarkStyle.HIDDEN }) {
            layout?.size?.width ?: 0
        } else {
            0
        },
    )
    val pad = with(LocalDensity.current) { 8.dp.toPx() }
    val layoutState = androidx.compose.runtime.rememberUpdatedState(layout)
    Box(
        modifier = Modifier
            .width(width)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (filled) fillColor else Color.Transparent, shape)
            .border(borderWidth, borderColor, shape)
            .pointerInput(value, marks) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val onGlyph = glyphHit(layoutState.value, down.position, pad)
                    if (!cellLongPressOpensCellMenu(onGlyph)) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: return@withTimeoutOrNull true
                            if (!change.pressed) return@withTimeoutOrNull true
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (dx * dx + dy * dy > slop * slop) return@withTimeoutOrNull true
                        }
                    }
                    if (released != null) return@awaitEachGesture
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                    onCellMenu()
                }
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = cellAlignment(h, v),
    ) {
        CompositionLocalProvider(LocalTextToolbar provides toolbar) {
            BasicTextField(
                value = field,
                onValueChange = { next ->
                    val previous = field.text
                    if (!hiddenUnlocked && HiddenText.editTouches(marks, previous, next.text)) {
                        onRequestUnlock()
                        return@BasicTextField
                    }
                    val cursorHidden = HiddenText.cursorInside(marks, next.selection.start, next.selection.end)
                    if (!hiddenUnlocked && cursorHidden && !prompt.open) onRequestUnlock()
                    val shifted = if (next.text == previous) marks else MarkEdits.shift(marks, previous, next.text)
                    field = next
                    if (next.selection.collapsed) {
                        menuVisible = false
                        menuOverflow = false
                    }
                    if (next.text != value || shifted != marks) publish(next.text, shifted, next.selection)
                },
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state -> onFocused(state.isFocused) }
                    .drawWithContent {
                        if (!hiddenUnlocked) drawSpoilerShadows(spoiler, marks, ink)
                        drawContent()
                    },
                textStyle = cellStyle,
                cursorBrush = SolidColor(NotesPurple),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                ),
                visualTransformation = remember(marks, hiddenUnlocked) {
                    CellVisual(marks, hiddenUnlocked)
                },
            )
        }
        if (layout != null && menuVisible && !field.selection.collapsed) {
            val shown = layout!!
            val length = shown.layoutInput.text.length
            val start = field.selection.min.coerceIn(0, length)
            val end = field.selection.max.coerceIn(0, length)
            if (start != end) {
                SelectionActionsPopup(
                    startHandle = shown.getCursorRect(start),
                    endHandle = shown.getCursorRect(end),
                    overflow = menuOverflow,
                    onCut = {
                        val a = field.selection.min
                        val b = field.selection.max
                        if (a != b && (hiddenUnlocked || !HiddenText.cursorInside(marks, a, b))) {
                            clipboard.setText(AnnotatedString(field.text.substring(a, b)))
                            val text = field.text.removeRange(a, b)
                            publish(text, MarkEdits.shift(marks, field.text, text), TextRange(a))
                        } else if (!hiddenUnlocked) {
                            onRequestUnlock()
                        }
                        menuVisible = false
                    },
                    onCopy = {
                        val a = field.selection.min
                        val b = field.selection.max
                        if (a != b && (hiddenUnlocked || !HiddenText.cursorInside(marks, a, b))) {
                            clipboard.setText(AnnotatedString(field.text.substring(a, b)))
                        } else if (!hiddenUnlocked) {
                            onRequestUnlock()
                        }
                        menuVisible = false
                    },
                    onMore = { menuOverflow = true },
                    onDismiss = {
                        menuVisible = false
                        field = field.copy(selection = TextRange(field.selection.end))
                    },
                    onBack = { menuOverflow = false },
                    onHidden = { apply(MarkStyle.HIDDEN) },
                    onSelectAll = {
                        if (field.text.isNotEmpty()) {
                            menuOverflow = false
                            menuVisible = true
                            field = field.copy(selection = TextRange(0, field.text.length))
                        }
                    },
                    onBold = { apply(MarkStyle.BOLD) },
                    onItalic = { apply(MarkStyle.ITALIC) },
                    onStrike = { apply(MarkStyle.STRIKE) },
                    onUnderline = { apply(MarkStyle.UNDERLINE) },
                )
            }
        }
    }
}

private class CellPrompt {
    var open: Boolean = false
}

private fun glyphHit(layout: TextLayoutResult?, position: Offset, pad: Float): Boolean {
    val result = layout ?: return false
    if (result.layoutInput.text.isEmpty()) return false
    val x = position.x - pad
    val y = position.y - pad
    if (y < -4f || y > result.size.height + 4f) return false
    val line = result.getLineForVerticalPosition(y).coerceIn(0, result.lineCount - 1)
    val left = result.getLineLeft(line)
    val right = result.getLineRight(line)
    return x in (left - 6f)..(right + 6f)
}

private fun textAlign(horizontal: Int): TextAlign = when (horizontal) {
    0 -> TextAlign.Start
    2 -> TextAlign.End
    else -> TextAlign.Center
}

private fun cellAlignment(horizontal: Int, vertical: Int): Alignment {
    return when (vertical) {
        0 -> when (horizontal) {
            0 -> Alignment.TopStart
            2 -> Alignment.TopEnd
            else -> Alignment.TopCenter
        }
        2 -> when (horizontal) {
            0 -> Alignment.BottomStart
            2 -> Alignment.BottomEnd
            else -> Alignment.BottomCenter
        }
        else -> when (horizontal) {
            0 -> Alignment.CenterStart
            2 -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    }
}

private fun cellStyles(marks: List<TextMark>, selection: TextRange): Set<MarkStyle> {
    val start = selection.min
    val end = selection.max
    return marks.mapNotNull { mark ->
        val hits = if (start == end) start in mark.start until mark.end else mark.start < end && mark.end > start
        if (hits) mark.style else null
    }.toSet()
}

private class CellVisual(private val marks: List<TextMark>, private val unlocked: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            marks.forEach { mark ->
                val start = mark.start.coerceIn(0, length)
                val end = mark.end.coerceIn(0, length)
                if (end <= start) return@forEach
                val style = when (mark.style) {
                    MarkStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    MarkStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    MarkStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    MarkStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                    MarkStyle.HIDDEN -> if (unlocked) null else SpanStyle(color = Color.Transparent)
                }
                if (style != null) addStyle(style, start, end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
