package com.alekseevich47.notes.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.player.PlaybackSnapshot
import com.alekseevich47.notes.player.PlayerUi
import com.alekseevich47.notes.ui.theme.NotesFont

@Composable
fun MiniPlayer(
    state: PlayerUi,
    active: Boolean,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!active && state.noteId == null) return
    // A new note must not keep the previous drag position.
    key(state.noteId) {
        var dragging by remember { mutableStateOf(false) }
        var drag by remember { mutableFloatStateOf(0f) }
        val fraction = PlaybackSnapshot.fraction(state.positionMs, state.durationMs)
        Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (state.playing) "Пауза" else "Слушать",
                    )
                }
                Text(
                    PlaybackSnapshot.clock(state.positionMs) + "  /  " + PlaybackSnapshot.clock(state.durationMs),
                    fontFamily = NotesFont,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, contentDescription = "Поделиться")
                }
            }
            Slider(
                value = if (dragging) drag else fraction,
                onValueChange = {
                    dragging = true
                    drag = it
                },
                onValueChangeFinished = {
                    onSeek(drag)
                    dragging = false
                },
            )
        }
    }
}
