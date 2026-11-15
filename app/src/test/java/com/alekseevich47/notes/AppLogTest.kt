package com.alekseevich47.notes

import com.alekseevich47.notes.log.AppLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AppLogTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "applog-" + System.nanoTime())
        dir.mkdirs()
        AppLog.install(dir, logcat = false)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun rotatesInsideOneToTwoMegabytes() {
        assertTrue(AppLog.MAX_BYTES in 1_000_000L..2_000_000L)
    }

    @Test
    fun writesAppLogUnderTheConfiguredDirectory() {
        AppLog.i("Settings", "theme SYSTEM -> DARK")
        val file = AppLog.file()
        assertEquals(File(dir, "app.log"), file)
        val text = file!!.readText()
        assertTrue(text.contains("I/Settings theme SYSTEM -> DARK"))
        assertFalse(text.contains("1234"))
    }

    @Test
    fun masksPathsPinsAndUrls() {
        assertEquals(
            "err note-3.wav",
            AppLog.sanitize("err /data/user/0/com.app/files/recordings/note-3.wav"),
        )
        assertEquals("HTTP 404 [url]", AppLog.sanitize("HTTP 404 https://example.com/model.onnx?token=secret"))
        assertEquals("pin ****", AppLog.sanitize("pin 1234"))
        assertEquals("pin set", AppLog.sanitize("pin set"))
        assertFalse(AppLog.sanitize("pin 9876 saved").contains("9876"))
        val long = AppLog.sanitize("note " + "я".repeat(400))
        assertTrue(long.endsWith("…[truncated]"))
        assertFalse(long.contains("я".repeat(400)))
    }

    @Test
    fun fileNeverStoresAbsoluteAudioPathOrPinDigits() {
        AppLog.e(
            "RecordingSession",
            "phase IDLE recording failed file=/data/user/0/com.app/files/recordings/note-9.wav",
            IllegalStateException("boom /data/user/0/com.app/files/recordings/note-9.wav pin 4321"),
        )
        val text = File(dir, "app.log").readText()
        assertFalse(text.contains("/data/user"))
        assertFalse(text.contains("4321"))
        assertTrue(text.contains("note-9.wav"))
        assertTrue(text.contains("****"))
        assertTrue(text.contains("IllegalStateException"))
    }

    @Test
    fun dropsOldestLinesOnceTheCapIsPassed() {
        AppLog.install(dir, maxBytes = 600, logcat = false)
        AppLog.i("T", "HEADSTART " + "a".repeat(80))
        repeat(20) { index -> AppLog.i("T", "mid-$index " + "b".repeat(80)) }
        AppLog.i("T", "TAILEND " + "c".repeat(40))
        val text = File(dir, "app.log").readText()
        assertTrue(text.contains("rotated"))
        assertTrue(text.contains("TAILEND"))
        assertFalse(text.contains("HEADSTART"))
        assertTrue(text.length < 2_000)
    }
}
