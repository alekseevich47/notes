package com.alekseevich47.notes.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alekseevich47.notes.data.download.ModelDownloader
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.domain.ModelKind
import com.alekseevich47.notes.domain.ThemeMode
import com.alekseevich47.notes.ui.components.NotesDialog
import com.alekseevich47.notes.ui.components.PinDialog
import com.alekseevich47.notes.ui.theme.NotesMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: NotesRepository,
    private val downloader: ModelDownloader,
) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.settings.value)
    val models = downloader.states

    fun setTheme(mode: ThemeMode) = viewModelScope.launch {
        repository.updateSettings { it.copy(themeMode = mode) }
    }

    fun setGiga(enabled: Boolean) = viewModelScope.launch {
        repository.updateSettings { it.copy(gigaFinalizeEnabled = enabled) }
    }

    fun setPin(pin: String) = viewModelScope.launch {
        repository.updateSettings { it.copy(pinHash = PinHasher.hashPin(pin)) }
    }

    fun clearPin() = viewModelScope.launch {
        repository.updateSettings { it.copy(pinHash = null) }
    }

    fun download(kind: ModelKind) = viewModelScope.launch {
        when (kind) {
            ModelKind.T_ONE -> downloader.downloadToneBundle()
            ModelKind.GIGA_AM -> downloader.downloadGigaBundle()
        }
    }

    fun verifyPin(pin: String): Boolean = PinHasher.matchesPin(pin, settings.value.pinHash)
    fun verifyReset(code: String): Boolean = PinHasher.matchesServiceReset(code)
}

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSetPin by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 120.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        Text("Модели распознавания", style = MaterialTheme.typography.titleMedium)
        Text(
            "В APK уже встроен sherpa-onnx. Скачайте T-one (быстрый стрим) и GigaAM (пунктуация, цифры, регистр).",
            color = NotesMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        models.forEach { state ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text("${state.kind.label} · ~${state.kind.approxMb} МБ")
                Text(
                    when {
                        state.installed -> "Установлено"
                        state.downloading -> "Загрузка…"
                        else -> "Не установлено"
                    },
                    color = NotesMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.downloading) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (!state.installed && !state.downloading) {
                    Button(
                        onClick = { viewModel.download(state.kind) },
                        modifier = Modifier.padding(top = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Скачать") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Финализация GigaAM", modifier = Modifier.weight(1f))
            Switch(checked = settings.gigaFinalizeEnabled, onCheckedChange = viewModel::setGiga)
        }

        Spacer(Modifier.height(20.dp))
        Text("Тема", style = MaterialTheme.typography.titleMedium)
        Row {
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.SYSTEM -> "Система"
                    ThemeMode.LIGHT -> "Светлая"
                    ThemeMode.DARK -> "Тёмная"
                }
                TextButton(onClick = { viewModel.setTheme(mode) }) {
                    Text(if (settings.themeMode == mode) "• $label" else label)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Пароль скрытого текста", style = MaterialTheme.typography.titleMedium)
        Text(
            if (settings.pinHash == null) "PIN не задан" else "PIN установлен",
            color = NotesMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row {
            Button(onClick = { showSetPin = true }, shape = RoundedCornerShape(14.dp)) {
                Text(if (settings.pinHash == null) "Задать PIN" else "Сменить PIN")
            }
            TextButton(onClick = { showReset = true }) { Text("Сброс") }
        }

        Spacer(Modifier.height(28.dp))
        Text("Разработчик", style = MaterialTheme.typography.titleMedium)
        Text("Loomixx", style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:loomixx.dev@yandex.ru")))
        }) { Text("loomixx.dev@yandex.ru") }
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/aalekseevich47")))
        }) { Text("Telegram @aalekseevich47") }
    }

    if (showSetPin) {
        PinDialog(
            title = "Новый PIN (4 цифры)",
            onDismiss = { showSetPin = false },
            onSubmit = {
                viewModel.setPin(it)
                showSetPin = false
                toast = "PIN сохранён"
            },
        )
    }
    if (showReset) {
        PinDialog(
            title = "Сервисный сброс",
            onDismiss = { showReset = false },
            onSubmit = {
                if (viewModel.verifyReset(it)) {
                    viewModel.clearPin()
                    showReset = false
                    toast = "PIN сброшен"
                } else {
                    toast = "Неверный код"
                }
            },
        )
    }
    toast?.let {
        NotesDialog(title = it, onDismiss = { toast = null }, confirmLabel = "OK", onConfirm = { toast = null }) {}
    }
}
