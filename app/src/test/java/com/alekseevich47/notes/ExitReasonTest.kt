package com.alekseevich47.notes

import com.alekseevich47.notes.log.exitReasonName
import org.junit.Assert.assertEquals
import org.junit.Test

class ExitReasonTest {
    @Test
    fun namesKnownExitReasons() {
        assertEquals("UNKNOWN", exitReasonName(0))
        assertEquals("EXIT_SELF", exitReasonName(1))
        assertEquals("LOW_MEMORY", exitReasonName(3))
        assertEquals("CRASH", exitReasonName(4))
        assertEquals("CRASH_NATIVE", exitReasonName(5))
        assertEquals("ANR", exitReasonName(6))
        assertEquals("EXCESSIVE_RESOURCE_USAGE", exitReasonName(9))
        assertEquals("USER_REQUESTED", exitReasonName(10))
        assertEquals("FREEZER", exitReasonName(14))
        assertEquals("PACKAGE_UPDATED", exitReasonName(16))
    }

    @Test
    fun unknownCodeKeepsTheNumber() {
        assertEquals("REASON_99", exitReasonName(99))
    }
}
