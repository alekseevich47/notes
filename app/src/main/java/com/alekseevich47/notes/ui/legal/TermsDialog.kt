package com.alekseevich47.notes.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alekseevich47.notes.ui.theme.NotesFont

@Composable
fun TermsDialog(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth(),
        ) {
            val scroll = rememberScrollState()
            val plate = MaterialTheme.colorScheme.surface
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.88f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(plate)
                    .padding(22.dp),
            ) {
                Text(
                    "Правила использования",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = NotesFont,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .scrollEdgeFades(scroll, plate)
                        .verticalScroll(scroll),
                ) {
                    Text(
                        "Правила использования приложения «Заметки»",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = NotesFont,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        TermsOfUse.EDITION,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = NotesFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TermsOfUse.sections.forEach { section ->
                        Text(
                            section.title,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = NotesFont,
                            fontWeight = FontWeight.Bold,
                        )
                        section.paragraphs.forEach { paragraph ->
                            Text(
                                paragraph,
                                modifier = Modifier.padding(bottom = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = NotesFont,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                LegalActionButton("Закрыть", onClose)
            }
        }
    }
}
