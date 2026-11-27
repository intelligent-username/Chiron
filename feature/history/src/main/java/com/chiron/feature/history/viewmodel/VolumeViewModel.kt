package com.chiron.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chiron.core.common.TimelineWindow
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
    private var earliestDate: LocalDate? = null

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
                val byDate = HashMap<LocalDate, Double>()
                for (row in rawRows) {
                    if (row.dateUtc <= 0L) continue
                    val date = runCatching {
                        Instant.ofEpochMilli(row.dateUtc).atZone(zone).toLocalDate()
                    }.getOrDefault(today)
                    if (date < minAllowedDate || date > today) continue
                    byDate[date] = (byDate[date] ?: 0.0) + row.volumeLbs
                }
                allDailyVolumes = byDate

                earliestDate = byDate.keys.minOrNull()
                val firstDate = earliestDate ?: today
                val firstWeekStart = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

                val todayWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                val maxWeekCount = if (exerciseFilter != null) {
                    val totalWeeks = ChronoUnit.WEEKS.between(firstWeekStart, todayWeek).toInt() + 1
                    maxOf(2, totalWeeks)
                } else {
                    val daysSinceFirstWorkout = ChronoUnit.DAYS.between(firstDate, today).coerceAtLeast(0L)
                    val maxDays = maxOf(90.0, daysSinceFirstWorkout / 3.0)
                    ceil(maxDays / 7.0).toInt().coerceAtLeast(2)
                }

                val totalWeeks = (ChronoUnit.WEEKS.between(firstWeekStart, todayWeek).toInt() + 1).coerceIn(1, 520)
                val weeklyTotalsArray = DoubleArray(totalWeeks)
                for ((date, vol) in byDate) {
                    if (vol <= 0.0) continue
                    val weekIndex = ChronoUnit.WEEKS.between(firstWeekStart, date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))).toInt()
                    if (weekIndex in 0 until totalWeeks) {
                        weeklyTotalsArray[weekIndex] += vol
                    }
                }
                val weeklyTotals = weeklyTotalsArray.toList()

                val rollingWeekCount = ((ChronoUnit.DAYS.between(firstDate, today) / 7) + 1)
                    .toInt().coerceIn(1, 4)
                val recentWeeks = (0 until rollingWeekCount).map { index ->
                    val end = today.minusDays(index * 7L)
                    val start = end.minusDays(6)
                    byDate.entries.sumOf { (date, volume) ->
                        if (date >= start && date <= end) volume else 0.0
                    }
                }
                val thisWeek = recentWeeks.firstOrNull() ?: 0.0
                val lastWeek = recentWeeks.getOrNull(1) ?: 0.0
                val allTimeTotal = weeklyTotals.sum()
                val highestEver = weeklyTotals.maxOrNull() ?: 0.0
                val lowestEver = weeklyTotals.filter { it > 0 }.minOrNull() ?: 0.0
                val rollingWeeklyAvg = recentWeeks.average()

                val rollingVolChange = if (recentWeeks.size >= 2) {
                    val diffs = recentWeeks.asReversed().zipWithNext { a, b -> b - a }
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
                    val updated = state.copy(
                        isLoading = false,
                        maxWeekCount = maxWeekCount,
                        weekCount = state.weekCount.coerceIn(2, maxWeekCount),
                        stats = stats
                    )
                    withWindow(updated, windowFor(updated, today))
                }
            }
        }
    }

    fun setMode(mode: VolumeMode) {
        _uiState.update { state ->
            if (state.mode == mode) return@update state
            val updated = state.copy(mode = mode, isAtCurrentWeek = true)
            withWindow(updated, windowFor(updated))
        }
    }

    fun setWeekCount(count: Int) {
        _uiState.update { state ->
            val clampedCount = count.coerceIn(2, state.maxWeekCount)
            if (clampedCount == state.weekCount) return@update state
            val updated = state.copy(weekCount = clampedCount, isAtCurrentWeek = true)
            withWindow(updated, windowFor(updated))
        }
    }

    fun goToPreviousWeek() {
        _uiState.update { state ->
            val current = windowFor(state)
            val previous = current.previous()
            if (previous == current) return@update state
            withWindow(state, previous)
        }
    }

    fun goToNextWeek() {
        _uiState.update { state ->
            val current = windowFor(state)
            val next = current.next()
            if (next == current) return@update state
            withWindow(state, next)
        }
    }

    fun toggleAbridgeGaps() {
        _uiState.update { state ->
            val updated = state.copy(abridgeGaps = !state.abridgeGaps)
            withWindow(updated, windowFor(updated))
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun windowFor(state: VolumeUiState, today: LocalDate = LocalDate.now()): TimelineWindow =
        TimelineWindow.endingAt(
            end = if (state.isAtCurrentWeek) today else state.currentPeriodEnd,
            spanDays = if (state.mode == VolumeMode.BY_DAY) 7 else state.weekCount * 7,
            earliest = earliestDate,
            today = today
        )

    private fun withWindow(state: VolumeUiState, window: TimelineWindow): VolumeUiState {
        val points = (0 until window.spanDays).map { offset ->
            val date = window.start.plusDays(offset.toLong())
            val label = when {
                state.mode == VolumeMode.BY_DAY -> date.format(VolumeDayFormatter)
                offset % 7 == 0 || offset == window.spanDays - 1 -> date.format(VolumeDateMonthDayFormatter)
                else -> ""
            }
            VolumePoint(label, allDailyVolumes[date] ?: 0.0, date)
        }
        return state.copy(
            currentPeriodEnd = window.end,
            points = if (state.abridgeGaps) {
                points.filter { it.volumeLbs > 0.0 }
            } else points,
            isAtFirstWeek = !window.canGoPrevious,
            isAtCurrentWeek = !window.canGoNext
        )
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

private val VolumeDateMonthDayFormatter = DateTimeFormatter.ofPattern("M/d")
private val VolumeDayFormatter = DateTimeFormatter.ofPattern("EEE")
