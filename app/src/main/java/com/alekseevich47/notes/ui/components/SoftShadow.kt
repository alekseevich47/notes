package com.alekseevich47.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import com.alekseevich47.notes.ui.theme.LocalPerfTier
import com.alekseevich47.notes.ui.theme.PerfTier
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
fun softShadowSteps(tier: PerfTier): Int {
    return if (tier == PerfTier.LOW) LowShadowSteps else NormalShadowSteps
}

/**
 * [steps] comes from the call site. A plain modifier factory cannot read
 * [LocalPerfTier] without the deprecated `composed` helper.
 */
fun Modifier.softShadowPadding(
    shape: Shape,
    color: Color,
    room: Dp,
    steps: Int = NormalShadowSteps,
): Modifier {
    return drawWithCache {
        val built = buildSoftShadow(this, size, layoutDirection, shape, color, room.toPx(), steps)
        onDrawBehind {
            if (built != null) drawBuiltShadow(built)
        }
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
    val steps = softShadowSteps(LocalPerfTier.current)
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
        }.drawWithCache {
            val built = buildSoftShadow(this, size, layoutDirection, shape, color, padPx.toFloat(), steps)
            onDrawBehind {
                if (built != null) drawBuiltShadow(built)
            }
        },
    ) {
    }
}

/**
 * Nested translucent copies of [shape]. Each copy is a little larger and a
 * little fainter, and the alphas are chosen so the composited result follows
 * a smoothstep down to 0 at [roomPx]. The plate interior is punched out so a
 * translucent fill above this shadow keeps its own color.
 */
fun DrawScope.drawSoftShapeShadow(
    shape: Shape,
    color: Color,
    roomPx: Float,
    steps: Int = NormalShadowSteps,
) {
    val built = buildSoftShadow(this, size, layoutDirection, shape, color, roomPx, steps) ?: return
    drawBuiltShadow(built)
}

private class BuiltShadow(
    val knockout: Path,
    val slices: List<ShadowSlice>,
)

private sealed class ShadowSlice {
    abstract val color: Color

    class Round(
        override val color: Color,
        val topLeft: Offset,
        val size: Size,
        val corner: CornerRadius,
    ) : ShadowSlice()

    class Fill(
        override val color: Color,
        val path: Path,
    ) : ShadowSlice()

    class Scaled(
        override val color: Color,
        val path: Path,
        val roomPx: Float,
        val sx: Float,
        val sy: Float,
        val pivot: Offset,
    ) : ShadowSlice()
}

/**
 * Outline, knockout, and one shape per shadow step. The draw lambda only fills
 * these. Alphas still follow [shadowFalloff].
 */
private fun buildSoftShadow(
    density: Density,
    size: Size,
    layoutDirection: LayoutDirection,
    shape: Shape,
    color: Color,
    roomPx: Float,
    steps: Int,
): BuiltShadow? {
    if (roomPx <= 1f || color.alpha <= 0f) return null
    val plateWidth = size.width - roomPx * 2f
    val plateHeight = size.height - roomPx * 2f
    if (plateWidth <= 1f || plateHeight <= 1f) return null
    val count = steps.coerceAtLeast(1)
    val plate = Size(plateWidth, plateHeight)
    val outline = shape.createOutline(plate, layoutDirection, density)
    // Tuck the knockout a pixel under the plate so the fade meets the fill
    // without a bright seam.
    val knockout = plateKnockout(outline, roomPx, tuck = 1.25f)
    val peak = color.alpha
    val paint = color.copy(alpha = 1f)
    val slices = ArrayList<ShadowSlice>(count)
    var covered = 0f
    for (step in count downTo 1) {
        val innerT = (step - 1) / count.toFloat()
        val outerT = step / count.toFloat()
        val target = shadowFalloff(innerT) * peak
        val delta = if (covered >= 0.98f) {
            0f
        } else {
            ((target - covered) / (1f - covered)).coerceIn(0f, 1f)
        }
        covered = target
        if (delta <= 0f) continue
        slices += shadowSlice(outline, roomPx, spread = roomPx * outerT, color = paint.copy(alpha = delta))
    }
    return BuiltShadow(knockout, slices)
}

private fun DrawScope.drawBuiltShadow(built: BuiltShadow) {
    clipPath(built.knockout, clipOp = ClipOp.Difference) {
        for (slice in built.slices) {
            when (slice) {
                is ShadowSlice.Round -> drawRoundRect(
                    color = slice.color,
                    topLeft = slice.topLeft,
                    size = slice.size,
                    cornerRadius = slice.corner,
                )
                is ShadowSlice.Fill -> drawPath(slice.path, slice.color)
                is ShadowSlice.Scaled -> translate(slice.roomPx, slice.roomPx) {
                    scale(slice.sx, slice.sy, pivot = slice.pivot) {
                        drawPath(slice.path, slice.color)
                    }
                }
            }
        }
    }
}

private fun shadowSlice(outline: Outline, roomPx: Float, spread: Float, color: Color): ShadowSlice {
    return when (outline) {
        is Outline.Rounded -> {
            val rect = outline.roundRect
            val radius = rect.uniformCorner()
            val width = rect.width + spread * 2f
            val height = rect.height + spread * 2f
            if (radius != null) {
                ShadowSlice.Round(
                    color = color,
                    topLeft = Offset(rect.left + roomPx - spread, rect.top + roomPx - spread),
                    size = Size(width, height),
                    corner = CornerRadius(
                        (radius.x + spread).coerceAtMost(width / 2f),
                        (radius.y + spread).coerceAtMost(height / 2f),
                    ),
                )
            } else {
                ShadowSlice.Fill(color, rect.inflatedPath(roomPx, spread))
            }
        }
        is Outline.Rectangle -> {
            val rect = outline.rect
            val width = rect.width + spread * 2f
            val height = rect.height + spread * 2f
            ShadowSlice.Round(
                color = color,
                topLeft = Offset(rect.left + roomPx - spread, rect.top + roomPx - spread),
                size = Size(width, height),
                corner = CornerRadius(
                    spread.coerceAtMost(width / 2f),
                    spread.coerceAtMost(height / 2f),
                ),
            )
        }
        is Outline.Generic -> {
            val bounds = outline.path.getBounds()
            val sx = (bounds.width + spread * 2f) / bounds.width.coerceAtLeast(1f)
            val sy = (bounds.height + spread * 2f) / bounds.height.coerceAtLeast(1f)
            ShadowSlice.Scaled(
                color = color,
                path = outline.path,
                roomPx = roomPx,
                sx = sx,
                sy = sy,
                pivot = bounds.center,
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

private const val NormalShadowSteps = 40
private const val LowShadowSteps = 12

/** 1 at the plate edge, 0 at the outer rim, flat at both ends. */
private fun shadowFalloff(t: Float): Float {
    val u = (1f - t).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
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
