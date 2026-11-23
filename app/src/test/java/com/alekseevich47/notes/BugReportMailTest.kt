package com.alekseevich47.notes

import com.alekseevich47.notes.data.report.BugReportForm
import com.alekseevich47.notes.data.report.BugReportInfo
import com.alekseevich47.notes.data.report.BugReportMail
import com.alekseevich47.notes.data.report.BugReportValidity
import com.alekseevich47.notes.data.report.imageSampleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportMailTest {
    private val info = BugReportInfo(
        versionName = "1.0.6",
        versionCode = 7,
        buildType = "debug",
        release = "14",
        sdk = 34,
        manufacturer = "Google",
        model = "Pixel",
    )

    @Test
    fun bodyMatchesTheLetterTemplate() {
        val body = BugReportMail.body(
            BugReportForm(name = "Анна", text = "Текст заметки не открылся сам"),
            info,
        )
        assertEquals(
            """
            Имя: Анна

            Что случилось:
            Текст заметки не открылся сам

            —
            Заметки 1.0.6 (7), debug
            Android 14 (API 34), Google Pixel
            """.trimIndent(),
            body,
        )
    }

    @Test
    fun bodyKeepsNewlinesAndDropsOtherControlCharacters() {
        val body = BugReportMail.body(
            BugReportForm(
                name = "Ан\u0000на",
                text = "первая\r\nвторая\tстрока\u007F",
            ),
            info.copy(
                manufacturer = "Goo\u0007gle",
                model = "Pi\u0000xel",
                release = "14\u0085",
            ),
        )
        assertTrue(body.contains("Имя: Анна"))
        assertTrue(body.contains("первая\nвтораястрока"))
        assertTrue(body.contains("Android 14 (API 34), Google Pixel"))
        assertFalse(body.contains('\u0000'))
        assertFalse(body.contains('\u0007'))
        assertFalse(body.contains('\r'))
        assertFalse(body.contains('\t'))
        assertFalse(body.contains('\u007F'))
        assertFalse(body.contains('\u0085'))
        assertTrue(body.contains('\n'))
    }

    @Test
    fun bodyIsCappedAt8000Characters() {
        val body = BugReportMail.body(
            BugReportForm(name = "А", text = "я".repeat(9_000)),
            info,
        )
        assertEquals(BugReportMail.BODY_MAX, body.length)
        assertTrue(body.startsWith("Имя: А\n\nЧто случилось:\n"))
        assertFalse(body.contains("\u0000"))
    }

    @Test
    fun validateRejectsABlankNameIncludingSpaces() {
        val enough = "а".repeat(BugReportMail.TEXT_MIN)
        assertEquals(BugReportValidity.nameBlank, BugReportForm("", enough).validate())
        assertEquals(BugReportValidity.nameBlank, BugReportForm("   ", enough).validate())
        assertEquals(BugReportValidity.nameBlank, BugReportForm("\n\t", enough).validate())
        assertEquals(BugReportValidity.nameBlank, BugReportForm("   ", "я".repeat(2_001)).validate())
        assertEquals(BugReportValidity.ok, BugReportForm("  Анна  ", enough).validate())
    }

    @Test
    fun validateMeasuresDescriptionLengthAfterTrim() {
        val name = "Анна"
        assertEquals(
            BugReportValidity.textTooShort,
            BugReportForm(name, "я".repeat(9)).validate(),
        )
        assertEquals(
            BugReportValidity.textTooShort,
            BugReportForm(name, "  " + "я".repeat(9) + "  ").validate(),
        )
        assertEquals(
            BugReportValidity.ok,
            BugReportForm(name, "я".repeat(10)).validate(),
        )
        assertEquals(
            BugReportValidity.ok,
            BugReportForm(name, "  " + "я".repeat(10) + "  ").validate(),
        )
        assertEquals(
            BugReportValidity.ok,
            BugReportForm(name, "я".repeat(2_000)).validate(),
        )
        assertEquals(
            BugReportValidity.textTooLong,
            BugReportForm(name, "я".repeat(2_001)).validate(),
        )
        assertEquals(
            BugReportValidity.textTooShort,
            BugReportForm(name, " ".repeat(2_000)).validate(),
        )
    }

    @Test
    fun subjectNamesTheVersion() {
        assertEquals(
            "Заметки 1.0.6: сообщение об ошибке",
            BugReportMail.subject("1.0.6"),
        )
        assertEquals(
            "Заметки 1.0.6-debug: сообщение об ошибке",
            BugReportMail.subject("1.0.6-debug"),
        )
    }

    @Test
    fun sampleSizeKeepsTheDecodedLongSideWithin1600() {
        assertEquals(1, imageSampleSize(800, 600, 1600))
        assertEquals(1, imageSampleSize(1600, 900, 1600))
        assertEquals(2, imageSampleSize(1601, 900, 1600))
        assertEquals(2, imageSampleSize(3200, 2000, 1600))
        assertEquals(4, imageSampleSize(4000, 3000, 1600))
        assertEquals(4, imageSampleSize(6399, 100, 1600))
        val sample = imageSampleSize(4032, 3024, 1600)
        assertTrue(4032 / sample <= 1600)
        assertTrue(3024 / sample <= 1600)
        assertTrue(sample > 0 && (sample and (sample - 1)) == 0)
    }
}
