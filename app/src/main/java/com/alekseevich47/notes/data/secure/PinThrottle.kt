package com.alekseevich47.notes.data.secure

/**
 * Lockout after repeated wrong PINs.
 *
 * [PinThrottleState.lockedUntilElapsed] is an elapsed-realtime deadline. Callers
 * pass that clock in. Nothing here reads the wall clock, so changing it cannot
 * shorten a lock. A boot id is compared separately: an elapsed deadline from the
 * previous boot is not meaningful on the new clock.
 */
data class PinThrottleState(
    val failures: Int = 0,
    val lockedUntilElapsed: Long = 0L,
)

object PinThrottle {
    const val FAILURES_BEFORE_LOCK = 5
    const val FIRST_LOCK_MS = 30_000L
    const val MAX_LOCK_MS = 900_000L

    fun recordWrong(
        state: PinThrottleState,
        nowElapsed: Long,
        wallClockMillis: Long = 0L,
    ): PinThrottleState {
        if (isLocked(state, nowElapsed, wallClockMillis)) return state
        val failures = state.failures + 1
        if (failures < FAILURES_BEFORE_LOCK) {
            return PinThrottleState(failures = failures, lockedUntilElapsed = 0L)
        }
        return PinThrottleState(
            failures = failures,
            lockedUntilElapsed = nowElapsed + nextDuration(state.failures),
        )
    }

    fun recordCorrect(state: PinThrottleState): PinThrottleState = PinThrottleState()

    fun isLocked(
        state: PinThrottleState,
        nowElapsed: Long,
        wallClockMillis: Long = 0L,
    ): Boolean = remainingMs(state, nowElapsed, wallClockMillis) > 0L

    fun remainingMs(
        state: PinThrottleState,
        nowElapsed: Long,
        @Suppress("UNUSED_PARAMETER") wallClockMillis: Long = 0L,
    ): Long = (state.lockedUntilElapsed - nowElapsed).coerceAtLeast(0L)

    /**
     * Trimmed boot id, or null when the read failed or was blank.
     * A null id must not be treated as a new boot.
     */
    fun bootIdOrNull(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * When [readBootId] differs from [storedBootId], [state.lockedUntilElapsed]
     * belongs to the previous boot. A failure count that still requires a lock
     * starts again at [nowElapsed]. Otherwise the deadline is cleared.
     * A failed read ([readBootId] null or blank) leaves the state as it is.
     */
    fun reconcile(
        state: PinThrottleState,
        storedBootId: String,
        readBootId: String?,
        nowElapsed: Long,
    ): PinThrottleState {
        val current = bootIdOrNull(readBootId) ?: return state
        return alignBoot(state, storedBootId, current, nowElapsed)
    }

    fun alignBoot(
        state: PinThrottleState,
        storedBootId: String,
        currentBootId: String,
        nowElapsed: Long,
    ): PinThrottleState {
        if (storedBootId == currentBootId) return state
        val required = durationForFailures(state.failures)
        if (required == 0L || state.lockedUntilElapsed == 0L) {
            return state.copy(lockedUntilElapsed = 0L)
        }
        return state.copy(lockedUntilElapsed = nowElapsed + required)
    }

    fun durationForFailures(failures: Int): Long {
        if (failures < FAILURES_BEFORE_LOCK) return 0L
        var duration = FIRST_LOCK_MS
        var steps = failures - FAILURES_BEFORE_LOCK
        while (steps > 0) {
            duration = (duration * 2).coerceAtMost(MAX_LOCK_MS)
            steps--
        }
        return duration
    }

    private fun nextDuration(failuresBeforeThisAttempt: Int): Long {
        val previous = durationForFailures(failuresBeforeThisAttempt)
        if (previous == 0L) return FIRST_LOCK_MS
        return (previous * 2).coerceAtMost(MAX_LOCK_MS)
    }
}
