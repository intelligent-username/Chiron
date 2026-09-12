package com.chiron.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chiron.core.database.ChironRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

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

    private var volumeJob: Job? = null

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
        volumeJob?.cancel()
        volumeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            repository.getVolumeSummaryByDayFlow(exerciseFilter).collect { rawRows ->
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now()
                val minAllowedDate = today.minusYears(5)
                val byDate = rawRows.filter { it.dateUtc > 0L }.associate { row ->
                    val date = runCatching {
                        Instant.ofEpochMilli(row.dateUtc).atZone(zone).toLocalDate()
                    }.getOrDefault(today)
                    date to row.volumeLbs
                }
                allDailyVolumes = byDate

                // Compute the earliest week boundary (clamped to at most 5 years ago)
                val rawEarliestDate = byDate.keys.minOrNull() ?: today
                val earliestDate = if (rawEarliestDate.isBefore(minAllowedDate)) minAllowedDate else rawEarliestDate
                firstWeekStart = earliestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

                val todayWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                val maxWeekCount = if (exerciseFilter != null) {
                    val totalWeeks = ChronoUnit.WEEKS.between(firstWeekStart, todayWeek).toInt() + 1
                    maxOf(2, totalWeeks)
                } else {
                    val daysSinceFirstWorkout = ChronoUnit.DAYS.between(earliestDate, today).coerceAtLeast(0L)
                    val maxDays = maxOf(90.0, daysSinceFirstWorkout / 3.0)
                    ceil(maxDays / 7.0).toInt().coerceAtLeast(2)
                }

                val weeklyTotals = mutableListOf<Double>()
                var w = firstWeekStart
                var loopGuard = 0
                while (w <= todayWeek && loopGuard < 500) {
                    var total = 0.0
                    for (d in 0..6) {
                        total += byDate[w.plusDays(d.toLong())] ?: 0.0
                    }
                    weeklyTotals.add(total)
                    w = w.plusWeeks(1)
                    loopGuard++
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
                    val clampedWeekCount = state.weekCount.coerceIn(2, maxWeekCount)
                    val minWeek = minWeekStart(state.mode, clampedWeekCount)
                    val clampedWeek = if (state.currentWeekStart < minWeek) minWeek else state.currentWeekStart
                    state.copy(
                        isLoading = false,
                        maxWeekCount = maxWeekCount,
                        weekCount = clampedWeekCount,
                        currentWeekStart = clampedWeek,
                        points = buildPoints(state.mode, clampedWeek, clampedWeekCount, state.abridgeGaps),
                        isAtFirstWeek = clampedWeek <= minWeek,
                        isAtCurrentWeek = isCurrentWeek(clampedWeek),
                        stats = stats
                    )
                }
            }
        }
    }

    fun setMode(mode: VolumeMode) {
        _uiState.update { state ->
            val minWeek = minWeekStart(mode, state.weekCount)
            val todayWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val clampedWeek = when {
                state.currentWeekStart < minWeek -> minWeek
                state.currentWeekStart > todayWeek && minWeek <= todayWeek -> todayWeek
                else -> state.currentWeekStart
            }
            state.copy(
                mode = mode,
                currentWeekStart = clampedWeek,
                points = buildPoints(mode, clampedWeek, state.weekCount, state.abridgeGaps),
                isAtFirstWeek = clampedWeek <= minWeek,
                isAtCurrentWeek = isCurrentWeek(clampedWeek)
            )
        }
    }

    fun setWeekCount(count: Int) {
        _uiState.update { state ->
            val clampedCount = count.coerceIn(2, state.maxWeekCount)
            val minWeek = minWeekStart(state.mode, clampedCount)
            val todayWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val clampedWeek = when {
                state.currentWeekStart < minWeek -> minWeek
                state.currentWeekStart > todayWeek && minWeek <= todayWeek -> todayWeek
                else -> state.currentWeekStart
            }
            state.copy(
                weekCount = clampedCount,
                currentWeekStart = clampedWeek,
                points = buildPoints(state.mode, clampedWeek, clampedCount, state.abridgeGaps),
                isAtFirstWeek = clampedWeek <= minWeek,
                isAtCurrentWeek = isCurrentWeek(clampedWeek)
            )
        }
    }

    fun goToPreviousWeek() {
        _uiState.update { state ->
            val minWeek = minWeekStart(state.mode, state.weekCount)
            if (state.currentWeekStart <= minWeek) return@update state
            val step = if (state.mode == VolumeMode.BY_DAY) 1 else state.weekCount
            val candidateWeek = state.currentWeekStart.minusWeeks(step.toLong())
            val newWeek = if (candidateWeek < minWeek) minWeek else candidateWeek
            state.copy(
                currentWeekStart = newWeek,
                points = buildPoints(state.mode, newWeek, state.weekCount, state.abridgeGaps),
                isAtFirstWeek = newWeek <= minWeek,
                isAtCurrentWeek = isCurrentWeek(newWeek)
            )
        }
    }

    fun goToNextWeek() {
        _uiState.update { state ->
            val todayWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            if (state.currentWeekStart >= todayWeek) return@update state
            val step = if (state.mode == VolumeMode.BY_DAY) 1 else state.weekCount
            val candidateWeek = state.currentWeekStart.plusWeeks(step.toLong())
            val newWeek = if (candidateWeek > todayWeek) todayWeek else candidateWeek
            val minWeek = minWeekStart(state.mode, state.weekCount)
            state.copy(
                currentWeekStart = newWeek,
                points = buildPoints(state.mode, newWeek, state.weekCount, state.abridgeGaps),
                isAtFirstWeek = newWeek <= minWeek,
                isAtCurrentWeek = isCurrentWeek(newWeek)
            )
        }
    }

    fun toggleAbridgeGaps() {
        _uiState.update { state ->
            val newAbridge = !state.abridgeGaps
            state.copy(
                abridgeGaps = newAbridge,
                points = buildPoints(state.mode, state.currentWeekStart, state.weekCount, newAbridge)
            )
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Earliest allowed currentWeekStart.
     * In BY_DAY mode: firstWeekStart.
     * In BY_WEEK mode: firstWeekStart + (weekCount - 1) weeks, so that the window's
     * starting day is anchored directly at the first recorded workout week.
     */
    private fun minWeekStart(mode: VolumeMode, weekCount: Int): LocalDate {
        return when (mode) {
            VolumeMode.BY_DAY -> firstWeekStart
            VolumeMode.BY_WEEK -> firstWeekStart.plusWeeks((weekCount - 1).toLong())
        }
    }

    private fun isCurrentWeek(weekStart: LocalDate): Boolean {
        val todayWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return weekStart >= todayWeek
    }

    /**
     * BY_DAY: 7 points (Sun–Sat) for the week anchored at [weekStart].
     * BY_WEEK: daily points for [weekCount] weeks ending at the week of [weekStart].
     */
    private fun buildPoints(mode: VolumeMode, weekStart: LocalDate, weekCount: Int, abridgeGaps: Boolean): List<VolumePoint> {
        val pts = when (mode) {
            VolumeMode.BY_DAY -> buildDayPoints(weekStart)
            VolumeMode.BY_WEEK -> buildLongTermPoints(weekStart, weekCount)
        }
        return if (abridgeGaps) pts.filter { it.volumeLbs > 0.0 } else pts
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
        
        val fmt = DateTimeFormatter.ofPattern("M/d")
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
