package com.alekseevich47.notes.service

/**
 * Decides which warmup start still owns the shared recognizers.
 *
 * A stop that loses the race with a newer start does not free that start's
 * engines. A recognizer published after its start was closed is released
 * instead of stored.
 */
internal class WarmLifetime {
    private val gate = Any()
    private var counter = 0
    private var current = NONE

    fun open(): Int = synchronized(gate) {
        counter += 1
        current = counter
        current
    }

    fun close(epoch: Int, retire: () -> Unit) {
        synchronized(gate) {
            if (epoch == NONE || current != epoch) return
            current = NONE
            retire()
        }
    }

    fun <T> publish(epoch: Int, engine: T, offer: (T) -> Unit, release: (T) -> Unit) {
        synchronized(gate) {
            if (epoch == NONE || current != epoch) {
                release(engine)
                return
            }
            offer(engine)
        }
    }

    companion object {
        const val NONE = 0
    }
}
