package com.chiron.feature.timer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chiron.core.database.ChironRepository
import com.chiron.core.database.bodyweight.BodyweightImportConfig
import com.chiron.core.database.dao.BodyweightUpsertCounts
import com.chiron.core.model.BodyWeightEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Real ViewModel over ChironRepository.observeBodyWeights. Canonical lbs. */
class BodyweightViewModel(
    private val repository: ChironRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BodyweightUiState())
    val uiState: StateFlow<BodyweightUiState> = _uiState.asStateFlow()

    private var allEntries: List<BodyWeightEntry> = emptyList()
    private var firstWeekStart: LocalDate = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    private var loadJob: Job? = null

    init {
        load()
    }

    /** Re-collect repository flow. */
    fun refresh() {
        load()
    }

    fun setMode(mode: BodyweightMode) {
        val current = _uiState.value
        val pts = BodyweightPointCalculator.buildPoints(mode, current.currentWeekStart, current.weekCount, allEntries)
        val stats = BodyweightPointCalculator.computeStats(pts)
        _uiState.update { state ->
            state.copy(mode = mode, points = pts, stats = stats)
        }
    }

    fun setWeekCount(count: Int) {
        val current = _uiState.value
        val clamped = count.coerceIn(2, current.maxWeekCount.coerceAtLeast(2))
        val pts = BodyweightPointCalculator.buildPoints(current.mode, current.currentWeekStart, clamped, allEntries)
        val stats = BodyweightPointCalculator.computeStats(pts)
        _uiState.update { state ->
            state.copy(weekCount = clamped, points = pts, stats = stats)
        }
    }

    fun goToPreviousWeek() {
        val current = _uiState.value
        val step = if (current.mode == BodyweightMode.BY_DAY) 1 else current.weekCount
        val newWeek = current.currentWeekStart.minusWeeks(step.toLong())
        if (newWeek < firstWeekStart) return
        val pts = BodyweightPointCalculator.buildPoints(current.mode, newWeek, current.weekCount, allEntries)
        val stats = BodyweightPointCalculator.computeStats(pts)
        _uiState.update { state ->
            state.copy(
                currentWeekStart = newWeek,
                points = pts,
                stats = stats,
                isAtFirstWeek = newWeek <= firstWeekStart,
                isAtCurrentWeek = BodyweightPointCalculator.isCurrentWeek(newWeek)
            )
        }
    }

    fun goToNextWeek() {
        val current = _uiState.value
        val todayWeek = BodyweightPointCalculator.todayWeekStart()
        val step = if (current.mode == BodyweightMode.BY_DAY) 1 else current.weekCount
        val newWeek = current.currentWeekStart.plusWeeks(step.toLong())
        if (newWeek > todayWeek) return
        val pts = BodyweightPointCalculator.buildPoints(current.mode, newWeek, current.weekCount, allEntries)
        val stats = BodyweightPointCalculator.computeStats(pts)
        _uiState.update { state ->
            state.copy(
                currentWeekStart = newWeek,
                points = pts,
                stats = stats,
                isAtFirstWeek = newWeek <= firstWeekStart,
                isAtCurrentWeek = BodyweightPointCalculator.isCurrentWeek(newWeek)
            )
        }
    }

    /** Log a weigh-in stamped now. */
    fun logWeight(weightLbs: Double) {
        val err = validate(weightLbs)
        if (err != null) {
            _uiState.update { it.copy(error = err) }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.insertBodyWeight(
                    BodyWeightEntry(timestampUtc = System.currentTimeMillis(), weightLbs = weightLbs)
                )
            }.onSuccess {
                _uiState.update { it.copy(error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Save failed") }
            }
        }
    }

    /** Edit weight, keep original timestamp. */
    fun updateEntry(id: Long, weightLbs: Double) {
        val err = validate(weightLbs)
        if (err != null) {
            _uiState.update { it.copy(error = err) }
            return
        }
        val existing = allEntries.firstOrNull { it.id == id }
        if (existing == null) {
            _uiState.update { it.copy(error = "Entry not found") }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.updateBodyWeight(existing.copy(weightLbs = weightLbs))
            }.onSuccess {
                _uiState.update { it.copy(error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Update failed") }
            }
        }
    }

    /** Delete a weigh-in. */
    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteBodyWeightById(id) }
                .onSuccess { _uiState.update { it.copy(error = null) } }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Delete failed") }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun importWeightsFromLines(
        lines: Sequence<String>,
        config: BodyweightImportConfig,
        onComplete: (Result<BodyweightUpsertCounts>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.importBodyWeightsFromLines(lines, config)
            onComplete(res)
        }
    }

    fun importWeightsFromFile(
        uri: Uri,
        config: BodyweightImportConfig,
        onComplete: (Result<BodyweightUpsertCounts>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.importBodyWeights(uri, config)
            onComplete(res)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { repository.observeBodyWeights() }
                .onSuccess { flow ->
                    flow.collect { rows -> onRows(rows) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private suspend fun onRows(rows: List<BodyWeightEntry>) = withContext(Dispatchers.Default) {
        allEntries = rows.filter { it.timestampUtc > 0L && it.weightLbs > 0.0 }.sortedBy { it.timestampUtc }
        firstWeekStart = BodyweightPointCalculator.computeFirstWeekStart(allEntries)
        val rawWeeks = runCatching {
            java.time.temporal.ChronoUnit.WEEKS.between(
                firstWeekStart.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                BodyweightPointCalculator.todayWeekStart().atStartOfDay(ZoneId.systemDefault()).toInstant()
            ).toInt() + 1
        }.getOrDefault(10)
        val maxWeeks = rawWeeks.coerceIn(2, 520)

        val current = _uiState.value
        val clampedWeekCount = current.weekCount.coerceIn(2, maxWeeks)
        val pts = BodyweightPointCalculator.buildPoints(current.mode, current.currentWeekStart, clampedWeekCount, allEntries)
        val stats = BodyweightPointCalculator.computeStats(pts)

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                weekCount = clampedWeekCount,
                points = pts,
                stats = stats,
                entries = allEntries.sortedByDescending { it.timestampUtc },
                maxWeekCount = maxWeeks,
                isAtFirstWeek = state.currentWeekStart <= firstWeekStart,
                isAtCurrentWeek = BodyweightPointCalculator.isCurrentWeek(state.currentWeekStart)
            )
        }
    }

    private fun validate(weightLbs: Double): String? = when {
        !weightLbs.isFinite() || weightLbs <= 0.0 -> "Enter a weight above 0"
        weightLbs < 20.0 || weightLbs > 1500.0 -> "Enter a weight between 20 and 1500 lbs"
        else -> null
    }

    class Factory(
        private val repository: ChironRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BodyweightViewModel(repository) as T
    }
}
