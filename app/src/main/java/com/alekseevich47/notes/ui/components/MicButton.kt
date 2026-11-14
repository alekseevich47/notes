package com.alekseevich47.notes.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.MicVisualState
import com.alekseevich47.notes.ui.theme.NotesSuccess
import com.alekseevich47.notes.ui.theme.NotesViolet
import com.alekseevich47.notes.ui.theme.NotesVioletDeep
import com.alekseevich47.notes.ui.theme.NotesVioletGlow
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MicButton(
    state: MicVisualState,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val infinite = rememberInfiniteTransition(label = "mic")
    val wave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "wave",
    )
    val fillLevel by animateFloatAsState(
        targetValue = when (state) {
            MicVisualState.RECORDING -> 0.25f + amplitude.coerceIn(0f, 1f) * 0.75f
            MicVisualState.IDLE -> 0.18f + 0.08f * ((sin(wave.toDouble()) + 1) / 2).toFloat()
            else -> 0f
        },
        animationSpec = tween(180),
        label = "fill",
    )
    val glow by animateFloatAsState(
        targetValue = if (state == MicVisualState.RECORDING) 0.35f + amplitude * 0.45f else 0f,
        label = "glow",
    )
    val dotPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "dots",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = size.toPx() * 0.06f, cap = StrokeCap.Round)
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val outline = Color(0xFFF5EEFF)

            if (glow > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NotesVioletGlow.copy(alpha = glow),
                            Color.Transparent,
                        ),
                    ),
                    radius = this.size.minDimension * 0.55f,
                )
            }

            when (state) {
                MicVisualState.IDLE, MicVisualState.RECORDING -> {
                    val micPath = Path().apply {
                        val w = this@Canvas.size.width
                        val h = this@Canvas.size.height
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = w * 0.38f,
                                top = h * 0.22f,
                                right = w * 0.62f,
                                bottom = h * 0.58f,
                                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                            ),
                        )
                    }
                    clipPath(micPath) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(NotesVioletGlow, NotesViolet, NotesVioletDeep),
                            ),
                            topLeft = Offset(0f, this.size.height * (1f - fillLevel)),
                            size = Size(this.size.width, this.size.height * fillLevel),
                        )
                    }
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(this.size.width * 0.38f, this.size.height * 0.22f),
                        size = Size(this.size.width * 0.24f, this.size.height * 0.36f),
                        cornerRadius = CornerRadius(this.size.width * 0.12f),
                        style = stroke,
                    )
                    drawArc(
                        color = outline,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(this.size.width * 0.28f, this.size.height * 0.42f),
                        size = Size(this.size.width * 0.44f, this.size.height * 0.32f),
                        style = stroke,
                    )
                    drawLine(
                        color = outline,
                        start = Offset(cx, this.size.height * 0.74f),
                        end = Offset(cx, this.size.height * 0.84f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
                MicVisualState.PROCESSING -> {
                    val baseY = cy
                    for (i in 0..2) {
                        val phase = ((dotPhase - i + 3f) % 3f)
                        val bounce = sin((phase.coerceIn(0f, 1f) * PI).toFloat())
                        val y = baseY - bounce * this.size.height * 0.12f
                        val alpha = 0.45f + 0.55f * (1f - kotlin.math.abs(phase - 0.5f) * 2f).coerceIn(0f, 1f)
                        drawCircle(
                            color = outline.copy(alpha = alpha),
                            radius = this.size.minDimension * 0.06f,
                            center = Offset(cx + (i - 1) * this.size.width * 0.18f, y),
                        )
                    }
                }
                MicVisualState.DONE -> {
                    drawCircle(color = outline, style = stroke, radius = this.size.minDimension * 0.32f)
                    val path = Path().apply {
                        moveTo(cx - this@Canvas.size.width * 0.14f, cy)
                        lineTo(cx - this@Canvas.size.width * 0.02f, cy + this@Canvas.size.height * 0.12f)
                        lineTo(cx + this@Canvas.size.width * 0.16f, cy - this@Canvas.size.height * 0.12f)
                    }
                    drawPath(path, color = NotesSuccess, style = Stroke(width = stroke.width, cap = StrokeCap.Round))
                }
            }
        }
    }
}
