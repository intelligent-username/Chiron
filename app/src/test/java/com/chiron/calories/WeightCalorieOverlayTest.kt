package com.chiron.calories

import com.chiron.core.common.CalorieEntry
import com.chiron.feature.timer.BodyweightPoint
import com.chiron.feature.timer.WeightCalorieOverlayCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightCalorieOverlayTest {
    private val day = LocalDate.of(2026, 1, 10)
    private val weights = (0..2).map { offset ->
        BodyweightPoint("", 150.0 + offset, 1L, day.plusDays(offset.toLong()), offset != 1)
    }

    private fun food(offset: Int, calories: Double, servings: Double = 1.0, id: String = "$offset") =
        CalorieEntry(id, day.plusDays(offset.toLong()), "", calories, 10.0, servings)

    @Test
    fun zeroLagSharesTheOriginalWeightDatesAndZeroFillsMissingCalories() {
        val overlay = WeightCalorieOverlayCalculator.build(weights, listOf(food(0, 1800.0), food(2, 2000.0)), 0)
        assertEquals(weights.map { it.date }, overlay.points.map { it.weightDate })
        assertEquals(weights.map { it.date }, overlay.points.map { it.calorieDate })
        assertEquals(listOf(1800.0, 0.0, 2000.0), overlay.points.map { it.calories })
    }

    @Test
    fun positiveLagUsesCaloriesFromLaterDatesWithoutShiftingWeights() {
        val overlay = WeightCalorieOverlayCalculator.build(weights, listOf(food(3, 2100.0), food(4, 2200.0)), 3)
        assertEquals(weights.map { it.date }, overlay.points.map { it.weightDate })
        assertEquals(day.plusDays(3), overlay.points.first().calorieDate)
        assertEquals(listOf(2100.0, 2200.0, 0.0), overlay.points.map { it.calories })
    }

    @Test
    fun negativeLagUsesCaloriesFromEarlierDatesIncludingOutsideTheDisplayedSpan() {
        val overlay = WeightCalorieOverlayCalculator.build(weights, listOf(food(-3, 1900.0), food(-2, 1700.0)), -3)
        assertEquals(day.minusDays(3), overlay.points.first().calorieDate)
        assertEquals(listOf(1900.0, 1700.0, 0.0), overlay.points.map { it.calories })
    }

    @Test
    fun totalsAllServingsAndFoodsForEachDay() {
        val overlay = WeightCalorieOverlayCalculator.build(weights,
            listOf(food(0, 600.0, 2.0, "first"), food(0, 850.0, 1.0, "second")), 0)
        assertEquals(2050.0, overlay.points.first().calories, 0.0)
        assertEquals(2500.0, overlay.axisMaximum, 0.0)
    }

    @Test
    fun axisOnlyUsesVisibleAlignedValuesAndLeavesWeightDataUntouched() {
        val original = weights.map { it.copy() }
        val overlay = WeightCalorieOverlayCalculator.build(weights, listOf(food(0, 1400.0), food(30, 9000.0)), 0)
        assertEquals(1500.0, overlay.axisMaximum, 0.0)
        assertEquals(original, weights)
        assertEquals(weights.size, overlay.points.size)
    }

    @Test
    fun emptyCalorieHistoryHasAFiniteNonzeroAxis() {
        val overlay = WeightCalorieOverlayCalculator.build(weights, emptyList(), 0)
        assertTrue(overlay.axisMaximum.isFinite() && overlay.axisMaximum > 0.0)
        assertTrue(overlay.points.all { it.calories == 0.0 })
    }

    @Test
    fun noWeightsProduceNoSyntheticWeightPoints() {
        val overlay = WeightCalorieOverlayCalculator.build(emptyList(), listOf(food(0, 2000.0)), 0)
        assertTrue(overlay.points.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLagOutsideTheSignedRange() {
        WeightCalorieOverlayCalculator.build(weights, emptyList(), -4)
    }
}
