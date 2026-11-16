package com.alekseevich47.notes.stt

import java.util.concurrent.atomic.AtomicReference

/**
 * Passes one value from a background loader to the capture loop.
 *
 * [publish] and [close] can overlap. Exactly one side is told to release the
 * value: capture when it [claim]s it, or the loader when [publish] loses.
 */
internal class ModelLoadSlot<T> {
    private val state = AtomicReference<Any>(Loading)

    private object Loading
    private object Claimed
    private object Closed
    private class Ready(val value: Any?)

    sealed interface Claim<out T> {
        data object Pending : Claim<Nothing>
        data class Taken<T>(val value: T?) : Claim<T>
    }

    /** @return false when the caller still owns [value] and must release it. */
    fun publish(value: T?): Boolean = state.compareAndSet(Loading, Ready(value))

    fun claim(): Claim<T> {
        while (true) {
            when (val current = state.get()) {
                Loading -> return Claim.Pending
                is Ready -> {
                    if (state.compareAndSet(current, Claimed)) {
                        @Suppress("UNCHECKED_CAST")
                        return Claim.Taken(current.value as T?)
                    }
                }
                else -> return Claim.Pending
            }
        }
    }

    /** A published value capture never took. The caller releases it. */
    fun close(): T? {
        while (true) {
            when (val current = state.get()) {
                is Ready -> {
                    if (state.compareAndSet(current, Closed)) {
                        @Suppress("UNCHECKED_CAST")
                        return current.value as T?
                    }
                }
                Loading -> if (state.compareAndSet(current, Closed)) return null
                Claimed -> if (state.compareAndSet(current, Closed)) return null
                Closed -> return null
                else -> return null
            }
        }
    }
}
