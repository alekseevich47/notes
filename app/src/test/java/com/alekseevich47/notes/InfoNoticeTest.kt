package com.alekseevich47.notes

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.alekseevich47.notes.data.secure.LATEST_INFO_NOTICE_ID
import com.alekseevich47.notes.data.secure.pendingNotice
import com.alekseevich47.notes.ui.legal.InfoNotices
import com.alekseevich47.notes.ui.legal.supportsGlyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoNoticeTest {
    @Test
    fun pendingNoticeShowsOnlyANewerId() {
        assertEquals(1, pendingNotice(0, 1))
        assertNull(pendingNotice(1, 1))
        assertNull(pendingNotice(2, 1))
        assertEquals(2, pendingNotice(1, 2))
    }

    @Test
    fun firstNoticeCopiesTheReleaseTextAndStyles() {
        val notice = InfoNotices.first
        assertEquals(LATEST_INFO_NOTICE_ID, notice.id)
        assertEquals("Информация", notice.title)
        assertEquals("Продолжить", notice.action)
        assertFalse(notice.dismissible)
        val body = notice.body
        listOf(
            "Будем очень признательны, если поставите нам оценку 🙏",
            "Мы очень рады, что вы решили попробовать наше приложение.",
            "Мы стабильно занимаемся развитием и исправлением работы данного сервиса.",
            "Если появятся идеи нового функционала, либо обнаружите ошибки - обязательно сообщите нам по контактам в разделе \"Настройки\".",
            "Также рекомендуется прочитать короткую информацию ниже.",
            "Для корректного использования голосовых заметок крайне рекомендуется установить 2 модели распознавания и обработки в настройках приложения.",
            "Без них возможна полная (или частичная) неработоспособность функции записи аудиозаметок.",
            "Все заметки и аудиозаписи обрабатываются исключительно на Вашем устройстве и не отправляются в интернет.",
            "При отправке логов содержимое записей (заметок) не отправляется; логирование происходит только системных показателей для обработки ошибок.",
            "Приятного использования!",
        ).forEach { phrase ->
            assertTrue(phrase, body.text.contains(phrase))
        }
        val firstLineEnd = body.text.indexOf('\n')
        assertTrue(firstLineEnd > 0)
        assertTrue(body.hasStyle(0, firstLineEnd, FontWeight.Bold, underline = false))
        val enjoy = "Приятного использования!"
        val enjoyAt = body.text.indexOf(enjoy)
        assertTrue(body.hasStyle(enjoyAt, enjoyAt + enjoy.length, FontWeight.Bold, underline = false))
        val recommend = "Также рекомендуется прочитать короткую информацию ниже."
        val recommendAt = body.text.indexOf(recommend)
        assertTrue(body.hasStyle(recommendAt, recommendAt + recommend.length, FontWeight.Bold, underline = true))
        val line = body.text.substring(0, firstLineEnd)
        val base = "Будем очень признательны, если поставите нам оценку 🙏"
        val glyphKnown = try {
            supportsGlyph("🥹")
        } catch (_: Exception) {
            null
        }
        if (glyphKnown == true) {
            assertEquals(base + "🥹", line)
        } else {
            assertEquals(base, line)
            assertFalse(body.text.contains("🥹"))
        }

        val footer = notice.footer
        assertNotNull(footer)
        checkNotNull(footer)
        assertEquals(
            "Нажимая кнопку \"Продолжить\", вы соглашаетесь с правилами использования приложения.",
            footer.text,
        )
        val linkText = "правилами использования"
        val linkAt = footer.text.indexOf(linkText)
        assertTrue(linkAt >= 0)
        assertTrue(footer.isUnderlined(linkAt, linkAt + linkText.length))
        val links = footer.getLinkAnnotations(linkAt, linkAt + linkText.length)
        assertTrue(links.any { (it.item as? LinkAnnotation.Clickable)?.tag == "terms" })
    }
}

private fun AnnotatedString.hasStyle(
    start: Int,
    end: Int,
    weight: FontWeight,
    underline: Boolean,
): Boolean {
    if (start < 0 || end <= start || end > text.length) return false
    return (start until end).all { index ->
        spanStyles.any { span ->
            index >= span.start &&
                index < span.end &&
                span.item.fontWeight == weight &&
                span.item.textDecoration.isUnderline() == underline
        }
    }
}

private fun AnnotatedString.isUnderlined(start: Int, end: Int): Boolean {
    if (start < 0 || end <= start || end > text.length) return false
    return (start until end).all { index ->
        spanStyles.any { span ->
            index >= span.start && index < span.end && span.item.textDecoration.isUnderline()
        }
    }
}

private fun TextDecoration?.isUnderline(): Boolean =
    this != null && contains(TextDecoration.Underline)
