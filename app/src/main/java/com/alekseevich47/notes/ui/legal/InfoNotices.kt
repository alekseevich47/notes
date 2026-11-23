package com.alekseevich47.notes.ui.legal

import android.graphics.Paint
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.alekseevich47.notes.data.secure.LATEST_INFO_NOTICE_ID

internal data class InfoNotice(
    val id: Int,
    val title: String,
    val body: AnnotatedString,
    val footer: AnnotatedString?,
    val action: String,
    val dismissible: Boolean,
)

internal fun supportsGlyph(text: String): Boolean = Paint().hasGlyph(text)

internal object InfoNotices {
    val first: InfoNotice by lazy { create(onTerms = null) }

    fun byId(id: Int, onTerms: () -> Unit): InfoNotice? {
        if (id != LATEST_INFO_NOTICE_ID) return null
        return create(onTerms)
    }

    private fun create(onTerms: (() -> Unit)?): InfoNotice {
        return InfoNotice(
            id = LATEST_INFO_NOTICE_ID,
            title = "Информация",
            body = body(includeGratefulGlyph),
            footer = footer(onTerms),
            action = "Продолжить",
            dismissible = false,
        )
    }
}

private const val GRATEFUL_GLYPH = "🥹"
private const val FIRST_LINE = "Будем очень признательны, если поставите нам оценку 🙏"
private const val RECOMMEND = "Также рекомендуется прочитать короткую информацию ниже."
private const val ENJOY = "Приятного использования!"

/**
 * [android.graphics.Paint] in JVM unit tests is a stub and throws.
 * A failed check leaves the glyph out, so the first launch never shows a tofu box.
 */
private val includeGratefulGlyph: Boolean by lazy {
    try {
        supportsGlyph(GRATEFUL_GLYPH)
    } catch (_: Exception) {
        false
    }
}

private fun body(includeGlyph: Boolean): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(FIRST_LINE)
        if (includeGlyph) append(GRATEFUL_GLYPH)
    }
    append("\n\n")
    append(
        "Мы очень рады, что вы решили попробовать наше приложение. Мы стабильно занимаемся развитием и исправлением работы данного сервиса. Если появятся идеи нового функционала, либо обнаружите ошибки - обязательно сообщите нам по контактам в разделе \"Настройки\". ",
    )
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
        append(RECOMMEND)
    }
    append("\n\n")
    append(
        "Для корректного использования голосовых заметок крайне рекомендуется установить 2 модели распознавания и обработки в настройках приложения. Без них возможна полная (или частичная) неработоспособность функции записи аудиозаметок. Все заметки и аудиозаписи обрабатываются исключительно на Вашем устройстве и не отправляются в интернет. При отправке логов содержимое записей (заметок) не отправляется; логирование происходит только системных показателей для обработки ошибок.",
    )
    append("\n\n")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(ENJOY)
    }
}

private fun footer(onTerms: (() -> Unit)?): AnnotatedString = buildAnnotatedString {
    append("Нажимая кнопку \"Продолжить\", вы соглашаетесь с ")
    val start = length
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
        append("правилами использования")
    }
    addLink(
        LinkAnnotation.Clickable(
            tag = "terms",
            styles = TextLinkStyles(
                style = SpanStyle(textDecoration = TextDecoration.Underline),
            ),
            linkInteractionListener = LinkInteractionListener { _ -> onTerms?.invoke() },
        ),
        start,
        length,
    )
    append(" приложения.")
}
