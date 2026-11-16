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
import kotlin.math.sin

private val RecordingColors = listOf(
    Color(0xFF6C4CFF),
    Color(0xFFFF5EAB),
    Color(0xFF3E7BFF),
    Color(0xFFC44CFF),
    Color(0xFF6C4CFF),
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
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
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
                        drawOutlineMic(glyph, ink)
                    }
                    MicPhase.RECORDING -> {
                        clipPath(glyph.inner) {
                            drawRecordingFill(glyph.body, easedLevel, colorShift)
                        }
                        drawOutlineMic(glyph, ink)
                    }
                    MicPhase.PROCESSING -> {
                        val centers = listOf(size.width / 2f - 16f, size.width / 2f, size.width / 2f + 16f)
                        centers.forEachIndexed { index, x ->
                            val phaseShift = (bounce + index * 0.18f) % 1f
                            val lift = sin(phaseShift * PI).toFloat() * 8f
                            drawCircle(
                                color = NotesPurple,
                                radius = 4.2f,
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
    val stroke = s * 0.065f
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
    val amp = body.height * 0.23f
    val base = body.bottom - body.height * 0.42f
    val path = Path()
    path.moveTo(body.left, body.bottom)
    var x = body.left
    while (x <= body.right) {
        path.lineTo(x, waveY(x, body, base, amp, turns))
        x += 1.1f
    }
    path.lineTo(body.right, waveY(body.right, body, base, amp, turns))
    path.lineTo(body.right, body.bottom)
    path.close()
    drawPath(
        path,
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
 * One cycle of a traveling sine. `turns` is 0..1 and wraps with sin(2π t),
 * so the shape at the end of the loop matches the start.
 */
private fun waveY(x: Float, body: Rect, base: Float, amplitude: Float, turns: Float): Float {
    val width = body.width.coerceAtLeast(1f)
    val u = (x - body.left) / width
    val cycles = 1f
    return base + sin((u * cycles + turns) * (2.0 * PI)).toFloat() * amplitude
}

private fun DrawScope.drawRecordingFill(body: Rect, level: Float, shift: Float) {
    val fill = 0.20f + level.coerceIn(0f, 1f) * 0.80f
    val top = body.bottom - body.height * fill
    val period = (body.height * 0.72f).coerceAtLeast(1f)
    val startY = body.top - shift * period
    drawRect(
        brush = Brush.verticalGradient(
            colors = RecordingColors,
            startY = startY,
            endY = startY + period,
            tileMode = TileMode.Repeated,
        ),
        topLeft = Offset(body.left, top),
        size = Size(body.width, (body.bottom - top).coerceAtLeast(0f)),
    )
}
