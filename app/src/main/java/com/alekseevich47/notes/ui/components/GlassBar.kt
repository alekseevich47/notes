package com.alekseevich47.notes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.ui.theme.NotesPurple
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.StateFlow

enum class ShellTab { NOTES, SETTINGS }

@Composable
fun GlassBottomBar(
    hazeState: HazeState,
    tab: ShellTab,
    phase: MicPhase,
    level: StateFlow<Float>,
    onTab: (ShellTab) -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val levelState = level.collectAsStateWithLifecycle()
    GlassBottomBar(
        hazeState = hazeState,
        tab = tab,
        phase = phase,
        level = { levelState.value },
        onTab = onTab,
        onMic = onMic,
        modifier = modifier,
    )
}

@Composable
fun GlassBottomBar(
    hazeState: HazeState,
    tab: ShellTab,
    phase: MicPhase,
    level: () -> Float,
    onTab: (ShellTab) -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(104.dp)
            .padding(horizontal = 18.dp),
    ) {
        LiquidGlassPlate(
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(62.dp),
            shape = RoundedCornerShape(31.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(Icons.Outlined.StickyNote2, "Заметки", tab == ShellTab.NOTES, Modifier.weight(1f)) {
                    onTab(ShellTab.NOTES)
                }
                Spacer(Modifier.width(92.dp))
                BarIcon(Icons.Outlined.Settings, "Настройки", tab == ShellTab.SETTINGS, Modifier.weight(1f)) {
                    onTab(ShellTab.SETTINGS)
                }
            }
        }
        MicButton(
            phase = phase,
            level = level,
            onClick = onMic,
            modifier = Modifier
                .align(Alignment.Center)
                .size(88.dp),
        )
    }
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (selected) NotesPurple else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
