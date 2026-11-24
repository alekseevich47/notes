package com.alekseevich47.notes.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tap, double-tap, and long-press for a text field. The instance is remembered
 * by the editor and its callbacks are replaced in place, so [pointerInput]
 * is not restarted on recomposition.
 */
class EditorGestureCallbacks {
    var onTapUp: (Offset) -> Unit = {}
    var onDoubleTap: (Offset) -> Unit = {}
    var onLongPress: (Offset) -> Unit = {}
    var onGestureEnd: () -> Unit = {}
}

/**
 * Initial-pass gestures. Events are not consumed: Compose still places the
 * caret and selects a word on long press.
 */
@Composable
internal fun Modifier.editorTapGestures(callbacks: EditorGestureCallbacks): Modifier {
    val current = rememberUpdatedState(callbacks)
    return pointerInput(Unit) {
        var hasShortTap = false
        var previousUp = 0L
        var previousX = 0f
        var previousY = 0f
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val longPress = viewConfiguration.longPressTimeoutMillis
            val gapLimit = viewConfiguration.doubleTapTimeoutMillis
            val origin = down.position
            val started = down.uptimeMillis
            var releasedAt: Offset? = null
            var releasedUptime = 0L
            var moved = false
            val finished = withTimeoutOrNull(longPress) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                    if (!change.pressed) {
                        releasedAt = change.position
                        releasedUptime = change.uptimeMillis
                        return@withTimeoutOrNull true
                    }
                    val dx = change.position.x - origin.x
                    val dy = change.position.y - origin.y
                    if (!TapClassifier.withinSlop(dx, dy, slop)) {
                        moved = true
                        return@withTimeoutOrNull false
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                false
            }
            val up = releasedAt
            if (finished == true && up != null && TapClassifier.countsAsShortTap(true, !moved)) {
                val callbacksNow = current.value
                callbacksNow.onTapUp(up)
                if (
                    hasShortTap &&
                    TapClassifier.isDoubleTap(
                        previousUp = previousUp,
                        nextDown = started,
                        previousX = previousX,
                        previousY = previousY,
                        nextX = up.x,
                        nextY = up.y,
                        gapLimit = gapLimit,
                        reach = slop * 2f,
                    )
                ) {
                    callbacksNow.onDoubleTap(up)
                    hasShortTap = false
                } else {
                    hasShortTap = true
                    previousUp = releasedUptime
                    previousX = up.x
                    previousY = up.y
                }
                return@awaitEachGesture
            }
            hasShortTap = false
            if (moved || finished == false) return@awaitEachGesture
            current.value.onLongPress(origin)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
            }
            current.value.onGestureEnd()
        }
    }
}

/** Pure tap timing. A long press is not a short tap, so it cannot open a double tap. */
internal object TapClassifier {
    fun isDoubleTap(
        previousUp: Long,
        nextDown: Long,
        previousX: Float,
        previousY: Float,
        nextX: Float,
        nextY: Float,
        gapLimit: Long,
        reach: Float,
    ): Boolean {
        val interval = nextDown - previousUp
        if (interval < 0L || interval > gapLimit) return false
        val dx = nextX - previousX
        val dy = nextY - previousY
        return dx * dx + dy * dy <= reach * reach
    }

    fun withinSlop(dx: Float, dy: Float, slop: Float): Boolean {
        return dx * dx + dy * dy <= slop * slop
    }

    fun countsAsShortTap(releasedBeforeLongPress: Boolean, withinSlop: Boolean): Boolean {
        return releasedBeforeLongPress && withinSlop
    }
}

/**
 * Long-press on empty space. [onGlyph] is false for a line tail, an empty
 * line, or the space under the text. A separator is blank too: [WordBounds]
 * would treat the character right after a word as that word.
 */
internal fun isBlankLongPress(text: String, offset: Int, onGlyph: Boolean): Boolean {
    if (!onGlyph) return true
    if (text.isEmpty()) return true
    val index = when {
        offset <= 0 -> 0
        offset >= text.length -> text.length - 1
        else -> offset
    }
    val start = if (
        index > 0 &&
        Character.isLowSurrogate(text[index]) &&
        Character.isHighSurrogate(text[index - 1])
    ) {
        index - 1
    } else {
        index
    }
    if (start !in text.indices) return true
    return !Character.isLetterOrDigit(text.codePointAt(start))
}

/** True when the finger is on glyphs, not the empty tail of the line. */
internal fun glyphHit(layout: TextLayoutResult?, position: Offset, pad: Float): Boolean {
    val result = layout ?: return false
    if (result.layoutInput.text.isEmpty()) return false
    val x = position.x - pad
    val y = position.y - pad
    if (y < -4f || y > result.size.height + 4f) return false
    val line = result.getLineForVerticalPosition(y).coerceIn(0, result.lineCount - 1)
    val left = result.getLineLeft(line)
    val right = result.getLineRight(line)
    return x in (left - 6f)..(right + 6f)
}
