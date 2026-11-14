package com.alekseevich47.notes.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.RoundRect
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
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.82f)) togetherWith
                    (fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.88f))
            },
            label = "micMorph",
        ) { visual ->
            when (visual) {
                MicVisualState.IDLE, MicVisualState.RECORDING -> MicIcon(
                    recording = visual == MicVisualState.RECORDING,
                    amplitude = amplitude,
                    size = size,
                )
                MicVisualState.PROCESSING -> ProcessingDots(size = size)
                MicVisualState.DONE -> DoneCheck(size = size)
            }
        }
    }
}

@Composable
private fun MicIcon(recording: Boolean, amplitude: Float, size: Dp) {
    val infinite = rememberInfiniteTransition(label = "idleWave")
    val wave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "wave",
    )
    val fillLevel by animateFloatAsState(
        targetValue = if (recording) {
            0.22f + amplitude.coerceIn(0f, 1f) * 0.78f
        } else {
            0.16f + 0.10f * ((sin(wave.toDouble()) + 1) / 2).toFloat()
        },
        animationSpec = tween(140),
        label = "fill",
    )
    val glow by animateFloatAsState(
        targetValue = if (recording) 0.30f + amplitude * 0.55f else 0.08f,
        label = "glow",
    )

    Canvas(modifier = Modifier.size(size)) {
        val stroke = Stroke(width = size.toPx() * 0.055f, cap = StrokeCap.Round)
        val outline = Color(0xFFF5EEFF)
        val cx = this.size.width / 2f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NotesVioletGlow.copy(alpha = glow),
                    Color.Transparent,
                ),
            ),
            radius = this.size.minDimension * 0.56f,
        )

        val micPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = this@Canvas.size.width * 0.38f,
                    top = this@Canvas.size.height * 0.22f,
                    right = this@Canvas.size.width * 0.62f,
                    bottom = this@Canvas.size.height * 0.58f,
                    cornerRadius = CornerRadius(this@Canvas.size.width * 0.12f),
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
}

@Composable
private fun ProcessingDots(size: Dp) {
    val infinite = rememberInfiniteTransition(label = "dots")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "dotPhase",
    )
    Canvas(modifier = Modifier.size(size)) {
        val outline = Color(0xFFF5EEFF)
        val cy = this.size.height / 2f
        val cx = this.size.width / 2f
        for (i in 0..2) {
            val local = ((phase - i + 3f) % 3f)
            val bounce = sin((local.coerceIn(0f, 1f) * PI).toFloat())
            val y = cy - bounce * this.size.height * 0.14f
            val alpha = 0.40f + 0.60f * (1f - kotlin.math.abs(local - 0.5f) * 2f).coerceIn(0f, 1f)
            drawCircle(
                color = outline.copy(alpha = alpha),
                radius = this.size.minDimension * 0.065f,
                center = Offset(cx + (i - 1) * this.size.width * 0.18f, y),
            )
        }
    }
}

@Composable
private fun DoneCheck(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val outline = Color(0xFFF5EEFF)
        val stroke = Stroke(width = size.toPx() * 0.06f, cap = StrokeCap.Round)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        drawCircle(
            color = NotesVioletGlow.copy(alpha = 0.22f),
            radius = this.size.minDimension * 0.40f,
        )
        drawCircle(color = outline, style = stroke, radius = this.size.minDimension * 0.32f)
        val path = Path().apply {
            moveTo(cx - this@Canvas.size.width * 0.14f, cy)
            lineTo(cx - this@Canvas.size.width * 0.02f, cy + this@Canvas.size.height * 0.12f)
            lineTo(cx + this@Canvas.size.width * 0.16f, cy - this@Canvas.size.height * 0.12f)
        }
        drawPath(path, color = NotesSuccess, style = stroke)
    }
}
