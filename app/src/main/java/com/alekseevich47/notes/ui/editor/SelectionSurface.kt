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
 * Handlers for one text block. [RichNoteEditor] refreshes the instance on each
 * composition so a long-lived pointer coroutine reads the latest layout.
 * [focused] wins over a non-empty selection in another block.
 */
internal class BlockSurfaceHandlers(
    val focused: Boolean = false,
    val readSelection: () -> SelectionSnapshot = { SelectionSnapshot.Collapsed },
    val pointInside: (Offset, SelectionSnapshot) -> Boolean = { _, _ -> true },
    val onClear: (Offset, SelectionSnapshot) -> Unit = { _, _ -> },
    /**
     * Finger-up on the note body. [cleared] is true when this gesture collapsed
     * the selection. [root] is null when the body is not attached.
     */
    val onGestureSettle: (cleared: Boolean, collapsedAtDown: Boolean, root: Offset?) -> Unit =
        { _, _, _ -> },
)

/**
 * Bridge between the note body (which sees every tap, including empty space
 * under the text) and every text block. Each block registers under its flow
 * index. A tap is dispatched to the focused block, or, when nothing is
 * focused, to the block with a non-collapsed selection.
 */
internal class NoteSurfaceBridge {
    var toRoot: (Offset) -> Offset? = { null }

    private val blocks = mutableMapOf<Int, BlockSurfaceHandlers>()
    private var atDown: SelectionSnapshot = SelectionSnapshot.Collapsed
    private var downIndex: Int? = null

    fun register(blockIndex: Int, handlers: BlockSurfaceHandlers) {
        blocks[blockIndex] = handlers
    }

    fun unregister(blockIndex: Int) {
        blocks.remove(blockIndex)
        if (downIndex == blockIndex) downIndex = null
    }

    /** Focused block wins. Otherwise the lowest index with a ranged selection. */
    private fun activeIndex(): Int? {
        var focused: Int? = null
        var selected: Int? = null
        for ((index, handlers) in blocks) {
            if (handlers.focused && (focused == null || index < focused)) focused = index
            val selection = handlers.readSelection()
            if (!selection.collapsed && (selected == null || index < selected)) selected = index
        }
        return focused ?: selected
    }

    fun down() {
        val index = activeIndex()
        downIndex = index
        atDown = index?.let { blocks[it]?.readSelection() } ?: SelectionSnapshot.Collapsed
    }

    fun up(
        local: Offset,
        dragDistance: Float,
        elapsedMillis: Long,
        touchSlop: Float,
        longPressTimeoutMillis: Long,
    ) {
        val handlers = downIndex?.let { blocks[it] } ?: return
        val selection = atDown
        val root = toRoot(local)
        val inside = if (selection.collapsed || root == null) {
            true
        } else {
            handlers.pointInside(root, selection)
        }
        val clear = shouldClearSelectionOnUp(
            collapsedAtDown = selection.collapsed,
            dragDistance = dragDistance,
            touchSlop = touchSlop,
            elapsedMillis = elapsedMillis,
            longPressTimeoutMillis = longPressTimeoutMillis,
            insideSelection = inside,
        )
        val didClear = clear && root != null
        if (didClear) handlers.onClear(root, selection)
        handlers.onGestureSettle(didClear, selection.collapsed, root)
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
