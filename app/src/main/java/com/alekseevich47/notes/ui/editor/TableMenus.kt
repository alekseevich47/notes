package com.alekseevich47.notes.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.alekseevich47.notes.ui.theme.NotesFont

internal data class TableMenuLine(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Row, column, and cell menus. Alignment sits in its own card; the actions
 * are a second card just under it, matching the detached stack in the editor.
 */
@Composable
internal fun TableActionsPopup(
    horizontal: Int,
    vertical: Int,
    onHorizontal: (Int) -> Unit,
    onVertical: (Int) -> Unit,
    lines: List<TableMenuLine>,
    onDismiss: () -> Unit,
    offset: IntOffset,
    horizontalIcons: List<ImageVector>,
    verticalIcons: List<ImageVector>,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(160), label = "table-menu")
    Popup(
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer { this.alpha = alpha }
                .widthIn(min = 220.dp, max = 280.dp),
        ) {
            MenuCard {
                Text(
                    "Выравнивание",
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                    fontFamily = NotesFont,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    horizontalIcons.forEachIndexed { index, icon ->
                        AlignButton(icon, selected = horizontal == index, onClick = { onHorizontal(index) })
                    }
                    Spacer(Modifier.size(6.dp))
                    verticalIcons.forEachIndexed { index, icon ->
                        AlignButton(icon, selected = vertical == index, onClick = { onVertical(index) })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            MenuCard {
                lines.forEach { line ->
                    val tint = when {
                        !line.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        line.destructive -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = line.enabled, onClick = line.onClick)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(line.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                        Text(
                            line.label,
                            modifier = Modifier.padding(start = 10.dp),
                            color = tint,
                            fontFamily = NotesFont,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun AlignButton(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val well = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(well)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
