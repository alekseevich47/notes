package com.alekseevich47.notes.ui.editor

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.PreviewPart
import com.alekseevich47.notes.domain.TextMark
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun SpoilerPreview(
    parts: List<PreviewPart>,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    val spoken = spoilerContentDescription(parts)
    val semantics = Modifier.clearAndSetSemantics { contentDescription = spoken }
    if (parts.none { it.hidden }) {
        Text(
            text = parts.joinToString(separator = "") { it.text },
            color = color,
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.then(semantics),
        )
        return
    }
    val annotated = remember(parts) { previewAnnotated(parts) }
    val marks = remember(parts) { previewMarks(parts) }
    SpoilerText(
        text = annotated,
        marks = marks,
        revealed = false,
        color = color,
        style = style,
        modifier = modifier.then(semantics),
        maxLines = maxLines,
    )
}

/**
 * What a screen reader should say. Visible characters stay; each hidden run
 * becomes one phrase, and repeated spaces collapse.
 */
internal fun spoilerContentDescription(parts: List<PreviewPart>): String {
    val raw = buildString {
        for (part in parts) {
            if (part.hidden) append("скрытый текст") else append(part.text)
        }
    }
    return raw.replace(Regex("\\s+"), " ").trim()
}

internal fun noteCardContentDescription(title: String, parts: List<PreviewPart>): String {
    return (title + " " + spoilerContentDescription(parts)).replace(Regex("\\s+"), " ").trim()
}

@Composable
fun SpoilerText(
    text: AnnotatedString,
    marks: List<TextMark>,
    revealed: Boolean,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val locked = !revealed && marks.any { it.style == MarkStyle.HIDDEN }
    val blur = if (locked) {
        lockedSpoilerModifier(
            text = text.text,
            marks = marks,
            style = style,
            ink = color,
            widthPx = layout?.size?.width ?: 0,
            maxLines = maxLines,
        )
    } else {
        Modifier
    }
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier.then(blur),
    )
}

/**
 * Measures and paints locked hidden text. Call this only from `if (locked)`
 * so a plain keystroke does not rebuild the spoiler layout.
 */
@Composable
internal fun lockedSpoilerModifier(
    text: String,
    marks: List<TextMark>,
    style: TextStyle,
    ink: Color,
    widthPx: Int,
    maxLines: Int = Int.MAX_VALUE,
): Modifier {
    val paint = rememberSpoilerPaint(
        text = text,
        marks = marks,
        style = style,
        ink = ink,
        widthPx = widthPx,
        maxLines = maxLines,
    )
    return Modifier.drawSpoilerShadows(paint, marks, ink)
}

/**
 * Layout used only to paint the locked smudge. Hidden glyphs are [ink];
 * everything else is transparent, so the blur cannot smear visible words.
 *
 * The field's own [TextLayoutResult] cannot be reused: its hidden spans are
 * [Color.Transparent], and drawing that result keeps those spans, so the
 * smudge is invisible. Reusing a layout captured before the span was applied
 * is what left the letters readable.
 */
internal class SpoilerPaint(
    val layout: TextLayoutResult?,
    val layer: GraphicsLayer,
    val cache: SpoilerBlurCache,
)

/**
 * Last recorded blur. A later frame draws this instead of recording again.
 * Identity is the measured layout, the pixel size, and the ink color.
 */
internal class SpoilerBlurCache {
    var layout: TextLayoutResult? = null
    var width: Int = -1
    var height: Int = -1
    var ink: Color = Color.Unspecified
    var smear: ImageBitmap? = null
    var ready: Boolean = false
}

@Composable
internal fun rememberSpoilerPaint(
    text: String,
    marks: List<TextMark>,
    style: TextStyle,
    ink: Color,
    widthPx: Int,
    maxLines: Int = Int.MAX_VALUE,
): SpoilerPaint {
    val layer = rememberGraphicsLayer()
    val cache = remember { SpoilerBlurCache() }
    val measurer = rememberTextMeasurer()
    val layout = remember(text, marks, style, ink, widthPx, maxLines) {
        if (widthPx <= 0 || text.isEmpty() || marks.none { it.style == MarkStyle.HIDDEN }) {
            null
        } else {
            measurer.measure(
                text = spoilerAnnotated(text, marks, ink),
                style = style.copy(color = Color.Transparent),
                overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
                maxLines = maxLines,
                constraints = Constraints(maxWidth = widthPx),
            )
        }
    }
    return SpoilerPaint(layout, layer, cache)
}

internal fun spoilerAnnotated(text: String, marks: List<TextMark>, ink: Color): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        marks.forEach { mark ->
            if (mark.style == MarkStyle.HIDDEN) return@forEach
            val start = mark.start.coerceIn(0, text.length)
            val end = mark.end.coerceIn(0, text.length)
            if (end <= start) return@forEach
            val paint = when (mark.style) {
                MarkStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                MarkStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                MarkStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                MarkStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                MarkStyle.HIDDEN -> return@forEach
            }
            addStyle(paint, start, end)
        }
        marks.forEach { mark ->
            if (mark.style != MarkStyle.HIDDEN) return@forEach
            val start = mark.start.coerceIn(0, length)
            val end = mark.end.coerceIn(0, length)
            if (end > start) addStyle(SpanStyle(color = ink), start, end)
        }
    }
}

internal fun previewAnnotated(parts: List<PreviewPart>): AnnotatedString {
    return buildAnnotatedString {
        parts.forEach { part ->
            val start = length
            append(part.text)
            if (part.hidden && length > start) {
                addStyle(SpanStyle(color = Color.Transparent), start, length)
            }
        }
    }
}

internal fun previewMarks(parts: List<PreviewPart>): List<TextMark> {
    var cursor = 0
    return parts.mapNotNull { part ->
        val start = cursor
        cursor += part.text.length
        if (part.hidden && cursor > start) TextMark(start, cursor, MarkStyle.HIDDEN) else null
    }
}

/**
 * Paints a blurred copy under the real text. [paint] must come from
 * [rememberSpoilerPaint], not from the on-screen text layout.
 * The layer or the pre-31 smear bitmap is recorded in the cache block.
 */
internal fun Modifier.drawSpoilerShadows(
    paint: SpoilerPaint,
    marks: List<TextMark>,
    ink: Color,
): Modifier = drawWithCache {
    val frame = recordSpoiler(this, paint, marks, ink)
    onDrawWithContent {
        if (frame.ready) {
            val layer = frame.layer
            val smear = frame.smear
            if (layer != null) drawLayer(layer) else if (smear != null) drawImage(smear)
        }
        drawContent()
    }
}

private class SpoilerFrame(
    val ready: Boolean,
    val layer: GraphicsLayer?,
    val smear: ImageBitmap?,
)

private fun recordSpoiler(
    scope: CacheDrawScope,
    paint: SpoilerPaint,
    marks: List<TextMark>,
    ink: Color,
): SpoilerFrame {
    val layout = paint.layout
    val width = scope.size.width.toInt()
    val height = scope.size.height.toInt()
    val cache = paint.cache
    if (layout == null || layout.layoutInput.text.isEmpty() || width <= 0 || height <= 0) {
        return SpoilerFrame(ready = false, layer = null, smear = null)
    }
    val same = cache.ready &&
        cache.layout === layout &&
        cache.width == width &&
        cache.height == height &&
        cache.ink == ink
    if (!same) {
        val radius = scope.spoilerRadius(layout)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            paint.layer.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
            paint.layer.record(
                density = scope,
                layoutDirection = scope.layoutDirection,
                size = IntSize(width, height),
            ) {
                drawText(layout)
            }
            cache.smear = null
        } else {
            cache.smear = renderSmear(
                density = scope,
                direction = scope.layoutDirection,
                layout = layout,
                marks = marks,
                color = ink,
                width = width,
                height = height,
                radiusPx = radius,
                reuse = cache.smear,
            )
        }
        cache.layout = layout
        cache.width = width
        cache.height = height
        cache.ink = ink
        cache.ready = true
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SpoilerFrame(ready = true, layer = paint.layer, smear = null)
    } else {
        SpoilerFrame(ready = cache.smear != null, layer = null, smear = cache.smear)
    }
}

private fun Density.spoilerRadius(layout: TextLayoutResult): Float {
    val font = layout.layoutInput.style.fontSize
    val fontPx = if (font == TextUnit.Unspecified) {
        layout.size.height / layout.lineCount.coerceAtLeast(1).toFloat()
    } else {
        font.toPx()
    }
    return (fontPx * HiddenText.BLUR_EM).coerceIn(12.dp.toPx(), 22.dp.toPx())
}

/**
 * API 26–30 has no blur effect. Sixteen offset copies are painted once into
 * a bitmap. Later frames blit that bitmap instead of calling drawText again.
 */
private fun renderSmear(
    density: Density,
    direction: LayoutDirection,
    layout: TextLayoutResult,
    marks: List<TextMark>,
    color: Color,
    width: Int,
    height: Int,
    radiusPx: Float,
    reuse: ImageBitmap?,
): ImageBitmap? {
    val path = expandedHiddenPath(layout, marks, radiusPx) ?: return null
    val bitmap = if (reuse != null && reuse.width == width && reuse.height == height) {
        reuse
    } else {
        ImageBitmap(width, height)
    }
    val canvas = Canvas(bitmap)
    canvas.drawRect(
        Rect(0f, 0f, width.toFloat(), height.toFloat()),
        Paint().apply { blendMode = BlendMode.Clear },
    )
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = direction,
        canvas = canvas,
        size = Size(width.toFloat(), height.toFloat()),
    ) {
        val strength = color.alpha.coerceIn(0.45f, 1f)
        clipPath(path) {
            val count = 16
            val distance = radiusPx * 0.85f
            for (index in 0 until count) {
                val angle = (Math.PI * 2.0 * index / count).toFloat()
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(cos(angle) * distance, sin(angle) * distance),
                    alpha = 0.2f * strength,
                )
            }
        }
    }
    return bitmap
}

private fun expandedHiddenPath(layout: TextLayoutResult, marks: List<TextMark>, outset: Float): Path? {
    val textLength = layout.layoutInput.text.length
    if (textLength == 0) return null
    val path = Path()
    var any = false
    marks.filter { it.style == MarkStyle.HIDDEN }.forEach { mark ->
        val start = mark.start.coerceIn(0, textLength)
        val end = mark.end.coerceIn(0, textLength)
        if (end <= start) return@forEach
        val last = (end - 1).coerceAtMost(textLength - 1)
        val startLine = layout.getLineForOffset(start.coerceAtMost(last))
        val endLine = layout.getLineForOffset(last)
        for (line in startLine..endLine) {
            val from = max(start, layout.getLineStart(line))
            val to = min(end, layout.getLineEnd(line, visibleEnd = true))
            if (to <= from) continue
            val left = layout.getHorizontalPosition(from, true)
            val right = layout.getHorizontalPosition(to, true)
            val barLeft = min(left, right)
            val barRight = max(left, right)
            if (barRight - barLeft < 0.5f) continue
            val top = layout.getLineTop(line)
            val bottom = layout.getLineBottom(line)
            if (bottom - top < 0.5f) continue
            path.addRect(
                Rect(
                    barLeft - outset,
                    top - outset * 0.35f,
                    barRight + outset,
                    bottom + outset * 0.35f,
                ),
            )
            any = true
        }
    }
    return if (any) path else null
}

@Composable
internal fun SpoilerInline(
    text: String,
    marks: List<TextMark>,
    revealed: Boolean,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        SpoilerText(
            text = buildAnnotatedString {
                append(text)
                if (!revealed) {
                    marks.forEach { mark ->
                        if (mark.style != MarkStyle.HIDDEN) return@forEach
                        val start = mark.start.coerceIn(0, length)
                        val end = mark.end.coerceIn(0, length)
                        if (end > start) addStyle(SpanStyle(color = Color.Transparent), start, end)
                    }
                }
            },
            marks = marks,
            revealed = revealed,
            color = color,
            style = style,
            maxLines = Int.MAX_VALUE,
        )
    }
}
