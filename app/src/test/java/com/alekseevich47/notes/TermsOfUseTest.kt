package com.alekseevich47.notes

import com.alekseevich47.notes.ui.legal.TermsOfUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermsOfUseTest {
    @Test
    fun sectionsArePresentAndNameTheRequiredFacts() {
        assertEquals("1.0", TermsOfUse.VERSION)
        assertEquals("Редакция 1.0 от 23 ноября 2026 г.", TermsOfUse.EDITION)
        assertTrue(TermsOfUse.sections.isNotEmpty())
        TermsOfUse.sections.forEach { section ->
            assertTrue(section.title.isNotBlank())
            assertTrue(section.paragraphs.isNotEmpty())
            section.paragraphs.forEach { paragraph ->
                assertTrue(paragraph.isNotBlank())
            }
        }
        val text = TermsOfUse.sections.joinToString("\n") { section ->
            section.title + "\n" + section.paragraphs.joinToString("\n")
        }
        assertTrue(text.contains("152-ФЗ"))
        assertTrue(text.contains("loomixx.dev@yandex.ru"))
        assertTrue(text.contains("Hugging Face"))
        assertTrue(text.contains("«Системное»"))
        assertTrue(text.contains("@alekseevich47"))
        assertTrue(text.contains("статьями 23 и 24"))
    }
}
