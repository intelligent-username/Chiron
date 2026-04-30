package com.chiron.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chiron.app.data.ChironRepository
import com.chiron.app.data.dao.DailyVolume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// ── Public data types ─────────────────────────────────────────────────────────

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
    /** True when we're at the oldest possible week (can't go further back) */
    val isAtFirstWeek: Boolean = false,
    /** True when we're at the current week (can't go forward) */
    val isAtCurrentWeek: Boolean = true,
    val stats: VolumeStats = VolumeStats()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class VolumeViewModel(
    private val repository: ChironRepository,
    private val displayInKg: Boolean = false
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolumeUiState(displayInKg = displayInKg))
    val uiState: StateFlow<VolumeUiState> = _uiState.asStateFlow()

    /** All loaded daily volumes, indexed by LocalDate. */
    private var allDailyVolumes: Map<LocalDate, Double> = emptyMap()
    /** The first Sunday on or before the earliest recorded workout. */
    private var firstWeekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    private var exerciseFilter: Long? = null

    init {
        load()
    }

    fun refresh() {
        load()
    }

    fun setExerciseFilter(exerciseId: Long?) {
        if (exerciseFilter != exerciseId) {
            exerciseFilter = exerciseId
            load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val rawRows: List<DailyVolume> = runCatching {
                repository.getVolumeSummaryByDay(exerciseFilter)
            }.getOrDefault(emptyList())

            val zone = ZoneId.systemDefault()
            val byDate = rawRows.associate { row ->
                val date = Instant.ofEpochMilli(row.dateUtc).atZone(zone).toLocalDate()
                date to row.volumeLbs
            }
            allDailyVolumes = byDate

            // Compute the earliest week boundary
            val earliestDate = byDate.keys.minOrNull() ?: LocalDate.now()
            firstWeekStart = earliestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

            val weeklyTotals = mutableListOf<Double>()
            var w = firstWeekStart
            val todayWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            while (w <= todayWeek) {
                var total = 0.0
                for(d in 0..6) {
                    total += byDate[w.plusDays(d.toLong())] ?: 0.0
                }
                weeklyTotals.add(total)
                w = w.plusWeeks(1)
            }

            val thisWeek = weeklyTotals.lastOrNull() ?: 0.0
            val lastWeek = if (weeklyTotals.size >= 2) weeklyTotals[weeklyTotals.size - 2] else 0.0
            val allTimeTotal = weeklyTotals.sum()
            val highestEver = weeklyTotals.maxOrNull() ?: 0.0
            val lowestEver = weeklyTotals.filter { it > 0 }.minOrNull() ?: 0.0
            val rollingWeeklyAvg = if (weeklyTotals.size >= 4) {
                weeklyTotals.takeLast(4).average()
            } else if (weeklyTotals.isNotEmpty()) {
                weeklyTotals.average()
            } else 0.0

            val rollingVolChange = if (weeklyTotals.size >= 2) {
                val last4 = weeklyTotals.takeLast(4)
                val diffs = last4.zipWithNext { a, b -> b - a }
                diffs.average()
            } else 0.0

            val stats = VolumeStats(
                thisWeek = thisWeek,
                lastWeek = lastWeek,
                rollingWeeklyAvg = rollingWeeklyAvg,
                rollingVolChange = rollingVolChange,
                highestEver = highestEver,
                lowestEver = lowestEver,
                allTimeTotal = allTimeTotal
            )

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    points = buildPoints(state.mode, state.currentWeekStart, state.weekCount),
                    isAtFirstWeek = state.currentWeekStart <= firstWeekStart,
                    isAtCurrentWeek = isCurrentWeek(state.currentWeekStart),
                    stats = stats
                )
            }
        }
    }

    fun setMode(mode: VolumeMode) {
        _uiState.update { state ->
            state.copy(
                mode = mode,
                points = buildPoints(mode, state.currentWeekStart, state.weekCount)
            )
        }
    }

    fun setWeekCount(count: Int) {
        _uiState.update { state ->
            state.copy(
                weekCount = count,
                points = buildPoints(state.mode, state.currentWeekStart, count)
            )
        }
    }

    fun goToPreviousWeek() {
        _uiState.update { state ->
            val step = if (state.mode == VolumeMode.BY_DAY) 1 else state.weekCount
            val newWeek = state.currentWeekStart.minusWeeks(step.toLong())
            if (newWeek < firstWeekStart) return@update state
            state.copy(
                currentWeekStart = newWeek,
                points = buildPoints(state.mode, newWeek, state.weekCount),
                isAtFirstWeek = newWeek <= firstWeekStart,
                isAtCurrentWeek = isCurrentWeek(newWeek)
            )
        }
    }

    fun goToNextWeek() {
        _uiState.update { state ->
            val todayWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val step = if (state.mode == VolumeMode.BY_DAY) 1 else state.weekCount
            val newWeek = state.currentWeekStart.plusWeeks(step.toLong())
            if (newWeek > todayWeek) return@update state
            state.copy(
                currentWeekStart = newWeek,
                points = buildPoints(state.mode, newWeek, state.weekCount),
                isAtFirstWeek = newWeek <= firstWeekStart,
                isAtCurrentWeek = isCurrentWeek(newWeek)
            )
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun isCurrentWeek(weekStart: LocalDate): Boolean {
        val todayWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return weekStart >= todayWeek
    }

    /**
     * BY_DAY: 7 points (Sun–Sat) for the week anchored at [weekStart].
     * BY_WEEK: daily points for [weekCount] weeks ending at the week of [weekStart].
     */
    private fun buildPoints(mode: VolumeMode, weekStart: LocalDate, weekCount: Int): List<VolumePoint> {
        return when (mode) {
            VolumeMode.BY_DAY -> buildDayPoints(weekStart)
            VolumeMode.BY_WEEK -> buildLongTermPoints(weekStart, weekCount)
        }
    }

    private fun buildDayPoints(weekStart: LocalDate): List<VolumePoint> {
        val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val vol = allDailyVolumes[date] ?: 0.0
            VolumePoint(
                label = dayLabels[offset],
                volumeLbs = vol,
                date = date
            )
        }
    }

    private fun buildLongTermPoints(weekStart: LocalDate, weekCount: Int): List<VolumePoint> {
        val clusterStart = weekStart.minusWeeks((weekCount - 1).toLong())
        val points = mutableListOf<VolumePoint>()
        
        val fmt = java.time.format.DateTimeFormatter.ofPattern("M/d")
        for (w in 0 until weekCount) {
            val currentW = clusterStart.plusWeeks(w.toLong())
            for (d in 0..6) {
                val date = currentW.plusDays(d.toLong())
                val vol = allDailyVolumes[date] ?: 0.0
                points.add(
                    VolumePoint(
                        label = if (d == 0) currentW.format(fmt) else "",
                        volumeLbs = vol,
                        date = date
                    )
                )
            }
        }
        return points
    }

    class Factory(
        private val repository: ChironRepository,
        private val displayInKg: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VolumeViewModel(repository, displayInKg) as T
    }
}
