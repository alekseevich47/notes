package com.alekseevich47.notes.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class MenuHideReason { HANDLE_DRAG, SCROLL, SELECTION_CHANGING }

/**
 * Whether the selection menu should be visible. Time is injected so tests
 * can advance a clock without Compose.
 */
internal class SelectionMenuModel(private val settleMillis: Long = 220L) {
    var wanted: Boolean = false
        private set
    var suppressed: Set<MenuHideReason> = emptySet()
        private set
    var dragging: Boolean = false
        private set
    var activeIsEnd: Boolean = true
        private set
    var anchor: Int = 0
        private set
    var now: Long = 0L
        private set
    var autoScrolling: Boolean = false

    private var showNotBefore: Long = 0L
    private var selectionQuietAt: Long = Long.MAX_VALUE
    private var lastSelectionAt: Long = 0L

    val target: Float
        get() {
            if (!wanted || suppressed.isNotEmpty()) return 0f
            if (now < showNotBefore) return 0f
            return 1f
        }

    fun advance(millis: Long) {
        now = millis
        if (MenuHideReason.SELECTION_CHANGING in suppressed && millis >= selectionQuietAt) {
            suppressed = suppressed - MenuHideReason.SELECTION_CHANGING
        }
        if (dragging && !autoScrolling && millis - lastSelectionAt >= 600L) {
            dragging = false
            if (MenuHideReason.HANDLE_DRAG in suppressed) {
                suppressed = suppressed - MenuHideReason.HANDLE_DRAG
                showNotBefore = millis + settleMillis
            }
        }
    }

    fun onSelectionChanged(old: TextRange, new: TextRange) {
        if (old == new) return
        trackEdge(old, new)
        lastSelectionAt = now
        if (new.collapsed) {
            wanted = false
            suppressed = suppressed - MenuHideReason.SELECTION_CHANGING
            showNotBefore = now
            dragging = false
            return
        }
        wanted = true
        suppressed = suppressed + MenuHideReason.SELECTION_CHANGING
        selectionQuietAt = now + settleMillis
        showNotBefore = now + settleMillis
    }

    fun onScroll(inProgress: Boolean) {
        if (inProgress) {
            suppressed = suppressed + MenuHideReason.SCROLL
            return
        }
        if (MenuHideReason.SCROLL !in suppressed) return
        suppressed = suppressed - MenuHideReason.SCROLL
        showNotBefore = now + settleMillis
    }

    fun onHandleDragBegan() {
        dragging = true
        suppressed = suppressed + MenuHideReason.HANDLE_DRAG
    }

    fun onHandleDragEnded() {
        if (!dragging && MenuHideReason.HANDLE_DRAG !in suppressed) return
        dragging = false
        suppressed = suppressed - MenuHideReason.HANDLE_DRAG
        showNotBefore = now + settleMillis
    }

    private fun trackEdge(old: TextRange, new: TextRange) {
        if (new.collapsed) {
            dragging = false
            return
        }
        val startMoved = old.start != new.start
        val endMoved = old.end != new.end
        when {
            startMoved && !endMoved -> {
                dragging = true
                activeIsEnd = false
                anchor = new.end
            }
            endMoved && !startMoved -> {
                dragging = true
                activeIsEnd = true
                anchor = new.start
            }
        }
    }
}

/**
 * One menu per editor. [alpha] is read from a graphics layer, not from the
 * menu's composition. [shouldCompose] flips only when the popup is added or
 * removed, so the fade itself does not recompose the note.
 */
@Stable
class SelectionMenuState(
    private val scope: CoroutineScope,
    private val settleMillis: Long = 220L,
) {
    private val model = SelectionMenuModel(settleMillis)
    val alpha = Animatable(0f)
    var shouldCompose by mutableStateOf(false)
        private set
    var dragging by mutableStateOf(false)
        private set
    var activeIsEnd by mutableStateOf(true)
        private set
    var anchor by mutableIntStateOf(0)
        private set

    val target: Float get() = model.target

    private var anim: Job? = null
    private var clock: Job? = null

    fun onSelectionChanged(old: TextRange, new: TextRange) {
        if (old == new) return
        if (old.collapsed && new.collapsed && !model.wanted && !model.dragging) return
        model.advance(android.os.SystemClock.uptimeMillis())
        model.onSelectionChanged(old, new)
        publish()
    }

    fun onScroll(inProgress: Boolean) {
        model.advance(android.os.SystemClock.uptimeMillis())
        model.onScroll(inProgress)
        publish()
    }

    fun onHandleDragBegan() {
        model.advance(android.os.SystemClock.uptimeMillis())
        model.onHandleDragBegan()
        publish()
    }

    fun onHandleDragEnded() {
        model.advance(android.os.SystemClock.uptimeMillis())
        model.onHandleDragEnded()
        publish()
    }

    fun setAutoScrolling(active: Boolean) {
        model.autoScrolling = active
    }

    private fun publish() {
        dragging = model.dragging
        activeIsEnd = model.activeIsEnd
        anchor = model.anchor
        val goal = model.target
        val snapClosed = !model.wanted && goal == 0f
        if (snapClosed && alpha.value == 0f && !shouldCompose) {
            armClock()
            return
        }
        if (goal > 0f) shouldCompose = true
        if (!snapClosed && goal == alpha.targetValue && anim?.isActive == true) {
            armClock()
            return
        }
        anim?.cancel()
        anim = scope.launch {
            if (snapClosed) {
                alpha.snapTo(0f)
                shouldCompose = false
            } else {
                alpha.animateTo(goal, tween(if (goal == 0f) 110 else 180))
                if (goal == 0f && model.target == 0f) shouldCompose = false
            }
        }
        armClock()
    }

    private fun armClock() {
        if (clock?.isActive == true) return
        if (!needsClock()) return
        clock = scope.launch {
            while (needsClock()) {
                delay(32)
                val beforeTarget = model.target
                val beforeDrag = model.dragging
                model.advance(android.os.SystemClock.uptimeMillis())
                if (model.target != beforeTarget || model.dragging != beforeDrag) publish()
            }
        }
    }

    private fun needsClock(): Boolean {
        return model.dragging ||
            model.suppressed.isNotEmpty() ||
            (model.wanted && model.target < 1f)
    }
}
