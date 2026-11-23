package com.chiron.bodyweight

import com.chiron.feature.timer.isFutureWeightDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyweightDateValidationTest {
    @Test
    fun comparesLocalCalendarDatesRatherThanTimeOfDay() {
        val today = LocalDate.of(2026, 1, 10)
        val zone = ZoneId.of("America/Los_Angeles")
        fun millis(value: String) = Instant.parse(value).toEpochMilli()

        assertFalse(isFutureWeightDate(millis("2026-01-10T07:30:00Z"), today, zone))
        assertFalse(isFutureWeightDate(millis("2026-01-11T07:30:00Z"), today, zone))
        assertTrue(isFutureWeightDate(millis("2026-01-11T08:00:00Z"), today, zone))
        assertTrue(isFutureWeightDate(millis("2026-01-10T16:00:00Z"), today, ZoneId.of("Pacific/Kiritimati")))
    }
}
