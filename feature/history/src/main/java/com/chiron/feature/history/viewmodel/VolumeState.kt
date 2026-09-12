package com.chiron.feature.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A single point on the volume line graph. */
data class VolumePoint(
    val label: String,        // "Mon", "Tue" … for day mode; "W1", "W2" … for week mode
    val volumeLbs: Double,
    val date: LocalDate       // Sunday of the week (week mode) or the actual date (day mode)
)

data class VolumeStats(
    val thisWeek: Double = 0.0,
    val lastWeek: Double = 0.0,
    val rollingWeeklyAvg: Double = 0.0,
    val rollingVolChange: Double = 0.0,
    val highestEver: Double = 0.0,
    val lowestEver: Double = 0.0,
    val allTimeTotal: Double = 0.0
)

enum class VolumeMode { BY_DAY, BY_WEEK }

data class VolumeUiState(
    val isLoading: Boolean = true,
    val mode: VolumeMode = VolumeMode.BY_DAY,
    /** Sunday of the currently displayed week (day mode) */
    val currentWeekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)),
    /** Points to render on the graph for the current view */
    val points: List<VolumePoint> = emptyList(),
    val displayInKg: Boolean = false,
    val weekCount: Int = 5,
    val maxWeekCount: Int = 13,
    /** True when we're at the oldest possible week (can't go further back) */
    val isAtFirstWeek: Boolean = false,
    /** True when we're at the current week (can't go forward) */
    val isAtCurrentWeek: Boolean = true,
    val abridgeGaps: Boolean = false,
    val stats: VolumeStats = VolumeStats()
)
