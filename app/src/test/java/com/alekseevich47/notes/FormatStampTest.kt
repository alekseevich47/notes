package com.alekseevich47.notes

import com.alekseevich47.notes.ui.notes.formatStamp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class FormatStampTest {
    @Test
    fun listStampUsesRussianDayMonthAndClock() {
        assertEquals("14 нояб., 22:13", formatStamp(1_700_000_000_000L, ZoneOffset.UTC))
    }
}
