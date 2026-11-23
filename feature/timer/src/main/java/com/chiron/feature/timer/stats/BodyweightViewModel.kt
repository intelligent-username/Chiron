package com.chiron.feature.timer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chiron.core.common.TimelineWindow
import com.chiron.core.common.UserSettingsRepository
import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.CalorieHistory
import com.chiron.core.common.CalorieTarget
import com.chiron.core.database.ChironRepository
import com.chiron.core.database.bodyweight.BodyweightImportConfig
import com.chiron.core.database.bodyweight.BodyweightFileParser
import com.chiron.core.database.dao.BodyweightUpsertCounts
import com.chiron.core.model.BodyWeightEntry
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Real ViewModel over ChironRepository.observeBodyWeights. Canonical lbs. */
class BodyweightViewModel(
    private val repository: ChironRepository,
    private val userSettingsRepository: UserSettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(BodyweightUiState())
    val uiState: StateFlow<BodyweightUiState> = _uiState.asStateFlow()
    private val _calorieHistory = MutableStateFlow(CalorieHistory())
    val calorieHistory: StateFlow<CalorieHistory> = _calorieHistory.asStateFlow()
    private val _calorieError = MutableStateFlow<String?>(null)
    val calorieError: StateFlow<String?> = _calorieError.asStateFlow()

    private var allEntries: List<BodyWeightEntry> = emptyList()
    private var earliestDate: LocalDate? = null
    private var preparedWeights = BodyweightPointCalculator.prepare(emptyList())
    private var loadJob: Job? = null

    init {
        load()
        userSettingsRepository?.let { settings ->
            viewModelScope.launch {
                try {
                    settings.calorieHistoryFlow.collect { _calorieHistory.value = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _calorieError.value = e.message ?: "Unable to load calorie history"
                }
            }
        }
    }

    fun saveCalorieEntries(entries: List<CalorieEntry>) = updateCalories { history ->
        val ids = entries.map { it.id }.toSet()
        history.copy(entries = history.entries.filterNot { it.id in ids } + entries)
    }

    fun deleteCalorieEntry(id: String) = updateCalories { history ->
        history.copy(entries = history.entries.filterNot { it.id == id })
    }

    fun saveCalorieTarget(target: CalorieTarget) = updateCalories { history ->
        history.copy(targets = history.targets.filterNot { it.date == target.date } + target)
    }

    private fun updateCalories(transform: (CalorieHistory) -> CalorieHistory) {
        val settings = userSettingsRepository
        if (settings == null) {
            _calorieError.value = "Calorie storage is unavailable"
            return
        }
        viewModelScope.launch {
            runCatching { settings.updateCalorieHistory(transform) }
                .onSuccess { _calorieError.value = null }
                .onFailure { _calorieError.value = it.message ?: "Could not save calorie data" }
        }
    }

    /** Re-collect repository flow. */
    fun refresh() {
        load()
    }

    /** Ensure period end stays anchored to current day if user was viewing current week across midnight rollover. */
    fun checkDayRollover() {
        val current = _uiState.value
        val today = LocalDate.now()
        if (current.isAtCurrentWeek && current.currentPeriodEnd < today) {
            val maxWeeks = BodyweightPointCalculator.computeMaxWeeks(earliestDate, today)
            _uiState.update { state ->
                val updated = state.copy(
                    weekCount = state.weekCount.coerceIn(1, maxWeeks),
                    maxWeekCount = maxWeeks
                )
                withWindow(updated, windowFor(updated, today))
            }
        }
    }

    fun setDisplayInKg(value: Boolean) {
        _uiState.update { it.copy(displayInKg = value) }
    }

    fun setMode(mode: BodyweightMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setWeekCount(count: Int) {
        _uiState.update { state ->
            val clamped = count.coerceIn(1, state.maxWeekCount.coerceAtLeast(1))
            if (clamped == state.weekCount) return@update state
            val updated = state.copy(weekCount = clamped, isAtCurrentWeek = true)
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

    /** Log a weigh-in stamped now or at a custom date/time. */
    fun logWeight(weightLbs: Double, timestampUtc: Long = System.currentTimeMillis()) {
        val err = validate(weightLbs) ?: if (isFutureWeightDate(timestampUtc)) FUTURE_WEIGHT_DATE_ERROR else null
        if (err != null) {
            _uiState.update { it.copy(error = err) }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.insertBodyWeight(
                    BodyWeightEntry(timestampUtc = timestampUtc, weightLbs = weightLbs)
                )
            }.onSuccess {
                _uiState.update { it.copy(error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Save failed") }
            }
        }
    }

    /** Edit weight and/or timestamp. */
    fun updateEntry(id: Long, weightLbs: Double, timestampUtc: Long? = null) {
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
        val updatedTimestamp = timestampUtc ?: existing.timestampUtc
        if (isFutureWeightDate(updatedTimestamp)) {
            _uiState.update { it.copy(error = FUTURE_WEIGHT_DATE_ERROR) }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.updateBodyWeight(existing.copy(weightLbs = weightLbs, timestampUtc = updatedTimestamp))
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

    /** Mass delete weigh-ins between [startUtc] and [endUtc] inclusive. */
    fun deleteEntriesInRange(startUtc: Long, endUtc: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteBodyWeightBetween(startUtc, endUtc) }
                .onSuccess { _uiState.update { it.copy(error = null) } }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to delete entries in range") }
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
            val res = try {
                val items = lines.toList()
                val parsed = BodyweightFileParser.parse(items.asSequence(), config)
                if (parsed.rows.any { isFutureWeightDate(it.timestampUtc) }) {
                    Result.failure(IllegalArgumentException(FUTURE_WEIGHT_DATE_ERROR))
                } else {
                    repository.importBodyWeightsFromLines(items.asSequence(), config)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            _uiState.update { it.copy(error = res.exceptionOrNull()?.message) }
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

    private fun windowFor(state: BodyweightUiState, today: LocalDate = LocalDate.now()): TimelineWindow =
        TimelineWindow.endingAt(
            end = if (state.isAtCurrentWeek) today else state.currentPeriodEnd,
            spanDays = state.weekCount * 7,
            earliest = earliestDate,
            today = today
        )

    private fun withWindow(state: BodyweightUiState, window: TimelineWindow): BodyweightUiState {
        val points = BodyweightPointCalculator.buildPoints(
            periodEnd = window.end,
            weekCount = state.weekCount,
            prepared = preparedWeights
        )
        return state.copy(
            currentPeriodEnd = window.end,
            points = points,
            stats = BodyweightPointCalculator.computeStats(points, allEntries.size),
            isAtFirstWeek = !window.canGoPrevious,
            isAtCurrentWeek = !window.canGoNext
        )
    }

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
        val entries = rows.filter { it.timestampUtc > 0L && it.weightLbs > 0.0 }.sortedBy { it.timestampUtc }
        val prepared = BodyweightPointCalculator.prepare(entries)
        val firstDate = BodyweightPointCalculator.computeEarliestInputDate(entries)
        val descendingEntries = entries.asReversed()
        val today = LocalDate.now()
        val maxWeeks = BodyweightPointCalculator.computeMaxWeeks(firstDate, today)

        // Publish the cache and rebuild from the latest UI state together; background
        // processing must not overwrite a navigation action made while it was running.
        withContext(Dispatchers.Main) {
            allEntries = entries
            earliestDate = firstDate
            preparedWeights = prepared
            _uiState.update { state ->
                val updated = state.copy(
                    isLoading = false,
                    earliestDate = firstDate,
                    weekCount = state.weekCount.coerceIn(1, maxWeeks),
                    maxWeekCount = maxWeeks,
                    entries = descendingEntries
                )
                withWindow(updated, windowFor(updated, today))
            }
        }
    }

    private fun validate(weightLbs: Double): String? = when {
        !weightLbs.isFinite() || weightLbs <= 0.0 -> "Enter a weight above 0"
        weightLbs < 20.0 || weightLbs > 1500.0 -> if (_uiState.value.displayInKg) "Enter a weight between 9 and 680 kg" else "Enter a weight between 20 and 1500 lbs"
        else -> null
    }

    class Factory(
        private val repository: ChironRepository,
        private val userSettingsRepository: UserSettingsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BodyweightViewModel(repository, userSettingsRepository) as T
    }
}
