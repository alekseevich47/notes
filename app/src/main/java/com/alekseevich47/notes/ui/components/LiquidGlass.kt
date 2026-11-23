package com.alekseevich47.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Frosted plate. Blur comes from Haze only — no AGSL RenderEffect, which
 * breaks compositing on Tecno / HiOS.
 */
@Immutable
data class GlassPalette(
    val fill: Color,
    val tint: Color,
    val edge: Color,
    val sheen: List<Color>,
)

object LiquidGlassTokens {
    val Corner = 32.dp
    val Blur = 22.dp
    val Elevation = 6.dp

    @Composable
    fun palette(): GlassPalette {
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
        return if (dark) {
            GlassPalette(
                fill = Color(0xFF1A1B22).copy(alpha = 0.82f),
                tint = Color(0xFF6C4CFF).copy(alpha = 0.16f),
                edge = Color.White.copy(alpha = 0.16f),
                sheen = listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.04f),
                    Color.Transparent,
                ),
            )
        } else {
            GlassPalette(
                fill = Color.White.copy(alpha = 0.74f),
                tint = Color(0xFFE8EEF8).copy(alpha = 0.28f),
                edge = Color.White.copy(alpha = 0.72f),
                sheen = listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
            )
        }
    }
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

@Composable
fun LiquidGlassPlate(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LiquidGlassTokens.Corner),
    elevation: Dp = LiquidGlassTokens.Elevation,
    halo: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LiquidGlassTokens.palette()
    // Same light drop shadow as the sticky player. It stays outside the offscreen
    // buffer: that buffer is only as big as the plate and would clip the shadow.
    Box(modifier = modifier) {
        if (elevation > 0.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(if (halo) Modifier.padding(18.dp) else Modifier)
                    .shadow(elevation, shape, clip = false),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            if (halo) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = Color.Transparent,
                                tints = emptyList(),
                                blurRadius = 16.dp,
                                noiseFactor = 0.04f,
                            ),
                        ) {
                            progressive = HazeProgressive.RadialGradient(
                                centerIntensity = 1f,
                                radiusIntensity = 0f,
                            )
                        },
                )
            }
            Box(
                modifier = Modifier
                    .then(if (halo) Modifier.padding(18.dp) else Modifier)
                    .clip(shape),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = palette.fill,
                                tints = listOf(HazeTint(palette.fill), HazeTint(palette.tint)),
                                blurRadius = LiquidGlassTokens.Blur,
                                noiseFactor = 0.08f,
                            ),
                        )
                        .background(palette.fill),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(1.dp, palette.edge, shape)
                        .background(Brush.linearGradient(palette.sheen, tileMode = TileMode.Clamp)),
                )
                content()
            }
        }
    }
}
