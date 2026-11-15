package com.alekseevich47.notes

import com.alekseevich47.notes.data.download.DownloadResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadResumeTest {
    @Test
    fun freshDownloadHasNoRange() {
        val plan = DownloadResume.plan(existingBytes = 0, totalBytes = 2_000)
        assertEquals(DownloadResume.Action.FRESH, plan.action)
        assertNull(plan.rangeHeader)
        assertEquals(0L, plan.existingBytes)
    }

    @Test
    fun partialFileResumesWithRange() {
        val plan = DownloadResume.plan(existingBytes = 400, totalBytes = 2_000)
        assertEquals(DownloadResume.Action.RESUME, plan.action)
        assertEquals("bytes=400-", plan.rangeHeader)
        assertEquals(1_600L, DownloadResume.remaining(400, 2_000))
    }

    @Test
    fun completeFileIsNotDownloadedAgain() {
        val plan = DownloadResume.plan(existingBytes = 2_000, totalBytes = 2_000)
        assertEquals(DownloadResume.Action.COMPLETE, plan.action)
        assertNull(plan.rangeHeader)
        assertEquals(0L, DownloadResume.remaining(2_000, 2_000))
    }

    @Test
    fun unknownLengthStillResumesWhenBytesExist() {
        val plan = DownloadResume.plan(existingBytes = 80, totalBytes = null)
        assertEquals(DownloadResume.Action.RESUME, plan.action)
        assertEquals("bytes=80-", plan.rangeHeader)
        assertNull(DownloadResume.remaining(80, null))
        assertEquals(0f, DownloadResume.progress(80, null))
    }

    @Test
    fun progressAndAggregateStayInRange() {
        assertEquals(0.25f, DownloadResume.progress(50, 200), 0.001f)
        assertEquals(1f, DownloadResume.progress(500, 200), 0.001f)
        val fraction = DownloadResume.aggregate(
            downloaded = listOf(100L, 50L, 0L),
            totals = listOf(100L, 200L, null),
        )
        assertEquals(150f / 300f, fraction, 0.001f)
        assertEquals(0f, DownloadResume.aggregate(emptyList(), emptyList()))
    }

    @Test
    fun contentRangeAndStatusDecideAppendOrRewrite() {
        assertEquals(2_400L, DownloadResume.parseTotal("bytes 100-199/2400"))
        assertNull(DownloadResume.parseTotal("bytes */*"))
        assertNull(DownloadResume.parseTotal(null))
        assertTrue(DownloadResume.isPartialContent(206))
        assertFalse(DownloadResume.isPartialContent(200))
        assertTrue(DownloadResume.shouldRewriteFromStart(statusCode = 200, resumed = true))
        assertFalse(DownloadResume.shouldRewriteFromStart(statusCode = 206, resumed = true))
        assertFalse(DownloadResume.shouldRewriteFromStart(statusCode = 200, resumed = false))
    }
}
