package com.alekseevich47.notes.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.abs

/** Grows from 0.35 to 1 over 900 ms while the handle stays in the edge zone. */
internal fun autoScrollRamp(heldMillis: Long): Float {
    val t = (heldMillis.coerceAtLeast(0L).toFloat() / 900f).coerceIn(0f, 1f)
    return 0.35f + 0.65f * t
}

/**
 * Pixels per second. Negative scrolls up, positive scrolls down, zero outside
 * the edge zones. A finger past [viewportBottom] (over the keyboard) is full depth.
 */
internal fun autoScrollVelocity(
    edgeY: Float,
    viewportTop: Float,
    viewportBottom: Float,
    zonePx: Float,
    minPxPerSec: Float,
    maxPxPerSec: Float,
    heldMillis: Long,
): Float {
    if (zonePx <= 0f || viewportBottom <= viewportTop) return 0f
    val topDepth = when {
        edgeY <= viewportTop -> 1f
        edgeY < viewportTop + zonePx -> (viewportTop + zonePx - edgeY) / zonePx
        else -> 0f
    }
    val bottomDepth = when {
        edgeY >= viewportBottom -> 1f
        edgeY > viewportBottom - zonePx -> (edgeY - (viewportBottom - zonePx)) / zonePx
        else -> 0f
    }
    val depth = if (topDepth >= bottomDepth && topDepth > 0f) -topDepth else bottomDepth
    if (depth == 0f) return 0f
    val f = (abs(depth) * autoScrollRamp(heldMillis)).coerceIn(0f, 1f)
    val speed = minPxPerSec + (maxPxPerSec - minPxPerSec) * f
    return if (depth < 0f) -speed else speed
}

/** Y of the active edge after a scroll step, clamped later by the layout. */
internal fun shiftedEdgeY(cursorCenterY: Float, dyPx: Float): Float = cursorCenterY + dyPx

internal fun extendSelectionOffset(
    layout: TextLayoutResult,
    activeEdgeOffset: Int,
    dyPx: Float,
): Int {
    val length = layout.layoutInput.text.length
    if (length <= 0) return 0
    val safe = activeEdgeOffset.coerceIn(0, length)
    val rect = layout.getCursorRect(safe)
    val y = shiftedEdgeY(rect.center.y, dyPx)
    return layout.getOffsetForPosition(Offset(rect.center.x, y)).coerceIn(0, length)
}
