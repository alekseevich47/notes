package com.alekseevich47.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * How far a soft shadow spreads past the shape. Wider than the old elevation
 * blur so the penumbra can ease to transparent instead of stopping on a rim.
 */
fun softShadowRoom(elevation: Dp): Dp {
    // 18.dp matches the bottom bar's side inset, so the fade can finish
    // before the screen edge.
    return (elevation.value + 6f).dp.coerceIn(16.dp, 18.dp)
}

/**
 * Pads [room] around the content and paints a soft shadow in that padding.
 * The fade stays inside the layout, so a popup window cannot clip it.
 * Draw happens outside the following clip.
 */
fun Modifier.softShadowPadding(shape: Shape, color: Color, room: Dp): Modifier {
    return drawBehind {
        drawSoftShapeShadow(shape, color, room.toPx())
    }.padding(room)
}

/**
 * Shadow layer larger than [modifier]'s layout, placed so the extra room sits
 * outside the plate. The plate's measured size does not change. Call this
 * outside any offscreen [androidx.compose.ui.graphics.graphicsLayer] or clip:
 * those buffers are the plate size and cut the fade to a hard edge.
 */
@Composable
fun SoftShadowLayer(
    shape: Shape,
    color: Color,
    room: Dp,
    modifier: Modifier = Modifier,
) {
    val padPx = with(LocalDensity.current) { room.roundToPx() }
    Canvas(
        modifier = modifier.layout { measurable, constraints ->
            if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight || padPx <= 0) {
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            } else {
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val placeable = measurable.measure(
                    Constraints.fixed(width + padPx * 2, height + padPx * 2),
                )
                layout(width, height) { placeable.place(-padPx, -padPx) }
            }
        },
    ) {
        drawSoftShapeShadow(shape, color, padPx.toFloat())
    }
}

/**
 * Nested translucent copies of [shape]. Each copy is a little larger and a
 * little fainter, and the alphas are chosen so the composited result follows
 * a smoothstep down to 0 at [roomPx]. The plate interior is punched out so a
 * translucent fill above this shadow keeps its own color.
 */
fun DrawScope.drawSoftShapeShadow(shape: Shape, color: Color, roomPx: Float) {
    if (roomPx <= 1f || color.alpha <= 0f) return
    val plateWidth = size.width - roomPx * 2f
    val plateHeight = size.height - roomPx * 2f
    if (plateWidth <= 1f || plateHeight <= 1f) return
    val plate = Size(plateWidth, plateHeight)
    val outline = shape.createOutline(plate, layoutDirection, this)
    // Tuck the knockout a pixel under the plate so the fade meets the fill
    // without a bright seam.
    val plateClip = plateKnockout(outline, roomPx, tuck = 1.25f)
    val peak = color.alpha
    val paint = color.copy(alpha = 1f)
    var covered = 0f
    clipPath(plateClip, clipOp = ClipOp.Difference) {
        for (step in ShadowSteps downTo 1) {
            val innerT = (step - 1) / ShadowSteps.toFloat()
            val outerT = step / ShadowSteps.toFloat()
            val target = shadowFalloff(innerT) * peak
            val delta = if (covered >= 0.98f) {
                0f
            } else {
                ((target - covered) / (1f - covered)).coerceIn(0f, 1f)
            }
            covered = target
            if (delta <= 0f) continue
            drawShadowCopy(
                outline = outline,
                roomPx = roomPx,
                spread = roomPx * outerT,
                color = paint.copy(alpha = delta),
            )
        }
    }
}

private fun plateKnockout(outline: Outline, roomPx: Float, tuck: Float): Path {
    return when (outline) {
        is Outline.Rounded -> outline.roundRect.inflatedPath(roomPx, -tuck)
        is Outline.Rectangle -> Path().apply {
            val rect = outline.rect
            addRect(
                Rect(
                    rect.left + roomPx + tuck,
                    rect.top + roomPx + tuck,
                    rect.right + roomPx - tuck,
                    rect.bottom + roomPx - tuck,
                ),
            )
        }
        is Outline.Generic -> Path().apply { addPath(outline.path, Offset(roomPx, roomPx)) }
    }
}

private const val ShadowSteps = 40

/** 1 at the plate edge, 0 at the outer rim, flat at both ends. */
private fun shadowFalloff(t: Float): Float {
    val u = (1f - t).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun DrawScope.drawShadowCopy(outline: Outline, roomPx: Float, spread: Float, color: Color) {
    when (outline) {
        is Outline.Rounded -> {
            val rect = outline.roundRect
            val radius = rect.uniformCorner()
            val width = rect.width + spread * 2f
            val height = rect.height + spread * 2f
            if (radius != null) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(rect.left + roomPx - spread, rect.top + roomPx - spread),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(
                        (radius.x + spread).coerceAtMost(width / 2f),
                        (radius.y + spread).coerceAtMost(height / 2f),
                    ),
                )
            } else {
                drawPath(rect.inflatedPath(roomPx, spread), color)
            }
        }
        is Outline.Rectangle -> {
            val rect = outline.rect
            val width = rect.width + spread * 2f
            val height = rect.height + spread * 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(rect.left + roomPx - spread, rect.top + roomPx - spread),
                size = Size(width, height),
                cornerRadius = CornerRadius(
                    spread.coerceAtMost(width / 2f),
                    spread.coerceAtMost(height / 2f),
                ),
            )
        }
        is Outline.Generic -> {
            val bounds = outline.path.getBounds()
            val sx = (bounds.width + spread * 2f) / bounds.width.coerceAtLeast(1f)
            val sy = (bounds.height + spread * 2f) / bounds.height.coerceAtLeast(1f)
            translate(roomPx, roomPx) {
                scale(sx, sy, pivot = bounds.center) {
                    drawPath(outline.path, color)
                }
            }
        }
    }
}

private fun RoundRect.uniformCorner(): CornerRadius? {
    val radius = topLeftCornerRadius
    return if (
        radius == topRightCornerRadius &&
        radius == bottomRightCornerRadius &&
        radius == bottomLeftCornerRadius
    ) {
        radius
    } else {
        null
    }
}

private fun RoundRect.inflatedPath(roomPx: Float, spread: Float): Path {
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = left + roomPx - spread,
                top = top + roomPx - spread,
                right = right + roomPx + spread,
                bottom = bottom + roomPx + spread,
                topLeftCornerRadius = topLeftCornerRadius.grow(spread),
                topRightCornerRadius = topRightCornerRadius.grow(spread),
                bottomRightCornerRadius = bottomRightCornerRadius.grow(spread),
                bottomLeftCornerRadius = bottomLeftCornerRadius.grow(spread),
            ),
        )
    }
}

private fun CornerRadius.grow(spread: Float): CornerRadius {
    return CornerRadius(max(0f, x + spread), max(0f, y + spread))
}
