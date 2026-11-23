package com.chiron.calories

import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.CalorieHistory
import com.chiron.core.common.CalorieTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalorieTrackingTest {
    private val day = LocalDate.of(2026, 1, 10)

    @Test
    fun servingTotalsAreCalculatedWithoutRounding() {
        val food = CalorieEntry("a", day, "Rice", 180.5, 4.0, 1.5)
        assertEquals(270.75, food.calories, 0.00001)
        assertEquals(6.0, food.protein, 0.00001)
    }

    @Test
    fun targetsApplyFromTheirEffectiveDates() {
        val old = CalorieTarget(day, 2000.0, 120.0)
        val newer = CalorieTarget(day.plusDays(3), 2500.0, 150.0)
        val history = CalorieHistory(targets = listOf(newer, old))
        assertNull(history.targetFor(day.minusDays(1)))
        assertEquals(old, history.targetFor(day.plusDays(2)))
        assertEquals(newer, history.targetFor(day.plusDays(3)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteCaloriesAreRejected() {
        CalorieEntry("a", day, "", Double.NaN, 0.0, 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroServingsAreRejected() {
        CalorieEntry("a", day, "", 100.0, 10.0, 0.0)
    }
}
