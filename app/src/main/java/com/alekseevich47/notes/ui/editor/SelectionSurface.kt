package com.alekseevich47.notes.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Selection captured when the finger went down. The finger-up decision uses
 * this snapshot so a long-press that creates a selection is not treated as a
 * tap on that new selection.
 */
internal data class SelectionSnapshot(val start: Int, val end: Int) {
    val collapsed: Boolean get() = start == end

    companion object {
        val Collapsed = SelectionSnapshot(0, 0)
    }
}

/**
 * Bridge between the note body (which sees every tap, including empty space
 * under the text) and the editor (which owns the selection and layout).
 * Lambdas are replaced on each composition so a long-lived pointer coroutine
 * always reads the latest layout.
 */
internal class NoteSurfaceBridge {
    var toRoot: (Offset) -> Offset? = { null }
    var readSelection: () -> SelectionSnapshot = { SelectionSnapshot.Collapsed }
    var pointInside: (Offset, SelectionSnapshot) -> Boolean = { _, _ -> true }
    var onClear: (Offset, SelectionSnapshot) -> Unit = { _, _ -> }

    private var atDown: SelectionSnapshot = SelectionSnapshot.Collapsed

    fun down() {
        atDown = readSelection()
    }

    fun up(
        local: Offset,
        dragDistance: Float,
        elapsedMillis: Long,
        touchSlop: Float,
        longPressTimeoutMillis: Long,
    ) {
        val selection = atDown
        val root = toRoot(local)
        val inside = if (selection.collapsed || root == null) {
            true
        } else {
            pointInside(root, selection)
        }
        if (
            !shouldClearSelectionOnUp(
                collapsedAtDown = selection.collapsed,
                dragDistance = dragDistance,
                touchSlop = touchSlop,
                elapsedMillis = elapsedMillis,
                longPressTimeoutMillis = longPressTimeoutMillis,
                insideSelection = inside,
            )
        ) {
            return
        }
        val clearAt = root ?: return
        onClear(clearAt, selection)
    }
}

/**
 * Observes presses on the note body without consuming them. Drags still scroll
 * and still move selection handles. A tap outside the highlight is reported
 * through [bridge].
 */
internal fun Modifier.observeSelectionTaps(bridge: NoteSurfaceBridge): Modifier =
    pointerInput(bridge) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            bridge.down()
            val origin = down.position
            val started = down.uptimeMillis
            val slop = viewConfiguration.touchSlop
            val longPress = viewConfiguration.longPressTimeoutMillis
            var farthest = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (event.changes.any { it.id != down.id && it.pressed }) {
                    farthest = slop + 1f
                }
                val traveled = (change.position - origin).getDistance()
                if (traveled > farthest) farthest = traveled
                if (!change.pressed) {
                    bridge.up(
                        local = change.position,
                        dragDistance = farthest,
                        elapsedMillis = (change.uptimeMillis - started).coerceAtLeast(0L),
                        touchSlop = slop,
                        longPressTimeoutMillis = longPress,
                    )
                    return@awaitEachGesture
                }
            }
        }
    }
