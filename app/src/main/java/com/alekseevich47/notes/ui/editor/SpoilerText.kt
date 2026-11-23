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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import androidx.compose.ui.unit.IntSize
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
    val annotated = remember(parts) { previewAnnotated(parts) }
    val marks = remember(parts) { previewMarks(parts) }
    SpoilerText(
        text = annotated,
        marks = marks,
        revealed = false,
        color = color,
        style = style,
        modifier = modifier,
        maxLines = maxLines,
    )
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
    val spoiler = rememberSpoilerPaint(
        text = text.text,
        marks = marks,
        style = style,
        ink = color,
        widthPx = if (locked) layout?.size?.width ?: 0 else 0,
        maxLines = maxLines,
    )
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawSpoilerShadows(spoiler, marks, color)
            drawContent()
        },
    )
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
)

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
    return SpoilerPaint(layout, layer)
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
 */
internal fun DrawScope.drawSpoilerShadows(paint: SpoilerPaint, marks: List<TextMark>, color: Color) {
    val layout = paint.layout ?: return
    if (layout.layoutInput.text.isEmpty()) return
    if (size.width <= 0f || size.height <= 0f) return
    val radius = spoilerRadius(layout)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        drawBlurred(paint.layer, layout, radius)
    } else {
        drawSmear(layout, marks, color, radius)
    }
}

private fun DrawScope.spoilerRadius(layout: TextLayoutResult): Float {
    val font = layout.layoutInput.style.fontSize
    val fontPx = if (font == TextUnit.Unspecified) {
        layout.size.height / layout.lineCount.coerceAtLeast(1).toFloat()
    } else {
        font.toPx()
    }
    return (fontPx * HiddenText.BLUR_EM).coerceIn(12.dp.toPx(), 22.dp.toPx())
}

private fun DrawScope.drawBlurred(layer: GraphicsLayer, layout: TextLayoutResult, radiusPx: Float) {
    layer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Decal)
    layer.record(
        density = this,
        layoutDirection = layoutDirection,
        size = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
    ) {
        drawText(layout)
    }
    drawLayer(layer)
}

/**
 * API 26–30 has no blur effect. Offset copies of the ink-only layout,
 * clipped to a box wider than the glyphs, average into a blob. A tight clip
 * would rebuild the letter from the overlapping part of each copy.
 */
private fun DrawScope.drawSmear(
    layout: TextLayoutResult,
    marks: List<TextMark>,
    color: Color,
    radiusPx: Float,
) {
    val path = expandedHiddenPath(layout, marks, radiusPx) ?: return
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
