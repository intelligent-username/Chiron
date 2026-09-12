package com.chiron.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chiron.core.database.ChironRepository
import com.chiron.core.model.BodyWeightEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val points: List<BodyweightPoint> = emptyList(),
    val weekCount: Int = 5,
    val isAtFirstWeek: Boolean = false,
    val isAtCurrentWeek: Boolean = true,
    val abridgeGaps: Boolean = true,
    val stats: BodyweightStats = BodyweightStats(),
    val entries: List<BodyWeightEntry> = emptyList(),
    val error: String? = null
)

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
        _uiState.update { state ->
            state.copy(
                mode = mode,
                points = buildPoints(mode, state.currentWeekStart, state.weekCount, state.abridgeGaps),
                stats = computeStats(buildPoints(mode, state.currentWeekStart, state.weekCount, state.abridgeGaps))
            )
        }
    }

    fun setWeekCount(count: Int) {
        val clamped = count.coerceIn(2, 10)
        _uiState.update { state ->
            state.copy(
                weekCount = clamped,
                points = buildPoints(state.mode, state.currentWeekStart, clamped, state.abridgeGaps),
                stats = computeStats(buildPoints(state.mode, state.currentWeekStart, clamped, state.abridgeGaps))
            )
        }
    }

    fun goToPreviousWeek() {
        _uiState.update { state ->
            val step = if (state.mode == BodyweightMode.BY_DAY) 1 else state.weekCount
            val newWeek = state.currentWeekStart.minusWeeks(step.toLong())
            if (newWeek < firstWeekStart) return@update state
            val pts = buildPoints(state.mode, newWeek, state.weekCount, state.abridgeGaps)
            state.copy(
                currentWeekStart = newWeek,
                points = pts,
                stats = computeStats(pts),
                isAtFirstWeek = newWeek <= firstWeekStart,
                isAtCurrentWeek = isCurrentWeek(newWeek)
            )
        }
    }

    fun goToNextWeek() {
        _uiState.update { state ->
            val todayWeek = todayWeekStart()
            val step = if (state.mode == BodyweightMode.BY_DAY) 1 else state.weekCount
            val newWeek = state.currentWeekStart.plusWeeks(step.toLong())
            if (newWeek > todayWeek) return@update state
            val pts = buildPoints(state.mode, newWeek, state.weekCount, state.abridgeGaps)
            state.copy(
                currentWeekStart = newWeek,
                points = pts,
                stats = computeStats(pts),
                isAtFirstWeek = newWeek <= firstWeekStart,
                isAtCurrentWeek = isCurrentWeek(newWeek)
            )
        }
    }

    fun toggleAbridgeGaps() {
        _uiState.update { state ->
            val abridged = !state.abridgeGaps
            val pts = buildPoints(state.mode, state.currentWeekStart, state.weekCount, abridged)
            state.copy(abridgeGaps = abridged, points = pts, stats = computeStats(pts))
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

    private fun onRows(rows: List<BodyWeightEntry>) {
        allEntries = rows.sortedBy { it.timestampUtc }
        updateFirstWeekStart()
        _uiState.update { state ->
            val pts = buildPoints(state.mode, state.currentWeekStart, state.weekCount, state.abridgeGaps)
            state.copy(
                isLoading = false,
                points = pts,
                stats = computeStats(pts),
                entries = allEntries.sortedByDescending { it.timestampUtc },
                isAtFirstWeek = state.currentWeekStart <= firstWeekStart,
                isAtCurrentWeek = isCurrentWeek(state.currentWeekStart)
            )
        }
    }

    private fun validate(weightLbs: Double): String? = when {
        !weightLbs.isFinite() || weightLbs <= 0.0 -> "Enter a weight above 0"
        weightLbs < 20.0 || weightLbs > 1500.0 -> "Enter a weight between 20 and 1500 lbs"
        else -> null
    }

    private fun computeStats(points: List<BodyweightPoint>): BodyweightStats {
        val weights = points.map { it.weightLbs }
        if (weights.isEmpty()) return BodyweightStats()
        return BodyweightStats(
            current = weights.last(),
            change = if (weights.size >= 2) weights.last() - weights[weights.size - 2] else 0.0,
            average = weights.average(),
            min = weights.min(),
            max = weights.max(),
            count = weights.size
        )
    }

    private fun buildPoints(
        mode: BodyweightMode,
        weekStart: LocalDate,
        weekCount: Int,
        abridgeGaps: Boolean
    ): List<BodyweightPoint> {
        // Semi-abridged: always include all days; gaps extrapolate via LOCF forward.
        // Actual input days marked isActualInput=true; gap days false.
        val pts = when (mode) {
            BodyweightMode.BY_DAY -> buildDayPoints(weekStart)
            BodyweightMode.BY_WEEK -> buildLongTermPoints(weekStart, weekCount)
        }
        return pts
    }

    private fun lastEntryOn(date: LocalDate): BodyWeightEntry? {
        val zone = ZoneId.systemDefault()
        return allEntries.filter {
            Instant.ofEpochMilli(it.timestampUtc).atZone(zone).toLocalDate() == date
        }.maxByOrNull { it.timestampUtc }
    }

    private fun buildDayPoints(weekStart: LocalDate): List<BodyweightPoint> {
        val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val sortedWeights = allEntries.sortedBy { it.timestampUtc }
        return (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val hit = lastEntryOn(date)
            val dayStartMs = java.time.Instant.ofEpochMilli(
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            ).toEpochMilli()
            val resolvedLbs = if (hit != null) hit.weightLbs else BodyweightResolver.getWeightForTimestamp(dayStartMs, sortedWeights)
            val isActual = hit != null
            BodyweightPoint(
                label = labels[offset],
                weightLbs = resolvedLbs ?: 0.0,
                timestampUtc = hit?.timestampUtc ?: 0L,
                date = date,
                isActualInput = isActual
            )
        }
    }

    private fun buildLongTermPoints(weekStart: LocalDate, weekCount: Int): List<BodyweightPoint> {
        val clusterStart = weekStart.minusWeeks((weekCount - 1).toLong())
        val fmt = java.time.format.DateTimeFormatter.ofPattern("M/d")
        val out = mutableListOf<BodyweightPoint>()
        val sortedWeights = allEntries.sortedBy { it.timestampUtc }
        for (w in 0 until weekCount) {
            val current = clusterStart.plusWeeks(w.toLong())
            for (d in 0..6) {
                val date = current.plusDays(d.toLong())
                val hit = lastEntryOn(date)
                val dayStartMs = java.time.Instant.ofEpochMilli(
                    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                ).toEpochMilli()
                val resolvedLbs = if (hit != null) hit.weightLbs else BodyweightResolver.getWeightForTimestamp(dayStartMs, sortedWeights)
                val isActual = hit != null
                val label = if (d == 0) current.format(fmt) else ""
                out.add(BodyweightPoint(label, resolvedLbs ?: 0.0, hit?.timestampUtc ?: 0L, date, isActual))
            }
        }
        return out
    }

    private fun updateFirstWeekStart() {
        val zone = ZoneId.systemDefault()
        val earliest = allEntries.minOfOrNull {
            Instant.ofEpochMilli(it.timestampUtc).atZone(zone).toLocalDate()
        } ?: LocalDate.now()
        firstWeekStart = earliest.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }

    private fun todayWeekStart(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    private fun isCurrentWeek(weekStart: LocalDate): Boolean = weekStart >= todayWeekStart()

    class Factory(
        private val repository: ChironRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BodyweightViewModel(repository) as T
    }
}
