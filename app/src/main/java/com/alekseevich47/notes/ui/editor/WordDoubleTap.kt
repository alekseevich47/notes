package com.alekseevich47.notes.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Two quick taps in the same spot. Events are not consumed, so the field still
 * places the caret and links still open. The previous tap is kept in locals
 * inside [pointerInput], not in a remembered object.
 */
@Composable
internal fun Modifier.onWordDoubleTap(onDoubleTap: (Offset) -> Unit): Modifier {
    val callback = rememberUpdatedState(onDoubleTap)
    return pointerInput(Unit) {
        var lastPosition: Offset? = null
        var lastUptime = 0L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val longPress = viewConfiguration.longPressTimeoutMillis
            val gapLimit = viewConfiguration.doubleTapTimeoutMillis
            val origin = down.position
            val started = down.uptimeMillis
            val released: PointerInputChange? = withTimeoutOrNull(longPress) {
                var up: PointerInputChange? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        up = change
                        break
                    }
                    val dx = change.position.x - origin.x
                    val dy = change.position.y - origin.y
                    if (dx * dx + dy * dy > slop * slop) break
                }
                up
            }
            if (released == null) {
                lastPosition = null
                return@awaitEachGesture
            }
            val previous = lastPosition
            val previousUptime = lastUptime
            lastPosition = released.position
            lastUptime = released.uptimeMillis
            if (previous == null) return@awaitEachGesture
            val interval = started - previousUptime
            if (interval < 0L || interval > gapLimit) return@awaitEachGesture
            val dx = released.position.x - previous.x
            val dy = released.position.y - previous.y
            val reach = slop * 2f
            if (dx * dx + dy * dy > reach * reach) return@awaitEachGesture
            callback.value(released.position)
            lastPosition = null
        }
    }
}
