package com.chiron.feature.timer

import com.chiron.core.common.CalorieEntry
import java.time.LocalDate
import kotlin.math.ceil

data class WeightCaloriePoint(
    val weightDate: LocalDate,
    val calorieDate: LocalDate,
    val calories: Double
)

data class WeightCalorieOverlay(
    val points: List<WeightCaloriePoint>,
    val lagDays: Int,
    val axisMaximum: Double
)

object WeightCalorieOverlayCalculator {
    /** Positive lag compares weight on D with calories on D + lag; negative lag uses earlier calories. */
    fun build(
        weightPoints: List<BodyweightPoint>,
        calorieEntries: List<CalorieEntry>,
        lagDays: Int
    ): WeightCalorieOverlay {
        require(lagDays in -3..3) { "Lag must be an integer between -3 and 3 days" }
        val totals = calorieEntries.groupBy { it.date }.mapValues { (_, entries) -> entries.sumOf { it.calories } }
        val points = weightPoints.map { weight ->
            val calorieDate = weight.date.plusDays(lagDays.toLong())
            WeightCaloriePoint(weight.date, calorieDate, totals[calorieDate] ?: 0.0)
        }
        val maximum = points.maxOfOrNull { it.calories } ?: 0.0
        val axisMaximum = (ceil(maximum / 500.0) * 500.0).coerceAtLeast(500.0)
        return WeightCalorieOverlay(points, lagDays, axisMaximum)
    }
}
