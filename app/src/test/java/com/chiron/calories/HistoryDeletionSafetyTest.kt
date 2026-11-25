package com.chiron.calories

import com.chiron.core.common.CalorieEntry
import com.chiron.core.model.BodyWeightEntry
import com.chiron.feature.timer.calorieDayUnchanged
import com.chiron.feature.timer.reviewWeightRange
import com.chiron.feature.timer.weightRangeUnchanged
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic immutable rows only; no application, repository or storage initialization. */
class HistoryDeletionSafetyTest {
    private val utc = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 3, 8)
    private val firstInstant = Instant.parse("2026-03-08T00:00:00Z").toEpochMilli()
    private val lastInstant = Instant.parse("2026-03-08T23:59:59.999Z").toEpochMilli()

    private fun weight(id: Long, timestamp: Long = firstInstant) =
        BodyWeightEntry(id = id, timestampUtc = timestamp, weightLbs = 180.0)

    private fun food(id: String, day: LocalDate = date) =
        CalorieEntry(id, day, "Oats", 120.0, 4.0, 2.0)

    @Test
    fun malformedReversedEmptyAndUnrepresentableRangesAreRejected() {
        val rows = listOf(weight(1))
        listOf(
            "" to date.toString(),
            "not-a-date" to date.toString(),
            "2026-02-30" to date.toString(),
            date.toString() to "2026-03-07",
            date.toString() to "+999999999-12-31",
            "-999999999-01-01" to date.toString(),
            "+999999999-12-30" to "+999999999-12-30",
            date.toString() to "2026-99-99"
        ).forEach { (start, end) ->
            assertNull("$start through $end", reviewWeightRange(start, end, rows, utc))
        }
        assertNull(reviewWeightRange(date.toString(), date.toString(), emptyList(), utc))
        assertNull(reviewWeightRange("2026-03-09", "2026-03-10", rows, utc))
    }

    @Test
    fun reviewIncludesBothBoundariesButNotAdjacentMilliseconds() {
        val before = weight(4, firstInstant - 1)
        val start = weight(3, firstInstant)
        val end = weight(1, lastInstant)
        val after = weight(2, lastInstant + 1)
        val review = requireNotNull(reviewWeightRange(
            " 2026-03-08 ", "2026-03-08", listOf(before, start, end, after), utc
        ))
        assertEquals(date, review.startDate)
        assertEquals(date, review.endDate)
        assertEquals("UTC", review.zoneId)
        assertEquals(firstInstant, review.startUtc)
        assertEquals(lastInstant, review.endUtc)
        assertEquals(listOf(end, start), review.entries)
    }

    @Test
    fun springDstUsesTwentyThreeHourLocalDay() {
        assertDstDay(
            day = "2026-03-08",
            start = "2026-03-08T05:00:00Z",
            nextDay = "2026-03-09T04:00:00Z",
            hours = 23
        )
    }

    @Test
    fun fallDstUsesTwentyFiveHourLocalDay() {
        assertDstDay(
            day = "2026-11-01",
            start = "2026-11-01T04:00:00Z",
            nextDay = "2026-11-02T05:00:00Z",
            hours = 25
        )
    }

    private fun assertDstDay(day: String, start: String, nextDay: String, hours: Int) {
        val zone = ZoneId.of("America/New_York")
        val startUtc = Instant.parse(start).toEpochMilli()
        val endUtc = Instant.parse(nextDay).toEpochMilli() - 1
        val first = weight(2, startUtc)
        val last = weight(1, endUtc)
        val rows = listOf(weight(3, startUtc - 1), first, last, weight(4, endUtc + 1))
        val review = requireNotNull(reviewWeightRange(day, day, rows, zone))
        assertEquals(startUtc, review.startUtc)
        assertEquals(endUtc, review.endUtc)
        assertEquals(hours.toLong() * 60 * 60 * 1000, review.endUtc - review.startUtc + 1)
        assertEquals(listOf(last, first), review.entries)
        assertTrue(weightRangeUnchanged(review, rows.reversed(), zone))
    }

    @Test
    fun rangeSnapshotIgnoresOrderAndUnrelatedRows() {
        val rows = listOf(weight(2), weight(1, lastInstant))
        val review = requireNotNull(reviewWeightRange("2026-03-08", "2026-03-08", rows, utc))
        assertTrue(weightRangeUnchanged(review, rows.reversed() + weight(3, lastInstant + 1), utc))
        assertTrue(weightRangeUnchanged(review, rows.map { it.copy() }, utc))
    }

    @Test
    fun sameCountReplacementAndEveryWeightFieldChangeInvalidateReview() {
        val row = weight(1)
        val review = requireNotNull(reviewWeightRange(
            "2026-03-08", "2026-03-08", listOf(row), utc
        ))
        listOf(
            row.copy(id = 2),
            row.copy(weightLbs = 181.0),
            row.copy(timestampUtc = firstInstant + 1),
            row.copy(timestampUtc = lastInstant + 1),
            row.copy(note = "Changed"),
            row.copy(source = "import")
        ).forEach { changed ->
            assertFalse(weightRangeUnchanged(review, listOf(changed), utc))
        }
        assertFalse(weightRangeUnchanged(review, emptyList(), utc))
        assertFalse(weightRangeUnchanged(review, listOf(row, weight(2)), utc))
    }

    @Test
    fun changedZoneOrReconstructedBoundsInvalidateReview() {
        val rows = listOf(weight(1))
        val review = requireNotNull(reviewWeightRange("2026-03-08", "2026-03-08", rows, utc))
        assertFalse(weightRangeUnchanged(review, rows, ZoneId.of("America/New_York")))
        // Even an equivalent offset with a different zone ID requires a fresh review.
        assertFalse(weightRangeUnchanged(review, rows, ZoneId.of("Etc/UTC")))
        assertFalse(weightRangeUnchanged(review.copy(startUtc = firstInstant + 1), rows, utc))
        assertFalse(weightRangeUnchanged(review.copy(endUtc = lastInstant - 1), rows, utc))
        assertFalse(weightRangeUnchanged(review.copy(endDate = LocalDate.MAX), rows, utc))
    }

    @Test
    fun daySnapshotIgnoresOrderAndOtherDates() {
        val rows = listOf(food("b"), food("a"))
        val otherDay = food("other", date.minusDays(1))
        assertTrue(calorieDayUnchanged(date, rows, rows.reversed() + otherDay))
        assertTrue(calorieDayUnchanged(date, rows + otherDay, rows.map { it.copy() }))
        assertTrue(calorieDayUnchanged(date, emptyList(), listOf(otherDay)))
    }

    @Test
    fun dayAdditionsRemovalsAndSameCountReplacementsInvalidateSnapshot() {
        val rows = listOf(food("a"), food("b"))
        assertFalse(calorieDayUnchanged(date, rows, rows + food("c")))
        assertFalse(calorieDayUnchanged(date, rows, rows.take(1)))
        assertFalse(calorieDayUnchanged(date, rows, emptyList()))
        assertFalse(calorieDayUnchanged(date, rows, listOf(food("a"), food("c"))))
    }

    @Test
    fun everyFoodFieldChangeInvalidatesSnapshot() {
        val row = food("a")
        listOf(
            row.copy(id = "replacement"),
            row.copy(date = date.plusDays(1)),
            row.copy(name = "Rice"),
            row.copy(caloriesPerServing = 121.0),
            row.copy(proteinPerServing = 5.0),
            row.copy(servings = 3.0)
        ).forEach { changed ->
            assertFalse(calorieDayUnchanged(date, listOf(row), listOf(changed)))
        }
    }
}
