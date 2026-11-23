package com.alekseevich47.notes.ui.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RecordSheet(
    visible: Boolean,
    phase: MicPhase,
    text: StateFlow<String>,
    modifier: Modifier = Modifier,
) {
    val textState = text.collectAsStateWithLifecycle()
    RecordSheet(
        visible = visible,
        phase = phase,
        text = { textState.value },
        modifier = modifier,
    )
}

@Composable
fun RecordSheet(
    visible: Boolean,
    phase: MicPhase,
    text: () -> String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it / 4 } + fadeIn(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 112.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                when (phase) {
                    MicPhase.PROCESSING -> "Обработка"
                    MicPhase.DONE -> "Готово"
                    else -> "Запись"
                },
                style = MaterialTheme.typography.titleMedium,
                fontFamily = NotesFont,
                color = NotesPurple,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (phase) {
                    MicPhase.PROCESSING -> "Наводим пунктуацию и регистр"
                    MicPhase.RECORDING -> "Нажмите микрофон ещё раз, чтобы остановить"
                    else -> ""
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotesFont,
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                LiveTranscript(text)
            }
        }
    }
}

@Composable
private fun LiveTranscript(text: () -> String) {
    val transcript = text()
    Text(
        text = transcript.ifBlank { "Слушаю…" },
        style = MaterialTheme.typography.headlineMedium,
        fontFamily = NotesFont,
        color = if (transcript.isBlank()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}
