package com.alekseevich47.notes.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple

@Composable
internal fun InfoDialog(
    notice: InfoNotice,
    onAction: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (notice.dismissible) onAction() },
        properties = DialogProperties(
            dismissOnBackPress = notice.dismissible,
            dismissOnClickOutside = notice.dismissible,
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
                    notice.title,
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
                        notice.body,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = NotesFont,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                notice.footer?.let { footer ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        footer,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = NotesFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(18.dp))
                LegalActionButton(notice.action, onAction)
            }
        }
    }
}

@Composable
internal fun LegalActionButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NotesPurple)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        color = Color.White,
        fontFamily = NotesFont,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
    )
}
