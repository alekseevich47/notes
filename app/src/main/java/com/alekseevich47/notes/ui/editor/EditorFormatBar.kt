package com.alekseevich47.notes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.ui.theme.NotesPurple

/**
 * Actions for the compact bar that sits above the keyboard. Lambdas are
 * refreshed from the editor after each composition; focus and active styles
 * are observable so the bar can show and highlight without owning the text.
 */
class NoteEditorController {
    var bodyFocused by mutableStateOf(false)
    var editingCell by mutableStateOf(false)
    var activeStyles by mutableStateOf(emptySet<MarkStyle>())
    var pendingOn by mutableStateOf(emptySet<MarkStyle>())
    var pendingOff by mutableStateOf(emptySet<MarkStyle>())
    var listActive by mutableStateOf(false)
    var tableBlock: Int = 0
    var tableCursor: Int = 0
    var screenDialogOpen by mutableStateOf(false)
    var linkDialogCount by mutableIntStateOf(0)
    val dialogOpen: Boolean get() = screenDialogOpen || linkDialogCount > 0
    var bodyScrolling by mutableStateOf(false)
    var tableScrolling by mutableStateOf(false)
    val scrolling: Boolean get() = bodyScrolling || tableScrolling
    var tableMenuOpen by mutableStateOf(false)
    var caretToken by mutableIntStateOf(0)
    var dragging by mutableStateOf(false)
    var autoScrolling by mutableStateOf(false)
    var activeEdgeRectInWindow: () -> Rect? = { null }
    var extendActiveEdge: (Float) -> Unit = {}
    var caretRectInWindow: () -> Rect? = { null }
    var restoreImeForCell: () -> Unit = {}
    var requestCloseTableMenu: () -> Unit = {}
    var bold: () -> Unit = {}
    var italic: () -> Unit = {}
    var underline: () -> Unit = {}
    var strike: () -> Unit = {}
    var hidden: () -> Unit = {}
    var list: () -> Unit = {}
    var table: () -> Unit = {}
}

/**
 * Counts open link dialogs. Several blocks can show one, so a single boolean
 * written by the last block would hide the others. The count is snapshot state
 * so leaving the composition drops it before the keyboard-hide check.
 */
@Composable
internal fun ReportLinkDialog(formats: NoteEditorController, open: Boolean) {
    val held = remember { object { var open = false } }
    if (open != held.open) {
        held.open = open
        formats.linkDialogCount = if (open) {
            formats.linkDialogCount + 1
        } else {
            (formats.linkDialogCount - 1).coerceAtLeast(0)
        }
    }
    DisposableEffect(formats) {
        onDispose {
            if (held.open) {
                held.open = false
                formats.linkDialogCount = (formats.linkDialogCount - 1).coerceAtLeast(0)
            }
        }
    }
}

@Composable
fun EditorFormatBar(
    controller: NoteEditorController,
    modifier: Modifier = Modifier,
) {
    val divider = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val plate = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val shown = (controller.activeStyles + controller.pendingOn) - controller.pendingOff
    ColumnBar(plate, divider, modifier) {
        // Read lambdas at click time: SideEffect in the editor refreshes them
        // without notifying this bar when activeStyles/listActive stay unchanged
        // (empty note, caret only). Capturing controller.bold here freezes {}.
        FormatIcon(Icons.Outlined.FormatBold, "Жирный", MarkStyle.BOLD in shown) { controller.bold() }
        BarDivider(divider)
        FormatIcon(Icons.Outlined.FormatItalic, "Курсив", MarkStyle.ITALIC in shown) { controller.italic() }
        FormatIcon(FormatStrikeS, "Зачёркнутый", MarkStyle.STRIKE in shown) { controller.strike() }
        FormatIcon(Icons.Outlined.FormatUnderlined, "Подчёркнутый", MarkStyle.UNDERLINE in shown) { controller.underline() }
        FormatIcon(Icons.Outlined.VisibilityOff, "Скрытый", MarkStyle.HIDDEN in shown) { controller.hidden() }
        BarDivider(divider)
        FormatIcon(FormatTableGrid, "Таблица", active = false) { controller.table() }
        BarDivider(divider)
        FormatIcon(Icons.Outlined.FormatListBulleted, "Список", controller.listActive) { controller.list() }
    }
}

@Composable
private fun ColumnBar(
    plate: Color,
    divider: Color,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().background(plate)) {
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun BarDivider(color: Color) {
    Box(
        Modifier
            .padding(vertical = 14.dp)
            .width(1.dp)
            .fillMaxHeight()
            .background(color),
    )
}

@Composable
private fun RowScope.FormatIcon(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val well = if (active) NotesPurple else MaterialTheme.colorScheme.surfaceVariant
    val glyph = if (active) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(if (active) RoundedCornerShape(9.dp) else CircleShape)
                .background(well),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = glyph,
            )
        }
    }
}

/** Letter S with a horizontal stroke through the middle. */
private val FormatStrikeS: ImageVector by lazy {
    ImageVector.Builder(
        name = "FormatStrikeS",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(16.4f, 7.8f)
            curveTo(16.4f, 5.15f, 14.25f, 3.9f, 11.8f, 3.9f)
            curveTo(8.85f, 3.9f, 7.15f, 5.45f, 7.25f, 7.55f)
            curveTo(7.35f, 9.55f, 9.15f, 10.45f, 12f, 12f)
            curveTo(14.95f, 13.6f, 16.85f, 14.55f, 16.75f, 16.65f)
            curveTo(16.65f, 18.85f, 14.35f, 20.15f, 11.8f, 20.15f)
            curveTo(8.7f, 20.15f, 6.95f, 18.4f, 7.15f, 16.15f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(4.4f, 12f)
            lineTo(19.6f, 12f)
        }
    }.build()
}

/** Small 3×3 rounded grid. Each cell is its own rounded square. */
private val FormatTableGrid: ImageVector by lazy {
    ImageVector.Builder(
        name = "FormatTableGrid",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val cell = 4.55f
        val gap = 1.3f
        val origin = (24f - (cell * 3f + gap * 2f)) / 2f
        val radius = 1.05f
        repeat(3) { row ->
            repeat(3) { column ->
                val x = origin + column * (cell + gap)
                val y = origin + row * (cell + gap)
                addRoundedCell(x, y, cell, radius)
            }
        }
    }.build()
}

private fun ImageVector.Builder.addRoundedCell(x: Float, y: Float, size: Float, radius: Float) {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(x + radius, y)
        lineTo(x + size - radius, y)
        arcTo(radius, radius, 0f, false, true, x + size, y + radius)
        lineTo(x + size, y + size - radius)
        arcTo(radius, radius, 0f, false, true, x + size - radius, y + size)
        lineTo(x + radius, y + size)
        arcTo(radius, radius, 0f, false, true, x, y + size - radius)
        lineTo(x, y + radius)
        arcTo(radius, radius, 0f, false, true, x + radius, y)
        close()
    }
}
