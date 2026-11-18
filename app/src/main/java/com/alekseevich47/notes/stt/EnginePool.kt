package com.alekseevich47.notes.stt

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * One native engine that recordings borrow and must not free.
 *
 * [retire] while a recording still holds the engine delays [release] until the
 * last [unborrow]. A second [unborrow] does not free it again. Replacing an
 * idle engine frees the previous one; replacing a borrowed engine waits.
 */
internal class EnginePool<T : Any> {
    private val lock = ReentrantLock()
    private var active: Slot<T>? = null
    private val retiring = ArrayList<Slot<T>>()

    private class Slot<T : Any>(
        val engine: T,
        var borrows: Int = 0,
    )

    fun ready(): Boolean = lock.withLock { active != null }

    fun offer(engine: T, accept: () -> Boolean = { true }, release: (T) -> Unit) {
        val drop = lock.withLock {
            if (!accept()) {
                engine
            } else {
                val previous = active
                active = Slot(engine)
                when {
                    previous == null -> null
                    previous.borrows == 0 -> previous.engine
                    else -> {
                        retiring.add(previous)
                        null
                    }
                }
            }
        }
        if (drop != null) release(drop)
    }

    fun borrow(): T? = lock.withLock {
        val slot = active ?: return@withLock null
        slot.borrows += 1
        slot.engine
    }

    fun unborrow(engine: T, release: (T) -> Unit) {
        val drop = lock.withLock {
            val current = active
            if (current != null && current.engine === engine) {
                if (current.borrows <= 0) return@withLock null
                current.borrows -= 1
                return@withLock null
            }
            val index = retiring.indexOfFirst { it.engine === engine }
            if (index < 0) return@withLock null
            val slot = retiring[index]
            if (slot.borrows <= 0) return@withLock null
            slot.borrows -= 1
            if (slot.borrows == 0) {
                retiring.removeAt(index)
                slot.engine
            } else {
                null
            }
        }
        if (drop != null) release(drop)
    }

    fun retire(release: (T) -> Unit) {
        val drop = ArrayList<T>(1)
        lock.withLock {
            val current = active ?: return@withLock
            active = null
            if (current.borrows == 0) {
                drop.add(current.engine)
            } else {
                retiring.add(current)
            }
        }
        drop.forEach(release)
    }
}
