package com.example

import com.example.vaanivideo.data.model.PageTurnItem
import com.example.vaanivideo.timeline.TimelineUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testFormatMs() {
        assertEquals("00:38.420", TimelineUtils.formatMs(38420L))
        assertEquals("01:05.810", TimelineUtils.formatMs(65810L))
        assertEquals("01:02:14.550", TimelineUtils.formatMs(3734550L))
    }

    @Test
    fun testParseToMs() {
        assertEquals(38420L, TimelineUtils.parseToMs("00:38.420"))
        assertEquals(65810L, TimelineUtils.parseToMs("01:05.810"))
        assertEquals(3734550L, TimelineUtils.parseToMs("01:02:14.550"))
        assertEquals(65000L, TimelineUtils.parseToMs("01:05"))
    }

    @Test
    fun testInitialTimelineGenerationAndValidation() {
        val pages = TimelineUtils.generateInitialTimeline(3, 90000L)
        assertEquals(3, pages.size)
        assertEquals(0L, pages[0].startMs)
        assertEquals(30000L, pages[0].endMs)
        assertEquals(30000L, pages[1].startMs)
        assertEquals(60000L, pages[1].endMs)
        assertEquals(60000L, pages[2].startMs)
        assertEquals(90000L, pages[2].endMs)

        val validation = TimelineUtils.validateTimeline(pages, 90000L, 3)
        assertTrue(validation.isValid)
        assertTrue(validation.errors.isEmpty())
    }

    @Test
    fun testApplyPageTurn() {
        val initialPages = TimelineUtils.generateInitialTimeline(3, 90000L)
        // User listens to audio and at 00:51.273 turns page 2
        val updated = TimelineUtils.applyPageTurn(
            pages = initialPages,
            targetPageNumber = 2,
            currentAudioMs = 51273L,
            totalDurationMs = 90000L
        )

        assertEquals(51273L, updated[0].endMs)
        assertEquals(51273L, updated[1].startMs)
    }

    @Test
    fun testValidationCatchesOverlaps() {
        val invalidPages = listOf(
            PageTurnItem(1, 0L, 40000L),
            PageTurnItem(2, 35000L, 60000L), // Overlap: 35000 < 40000
            PageTurnItem(3, 60000L, 90000L)
        )
        val validation = TimelineUtils.validateTimeline(invalidPages, 90000L, 3)
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("overlaps") })
    }
}
