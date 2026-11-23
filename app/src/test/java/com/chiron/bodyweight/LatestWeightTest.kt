package com.chiron.bodyweight

import com.chiron.core.model.BodyWeightEntry
import com.chiron.feature.timer.BodyweightPointCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LatestWeightTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 1, 10)

    private fun entry(id: Long, instant: String, weight: Double = 150.0) =
        BodyWeightEntry(id = id, timestampUtc = Instant.parse(instant).toEpochMilli(), weightLbs = weight)

    @Test
    fun emptyAndInvalidEntriesHaveNoReading() {
        val result = BodyweightPointCalculator.selectLatestWeights(
            listOf(BodyWeightEntry(timestampUtc = 0, weightLbs = 150.0),
                BodyWeightEntry(timestampUtc = 1, weightLbs = Double.NaN),
                BodyWeightEntry(timestampUtc = 1, weightLbs = -1.0)),
            today, zone
        )
        assertNull(result.latest)
        assertNull(result.previousDay)
        assertNull(BodyweightPointCalculator.selectLatestWeights(emptyList(), today, zone).latest)
    }

    @Test
    fun futureEntriesAreIgnoredInFavorOfStaleReadings() {
        val earlier = entry(1, "2026-01-08T12:00:00Z")
        val stale = entry(2, "2026-01-09T12:00:00Z")
        val future = entry(3, "2026-01-11T12:00:00Z")
        val staleResult = BodyweightPointCalculator.selectLatestWeights(listOf(stale, earlier), today, zone)
        assertEquals(stale, staleResult.latest?.entry)
        assertEquals(today.minusDays(1), staleResult.latest?.date)
        assertNull(staleResult.previousDay)
        val futureResult = BodyweightPointCalculator.selectLatestWeights(listOf(stale, future), today, zone)
        assertEquals(stale, futureResult.latest?.entry)
        assertEquals(today.minusDays(1), futureResult.latest?.date)
        assertNull(futureResult.previousDay)
        assertNull(BodyweightPointCalculator.selectLatestWeights(listOf(future), today, zone).latest)
    }

    @Test
    fun latestTodayAndMostRecentEarlierDayAreSelected() {
        val old = entry(1, "2026-01-08T18:00:00Z")
        val previous = entry(2, "2026-01-09T22:00:00Z")
        val morning = entry(3, "2026-01-10T08:00:00Z")
        val evening = entry(4, "2026-01-10T20:00:00Z")
        val result = BodyweightPointCalculator.selectLatestWeights(listOf(morning, old, evening, previous), today, zone)
        assertEquals(evening, result.latest?.entry)
        assertEquals(previous, result.previousDay?.entry)
        assertEquals(today.minusDays(1), result.previousDay?.date)
        assertNull(BodyweightPointCalculator.selectLatestWeights(listOf(morning, evening), today, zone).previousDay)
        val future = entry(5, "2026-01-11T08:00:00Z")
        assertEquals(result, BodyweightPointCalculator.selectLatestWeights(listOf(future, morning, old, evening, previous), today, zone))
    }

    @Test
    fun tiesUseGreatestIdAndSecondsAreNormalized() {
        val millis = entry(2, "2026-01-10T12:00:00Z")
        val seconds = millis.copy(id = 3, timestampUtc = millis.timestampUtc / 1000)
        val result = BodyweightPointCalculator.selectLatestWeights(listOf(seconds, millis), today, zone)
        assertEquals(seconds.copy(timestampUtc = millis.timestampUtc), result.latest?.entry)
        assertEquals(result, BodyweightPointCalculator.selectLatestWeights(listOf(millis, seconds), today, zone))
    }

    @Test
    fun localZoneDeterminesTodayAndPreviousDay() {
        val la = ZoneId.of("America/Los_Angeles")
        val previous = entry(1, "2026-01-10T07:30:00Z")
        val current = entry(2, "2026-01-10T08:30:00Z")
        val result = BodyweightPointCalculator.selectLatestWeights(listOf(current, previous), today, la)
        assertEquals(today, result.latest?.date)
        assertEquals(today.minusDays(1), result.previousDay?.date)
        assertEquals(previous, result.previousDay?.entry)
    }
}
