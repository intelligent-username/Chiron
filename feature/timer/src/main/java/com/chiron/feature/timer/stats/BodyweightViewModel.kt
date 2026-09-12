package com.chiron.feature.timer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chiron.core.common.UserSettingsRepository
import com.chiron.core.database.ChironRepository
import com.chiron.core.database.bodyweight.BodyweightImportConfig
import com.chiron.core.database.dao.BodyweightUpsertCounts
import com.chiron.core.model.BodyWeightEntry
import java.time.LocalDate
import java.time.ZoneId
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
    private val repository: ChironRepository,
    private val userSettingsRepository: UserSettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(BodyweightUiState())
    val uiState: StateFlow<BodyweightUiState> = _uiState.asStateFlow()

    private var allEntries: List<BodyWeightEntry> = emptyList()
    private var earliestDate: LocalDate? = null
    private var loadJob: Job? = null

    init {
        // Observe persistent unit preference if repository provided
        userSettingsRepository?.let { settings ->
            viewModelScope.launch {
                settings.displayInKgFlow.collect { isKg ->
                    _uiState.update { it.copy(displayInKg = isKg) }
                }
            }
        }
        load()
    }

    /** Re-collect repository flow. */
    fun refresh() {
        load()
    }

    fun setDisplayInKg(value: Boolean) {
        _uiState.update { it.copy(displayInKg = value) }
        viewModelScope.launch {
            userSettingsRepository?.setDisplayInKg(value)
        }
    }

    fun setMode(mode: BodyweightMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setWeekCount(count: Int) {
        val current = _uiState.value
        val clamped = count.coerceIn(1, current.maxWeekCount.coerceAtLeast(1))
        val pts = BodyweightPointCalculator.buildPoints(
            periodEnd = current.currentPeriodEnd,
            weekCount = clamped,
            earliestDate = earliestDate,
            allEntries = allEntries
        )
        val stats = BodyweightPointCalculator.computeStats(pts)
        val windowStart = current.currentPeriodEnd.minusDays((clamped * 7 - 1).toLong())
        val isAtFirst = earliestDate == null || windowStart <= earliestDate
        _uiState.update { state ->
            state.copy(
                weekCount = clamped,
                points = pts,
                stats = stats,
                isAtFirstWeek = isAtFirst
            )
        }
    }

    fun goToPreviousWeek() {
        val current = _uiState.value
        val windowStart = current.currentPeriodEnd.minusDays((current.weekCount * 7 - 1).toLong())
        if (earliestDate != null && windowStart <= earliestDate) {
            return // Strictly at earliest date
        }

        val tentativeEnd = current.currentPeriodEnd.minusWeeks(1)
        val tentativeStart = tentativeEnd.minusDays((current.weekCount * 7 - 1).toLong())
        val newEnd = if (earliestDate != null && tentativeStart < earliestDate) {
            // Clamp so earliest date is the first visible date
            earliestDate!!.plusDays((current.weekCount * 7 - 1).toLong()).coerceAtMost(LocalDate.now())
        } else {
            tentativeEnd
        }

        if (newEnd == current.currentPeriodEnd) return

        val pts = BodyweightPointCalculator.buildPoints(
            periodEnd = newEnd,
            weekCount = current.weekCount,
            earliestDate = earliestDate,
            allEntries = allEntries
        )
        val stats = BodyweightPointCalculator.computeStats(pts)
        val newStart = newEnd.minusDays((current.weekCount * 7 - 1).toLong())

        _uiState.update { state ->
            state.copy(
                currentPeriodEnd = newEnd,
                points = pts,
                stats = stats,
                isAtFirstWeek = earliestDate == null || newStart <= earliestDate,
                isAtCurrentWeek = newEnd >= LocalDate.now()
            )
        }
    }

    fun goToNextWeek() {
        val current = _uiState.value
        val today = LocalDate.now()
        if (current.currentPeriodEnd >= today) return

        val newEnd = current.currentPeriodEnd.plusWeeks(1).coerceAtMost(today)
        val pts = BodyweightPointCalculator.buildPoints(
            periodEnd = newEnd,
            weekCount = current.weekCount,
            earliestDate = earliestDate,
            allEntries = allEntries
        )
        val stats = BodyweightPointCalculator.computeStats(pts)
        val newStart = newEnd.minusDays((current.weekCount * 7 - 1).toLong())

        _uiState.update { state ->
            state.copy(
                currentPeriodEnd = newEnd,
                points = pts,
                stats = stats,
                isAtFirstWeek = earliestDate == null || newStart <= earliestDate,
                isAtCurrentWeek = newEnd >= today
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
        earliestDate = BodyweightPointCalculator.computeEarliestInputDate(allEntries)
        val today = LocalDate.now()
        val maxWeeks = BodyweightPointCalculator.computeMaxWeeks(earliestDate, today)

        val current = _uiState.value
        val clampedWeekCount = current.weekCount.coerceIn(1, maxWeeks)
        val periodEnd = current.currentPeriodEnd.coerceAtMost(today)

        val pts = BodyweightPointCalculator.buildPoints(
            periodEnd = periodEnd,
            weekCount = clampedWeekCount,
            earliestDate = earliestDate,
            allEntries = allEntries
        )
        val stats = BodyweightPointCalculator.computeStats(pts)
        val windowStart = periodEnd.minusDays((clampedWeekCount * 7 - 1).toLong())
        val isAtFirst = earliestDate == null || windowStart <= earliestDate
        val isAtCurrent = periodEnd >= today

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                earliestDate = earliestDate,
                currentPeriodEnd = periodEnd,
                weekCount = clampedWeekCount,
                maxWeekCount = maxWeeks,
                points = pts,
                stats = stats,
                entries = allEntries.sortedByDescending { it.timestampUtc },
                isAtFirstWeek = isAtFirst,
                isAtCurrentWeek = isAtCurrent
            )
        }
    }

    private fun validate(weightLbs: Double): String? = when {
        !weightLbs.isFinite() || weightLbs <= 0.0 -> "Enter a weight above 0"
        weightLbs < 20.0 || weightLbs > 1500.0 -> "Enter a weight between 20 and 1500 lbs"
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
