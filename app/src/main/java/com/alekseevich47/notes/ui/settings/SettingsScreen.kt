package com.alekseevich47.notes.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import com.alekseevich47.notes.data.download.DownloadStatus
import com.alekseevich47.notes.data.download.ModelCardState
import com.alekseevich47.notes.data.secure.NotesPrefs
import com.alekseevich47.notes.domain.ThemeMode
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.ui.components.PinDialog
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple

@Composable
fun SettingsScreen(
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val pin by viewModel.pinHash.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val modelEnabled by viewModel.modelEnabled.collectAsStateWithLifecycle()
    val warmup by viewModel.warmupOnBoot.collectAsStateWithLifecycle()
    val warmupAvailable = viewModel.warmupAvailable
    val recognizer by viewModel.recognizer.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pinMode by remember { mutableStateOf<PinMode?>(null) }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinLockedUntil by remember { mutableLongStateOf(0L) }
    var changeStep by remember { mutableStateOf(ChangeStep.CURRENT) }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                SettingsCopy.WARMUP_TITLE,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (warmup && warmupAvailable) {
                            Modifier.clickable(onClick = viewModel::openWarmupPowerSettings)
                        } else {
                            Modifier
                        },
                    ),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = NotesFont,
            )
            Switch(
                checked = warmup && warmupAvailable,
                onCheckedChange = viewModel::setWarmupOnBoot,
                enabled = warmupAvailable,
                colors = SwitchDefaults.colors(checkedTrackColor = NotesPurple),
            )
        }
        Text(
            if (warmupAvailable) SettingsCopy.WARMUP_SUBTITLE else SettingsCopy.WARMUP_UNAVAILABLE,
            modifier = Modifier.then(
                if (warmup && warmupAvailable) {
                    Modifier.clickable(onClick = viewModel::openWarmupPowerSettings)
                } else {
                    Modifier
                },
            ),
            fontFamily = NotesFont,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(22.dp))
        SectionLabel(SettingsCopy.RECOGNIZER_TITLE)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChip(SettingsCopy.RECOGNIZER_TONE, recognizer == NotesPrefs.RECOGNIZER_TONE) {
                viewModel.setRecognizer(NotesPrefs.RECOGNIZER_TONE)
            }
            ThemeChip(SettingsCopy.RECOGNIZER_SYSTEM, recognizer == NotesPrefs.RECOGNIZER_SYSTEM) {
                viewModel.setRecognizer(NotesPrefs.RECOGNIZER_SYSTEM)
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("Модели распознавания")
        Text(
            SettingsCopy.MODELS,
            fontFamily = NotesFont,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        models.forEach { card ->
            val enabled = modelEnabled[card.kind] != false
            ModelCard(
                card = card,
                enabled = enabled,
                onDownload = { viewModel.startDownload(card.kind) },
                onPause = { viewModel.pauseDownload(card.kind) },
                onStop = { viewModel.stopDownload(card.kind) },
                onToggle = { viewModel.setModelEnabled(card.kind, !enabled) },
                onDelete = { viewModel.deleteModel(card.kind) },
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("PIN")
        Text(
            SettingsCopy.PIN,
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
                changeStep = ChangeStep.CURRENT
                viewModel.clearPinVerified()
                pinLockedUntil = viewModel.pinLockDeadline()
                pinMode = PinMode.CHANGE
            }
            ActionText("Удалить PIN") {
                pinError = null
                pinLockedUntil = viewModel.pinLockDeadline()
                pinMode = PinMode.CLEAR
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("Loomixx")
        Text(
            SettingsCopy.versionLabel(BuildConfig.VERSION_NAME),
            fontFamily = NotesFont,
            fontWeight = FontWeight.SemiBold,
        )
        ContactLink(
            icon = {
                Icon(
                    Icons.Outlined.Email,
                    contentDescription = null,
                    tint = NotesPurple,
                    modifier = Modifier.size(18.dp),
                )
            },
            label = SettingsCopy.EMAIL,
        ) {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${SettingsCopy.EMAIL}")))
        }
        ContactLink(
            icon = {
                Icon(
                    TelegramPlane,
                    contentDescription = null,
                    tint = NotesPurple,
                    modifier = Modifier.size(18.dp),
                )
            },
            label = SettingsCopy.TELEGRAM_HANDLE,
        ) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SettingsCopy.TELEGRAM_URL)))
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("Диагностика")
        ActionText("Отправить лог") {
            runCatching { AppLog.share(context) }
                .onFailure { onToast("Не удалось отправить лог") }
        }
    }

    val mode = pinMode
    if (mode != null) {
        val title = when (mode) {
            PinMode.CREATE -> if (firstPin == null) "Новый PIN" else "Повторите PIN"
            PinMode.CHANGE -> when (changeStep) {
                ChangeStep.CURRENT -> "Текущий PIN"
                ChangeStep.NEW -> "Новый PIN"
                ChangeStep.REPEAT -> "Повторите PIN"
            }
            PinMode.CLEAR -> "Текущий PIN"
        }
        val lockApplies = mode == PinMode.CLEAR || (mode == PinMode.CHANGE && changeStep == ChangeStep.CURRENT)
        PinDialog(
            title = title,
            subtitle = "4 цифры",
            error = pinError,
            lockedUntilElapsed = if (lockApplies) pinLockedUntil else 0L,
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
                                SettingsViewModel.PinChange.REJECTED -> {
                                    pinError = "Неверный PIN"
                                }
                            }
                        }
                    }
                    PinMode.CHANGE -> when (changeStep) {
                        ChangeStep.CURRENT -> {
                            if (viewModel.verifyCurrentPin(value)) {
                                changeStep = ChangeStep.NEW
                                pinError = null
                                pinLockedUntil = 0L
                            } else {
                                pinError = "Неверный PIN"
                                pinLockedUntil = viewModel.pinLockDeadline()
                            }
                        }
                        ChangeStep.NEW -> {
                            firstPin = value
                            changeStep = ChangeStep.REPEAT
                            pinError = null
                        }
                        ChangeStep.REPEAT -> {
                            when (viewModel.submitNewPin(firstPin.orEmpty(), value)) {
                                SettingsViewModel.PinChange.SAVED -> {
                                    pinMode = null
                                    onToast("PIN сохранён")
                                }
                                SettingsViewModel.PinChange.MISMATCH -> {
                                    firstPin = null
                                    changeStep = ChangeStep.NEW
                                    pinError = "Не совпало, начните снова"
                                }
                                SettingsViewModel.PinChange.RESET -> {
                                    pinMode = null
                                    onToast("PIN сброшен")
                                }
                                SettingsViewModel.PinChange.REJECTED -> {
                                    firstPin = null
                                    changeStep = ChangeStep.CURRENT
                                    pinError = "Неверный PIN"
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
                            pinLockedUntil = viewModel.pinLockDeadline()
                        }
                    }
                }
            },
            onDismiss = {
                pinMode = null
                viewModel.clearPinVerified()
            },
        )
    }
}

private enum class PinMode { CREATE, CHANGE, CLEAR }

private enum class ChangeStep { CURRENT, NEW, REPEAT }

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
    enabled: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onToggle: () -> Unit,
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (card.status) {
                DownloadStatus.NOT_INSTALLED, DownloadStatus.FAILED -> RowAction("Скачать", onDownload)
                DownloadStatus.DOWNLOADING -> {
                    RowAction("Пауза", onPause)
                    RowAction("Стоп", onStop)
                }
                DownloadStatus.PAUSED -> {
                    RowAction("Продолжить", onDownload)
                    RowAction("Стоп", onStop)
                }
                DownloadStatus.INSTALLED -> {
                    RowAction(SettingsCopy.modelSwitchLabel(enabled), onToggle)
                    RowAction("Удалить", onDelete)
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
private fun RowScope.RowAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .alignByBaseline()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        color = NotesPurple,
        fontFamily = NotesFont,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ContactLink(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(label, color = NotesPurple, fontFamily = NotesFont)
    }
}

private val TelegramPlane: ImageVector = ImageVector.Builder(
    name = "TelegramPlane",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M9.78,18.65l0.28,-4.23l7.68,-6.92c0.34,-0.31 -0.07,-0.46 -0.52,-0.19L7.74,13.3L3.64,12c-0.88,-0.25 -0.89,-0.86 0.2,-1.3l15.97,-6.16c0.73,-0.33 1.43,0.18 1.15,1.3l-2.72,12.81c-0.19,0.91 -0.74,1.13 -1.5,0.7L12.6,16.3l-1.99,1.93c-0.23,0.23 -0.42,0.42 -0.83,0.42z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    )
}.build()
