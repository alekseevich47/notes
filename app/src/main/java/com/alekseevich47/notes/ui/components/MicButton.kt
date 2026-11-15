package com.alekseevich47.notes.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.sin

@Composable
fun MicButton(
    phase: MicPhase,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "mic")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "wave",
    )
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
        label = "dots",
    )
    val plate = MaterialTheme.colorScheme.surface
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
                    color = NotesPurple.copy(alpha = 0.18f + level * 0.28f),
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
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val body = androidx.compose.ui.geometry.Rect(
                    left = cx - w * 0.16f,
                    top = h * 0.16f,
                    right = cx + w * 0.16f,
                    bottom = h * 0.58f,
                )
                val micPath = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            body,
                            CornerRadius(body.width / 2f, body.width / 2f),
                        ),
                    )
                }
                when (target) {
                    MicPhase.IDLE -> {
                        clipPath(micPath) {
                            val shift = wave * body.width
                            val path = Path()
                            val base = body.bottom - body.height * 0.28f
                            path.moveTo(body.left, body.bottom)
                            var x = body.left
                            while (x <= body.right) {
                                val y = base + sin((x + shift) / 8f) * body.height * 0.08f
                                path.lineTo(x, y)
                                x += 2f
                            }
                            path.lineTo(body.right, body.bottom)
                            path.close()
                            drawPath(
                                path,
                                brush = Brush.verticalGradient(
                                    listOf(NotesPurple.copy(alpha = 0.15f), NotesPurple.copy(alpha = 0.55f)),
                                    startY = body.top,
                                    endY = body.bottom,
                                ),
                            )
                        }
                        drawPath(micPath, color = Color(0xFFE7E2F2), style = Stroke(width = 2.4f))
                        drawMicStand(cx, body.bottom, h, Color(0xFFD9D3E6))
                    }
                    MicPhase.RECORDING -> {
                        val fill = (0.22f + level.coerceIn(0f, 1f) * 0.78f)
                        clipPath(micPath) {
                            val top = body.bottom - body.height * fill
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(NotesPurple, NotesPurpleDeep),
                                    startY = top,
                                    endY = body.bottom,
                                ),
                                topLeft = Offset(body.left, top),
                                size = Size(body.width, body.bottom - top),
                            )
                        }
                        drawPath(micPath, color = Color.White.copy(alpha = 0.85f), style = Stroke(width = 2.2f))
                        drawMicStand(cx, body.bottom, h, Color.White.copy(alpha = 0.9f))
                    }
                    MicPhase.PROCESSING -> {
                        val centers = listOf(cx - 16f, cx, cx + 16f)
                        centers.forEachIndexed { index, x ->
                            val phaseShift = (bounce + index * 0.18f) % 1f
                            val lift = sin(phaseShift * Math.PI).toFloat() * 8f
                            drawCircle(
                                color = NotesPurple,
                                radius = 4.2f,
                                center = Offset(x, h / 2f - lift),
                            )
                        }
                    }
                    MicPhase.DONE -> {
                        drawCircle(color = Color(0xFF2F9E44), style = Stroke(width = 3f), radius = w * 0.28f)
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMicStand(
    cx: Float,
    bodyBottom: Float,
    height: Float,
    color: Color,
) {
    drawLine(color, Offset(cx, bodyBottom + 2f), Offset(cx, height * 0.78f), strokeWidth = 2.4f, cap = StrokeCap.Round)
    drawLine(
        color,
        Offset(cx - 10f, height * 0.78f),
        Offset(cx + 10f, height * 0.78f),
        strokeWidth = 2.4f,
        cap = StrokeCap.Round,
    )
    drawArc(
        color = color,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx - 16f, bodyBottom - 8f),
        size = Size(32f, 28f),
        style = Stroke(width = 2.2f, cap = StrokeCap.Round),
    )
}
