package com.alekseevich47.notes

import com.alekseevich47.notes.data.secure.PinThrottle
import com.alekseevich47.notes.data.secure.PinThrottleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinThrottleTest {
    @Test
    fun firstFourWrongsStayOpenAndTheFifthLocksForThirtySeconds() {
        var state = PinThrottleState()
        val now = 1_000L
        repeat(4) { index ->
            state = PinThrottle.recordWrong(state, now)
            assertFalse(PinThrottle.isLocked(state, now))
            assertEquals(index + 1, state.failures)
            assertEquals(0L, state.lockedUntilElapsed)
        }
        state = PinThrottle.recordWrong(state, now)
        assertEquals(5, state.failures)
        assertTrue(PinThrottle.isLocked(state, now))
        assertEquals(30_000L, PinThrottle.remainingMs(state, now))
        assertEquals(now + 30_000L, state.lockedUntilElapsed)
    }

    @Test
    fun aWrongDuringTheLockIsRefusedAndDoesNotAddAFailure() {
        val locked = lockAt(now = 5_000L)
        val refused = PinThrottle.recordWrong(locked, nowElapsed = 5_000L + 1_000L)
        assertEquals(locked, refused)
        assertEquals(30_000L - 1_000L, PinThrottle.remainingMs(refused, 6_000L))
    }

    @Test
    fun eachLaterWrongDoublesTheLockUntilFifteenMinutes() {
        var state = PinThrottleState()
        var now = 0L
        repeat(4) { state = PinThrottle.recordWrong(state, now) }
        val durations = mutableListOf<Long>()
        repeat(7) {
            state = PinThrottle.recordWrong(state, now)
            durations += PinThrottle.remainingMs(state, now)
            val refused = PinThrottle.recordWrong(state, now + 1L)
            assertEquals(state.failures, refused.failures)
            assertEquals(state.lockedUntilElapsed, refused.lockedUntilElapsed)
            now = state.lockedUntilElapsed
            assertFalse(PinThrottle.isLocked(state, now))
        }
        assertEquals(
            listOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 900_000L, 900_000L),
            durations,
        )
    }

    @Test
    fun aCorrectPinResetsFailuresAndTheLock() {
        val locked = lockAt(now = 2_000L)
        val cleared = PinThrottle.recordCorrect(locked)
        assertEquals(PinThrottleState(), cleared)
        assertFalse(PinThrottle.isLocked(cleared, 2_000L))
        val firstAgain = PinThrottle.recordWrong(cleared, 2_000L)
        assertEquals(1, firstAgain.failures)
        assertFalse(PinThrottle.isLocked(firstAgain, 2_000L))
    }

    @Test
    fun movingTheWallClockDoesNotShortenTheLock() {
        val earlyWall = 1_700_000_000_000L
        val lateWall = earlyWall + 86_400_000L
        var state = PinThrottleState()
        repeat(4) {
            state = PinThrottle.recordWrong(state, nowElapsed = 5_000L, wallClockMillis = earlyWall)
        }
        state = PinThrottle.recordWrong(state, nowElapsed = 5_000L, wallClockMillis = earlyWall)
        val before = PinThrottle.remainingMs(state, nowElapsed = 10_000L, wallClockMillis = earlyWall)
        val after = PinThrottle.remainingMs(state, nowElapsed = 10_000L, wallClockMillis = lateWall)
        assertEquals(before, after)
        assertEquals(25_000L, after)
        assertTrue(PinThrottle.isLocked(state, nowElapsed = 10_000L, wallClockMillis = lateWall))
        assertEquals(state, PinThrottle.alignBoot(state, "boot", "boot", nowElapsed = 1L))
    }

    @Test
    fun aNewBootRestartsWhateverLockTheFailuresStillRequire() {
        val state = PinThrottleState(failures = 6, lockedUntilElapsed = 80_000L)
        val aligned = PinThrottle.alignBoot(state, "previous-boot", "current-boot", nowElapsed = 4_000L)
        assertEquals(6, aligned.failures)
        assertEquals(4_000L + 60_000L, aligned.lockedUntilElapsed)
    }

    @Test
    fun aNewBootClearsADeadlineWhenNoLockRemains() {
        val state = PinThrottleState(failures = 4, lockedUntilElapsed = 80_000L)
        val aligned = PinThrottle.alignBoot(state, "previous-boot", "current-boot", nowElapsed = 4_000L)
        assertEquals(4, aligned.failures)
        assertEquals(0L, aligned.lockedUntilElapsed)
        val neverLocked = PinThrottleState(failures = 6, lockedUntilElapsed = 0L)
        assertEquals(
            neverLocked,
            PinThrottle.alignBoot(neverLocked, "previous-boot", "current-boot", nowElapsed = 4_000L),
        )
    }

    @Test
    fun aFailedBootIdReadDoesNotClearTheLock() {
        val state = PinThrottleState(failures = 5, lockedUntilElapsed = 40_000L)
        assertEquals(state, PinThrottle.reconcile(state, "stored-boot", readBootId = null, nowElapsed = 1L))
        assertEquals(state, PinThrottle.reconcile(state, "stored-boot", readBootId = "  ", nowElapsed = 1L))
        val restarted = PinThrottle.reconcile(state, "stored-boot", "other-boot", nowElapsed = 2_000L)
        assertEquals(2_000L + 30_000L, restarted.lockedUntilElapsed)
    }

    private fun lockAt(now: Long): PinThrottleState {
        var state = PinThrottleState()
        repeat(5) { state = PinThrottle.recordWrong(state, now) }
        return state
    }
}
