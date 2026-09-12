package com.chiron.feature.timer

import com.chiron.core.model.BodyWeightEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A single point on the bodyweight line graph. */
data class BodyweightPoint(
    val label: String,
    val weightLbs: Double,
    val timestampUtc: Long,
    val date: LocalDate,
    val isActualInput: Boolean = false
)

data class BodyweightStats(
    val current: Double? = null,
    val change: Double? = null,
    val average: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val count: Int = 0
)

enum class BodyweightMode { BY_DAY, BY_WEEK }

data class BodyweightUiState(
    val isLoading: Boolean = true,
    val mode: BodyweightMode = BodyweightMode.BY_DAY,
    val currentWeekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)),
    val currentPeriodEnd: LocalDate = LocalDate.now(),
    val points: List<BodyweightPoint> = emptyList(),
    val weekCount: Int = 5,
    val isAtFirstWeek: Boolean = false,
    val isAtCurrentWeek: Boolean = true,
    val abridgeGaps: Boolean = true,
    val maxWeekCount: Int = 52,
    val earliestDate: LocalDate? = null,
    val displayInKg: Boolean = false,
    val stats: BodyweightStats = BodyweightStats(),
    val entries: List<BodyWeightEntry> = emptyList(),
    val error: String? = null
)
