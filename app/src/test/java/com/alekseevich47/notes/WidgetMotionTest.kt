package com.alekseevich47.notes

import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.ui.widgets.WidgetMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class WidgetMotionTest {
    @Test
    fun recordingZoomFollowsLevelContinuously() {
        val scales = (0..20).map { WidgetMotion.recordingScale(it / 20f) }
        assertEquals(1f, scales.first(), 0.001f)
        assertEquals(1.18f, scales.last(), 0.001f)
        assertTrue(scales.last() > 1.08f)
        assertTrue(scales.last() <= 1.2f)
        scales.zipWithNext().forEach { (earlier, later) ->
            assertTrue(later + 0.0001f >= earlier)
            assertTrue(later - earlier < 0.02f)
        }
        val glow = (0..10).map { WidgetMotion.glowAlpha(it / 10f) }
        assertTrue(glow.zipWithNext().all { (a, b) -> b + 0.0001f >= a })
        assertTrue(glow.last() > 0.95f)
    }

    @Test
    fun levelGlidesInsteadOfStepping() {
        var value = 0f
        val steps = mutableListOf(value)
        repeat(6) {
            value = WidgetMotion.easeLevel(value, 1f)
            steps += value
        }
        assertTrue(steps.zipWithNext().all { (a, b) -> b > a })
        assertTrue(steps[1] <= 0.5f)
        assertTrue(steps[1] < 0.75f)
        assertTrue(steps.last() > 0.9f)
        assertEquals(0f, WidgetMotion.easeLevel(Float.NaN, 0f), 0.001f)
    }

    @Test
    fun processingWaveIsDenseAndOverlapped() {
        assertTrue(WidgetMotion.DOT_FRAMES >= 12)
        val lifts = (0 until WidgetMotion.DOT_FRAMES).map { WidgetMotion.dotLift(it, 0) }
        lifts.zipWithNext { a, b -> assertTrue(abs(a - b) < 0.35f) }
        val peak = lifts.indices.maxBy { lifts[it] }
        val neighbour = WidgetMotion.dotLift(peak, 1)
        assertTrue(abs(lifts[peak] - neighbour) > 0.2f)
        assertTrue(neighbour > 0.2f)
        assertTrue((0 until 3).map { WidgetMotion.dotLift(peak, it) }.toSet().size == 3)
        repeat(40) { frame ->
            assertTrue(WidgetMotion.dotFrame(frame) in 0 until WidgetMotion.DOT_FRAMES)
            repeat(3) { index ->
                assertTrue(WidgetMotion.dotLift(frame, index) in -1f..1f)
            }
        }
    }

    @Test
    fun dotDrawablesMatchTheWaveLength() {
        val frames = repoFile("src/main/res/drawable")
            .listFiles { file -> file.name.matches(Regex("ic_widget_dots_\\d+\\.xml")) }
            .orEmpty()
        assertEquals(WidgetMotion.DOT_FRAMES, frames.size)
    }

    @Test
    fun newWordsFadeAndSlideIn() {
        val first = WidgetMotion.wordFadeAlpha(0)
        val last = WidgetMotion.wordFadeAlpha(WidgetMotion.FADE_FRAMES)
        assertTrue(last > first)
        assertEquals(1f, last, 0.001f)
        assertTrue(first >= 0.5f)
        assertTrue(WidgetMotion.wordSlideDp(0) > WidgetMotion.wordSlideDp(WidgetMotion.FADE_FRAMES))
        assertEquals(0f, WidgetMotion.wordSlideDp(WidgetMotion.FADE_FRAMES), 0.001f)
    }

    @Test
    fun fadeResetsWhenTheWordsChange() {
        val same = listOf("один", "два")
        assertFalse(WidgetMotion.shouldResetFade(same, same))
        assertTrue(WidgetMotion.shouldResetFade(same, listOf("один", "два три")))
        assertTrue(WidgetMotion.shouldResetFade(same, listOf("один", "два", "три")))
        assertTrue(WidgetMotion.shouldResetFade(emptyList(), listOf("один")))
    }

    @Test
    fun wordsWrapOntoTheNextLine() {
        assertEquals(listOf("один два", "три"), WidgetMotion.wrapWords("один два три", 10))
        assertEquals(listOf("aa", "bbbbbbbbbb", "cc"), WidgetMotion.wrapWords("aa bbbbbbbbbb cc", 6))
        assertTrue(WidgetMotion.wrapWords("   ", 12).isEmpty())
        val source = "альфа бета гамма дельта"
        assertEquals(source, WidgetMotion.wrapWords(source, 12).joinToString(" "))
        assertTrue(WidgetMotion.charsForWidth(320) > WidgetMotion.charsForWidth(180))
    }

    @Test
    fun finishedNoteReplacesTranscriptWithThePrompt() {
        val prompt = listOf(WidgetMotion.SAVED_NOTE_PROMPT)
        assertEquals("Нажмите на микрофон, чтобы создать заметку", WidgetMotion.SAVED_NOTE_PROMPT)
        assertEquals(prompt, WidgetMotion.linesFor(MicPhase.DONE, "привет мир", 250))
        assertEquals(prompt, WidgetMotion.linesFor(MicPhase.IDLE, "привет мир", 250))
        assertEquals(listOf("привет мир"), WidgetMotion.linesFor(MicPhase.RECORDING, "привет мир", 250))
        assertEquals(listOf("привет мир"), WidgetMotion.linesFor(MicPhase.PROCESSING, "привет мир", 250))
        val xml = repoFile("src/main/res/values/strings.xml").readText()
        assertTrue(xml.contains(">${WidgetMotion.SAVED_NOTE_PROMPT}<"))
    }

    @Test
    fun providerMetadataIsOneByOneAndFourByOne() {
        val small = repoFile("src/main/res/xml/widget_2x2_info.xml").readText()
        val wide = repoFile("src/main/res/xml/widget_4x2_info.xml").readText()
        assertTrue(small.contains("android:targetCellWidth=\"1\""))
        assertTrue(small.contains("android:targetCellHeight=\"1\""))
        assertTrue(small.contains("android:minWidth=\"40dp\""))
        assertTrue(small.contains("android:minHeight=\"40dp\""))
        assertTrue(small.contains("android:resizeMode=\"none\""))
        assertTrue(wide.contains("android:targetCellWidth=\"4\""))
        assertTrue(wide.contains("android:targetCellHeight=\"1\""))
        assertTrue(wide.contains("android:minWidth=\"250dp\""))
        assertTrue(wide.contains("android:minHeight=\"48dp\""))
    }

    @Test
    fun processingFramesAdvanceQuickly() {
        assertEquals(40, WidgetMotion.DOT_FRAME_MS)
        val service = repoFile("src/main/java/com/alekseevich47/notes/service/RecordingService.kt").readText()
        assertTrue(service.contains("WidgetMotion.DOT_FRAME_MS"))
        assertFalse(service.contains("delay(72)"))
    }

    @Test
    fun liveTextFollowsWidgetPrefs() {
        assertFalse(WidgetMotion.liveTextEnabled(null, false))
        assertFalse(WidgetMotion.liveTextEnabled("tone", false))
        assertFalse(WidgetMotion.liveTextEnabled("  ", false))
        assertTrue(WidgetMotion.liveTextEnabled("tone", true))
        assertTrue(WidgetMotion.liveTextEnabled("system", false))
        assertTrue(WidgetMotion.liveTextEnabled("SYSTEM", true))
        val hint = WidgetMotion.LIVE_TEXT_OFF
        assertEquals(
            "Чтобы видеть текст во время записи, включите в настройках автозагрузку моделей или системное распознавание.",
            hint,
        )
        assertEquals(hint, WidgetMotion.displayText(MicPhase.RECORDING, "привет", 280, liveText = false))
        assertEquals(hint, WidgetMotion.displayText(MicPhase.IDLE, "привет", 280, liveText = false))
        assertEquals(WidgetMotion.SAVED_NOTE_PROMPT, WidgetMotion.displayText(MicPhase.IDLE, "привет", 280, liveText = true))
        assertEquals("привет", WidgetMotion.displayText(MicPhase.RECORDING, "привет", 280, liveText = true))
        val strings = repoFile("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains(">${hint}<"))
    }

    @Test
    fun statusLineMatchesTheMicPhase() {
        assertEquals(WidgetMotion.STATUS_LOADING, WidgetMotion.statusLabel(MicPhase.IDLE, modelLoading = true))
        assertEquals(WidgetMotion.STATUS_LOADING, WidgetMotion.statusLabel(MicPhase.RECORDING, modelLoading = true))
        assertEquals(WidgetMotion.STATUS_RECORDING, WidgetMotion.statusLabel(MicPhase.RECORDING, modelLoading = false))
        assertEquals(WidgetMotion.STATUS_PROCESSING, WidgetMotion.statusLabel(MicPhase.PROCESSING, modelLoading = true))
        assertNull(WidgetMotion.statusLabel(MicPhase.DONE, modelLoading = true))
        assertNull(WidgetMotion.statusLabel(MicPhase.IDLE, modelLoading = false))
        assertEquals("Загрузка...", WidgetMotion.STATUS_LOADING)
        assertEquals("Запись...", WidgetMotion.STATUS_RECORDING)
        assertEquals("Обработка...", WidgetMotion.STATUS_PROCESSING)
        assertTrue(WidgetMotion.modelLoading(prefFlag = false, statusLines = listOf("DOWNLOADING")))
        assertFalse(WidgetMotion.modelLoading(prefFlag = false, statusLines = listOf("INSTALLED")))
        assertTrue(WidgetMotion.modelLoading(prefFlag = true, statusLines = emptyList()))
    }

    @Test
    fun circleUsesTheSmallerSideAndWideMicIsAButton() {
        assertEquals(74, WidgetMotion.smallMicSideDp(widthDp = 80, heightDp = 120, labelDp = 16))
        assertEquals(50, WidgetMotion.smallMicSideDp(widthDp = 90, heightDp = 70, labelDp = 16))
        assertEquals(0, WidgetMotion.smallMicSideDp(widthDp = 0, heightDp = 80, labelDp = 0))
        val wide = WidgetMotion.wideMicSideDp(widthDp = 280, heightDp = 84)
        assertTrue(wide > 48)
        assertTrue(wide <= 76)
        assertTrue(WidgetMotion.WIDE_MIC_HEIGHT_FRACTION >= 0.6f)
        val small = repoFile("src/main/res/layout/widget_2x2.xml").readText()
        val wideLayout = repoFile("src/main/res/layout/widget_4x2.xml").readText()
        assertFalse(small.contains("fitXY"))
        assertFalse(wideLayout.contains("fitXY"))
        assertFalse(wideLayout.contains("ListView"))
        assertTrue(small.contains("android:textSize=\"11sp\""))
        assertTrue(wideLayout.contains("@+id/widget_words"))
        assertTrue(wideLayout.contains("@+id/widget_text_block"))
        val mic = repoFile("src/main/res/drawable/ic_widget_mic.xml").readText()
        assertTrue(mic.contains("android:strokeWidth=\"1.15\""))
        assertFalse(mic.contains("android:strokeWidth=\"1.7\""))
        assertTrue(mic.contains("android:fillColor=\"@android:color/transparent\""))
        val manifest = repoFile("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("WidgetWordsService"))
    }

    @Test
    fun newTextFadesInAndLeavingLinesFadeOut() {
        val appended = WidgetMotion.blockPaint(base = "один", incoming = "один два", frame = 0)
        assertEquals("один два", appended.incoming)
        assertEquals(4, appended.solidUntil)
        assertTrue(appended.suffixAlpha < 0.5f)
        assertEquals("", appended.outgoing)
        val done = WidgetMotion.blockPaint(base = "один", incoming = "один два", frame = WidgetMotion.FADE_FRAMES)
        assertEquals(1f, done.suffixAlpha, 0.001f)
        assertEquals("", done.outgoing)
        val leaving = WidgetMotion.blockPaint(base = "старая строка", incoming = "новая строка", frame = 0)
        assertEquals("старая строка", leaving.outgoing)
        assertEquals(1f, leaving.outgoingAlpha, 0.001f)
        assertEquals(0f, leaving.suffixAlpha, 0.001f)
        val mid = WidgetMotion.blockPaint(base = "старая строка", incoming = "новая строка", frame = WidgetMotion.FADE_FRAMES)
        assertEquals("", mid.outgoing)
        assertEquals(1f, mid.suffixAlpha, 0.001f)
        val (outStart, inStart) = WidgetMotion.crossfade(0)
        val (outEnd, inEnd) = WidgetMotion.crossfade(WidgetMotion.FADE_FRAMES)
        assertEquals(1f, outStart, 0.001f)
        assertEquals(0f, inStart, 0.001f)
        assertEquals(0f, outEnd, 0.001f)
        assertEquals(1f, inEnd, 0.001f)
    }

    @Test
    fun colorAlphaKeepsRgb() {
        assertEquals(0xFF1B1430.toInt(), WidgetMotion.colorWithAlpha(0xFF1B1430.toInt(), 1f))
        assertEquals(0x001B1430, WidgetMotion.colorWithAlpha(0xFF1B1430.toInt(), 0f))
    }

    private fun repoFile(relative: String): File {
        val direct = File(relative)
        if (direct.exists()) return direct
        val nested = File("app/$relative")
        if (nested.exists()) return nested
        error("missing $relative (cwd=${File(".").absolutePath})")
    }
}
