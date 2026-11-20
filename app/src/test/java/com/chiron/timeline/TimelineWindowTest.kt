package com.chiron.timeline

import com.chiron.core.common.TimelineWindow
import com.chiron.core.model.BodyWeightEntry
import com.chiron.feature.timer.BodyweightPointCalculator
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineWindowTest {
    private val today = LocalDate.of(2026, 11, 19)

    private fun window(days: Int, earliest: LocalDate = today.minusDays(1000)) =
        TimelineWindow.endingAt(today, days, earliest, today)

    @Test
    fun latestWindowEndsTodayAndHasTheExactSelectedSpan() {
        for (days in listOf(7, 28, 30, 84, 3640)) {
            val current = window(days)
            assertEquals(today, current.end)
            assertEquals(days.toLong(), ChronoUnit.DAYS.between(current.start, current.end) + 1)
            assertFalse(current.canGoNext)
        }
    }

    @Test
    fun ordinaryNavigationMovesByTheWholeSelectedSpan() {
        for (days in listOf(7, 28, 30, 84)) {
            val current = window(days)
            val previous = current.previous()
            assertEquals(current.end.minusDays(days.toLong()), previous.end)
            assertEquals(current.start.minusDays(days.toLong()), previous.start)
            assertEquals(current, previous.next())
        }
    }

    @Test
    fun oldestWindowStartsAtTheExactFirstDateAndDoesNotShrink() {
        val earliest = today.minusDays(70)
        val beforeEdge = window(30, earliest).previous()
        val edge = beforeEdge.previous()
        assertEquals(earliest, edge.start)
        assertEquals(earliest.plusDays(29), edge.end)
        assertFalse(edge.canGoPrevious)
        assertEquals(edge, edge.previous())
        assertEquals(beforeEdge, edge.next())
    }

    @Test
    fun edgeReversalWorksForEveryReachableWindowAndScale() {
        for (days in listOf(7, 28, 30, 84)) {
            var current = window(days)
            while (current.canGoPrevious) {
                val previous = current.previous()
                assertTrue(previous.end < current.end)
                assertEquals(current, previous.next())
                assertEquals(days.toLong(), ChronoUnit.DAYS.between(previous.start, previous.end) + 1)
                current = previous
            }
            while (current.canGoNext) {
                val next = current.next()
                assertTrue(next.end > current.end)
                assertEquals(current, next.previous())
                current = next
            }
            assertEquals(today, current.end)
        }
    }

    @Test
    fun scaleChangeUsesTodayRatherThanTheOldWindowAsAnchor() {
        val old = window(28).previous().previous()
        val resized = TimelineWindow.endingAt(today, 84, today.minusDays(1000), today)
        assertTrue(old.end < resized.end)
        assertEquals(today.minusDays(83), resized.start)
        assertEquals(today.minusDays(84), resized.previous().end)
        assertEquals(resized, resized.previous().next())
    }

    @Test
    fun outOfBoundsEndsClampWithoutChangingSpan() {
        val earliest = today.minusDays(100)
        val oldest = TimelineWindow.endingAt(today.minusYears(2), 30, earliest, today)
        val newest = TimelineWindow.endingAt(today.plusYears(2), 30, earliest, today)
        assertEquals(earliest, oldest.start)
        assertEquals(earliest.plusDays(29), oldest.end)
        assertEquals(today, newest.end)
        assertEquals(today.minusDays(29), newest.start)
    }

    @Test
    fun shortHistoryKeepsFullSpanAndDoesNotAllowMeaninglessPaging() {
        for (earliest in listOf(null, today, today.minusDays(3))) {
            val current = TimelineWindow.endingAt(today, 30, earliest, today)
            assertEquals(today.minusDays(29), current.start)
            assertEquals(today, current.end)
            assertFalse(current.canGoPrevious)
            assertFalse(current.canGoNext)
        }
    }

    @Test
    fun dailySpansRemainExactAcrossMonthsYearsAndLeapDays() {
        for (anchor in listOf(LocalDate.of(2024, 3, 1), LocalDate.of(2026, 1, 4))) {
            val current = TimelineWindow.endingAt(anchor, 30, anchor.minusYears(2), anchor)
            assertEquals(anchor.minusDays(29), current.start)
            assertEquals(anchor.minusDays(30), current.previous().end)
            assertEquals(current, current.previous().next())
        }
    }

    @Test
    fun newDataBoundsReclampAnExistingWindow() {
        val oldEdge = window(30, today.minusDays(70)).previous().previous()
        val newFirst = today.minusDays(50)
        val refreshed = TimelineWindow.endingAt(oldEdge.end, 30, newFirst, today)
        assertEquals(newFirst, refreshed.start)
        assertFalse(refreshed.canGoPrevious)
        assertEquals(today, refreshed.next().end)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySpansAreRejected() {
        window(0)
    }

    @Test
    fun cachedBodyweightPointsKeepFullSpanAndTheActualEdgeInput() {
        val earliest = today.minusDays(70)
        val prepared = BodyweightPointCalculator.prepare(listOf(entry(1, earliest, 170.0), entry(2, today, 180.0)))
        val edge = TimelineWindow.endingAt(today, 28, earliest, today).previous().previous()
        val points = BodyweightPointCalculator.buildPoints(edge.end, 4, prepared)
        assertEquals(28, points.size)
        assertEquals(earliest, points.first().date)
        assertTrue(points.first().isActualInput)
        assertEquals(edge.end, points.last().date)
    }

    @Test
    fun shortBodyweightHistoryDoesNotSilentlyShrinkTheChart() {
        val prepared = BodyweightPointCalculator.prepare(listOf(entry(1, today, 180.0)))
        val points = BodyweightPointCalculator.buildPoints(today, 4, prepared)
        assertEquals(28, points.size)
        assertEquals(today.minusDays(27), points.first().date)
        assertFalse(points.first().isActualInput)
        assertTrue(points.last().isActualInput)
    }

    @Test
    fun dailyCacheChoosesLatestNormalizedTimestampNotInputOrder() {
        val first = entry(1, today, 170.0)
        val later = first.copy(id = 2, timestampUtc = first.timestampUtc / 1000 + 3600, weightLbs = 180.0)
        val prepared = BodyweightPointCalculator.prepare(listOf(later, first))
        val points = BodyweightPointCalculator.buildPoints(today, 1, prepared)
        assertEquals(1, prepared.knownDates.size)
        assertEquals(180.0, points.last().weightLbs, 0.0)
        assertEquals(later.timestampUtc * 1000, points.last().timestampUtc)
    }

    private fun entry(id: Long, date: LocalDate, weight: Double) = BodyWeightEntry(
        id = id,
        timestampUtc = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        weightLbs = weight
    )
}
