package com.alekseevich47.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotesDateRangePickerModal(
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?) -> Unit,
) {
    var from by remember { mutableStateOf(LocalDate.now().minusDays(7)) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text("Период", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text("С: ${from.format(fmt)}")
            Text("По: ${to.format(fmt)}")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { from = from.minusDays(1) }) { Text("− от") }
                TextButton(onClick = { from = from.plusDays(1) }) { Text("+ от") }
                TextButton(onClick = { to = to.minusDays(1) }) { Text("− до") }
                TextButton(onClick = { to = to.plusDays(1) }) { Text("+ до") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    onConfirm(null, null)
                    onDismiss()
                }) { Text("Сбросить") }
                TextButton(onClick = {
                    val zone = ZoneId.systemDefault()
                    val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
                    val toMs = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                    onConfirm(fromMs, toMs)
                    onDismiss()
                }) { Text("Применить") }
            }
        }
    }
}
