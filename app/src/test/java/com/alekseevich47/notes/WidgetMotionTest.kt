package com.alekseevich47.notes

import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.ui.widgets.WidgetMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class WidgetMotionTest {
    @Test
    fun recordingZoomFollowsLevelContinuously() {
        val scales = (0..20).map { WidgetMotion.recordingScale(it / 20f) }
        assertEquals(1f, scales.first(), 0.001f)
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
