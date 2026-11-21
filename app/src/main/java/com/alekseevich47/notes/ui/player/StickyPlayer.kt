package com.alekseevich47.notes.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.player.PlaybackSnapshot
import com.alekseevich47.notes.player.PlayerUi
import com.alekseevich47.notes.ui.theme.NotesFont
import kotlinx.coroutines.CancellationException
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

/** A drag that begins on the duration slider seeks. Every other drag can dismiss. */
internal fun stickyDragMovesPlayer(downOnSlider: Boolean): Boolean = !downOnSlider

private fun sliderRect(player: LayoutCoordinates?, slider: LayoutCoordinates?): Rect {
    if (player == null || slider == null || !player.isAttached || !slider.isAttached) return Rect.Zero
    val origin = player.localPositionOf(slider, Offset.Zero)
    return Rect(
        left = origin.x,
        top = origin.y,
        right = origin.x + slider.size.width,
        bottom = origin.y + slider.size.height,
    )
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
    var playerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var sliderCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sliderBounds = rememberUpdatedState(sliderRect(playerCoords, sliderCoords))
    val fraction = PlaybackSnapshot.fraction(state.positionMs, state.durationMs)
    val translation = if (settling) settle.value else dragX
    val shape = RoundedCornerShape(18.dp)
    val bounce = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    fun bounceBack() {
        val from = dragX
        scope.launch {
            settling = true
            settle.snapTo(from)
            settle.animateTo(0f, bounce)
            dragX = 0f
            settling = false
        }
    }
    fun finishDrag() {
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
                settle.animateTo(0f, bounce)
                dragX = 0f
                settling = false
            }
        }
    }
    Column(
        modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationX = translation }
            .shadow(6.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .onGloballyPositioned { playerCoords = it }
            .pointerInput(settling) {
                if (settling) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!stickyDragMovesPlayer(sliderBounds.value.contains(down.position))) {
                        return@awaitEachGesture
                    }
                    val slop = viewConfiguration.touchSlop
                    var tracking = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (tracking) finishDrag()
                                break
                            }
                            if (!tracking) {
                                val totalX = change.position.x - down.position.x
                                val totalY = change.position.y - down.position.y
                                if (abs(totalX) <= slop && abs(totalY) <= slop) continue
                                if (abs(totalY) > abs(totalX)) break
                                tracking = true
                                change.consume()
                                dragX += totalX
                                continue
                            }
                            val dx = change.positionChange().x
                            change.consume()
                            dragX += dx
                        }
                    } catch (cancelled: CancellationException) {
                        if (tracking) bounceBack()
                        throw cancelled
                    }
                }
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
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { sliderCoords = it },
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
