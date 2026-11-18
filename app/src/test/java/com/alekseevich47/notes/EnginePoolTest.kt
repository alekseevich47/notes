package com.alekseevich47.notes

import com.alekseevich47.notes.service.WarmLifetime
import com.alekseevich47.notes.stt.EnginePool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginePoolTest {
    private class Token(val name: String)

    @Test
    fun borrowDoesNotReleaseAndTheNextRecordingReusesIt() {
        val pool = EnginePool<Token>()
        val released = mutableListOf<String>()
        val engine = Token("a")
        pool.offer(engine) { released += it.name }
        val first = pool.borrow()
        pool.unborrow(first!!) { released += it.name }
        val second = pool.borrow()
        assertSame(engine, second)
        assertTrue(released.isEmpty())
        pool.retire { released += it.name }
        assertTrue(released.isEmpty())
        pool.unborrow(second!!) { released += it.name }
        assertEquals(listOf("a"), released)
        pool.unborrow(second) { released += it.name }
        assertEquals(listOf("a"), released)
    }

    @Test
    fun idleRetireReleasesOnce() {
        val pool = EnginePool<Token>()
        val released = mutableListOf<String>()
        val engine = Token("a")
        pool.offer(engine) { released += it.name }
        pool.retire { released += it.name }
        pool.retire { released += it.name }
        assertEquals(listOf("a"), released)
        assertNull(pool.borrow())
    }

    @Test
    fun replacementWaitsUntilTheBorrowEnds() {
        val pool = EnginePool<Token>()
        val released = mutableListOf<String>()
        val first = Token("a")
        val second = Token("b")
        pool.offer(first) { released += it.name }
        assertSame(first, pool.borrow())
        pool.offer(second) { released += it.name }
        assertTrue(released.isEmpty())
        assertSame(second, pool.borrow())
        pool.unborrow(first) { released += it.name }
        assertEquals(listOf("a"), released)
        pool.unborrow(second) { released += it.name }
        assertEquals(listOf("a"), released)
    }

    @Test
    fun rejectedOfferReleasesOnlyTheNewEngine() {
        val pool = EnginePool<Token>()
        val released = mutableListOf<String>()
        val first = Token("a")
        pool.offer(first) { released += it.name }
        pool.offer(Token("b"), accept = { false }) { released += it.name }
        assertEquals(listOf("b"), released)
        assertSame(first, pool.borrow())
    }
}

class WarmLifetimeTest {
    @Test
    fun closeDoesNotRetireANewerStart() {
        val life = WarmLifetime()
        val first = life.open()
        val second = life.open()
        var retired = 0
        life.close(first) { retired += 1 }
        assertEquals(0, retired)
        life.close(second) { retired += 1 }
        assertEquals(1, retired)
        life.close(second) { retired += 1 }
        assertEquals(1, retired)
    }

    @Test
    fun publishAfterCloseReleasesInsteadOfStoring() {
        val life = WarmLifetime()
        val epoch = life.open()
        life.close(epoch) { }
        val stored = mutableListOf<String>()
        val released = mutableListOf<String>()
        life.publish(epoch, "late", { stored += it }, { released += it })
        assertTrue(stored.isEmpty())
        assertEquals(listOf("late"), released)
    }

    @Test
    fun publishOfTheCurrentStartIsStored() {
        val life = WarmLifetime()
        val epoch = life.open()
        val stored = mutableListOf<String>()
        val released = mutableListOf<String>()
        life.publish(epoch, "warm", { stored += it }, { released += it })
        assertEquals(listOf("warm"), stored)
        assertTrue(released.isEmpty())
    }
}
