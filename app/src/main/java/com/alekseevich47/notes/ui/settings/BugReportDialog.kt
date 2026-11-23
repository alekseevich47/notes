package com.alekseevich47.notes.ui.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.alekseevich47.notes.data.report.BugReportForm
import com.alekseevich47.notes.data.report.BugReportMail
import com.alekseevich47.notes.data.report.BugReportMailer
import com.alekseevich47.notes.data.report.BugReportValidity
import com.alekseevich47.notes.ui.legal.TermsDialog
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NO_MAIL_APP =
    "Не найдено почтовое приложение. Напишите на ${SettingsCopy.EMAIL}"

private val shotSaver = listSaver<List<Uri>, String>(
    save = { value -> value.map { it.toString() } },
    restore = { saved -> saved.map { Uri.parse(it) } },
)

@Composable
fun BugReportDialog(
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var withLog by rememberSaveable { mutableStateOf(true) }
    var shots by rememberSaveable(stateSaver = shotSaver) { mutableStateOf(emptyList<Uri>()) }
    var thumbs by remember { mutableStateOf<Map<Uri, ImageBitmap>>(emptyMap()) }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var showTerms by remember { mutableStateOf(false) }
    val targetPx = with(LocalDensity.current) { 128.dp.roundToPx() }.coerceAtLeast(1)
    val formScroll = rememberScrollState()
    val shotScroll = rememberScrollState()
    val canSend = !sending &&
        BugReportForm(name, description).validate() == BugReportValidity.ok

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
    ) { picked ->
        if (picked.isEmpty()) return@rememberLauncherForActivityResult
        shots = mergeShots(shots, picked)
    }

    LaunchedEffect(shots, targetPx) {
        val keep = LinkedHashMap<Uri, ImageBitmap>(shots.size)
        val missing = ArrayList<Uri>()
        for (uri in shots) {
            val ready = thumbs[uri]
            if (ready != null) keep[uri] = ready else missing.add(uri)
        }
        if (missing.isNotEmpty()) {
            val decoded = withContext(Dispatchers.IO) {
                val out = ArrayList<Pair<Uri, ImageBitmap>>(missing.size)
                for (uri in missing) {
                    ensureActive()
                    val bitmap = BugReportMailer.decodeBitmap(appContext, uri, targetPx) ?: continue
                    out.add(uri to bitmap.asImageBitmap())
                }
                out
            }
            for ((uri, image) in decoded) keep[uri] = image
        }
        thumbs = keep
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    Dialog(
        onDismissRequest = { if (!sending) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = !sending,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.statusBars
                        .union(WindowInsets.navigationBars)
                        .union(WindowInsets.ime),
                )
                .padding(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Сообщить об ошибке",
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = NotesFont,
                    )
                    IconButton(onClick = onDismiss, enabled = !sending) {
                        Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(formScroll),
                ) {
                    FieldLabel("Ваше имя")
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = name,
                        onValueChange = { value ->
                            name = oneLine(value, BugReportMail.NAME_MAX)
                            sendError = null
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = NotesFont,
                        ),
                        cursorBrush = SolidColor(NotesPurple),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = fieldModifier(),
                        decorationBox = { inner ->
                            Box {
                                if (name.isEmpty()) {
                                    Text(
                                        "Как к вам обращаться",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = NotesFont,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Что случилось")
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = description,
                        onValueChange = { value ->
                            description = if (value.length <= BugReportMail.TEXT_MAX) {
                                value
                            } else {
                                value.take(BugReportMail.TEXT_MAX)
                            }
                            sendError = null
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = NotesFont,
                        ),
                        cursorBrush = SolidColor(NotesPurple),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        modifier = fieldModifier().heightIn(min = 120.dp),
                        decorationBox = { inner ->
                            Box {
                                if (description.isEmpty()) {
                                    Text(
                                        "Опишите, что вы делали и что пошло не так",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = NotesFont,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    val counted = description.trim().length
                    Text(
                        "$counted / ${BugReportMail.TEXT_MAX}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = NotesFont,
                        color = if (description.isNotEmpty() && counted < BugReportMail.TEXT_MIN) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Скриншоты (по желанию)")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(shotScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        shots.forEach { uri ->
                            key(uri) {
                                ShotThumb(
                                    image = thumbs[uri],
                                    onRemove = {
                                        shots = shots.filterNot { it == uri }
                                        sendError = null
                                    },
                                )
                            }
                        }
                        val room = shots.size < BugReportMail.MAX_SHOTS
                        Text(
                            "Прикрепить",
                            modifier = Modifier
                                .alpha(if (room && !sending) 1f else 0.4f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(
                                    enabled = room && !sending,
                                    role = Role.Button,
                                ) {
                                    picker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = NotesPurple,
                            fontFamily = NotesFont,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Приложить технический журнал",
                                fontFamily = NotesFont,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Без текста ваших заметок",
                                fontFamily = NotesFont,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = withLog,
                            onCheckedChange = {
                                withLog = it
                                sendError = null
                            },
                            enabled = !sending,
                            colors = SwitchDefaults.colors(checkedTrackColor = NotesPurple),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    ConsentLine(onTerms = { showTerms = true })
                    Spacer(Modifier.height(8.dp))
                }
                sendError?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.padding(bottom = 6.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = NotesFont,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Отмена",
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !sending, role = Role.Button, onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        fontFamily = NotesFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Отправить",
                        modifier = Modifier
                            .alpha(if (canSend) 1f else 0.4f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = canSend, role = Role.Button) {
                                val form = BugReportForm(name, description)
                                if (form.validate() != BugReportValidity.ok || sending) return@clickable
                                val chosen = shots.toList()
                                val includeLog = withLog
                                sending = true
                                sendError = null
                                scope.launch {
                                    try {
                                        val prepared = BugReportMailer.prepare(
                                            context,
                                            form,
                                            chosen,
                                            includeLog,
                                        )
                                        try {
                                            BugReportMailer.launch(context, prepared)
                                            onDismiss()
                                        } catch (error: ActivityNotFoundException) {
                                            sendError = NO_MAIL_APP
                                            onToast(NO_MAIL_APP)
                                        }
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (_: Exception) {
                                        val message = "Не удалось подготовить сообщение"
                                        sendError = message
                                        onToast(message)
                                    } finally {
                                        sending = false
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        fontFamily = NotesFont,
                        fontWeight = FontWeight.SemiBold,
                        color = NotesPurple,
                    )
                }
                Text(
                    "Откроется почтовое приложение — письмо можно проверить перед отправкой.",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = NotesFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (showTerms) {
        TermsDialog(onClose = { showTerms = false })
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontFamily = NotesFont,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun fieldModifier(): Modifier {
    return Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 14.dp, vertical = 12.dp)
}

@Composable
private fun ConsentLine(onTerms: () -> Unit) {
    val link = NotesPurple
    val text = buildAnnotatedString {
        append("Отправляя сообщение, вы соглашаетесь с ")
        val start = length
        append("правилами использования")
        addLink(
            LinkAnnotation.Clickable(
                tag = "terms",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = link,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = LinkInteractionListener { onTerms() },
            ),
            start,
            length,
        )
        append(" приложения.")
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = NotesFont,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ShotThumb(image: ImageBitmap?, onRemove: () -> Unit) {
    Box(Modifier.size(64.dp)) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Скриншот",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Убрать скриншот",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove, role = Role.Button)
                .padding(2.dp)
                .size(14.dp),
        )
    }
}

private fun oneLine(raw: String, limit: Int): String {
    val stripped = raw.replace("\n", "").replace("\r", "")
    return if (stripped.length <= limit) stripped else stripped.take(limit)
}

private fun mergeShots(current: List<Uri>, picked: List<Uri>): List<Uri> {
    val seen = HashSet<Uri>(current.size + picked.size)
    val merged = ArrayList<Uri>(BugReportMail.MAX_SHOTS)
    for (uri in current) {
        if (seen.add(uri)) merged.add(uri)
        if (merged.size == BugReportMail.MAX_SHOTS) return merged
    }
    for (uri in picked) {
        if (merged.size == BugReportMail.MAX_SHOTS) break
        if (seen.add(uri)) merged.add(uri)
    }
    return merged
}
