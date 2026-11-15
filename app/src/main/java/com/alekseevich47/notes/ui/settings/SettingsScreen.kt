package com.alekseevich47.notes.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alekseevich47.notes.data.download.DownloadStatus
import com.alekseevich47.notes.data.download.ModelCardState
import com.alekseevich47.notes.domain.ThemeMode
import com.alekseevich47.notes.ui.components.PinDialog
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple

@Composable
fun SettingsScreen(
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val theme by viewModel.theme.collectAsState()
    val pin by viewModel.pinHash.collectAsState()
    val models by viewModel.models.collectAsState()
    val context = LocalContext.current
    var pinMode by remember { mutableStateOf<PinMode?>(null) }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 130.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.displaySmall, fontFamily = NotesFont)
        Spacer(Modifier.height(18.dp))
        SectionLabel("Тема")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChip("Как в системе", theme == ThemeMode.SYSTEM) { viewModel.setTheme(ThemeMode.SYSTEM) }
            ThemeChip("Светлая", theme == ThemeMode.LIGHT) { viewModel.setTheme(ThemeMode.LIGHT) }
            ThemeChip("Тёмная", theme == ThemeMode.DARK) { viewModel.setTheme(ThemeMode.DARK) }
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("Модели распознавания")
        Text(
            "Без моделей работает системное распознавание ru-RU. T-one даёт текст во время записи, GigaAM оформляет финал.",
            fontFamily = NotesFont,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        models.forEach { card ->
            ModelCard(
                card = card,
                onDownload = { viewModel.startDownload(card.kind) },
                onPause = { viewModel.pauseDownload(card.kind) },
                onStop = { viewModel.stopDownload(card.kind) },
                onDelete = { viewModel.deleteModel(card.kind) },
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("PIN")
        Text(
            "Четыре цифры открывают скрытый текст. Сервисный сброс сравнивается только по хэшу.",
            fontFamily = NotesFont,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        if (pin.isNullOrBlank()) {
            ActionText("Задать PIN") {
                firstPin = null
                pinError = null
                pinMode = PinMode.CREATE
            }
        } else {
            ActionText("Сменить PIN") {
                firstPin = null
                pinError = null
                pinMode = PinMode.CREATE
            }
            ActionText("Удалить PIN") {
                pinError = null
                pinMode = PinMode.CLEAR
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("Loomixx")
        Text("Заметки 1.0.0", fontFamily = NotesFont, fontWeight = FontWeight.SemiBold)
        LinkText("loomixx.dev@yandex.ru") {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:loomixx.dev@yandex.ru")))
        }
        LinkText("Telegram") {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/aalekseevich47")))
        }
    }

    val mode = pinMode
    if (mode != null) {
        val title = when (mode) {
            PinMode.CREATE -> if (firstPin == null) "Новый PIN" else "Повторите PIN"
            PinMode.CLEAR -> "Текущий PIN"
        }
        PinDialog(
            title = title,
            subtitle = "4 цифры",
            error = pinError,
            onSubmit = { value ->
                when (mode) {
                    PinMode.CREATE -> {
                        val draft = firstPin
                        if (draft == null) {
                            firstPin = value
                            pinError = null
                        } else {
                            when (viewModel.submitNewPin(draft, value)) {
                                SettingsViewModel.PinChange.SAVED -> {
                                    pinMode = null
                                    onToast("PIN сохранён")
                                }
                                SettingsViewModel.PinChange.MISMATCH -> {
                                    firstPin = null
                                    pinError = "Не совпало, начните снова"
                                }
                                SettingsViewModel.PinChange.RESET -> {
                                    pinMode = null
                                    onToast("PIN сброшен")
                                }
                            }
                        }
                    }
                    PinMode.CLEAR -> {
                        if (viewModel.clearWith(value)) {
                            pinMode = null
                            onToast("PIN удалён")
                        } else {
                            pinError = "Неверный PIN"
                        }
                    }
                }
            },
            onDismiss = { pinMode = null },
        )
    }
}

private enum class PinMode { CREATE, CLEAR }

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontFamily = NotesFont, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) NotesPurple else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
        fontFamily = NotesFont,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ModelCard(
    card: ModelCardState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(14.dp),
    ) {
        Text(card.kind.title, fontFamily = NotesFont, fontWeight = FontWeight.SemiBold)
        Text(card.kind.detail, fontFamily = NotesFont, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (card.status == DownloadStatus.DOWNLOADING || card.status == DownloadStatus.PAUSED) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { card.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${(card.progress * 100).toInt()}%", fontFamily = NotesFont, modifier = Modifier.padding(top = 4.dp))
        }
        card.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontFamily = NotesFont, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            when (card.status) {
                DownloadStatus.NOT_INSTALLED, DownloadStatus.FAILED -> ActionText("Скачать", onDownload)
                DownloadStatus.DOWNLOADING -> {
                    ActionText("Пауза", onPause)
                    ActionText("Стоп", onStop)
                }
                DownloadStatus.PAUSED -> {
                    ActionText("Продолжить", onDownload)
                    ActionText("Стоп", onStop)
                }
                DownloadStatus.INSTALLED -> {
                    Text("Установлено", fontFamily = NotesFont, color = NotesPurple, fontWeight = FontWeight.SemiBold)
                    ActionText("Удалить", onDelete)
                }
            }
        }
    }
}

@Composable
private fun ActionText(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        color = NotesPurple,
        fontFamily = NotesFont,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LinkText(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        color = NotesPurple,
        fontFamily = NotesFont,
    )
}
