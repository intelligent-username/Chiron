package com.chiron.feature.timer

import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.TimelineWindow
import com.chiron.core.model.BodyWeightEntry
import java.time.LocalDate

data class CalorieGraphPoint(
    val date: LocalDate,
    val calories: Double,
    val protein: Double,
    val weightDate: LocalDate,
    val weightLbs: Double?,
    val actualWeight: Boolean
)

object CalorieGraphCalculator {
    fun window(end: LocalDate, weeks: Int, entries: List<CalorieEntry>, today: LocalDate = LocalDate.now()): TimelineWindow =
        TimelineWindow.endingAt(end, weeks.coerceIn(1, 520) * 7, entries.minOfOrNull { it.date }, today)

    fun build(
        entries: List<CalorieEntry>,
        weights: List<BodyWeightEntry>,
        end: LocalDate,
        weeks: Int,
        lag: Int
    ): List<CalorieGraphPoint> {
        require(lag in -3..3)
        val count = weeks.coerceIn(1, 520) * 7
        val start = end.minusDays(count - 1L)
        val grouped = entries.groupBy { it.date }
        val weightByDate = BodyweightPointCalculator.buildPoints(
            end.plusDays(3), weeks.coerceIn(1, 520) + 1, BodyweightPointCalculator.prepare(weights)
        ).associateBy { it.date }
        return (0 until count).map { index ->
            val date = start.plusDays(index.toLong())
            val foods = grouped[date].orEmpty()
            val weightDate = date.plusDays(lag.toLong())
            val weight = weightByDate[weightDate]
            CalorieGraphPoint(date, foods.sumOf { it.calories }, foods.sumOf { it.protein },
                weightDate, weight?.weightLbs, weight?.isActualInput == true)
        }
    }

    fun history(entries: List<CalorieEntry>): List<CalorieEntry> =
        entries.sortedByDescending { it.date }
}
