package com.alekseevich47.notes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    var activeStyles by mutableStateOf(emptySet<MarkStyle>())
    var listActive by mutableStateOf(false)
    var bold: () -> Unit = {}
    var italic: () -> Unit = {}
    var underline: () -> Unit = {}
    var strike: () -> Unit = {}
    var hidden: () -> Unit = {}
    var list: () -> Unit = {}
    var table: () -> Unit = {}
}

@Composable
fun EditorFormatBar(
    controller: NoteEditorController,
    modifier: Modifier = Modifier,
) {
    val divider = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val plate = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ColumnBar(plate, divider, modifier) {
        FormatIcon(Icons.Outlined.FormatBold, "Жирный", MarkStyle.BOLD in controller.activeStyles, controller.bold)
        FormatIcon(Icons.Outlined.FormatItalic, "Курсив", MarkStyle.ITALIC in controller.activeStyles, controller.italic)
        FormatIcon(Icons.Outlined.FormatUnderlined, "Подчёркнутый", MarkStyle.UNDERLINE in controller.activeStyles, controller.underline)
        FormatIcon(Icons.Outlined.FormatStrikethrough, "Зачёркнутый", MarkStyle.STRIKE in controller.activeStyles, controller.strike)
        FormatIcon(Icons.Outlined.VisibilityOff, "Скрытый", MarkStyle.HIDDEN in controller.activeStyles, controller.hidden)
        FormatIcon(Icons.Outlined.TableChart, "Таблица", active = false, onClick = controller.table)
        FormatIcon(Icons.Outlined.FormatListNumbered, "Список", controller.listActive, controller.list)
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
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun RowScope.FormatIcon(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) NotesPurple.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(21.dp),
            tint = if (active) NotesPurple else MaterialTheme.colorScheme.onSurface,
        )
    }
}
