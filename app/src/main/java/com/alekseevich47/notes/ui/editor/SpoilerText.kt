package com.alekseevich47.notes.ui.editor

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.PreviewPart
import com.alekseevich47.notes.domain.TextMark
import kotlin.math.max
import kotlin.math.min

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
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            val result = layout
            if (locked && result != null) drawSpoilerShadows(result, marks, color)
            drawContent()
        },
    )
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
 * Locked hidden glyphs are filled transparent by the span style. This paints
 * several offset low-alpha copies of the same layout, clipped to those spans.
 */
fun DrawScope.drawSpoilerShadows(layout: TextLayoutResult, marks: List<TextMark>, color: Color) {
    val textLength = layout.layoutInput.text.length
    if (textLength == 0) return
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
            path.addRect(Rect(barLeft, top, barRight, bottom))
            any = true
        }
    }
    if (!any) return
    clipPath(path) {
        val offsets = HiddenText.shadowOffsetsDp
        val alphas = HiddenText.shadowAlphas
        offsets.forEachIndexed { index, (dxDp, dyDp) ->
            val alpha = alphas.getOrElse(index) { 0.1f }
            drawText(
                textLayoutResult = layout,
                color = color.copy(alpha = alpha),
                topLeft = Offset(dxDp.dp.toPx(), dyDp.dp.toPx()),
            )
        }
    }
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
