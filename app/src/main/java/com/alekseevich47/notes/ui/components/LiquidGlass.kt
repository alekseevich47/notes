package com.alekseevich47.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

object LiquidGlassTokens {
    val Fill = Color.White.copy(alpha = 0.72f)
    val FillDark = Color(0xFF2A1D3C).copy(alpha = 0.72f)
    val TintCool = Color(0xFFE8EEF8).copy(alpha = 0.20f)
    val Specular = Color.White.copy(alpha = 0.45f)
    val ShadowTint = Color(0xFF1F2687).copy(alpha = 0.22f)
    val DefaultCorner = 28.dp
    val BlurRadius = 22.dp
    val Elevation = 16.dp
}

@Composable
fun LiquidGlassPlate(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    shape: Shape = RoundedCornerShape(LiquidGlassTokens.DefaultCorner),
    elevation: Dp = LiquidGlassTokens.Elevation,
    content: @Composable BoxScope.() -> Unit,
) {
    val fill = if (dark) LiquidGlassTokens.FillDark else LiquidGlassTokens.Fill
    Box(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = LiquidGlassTokens.ShadowTint,
                    spotColor = LiquidGlassTokens.ShadowTint,
                )
                .clip(shape),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = fill,
                            tints = listOf(
                                HazeTint(fill),
                                HazeTint(LiquidGlassTokens.TintCool),
                            ),
                            blurRadius = LiquidGlassTokens.BlurRadius,
                            noiseFactor = 0.08f,
                        ),
                    )
                    .background(fill),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, LiquidGlassTokens.Specular, shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                            tileMode = TileMode.Clamp,
                        ),
                    ),
            )
            content()
        }
    }
}
