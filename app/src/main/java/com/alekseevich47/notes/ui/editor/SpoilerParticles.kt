package com.alekseevich47.notes.ui.editor

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.theme.LocalPerfTier
import com.alekseevich47.notes.ui.theme.LocalReduceMotion
import com.alekseevich47.notes.ui.theme.PerfTier
import kotlinx.coroutines.isActive
import kotlin.math.sin

internal fun spoilerParticleCount(areaPx: Float, lowTier: Boolean): Int {
    if (areaPx <= 0f) return 0
    val cell = if (lowTier) 96f else 48f
    return (areaPx / cell).toInt().coerceAtMost(PARTICLE_CAP)
}

/** Particles inside the growing circle fade. Outside it they stay solid. */
internal fun spoilerParticleAlpha(distance: Float, radius: Float, progress: Float): Float {
    if (progress <= 0f || radius <= 0f || distance >= radius) return 1f
    return (1f - progress).coerceIn(0f, 1f)
}

internal fun spoilerParticlesRun(
    reduceMotion: Boolean,
    resumed: Boolean,
    onScreen: Boolean,
    covering: Boolean,
): Boolean = covering && !reduceMotion && resumed && onScreen

internal class SpoilerLayer(
    val modifier: Modifier,
    val glyphsHidden: Boolean,
)

/**
 * Locked spoilers in the editor. [SpoilerPreview] does not call this: the list
 * keeps the cached static blur. Reduce motion does too, and never starts particles.
 * Glyphs stay hidden until a successful PIN, then a circle reveals them.
 */
@Composable
internal fun rememberSpoilerLayer(
    text: String,
    marks: List<TextMark>,
    style: TextStyle,
    ink: androidx.compose.ui.graphics.Color,
    layout: TextLayoutResult?,
    widthPx: Int,
    hiddenUnlocked: Boolean,
): SpoilerLayer {
    val reduceMotion = LocalReduceMotion.current
    val tier = LocalPerfTier.current
    val resumed = rememberParticleResumed()
    val view = LocalView.current
    val dotPx = with(LocalDensity.current) { 2.2.dp.toPx() }
    var onScreen by remember { mutableStateOf(false) }
    var hold by remember { mutableStateOf(false) }
    var seenUnlocked by remember { mutableStateOf(hiddenUnlocked) }
    val anim = remember { Animatable(0f) }
    val tick = remember { mutableLongStateOf(0L) }
    val field = remember { ParticleField() }
    val clip = remember { android.graphics.Path() }
    var revealLive by remember { mutableStateOf(false) }
    val hasHidden = marks.any { it.style == MarkStyle.HIDDEN }
    if (hiddenUnlocked != seenUnlocked) {
        seenUnlocked = hiddenUnlocked
        if (!hiddenUnlocked) hold = false
        else if (hasHidden && !reduceMotion) hold = true
    }
    val glyphsHidden = hasHidden && (!hiddenUnlocked || (hold && !reduceMotion))
    if (!reduceMotion && glyphsHidden && layout != null) {
        field.rebuildIfNeeded(layout, marks, tier, dotPx, ink)
    }
    val particlesWanted = glyphsHidden && !reduceMotion && field.count > 0
    val run = spoilerParticlesRun(reduceMotion, resumed, onScreen, particlesWanted)

    LaunchedEffect(hold) {
        if (!hold) {
            revealLive = false
            return@LaunchedEffect
        }
        anim.snapTo(0f)
        revealLive = true
        anim.animateTo(1f, tween(durationMillis = 400))
        revealLive = false
        hold = false
    }
    LaunchedEffect(run, layout, marks, tier) {
        if (!run) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameMillis { frame ->
                val dt = if (last == 0L) 0.016f else (frame - last).coerceIn(0L, 32L) / 1000f
                last = frame
                field.step(dt)
                tick.longValue = frame
            }
        }
    }

    val modifier = when {
        !glyphsHidden -> Modifier
        !particlesWanted -> lockedSpoilerModifier(
            text = text,
            marks = marks,
            style = style,
            ink = ink,
            widthPx = widthPx,
        )
        else -> {
            val inkLayout = rememberSpoilerPaint(
                text = text,
                marks = marks,
                style = style,
                ink = ink,
                widthPx = widthPx,
            ).layout
            Modifier
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInWindow()
                    val visible = bounds.width > 0f &&
                        bounds.height > 0f &&
                        bounds.right > 0f &&
                        bounds.bottom > 0f &&
                        bounds.left < view.width &&
                        bounds.top < view.height
                    if (visible != onScreen) onScreen = visible
                }
                .pointerInput(field) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        if (field.contains(down.position.x, down.position.y)) {
                            field.hasTap = true
                            field.tapX = down.position.x
                            field.tapY = down.position.y
                        }
                    }
                }
                .drawWithContent {
                    tick.longValue
                    val progress = if (revealLive && hiddenUnlocked) anim.value else 0f
                    drawContent()
                    val reveal = inkLayout
                    if (progress > 0f && reveal != null) {
                        val radius = progress * field.coverRadius()
                        val originX = field.originX()
                        val originY = field.originY()
                        var checkpoint = 0
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            checkpoint = native.save()
                            clip.rewind()
                            clip.addCircle(originX, originY, radius, android.graphics.Path.Direction.CW)
                            native.clipPath(clip)
                        }
                        drawText(reveal)
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.restoreToCount(checkpoint)
                        }
                    }
                    field.draw(this, progress)
                }
        }
    }
    return SpoilerLayer(modifier, glyphsHidden)
}

private const val PARTICLE_CAP = 600
private const val STRIDE = 5

/**
 * x, y, vx, vy, phase. Point buffers are filled for [android.graphics.Canvas.drawPoints]
 * and reused every frame.
 */
private class ParticleField {
    var count: Int = 0
    private var ready: Boolean = false
    private var signature: Int = 0
    private var data = FloatArray(0)
    private var home = IntArray(0)
    private var rects = FloatArray(0)
    private var rectCount = 0
    private var outside = FloatArray(0)
    private var inside = FloatArray(0)
    private var outsideN = 0
    private var insideN = 0
    var hasTap: Boolean = false
    var tapX: Float = 0f
    var tapY: Float = 0f
    private var fallbackX: Float = 0f
    private var fallbackY: Float = 0f
    private var colorBits: Int = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val rectScratch = ArrayList<Rect>(8)

    fun rebuildIfNeeded(
        layout: TextLayoutResult,
        marks: List<TextMark>,
        tier: PerfTier,
        dotPx: Float,
        ink: androidx.compose.ui.graphics.Color,
    ) {
        var next = signatureOf(layout, marks, tier)
        next = 31 * next + ink.hashCode()
        next = 31 * next + dotPx.toBits()
        if (ready && next == signature) return
        ready = true
        signature = next
        rectScratch.clear()
        expandedHiddenPath(layout, marks, 0f, rectScratch)
        rectCount = rectScratch.size
        if (rectCount == 0) {
            count = 0
            return
        }
        var area = 0f
        val packed = FloatArray(rectCount * 4)
        for (index in 0 until rectCount) {
            val rect = rectScratch[index]
            val at = index * 4
            packed[at] = rect.left
            packed[at + 1] = rect.top
            packed[at + 2] = rect.right
            packed[at + 3] = rect.bottom
            area += (rect.right - rect.left) * (rect.bottom - rect.top)
        }
        rects = packed
        val first = rectScratch[0]
        fallbackX = (first.left + first.right) * 0.5f
        fallbackY = (first.top + first.bottom) * 0.5f
        val nextCount = spoilerParticleCount(area, tier == PerfTier.LOW)
        count = nextCount
        if (nextCount == 0) return
        data = FloatArray(nextCount * STRIDE)
        home = IntArray(nextCount)
        outside = FloatArray(nextCount * 2)
        inside = FloatArray(nextCount * 2)
        paint.strokeWidth = dotPx
        colorBits = android.graphics.Color.argb(
            (ink.alpha * 255f).toInt().coerceIn(0, 255),
            (ink.red * 255f).toInt().coerceIn(0, 255),
            (ink.green * 255f).toInt().coerceIn(0, 255),
            (ink.blue * 255f).toInt().coerceIn(0, 255),
        )
        paint.color = colorBits
        seed(nextCount, area)
    }

    fun originX(): Float = if (hasTap) tapX else fallbackX

    fun originY(): Float = if (hasTap) tapY else fallbackY

    fun coverRadius(): Float {
        val ox = originX()
        val oy = originY()
        var max = 1f
        var index = 0
        while (index < rectCount) {
            val at = index * 4
            max = maxOf(max, dist(ox, oy, rects[at], rects[at + 1]))
            max = maxOf(max, dist(ox, oy, rects[at + 2], rects[at + 1]))
            max = maxOf(max, dist(ox, oy, rects[at], rects[at + 3]))
            max = maxOf(max, dist(ox, oy, rects[at + 2], rects[at + 3]))
            index++
        }
        return max
    }

    fun contains(x: Float, y: Float): Boolean {
        var index = 0
        while (index < rectCount) {
            val at = index * 4
            if (x >= rects[at] && x <= rects[at + 2] && y >= rects[at + 1] && y <= rects[at + 3]) {
                return true
            }
            index++
        }
        return false
    }

    fun step(dt: Float) {
        if (count == 0) return
        var index = 0
        var cursor = 0
        while (index < count) {
            val slot = home[index] * 4
            val left = rects[slot]
            val top = rects[slot + 1]
            val right = rects[slot + 2]
            val bottom = rects[slot + 3]
            var x = data[cursor]
            var y = data[cursor + 1]
            var vx = data[cursor + 2]
            var vy = data[cursor + 3]
            var phase = data[cursor + 4]
            phase += dt * 3.2f
            x += vx * dt
            y += vy * dt + sin(phase) * 8f * dt
            if (x < left) {
                x = left
                vx = -vx
            } else if (x > right) {
                x = right
                vx = -vx
            }
            if (y < top) {
                y = top
                vy = -vy
            } else if (y > bottom) {
                y = bottom
                vy = -vy
            }
            data[cursor] = x
            data[cursor + 1] = y
            data[cursor + 2] = vx
            data[cursor + 3] = vy
            data[cursor + 4] = phase
            cursor += STRIDE
            index++
        }
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, progress: Float) {
        if (count == 0) return
        val radius = if (progress > 0f) progress * coverRadius() else 0f
        val ox = originX()
        val oy = originY()
        var outsideWrite = 0
        var insideWrite = 0
        var index = 0
        var cursor = 0
        while (index < count) {
            val x = data[cursor]
            val y = data[cursor + 1]
            val dx = x - ox
            val dy = y - oy
            val distance = if (progress > 0f) dist(0f, 0f, dx, dy) else Float.MAX_VALUE
            if (spoilerParticleAlpha(distance, radius, progress) < 1f) {
                inside[insideWrite] = x
                inside[insideWrite + 1] = y
                insideWrite += 2
            } else {
                outside[outsideWrite] = x
                outside[outsideWrite + 1] = y
                outsideWrite += 2
            }
            cursor += STRIDE
            index++
        }
        outsideN = outsideWrite
        insideN = insideWrite
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            paint.color = colorBits
            if (outsideN > 0) {
                paint.alpha = 255
                native.drawPoints(outside, 0, outsideN, paint)
            }
            if (insideN > 0) {
                val fade = ((1f - progress) * 255f).toInt().coerceIn(0, 255)
                if (fade > 0) {
                    paint.alpha = fade
                    native.drawPoints(inside, 0, insideN, paint)
                }
            }
        }
    }

    private fun seed(nextCount: Int, area: Float) {
        var rng = signature xor 0x9E3779B9.toInt()
        fun unit(): Float {
            rng = rng * 1664525 + 1013904223
            return ((rng ushr 8) and 0xFFFFFF) / 16777215f
        }
        val step = area / nextCount
        var target = step * 0.5f
        var rect = 0
        var acc = rectArea(0)
        var index = 0
        var cursor = 0
        while (index < nextCount) {
            while (acc < target && rect < rectCount - 1) {
                rect++
                acc += rectArea(rect)
            }
            val slot = rect * 4
            val left = rects[slot]
            val top = rects[slot + 1]
            val right = rects[slot + 2]
            val bottom = rects[slot + 3]
            home[index] = rect
            data[cursor] = left + unit() * (right - left)
            data[cursor + 1] = top + unit() * (bottom - top)
            data[cursor + 2] = (unit() - 0.5f) * 28f
            data[cursor + 3] = (unit() - 0.5f) * 28f
            data[cursor + 4] = unit() * 6.2831855f
            target += step
            cursor += STRIDE
            index++
        }
    }

    private fun rectArea(index: Int): Float {
        val at = index * 4
        return (rects[at + 2] - rects[at]) * (rects[at + 3] - rects[at + 1])
    }

    private fun signatureOf(layout: TextLayoutResult, marks: List<TextMark>, tier: PerfTier): Int {
        var hash = layout.layoutInput.text.hashCode()
        hash = 31 * hash + layout.size.width
        hash = 31 * hash + layout.size.height
        hash = 31 * hash + marks.hashCode()
        hash = 31 * hash + tier.ordinal
        return hash
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

@Composable
private fun rememberParticleResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    val resumed = remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { source, _ ->
            resumed.value = source.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        resumed.value = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed.value
}
