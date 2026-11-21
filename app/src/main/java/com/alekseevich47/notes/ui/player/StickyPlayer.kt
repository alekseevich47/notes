package com.alekseevich47.notes.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.player.PlaybackSnapshot
import com.alekseevich47.notes.player.PlayerUi
import com.alekseevich47.notes.ui.theme.NotesFont
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Dismiss only when the finger is released past 30% of the width.
 * Crossing that line and coming back before release keeps the player.
 */
internal fun stickyShouldDismiss(dx: Float, widthPx: Float): Boolean {
    if (widthPx <= 0f) return false
    return abs(dx) > widthPx * 0.30f
}

@Composable
fun StickyPlayer(
    state: PlayerUi,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settle = remember { Animatable(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(1f) }
    var draggingSeek by remember { mutableStateOf(false) }
    var seek by remember { mutableFloatStateOf(0f) }
    val fraction = PlaybackSnapshot.fraction(state.positionMs, state.durationMs)
    val translation = if (settling) settle.value else dragX
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationX = translation }
            .shadow(6.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(settling) {
                if (settling) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val released = dragX
                        val limit = width
                        scope.launch {
                            settling = true
                            settle.snapTo(released)
                            if (stickyShouldDismiss(released, limit)) {
                                val target = if (released > 0f) limit else -limit
                                settle.animateTo(target, tween(170))
                                onDismiss()
                            } else {
                                settle.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                                dragX = 0f
                                settling = false
                            }
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragX += amount
                    },
                )
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            state.title.ifBlank { "Заметка" },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
            fontFamily = NotesFont,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (state.playing) "Пауза" else "Слушать",
                )
            }
            Slider(
                value = if (draggingSeek) seek else fraction,
                onValueChange = {
                    draggingSeek = true
                    seek = it
                },
                onValueChangeFinished = {
                    onSeek(seek)
                    draggingSeek = false
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                PlaybackSnapshot.clock(state.positionMs) + " / " + PlaybackSnapshot.clock(state.durationMs),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                fontFamily = NotesFont,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
