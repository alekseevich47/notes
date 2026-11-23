package com.alekseevich47.notes.ui.legal

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 12.dp plate-colored fades on the scroll viewport. Drawn in the same pass as the
 * content, so the list under the dialog is not recomposed while the text moves.
 */
@Composable
internal fun Modifier.scrollEdgeFades(scroll: ScrollState, color: Color): Modifier {
    val showTop by remember(scroll) { derivedStateOf { scroll.canScrollBackward } }
    val showBottom by remember(scroll) { derivedStateOf { scroll.canScrollForward } }
    return drawWithContent {
        drawContent()
        val fadePx = 12.dp.toPx()
        if (showTop && fadePx < size.height) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    startY = 0f,
                    endY = fadePx,
                ),
                size = Size(size.width, fadePx),
            )
        }
        if (showBottom && fadePx < size.height) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0f), color),
                    startY = size.height - fadePx,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - fadePx),
                size = Size(size.width, fadePx),
            )
        }
    }
}
