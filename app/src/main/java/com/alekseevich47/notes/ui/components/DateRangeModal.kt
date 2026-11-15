package com.alekseevich47.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple
import com.alekseevich47.notes.ui.theme.NotesPurpleSoft
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class DayRange(val start: LocalDate, val end: LocalDate)

@Composable
fun DateRangeModal(
    initial: DayRange?,
    onConfirm: (DayRange) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(initial?.start ?: LocalDate.now())) }
    var start by remember { mutableStateOf(initial?.start) }
    var end by remember { mutableStateOf(initial?.end) }
    var drag by remember { mutableFloatStateOf(0f) }
    val locale = remember { Locale.forLanguageTag("ru") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp)
                .pointerInput(month) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag > 48f) month = month.minusMonths(1)
                            if (drag < -48f) month = month.plusMonths(1)
                            drag = 0f
                        },
                        onHorizontalDrag = { _, amount -> drag += amount },
                    )
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    monthTitle(month, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = NotesFont,
                )
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Назад")
                }
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Вперёд")
                }
            }
            Text(
                hint(start, end),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotesFont,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс").forEach { day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = NotesFont,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val cells = remember(month) { monthCells(month) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        DayCell(
                            day = day,
                            start = start,
                            end = end,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val picked = day ?: return@DayCell
                                when {
                                    start == null || (start != null && end != null) -> {
                                        start = picked
                                        end = null
                                    }
                                    picked.isBefore(start) -> {
                                        end = start
                                        start = picked
                                    }
                                    else -> end = picked
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Сбросить",
                    modifier = Modifier.clickable {
                        onClear()
                        onDismiss()
                    }.padding(8.dp),
                    fontFamily = NotesFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Показать",
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (start == null) MaterialTheme.colorScheme.surfaceVariant else NotesPurple)
                        .clickable(enabled = start != null) {
                            val from = start ?: return@clickable
                            onConfirm(DayRange(from, end ?: from))
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    color = if (start == null) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.White,
                    fontFamily = NotesFont,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate?,
    start: LocalDate?,
    end: LocalDate?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val inRange = day != null && start != null && end != null && !day.isBefore(start) && !day.isAfter(end)
    val endpoint = day != null && (day == start || day == end)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(if (endpoint) CircleShape else RoundedCornerShape(10.dp))
            .background(
                when {
                    endpoint -> NotesPurple
                    inRange -> NotesPurpleSoft
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .clickable(enabled = day != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null) {
            Text(
                day.dayOfMonth.toString(),
                fontFamily = NotesFont,
                fontWeight = if (endpoint) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    endpoint -> androidx.compose.ui.graphics.Color.White
                    day == LocalDate.now() -> NotesPurple
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value + 6) % 7
    val days = month.lengthOfMonth()
    return buildList {
        repeat(lead) { add(null) }
        for (day in 1..days) add(month.atDay(day))
        while (size % 7 != 0) add(null)
    }
}

private fun monthTitle(month: YearMonth, locale: Locale): String {
    val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    val titled = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    return "$titled ${month.year}"
}

private fun hint(start: LocalDate?, end: LocalDate?): String {
    if (start == null) return "Выберите день или период"
    if (end == null || end == start) return formatDay(start)
    return formatDay(start) + "  —  " + formatDay(end)
}

fun formatDay(day: LocalDate): String {
    val locale = Locale.forLanguageTag("ru")
    val month = day.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replace(".", "")
    return "${day.dayOfMonth} $month"
}
