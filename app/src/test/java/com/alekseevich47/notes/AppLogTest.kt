package com.alekseevich47.notes

import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.log.processMarker
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
    fun processStartLinesKeepDeviceFactsAndDropNothingSensitive() {
        val lines = AppLog.processStartLines(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 7",
            device = "panther",
            release = "14",
            sdk = 34,
            incremental = "11234567",
            display = "AP2A.240805.005",
            abis = "arm64-v8a,armeabi-v7a",
            locale = "ru-RU",
            versionName = "1.0.4-debug",
            versionCode = 5,
            buildType = "debug",
            debuggable = true,
            pid = 4242,
            memoryClass = 256,
            lowRam = false,
            totalMemMb = 3814,
            availMemMb = 1200,
            freeDiskMb = 2048,
            perfTier = "NORMAL",
            toneInstalled = true,
            toneEnabled = false,
            gigaInstalled = false,
            gigaEnabled = true,
            recognizer = "tone",
            warmupOnBoot = true,
            micGranted = true,
            notificationsGranted = false,
            batteryOptIgnored = true,
            timezone = "UTC",
            theme = "DARK",
        )
        val text = lines.joinToString("\n")
        assertTrue(text.contains("manufacturer=Google"))
        assertTrue(text.contains("brand=google"))
        assertTrue(text.contains("model=Pixel 7"))
        assertTrue(text.contains("device=panther"))
        assertTrue(text.contains("release=14"))
        assertTrue(text.contains("sdk=34"))
        assertTrue(text.contains("incremental=11234567"))
        assertTrue(text.contains("display=AP2A.240805.005"))
        assertTrue(text.contains("abis=arm64-v8a,armeabi-v7a"))
        assertTrue(text.contains("locale=ru-RU"))
        assertTrue(text.contains("versionName=1.0.4-debug"))
        assertTrue(text.contains("versionCode=5"))
        assertTrue(text.contains("buildType=debug"))
        assertTrue(text.contains("debuggable=true"))
        assertTrue(text.contains("pid=4242"))
        assertTrue(text.contains("memoryClass=256"))
        assertTrue(text.contains("lowRam=false"))
        assertTrue(text.contains("totalMemMb=3814"))
        assertTrue(text.contains("availMemMb=1200"))
        assertTrue(text.contains("freeDiskMb=2048"))
        assertTrue(text.contains("perfTier=NORMAL"))
        assertTrue(text.contains("toneInstalled=true"))
        assertTrue(text.contains("toneEnabled=false"))
        assertTrue(text.contains("gigaInstalled=false"))
        assertTrue(text.contains("gigaEnabled=true"))
        assertTrue(text.contains("recognizer=tone"))
        assertTrue(text.contains("warmupOnBoot=true"))
        assertTrue(text.contains("micGranted=true"))
        assertTrue(text.contains("notificationsGranted=false"))
        assertTrue(text.contains("batteryOptIgnored=true"))
        assertTrue(text.contains("timezone=UTC"))
        assertTrue(text.contains("theme=DARK"))
        assertFalse(text.contains("http"))
        assertFalse(text.contains("/"))
        lines.forEach { line -> assertEquals(line, AppLog.sanitize(line)) }
    }

    @Test
    fun processStartHeaderIsRewrittenAtTheTop() {
        AppLog.i("T", "older line")
        AppLog.rewriteHeader(
            AppLog.processStartLines(
                manufacturer = "Google",
                brand = "google",
                model = "Pixel 7",
                device = "panther",
                release = "14",
                sdk = 34,
                incremental = "11234567",
                display = "AP2A.240805.005",
                abis = "arm64-v8a,armeabi-v7a",
                locale = "ru-RU",
                versionName = "1.0.4-debug",
                versionCode = 5,
            ),
        )
        AppLog.i("T", "newer line")
        val first = File(dir, "app.log").readText()
        assertTrue(first.startsWith("===== notes log header ====="))
        assertTrue(first.indexOf("brand=google") < first.indexOf("older line"))
        assertTrue(first.indexOf("older line") < first.indexOf("newer line"))
        assertTrue(first.contains("manufacturer=Google"))
        assertTrue(first.contains("model=Pixel 7"))
        assertTrue(first.contains("release=14"))
        assertTrue(first.contains("sdk=34"))
        assertTrue(first.contains("display=AP2A.240805.005"))
        assertTrue(first.contains("abis=arm64-v8a,armeabi-v7a"))
        assertTrue(first.contains("locale=ru-RU"))
        assertTrue(first.contains("versionCode=5"))

        AppLog.rewriteHeader(
            AppLog.processStartLines(
                manufacturer = "Google",
                brand = "google",
                model = "Pixel 7",
                device = "panther",
                release = "14",
                sdk = 34,
                incremental = "11234567",
                display = "AP2A.240805.005",
                abis = "arm64-v8a,armeabi-v7a",
                locale = "ru-RU",
                versionName = "1.0.5-debug",
                versionCode = 6,
            ),
        )
        val again = File(dir, "app.log").readText()
        assertTrue(again.startsWith("===== notes log header ====="))
        assertEquals(1, Regex("notes log header").findAll(again).count())
        assertTrue(again.contains("versionName=1.0.5-debug"))
        assertTrue(again.contains("versionCode=6"))
        assertFalse(again.contains("versionCode=5"))
        assertTrue(again.indexOf("versionCode=6") < again.indexOf("older line"))
        assertTrue(again.contains("older line"))
        assertTrue(again.contains("newer line"))
        assertFalse(again.contains("http"))
        assertFalse(again.contains(".wav"))
    }

    @Test
    fun headerStaysAtTheTopAfterRotation() {
        AppLog.install(dir, maxBytes = 800, logcat = false)
        AppLog.rewriteHeader(
            listOf(
                "manufacturer=Google brand=google model=Pixel",
                "android release=14 sdk=34",
                "android display=AP2A",
                "abis=arm64-v8a locale=ru-RU versionName=1.0.5 versionCode=6",
            ),
        )
        repeat(30) { index -> AppLog.i("T", "line-$index " + "x".repeat(40)) }
        val text = File(dir, "app.log").readText()
        assertTrue(text.startsWith("===== notes log header ====="))
        assertTrue(text.contains("rotated"))
        assertTrue(text.indexOf("manufacturer=Google") < text.indexOf("rotated"))
        assertTrue(text.contains("versionName=1.0.5"))
        assertTrue(text.contains("versionCode=6"))
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
        assertEquals(
            "HTTP 404 [url example.com]",
            AppLog.sanitize("HTTP 404 https://example.com/model.onnx?token=secret"),
        )
        assertEquals(
            "[url huggingface.co]",
            AppLog.sanitize("https://huggingface.co/some/path?q=1"),
        )
        assertEquals("[url example.com:8443]", AppLog.sanitize("https://example.com:8443/a?q=1"))
        assertEquals("[url example.com]", AppLog.sanitize("https://user:secret@example.com/hidden"))
        assertEquals("[url]", AppLog.sanitize("http:///no-host/path"))
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
    fun processMarkerNamesPidAndSession() {
        assertEquals("===== process start pid=42 session=7 =====", processMarker(42, 7))
    }

    @Test
    fun renderErrorKeepsCauseFramesAndDoesNotCutThem() {
        fun frame(className: String, method: String, line: Int): StackTraceElement {
            return StackTraceElement(className, method, "F.kt", line)
        }
        val fifth = IllegalStateException("level-5")
        fifth.stackTrace = arrayOf(frame("com.app.C5", "only5", 50))
        val fourth = IllegalStateException("level-4", fifth)
        fourth.stackTrace = Array(8) { index -> frame("com.app.C4", "m$index", 40 + index) }
        val third = IllegalStateException("level-3", fourth)
        third.stackTrace = arrayOf(frame("com.app.C3", "m3", 30))
        val second = IllegalStateException("level-2", third)
        second.stackTrace = arrayOf(frame("com.app.C2", "m2", 20))
        val firstCause = IllegalStateException("see https://huggingface.co/some/path?q=1", second)
        firstCause.stackTrace = arrayOf(frame("com.app.C1", "m1", 10))
        val longMethod = "m".repeat(280)
        val top = IllegalStateException("я".repeat(300), firstCause)
        top.stackTrace = Array(30) { index ->
            frame("com.app.Top", if (index == 0) longMethod else "t$index", index + 1)
        }
        val suppressed = IllegalStateException("suppressed-message")
        suppressed.stackTrace = arrayOf(frame("com.app.Sup", "hidden", 9))
        top.addSuppressed(suppressed)

        AppLog.e("T", "failed", top)
        val text = File(dir, "app.log").readText()
        assertTrue(text.contains("com.app.Top.$longMethod:1"))
        assertTrue(text.contains("com.app.Top.t23:24"))
        assertFalse(text.contains("com.app.Top.t24"))
        assertTrue(text.contains("com.app.C1.m1:10"))
        assertTrue(text.contains("com.app.C4.m0:40"))
        assertTrue(text.contains("com.app.C4.m5:45"))
        assertFalse(text.contains("com.app.C4.m6"))
        assertFalse(text.contains("com.app.C5"))
        assertFalse(text.contains("level-5"))
        assertTrue(text.contains("[url huggingface.co]"))
        assertFalse(text.contains("some/path"))
        assertFalse(text.contains("q=1"))
        assertTrue(text.contains("suppressed java.lang.IllegalStateException: suppressed-message"))
        assertFalse(text.contains("com.app.Sup.hidden"))
        assertTrue(text.contains("…[truncated]"))
        assertFalse(text.contains("я".repeat(300)))
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
