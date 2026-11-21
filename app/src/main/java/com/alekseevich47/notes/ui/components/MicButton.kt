package com.alekseevich47.notes.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.ui.theme.NotesPurple
import com.alekseevich47.notes.ui.theme.NotesPurpleDeep
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Violet, pink, and blue walked in small steps so the capsule reads as one
 * blend. The first and last stops match, which keeps the slow scroll seamless.
 */
private val RecordingColors = blendStops(
    listOf(
        Color(0xFF8B74FF),
        Color(0xFFA07CFF),
        Color(0xFFC48CF0),
        Color(0xFFE09AD4),
        Color(0xFFF0A0CC),
        Color(0xFFE4A8E4),
        Color(0xFFC8B4FF),
        Color(0xFFA8B8FF),
        Color(0xFF88A8FF),
        Color(0xFF908CFF),
        Color(0xFF8B74FF),
    ),
    stepsBetween = 3,
)

@Composable
fun MicButton(
    phase: MicPhase,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "mic")
    // 0 and 1 are the same angle of sin(2π t), so the restart is invisible.
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "wave",
    )
    val colorShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8400, easing = LinearEasing), RepeatMode.Restart),
        label = "colors",
    )
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
        label = "dots",
    )
    val easedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "level",
    )
    val plate = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val idleInk = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .shadow(
                elevation = if (phase == MicPhase.RECORDING) 18.dp else 10.dp,
                shape = CircleShape,
                ambientColor = NotesPurple.copy(alpha = if (phase == MicPhase.RECORDING) 0.45f else 0.18f),
                spotColor = NotesPurple.copy(alpha = if (phase == MicPhase.RECORDING) 0.55f else 0.2f),
            )
            .clip(CircleShape)
            .background(plate)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Микрофон" },
        contentAlignment = Alignment.Center,
    ) {
        if (phase == MicPhase.RECORDING) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = NotesPurple.copy(alpha = 0.16f + easedLevel * 0.26f),
                    radius = size.minDimension / 2f,
                )
            }
        }
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "mic-phase",
        ) { target ->
            Canvas(Modifier.size(64.dp)) {
                val glyph = outlineMic(size)
                when (target) {
                    MicPhase.IDLE -> {
                        clipPath(glyph.inner) {
                            drawIdleWave(glyph.body, wave)
                        }
                        drawOutlineMic(glyph, idleInk)
                    }
                    MicPhase.RECORDING -> {
                        clipPath(glyph.inner) {
                            drawRecordingFill(glyph.body, easedLevel, colorShift, wave)
                        }
                        drawOutlineMic(glyph, ink)
                    }
                    MicPhase.PROCESSING -> {
                        // About 1.9× the old 4.2px discs, spaced so the discs do not meet.
                        val dotRadius = 8f
                        val dotSpread = 22f
                        val centers = listOf(
                            size.width / 2f - dotSpread,
                            size.width / 2f,
                            size.width / 2f + dotSpread,
                        )
                        centers.forEachIndexed { index, x ->
                            val phaseShift = (bounce + index * 0.18f) % 1f
                            val lift = sin(phaseShift * PI).toFloat() * 8f
                            drawCircle(
                                color = NotesPurple,
                                radius = dotRadius,
                                center = Offset(x, size.height / 2f - lift),
                            )
                        }
                    }
                    MicPhase.DONE -> {
                        val cx = size.width / 2f
                        val h = size.height
                        drawCircle(color = Color(0xFF2F9E44), style = Stroke(width = 3f), radius = size.width * 0.28f)
                        val check = Path().apply {
                            moveTo(cx - 10f, h / 2f)
                            lineTo(cx - 2f, h / 2f + 8f)
                            lineTo(cx + 12f, h / 2f - 8f)
                        }
                        drawPath(check, color = Color(0xFF2F9E44), style = Stroke(width = 3.4f, cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}

private data class OutlineMic(
    val capsule: Path,
    val inner: Path,
    val body: Rect,
    val cradleTopLeft: Offset,
    val cradleSize: Float,
    val stemTop: Offset,
    val stemBottom: Offset,
    val baseHalf: Float,
    val stroke: Float,
)

/**
 * Hollow capsule, U cradle, short stem, and a base the width of the head.
 * The outline is stroke only — the wave and the recording gradient are clipped
 * inside the capsule.
 */
private fun outlineMic(size: Size): OutlineMic {
    val s = size.minDimension
    val cx = size.width / 2f
    // Centerlines traced from the reference outline: hollow capsule, U cradle,
    // short stem, and a base as wide as the head. Stroke sits on these paths.
    val stroke = s * 0.065f * (2f / 3f)
    val half = s * 0.138f
    val top = s * 0.113f
    val bottom = s * 0.612f
    val body = Rect(cx - half, top, cx + half, bottom)
    val capsule = Path().apply {
        addRoundRect(RoundRect(body, CornerRadius(half, half)))
    }
    val inset = stroke * 0.58f
    val innerRect = Rect(body.left + inset, body.top + inset, body.right - inset, body.bottom - inset)
    val innerRadius = innerRect.width / 2f
    val inner = Path().apply {
        addRoundRect(RoundRect(innerRect, CornerRadius(innerRadius, innerRadius)))
    }
    val cradleR = s * 0.275f
    val armY = s * 0.460f
    val cradleBottom = armY + cradleR
    val baseY = s * 0.891f
    return OutlineMic(
        capsule = capsule,
        inner = inner,
        body = innerRect,
        cradleTopLeft = Offset(cx - cradleR, armY - cradleR),
        cradleSize = cradleR * 2f,
        stemTop = Offset(cx, cradleBottom - stroke * 0.2f),
        stemBottom = Offset(cx, baseY),
        baseHalf = s * 0.137f,
        stroke = stroke,
    )
}

private fun DrawScope.drawOutlineMic(glyph: OutlineMic, color: Color) {
    val stroke = Stroke(width = glyph.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(glyph.capsule, color = color, style = stroke)
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = glyph.cradleTopLeft,
        size = Size(glyph.cradleSize, glyph.cradleSize),
        style = stroke,
    )
    drawLine(
        color = color,
        start = glyph.stemTop,
        end = glyph.stemBottom,
        strokeWidth = glyph.stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(glyph.stemBottom.x - glyph.baseHalf, glyph.stemBottom.y),
        end = Offset(glyph.stemBottom.x + glyph.baseHalf, glyph.stemBottom.y),
        strokeWidth = glyph.stroke,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawIdleWave(body: Rect, turns: Float) {
    val height = body.height.coerceAtLeast(1f)
    // One broad hump across the head, not a steep crest and trough.
    val amp = height * 0.09f
    val base = body.bottom - height * 0.34f
    drawPath(
        wavePath(body, base, amp, turns, cycles = 0.5f),
        brush = Brush.verticalGradient(
            colors = listOf(
                NotesPurple.copy(alpha = 0.22f),
                NotesPurple.copy(alpha = 0.78f),
                NotesPurpleDeep.copy(alpha = 0.95f),
            ),
            startY = base - amp,
            endY = body.bottom,
        ),
    )
}

/**
 * Traveling sine under the capsule, drawn as cubics that follow the slope so
 * crests stay round. `cycles` is a whole or half turn across the width.
 * `turns` is 0..1 and sin(2π t) matches at both ends, so the loop has no seam.
 */
private fun wavePath(body: Rect, base: Float, amplitude: Float, turns: Float, cycles: Float): Path {
    val width = body.width.coerceAtLeast(1f)
    val segments = 12
    val path = Path()
    path.moveTo(body.left, body.bottom)
    path.lineTo(body.left, waveSample(0f, base, amplitude, turns, cycles))
    for (index in 0 until segments) {
        val u0 = index / segments.toFloat()
        val u1 = (index + 1) / segments.toFloat()
        val x0 = body.left + width * u0
        val x1 = body.left + width * u1
        val y0 = waveSample(u0, base, amplitude, turns, cycles)
        val y1 = waveSample(u1, base, amplitude, turns, cycles)
        val dx = (x1 - x0) / 3f
        path.cubicTo(
            x0 + dx,
            y0 + waveSlope(u0, amplitude, turns, cycles) / width * dx,
            x1 - dx,
            y1 - waveSlope(u1, amplitude, turns, cycles) / width * dx,
            x1,
            y1,
        )
    }
    path.lineTo(body.right, body.bottom)
    path.close()
    return path
}

private fun waveSample(u: Float, base: Float, amplitude: Float, turns: Float, cycles: Float): Float {
    return base + sin((u * cycles + turns) * (2.0 * PI)).toFloat() * amplitude
}

private fun waveSlope(u: Float, amplitude: Float, turns: Float, cycles: Float): Float {
    return (cos((u * cycles + turns) * (2.0 * PI)) * (2.0 * PI) * cycles * amplitude).toFloat()
}

private fun DrawScope.drawRecordingFill(body: Rect, level: Float, shift: Float, turns: Float) {
    val height = body.height.coerceAtLeast(1f)
    val amp = height * 0.055f
    val room = amp / height + 0.012f
    val fill = loudFill(level).coerceAtMost(1f - room)
    val base = body.bottom - height * fill
    val period = height * 1.3f
    val startY = body.top - shift * period
    drawPath(
        wavePath(body, base, amp, turns, cycles = 1f),
        brush = Brush.verticalGradient(
            colors = RecordingColors,
            startY = startY,
            endY = startY + period,
            tileMode = TileMode.Repeated,
        ),
    )
}

/** Loud speech fills most of the head; silence stays a low band. */
private fun loudFill(level: Float): Float {
    val gained = (level.coerceIn(0f, 1f) * 1.85f).coerceAtMost(1f)
    return 0.14f + sqrt(gained) * 0.84f
}

private fun blendStops(anchors: List<Color>, stepsBetween: Int): List<Color> {
    val stops = ArrayList<Color>(1 + (anchors.size - 1) * stepsBetween)
    for (index in 0 until anchors.lastIndex) {
        val from = anchors[index]
        val to = anchors[index + 1]
        for (step in 0 until stepsBetween) {
            stops += lerp(from, to, step / stepsBetween.toFloat())
        }
    }
    stops += anchors.last()
    return stops
}
