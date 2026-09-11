package com.chiron.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ── Public data types ─────────────────────────────────────────────────────────

// UI-only weigh-in row. Canonical lbs, mirrors future BodyWeightEntry shape.
// TODO(DB): replace with foundation BodyWeightEntry and observe via
// ChironRepository.observeBodyweights() after Tier-1 lifted.
data class BodyweightEntry(
    val id: Long,
    val timestampUtc: Long,
    val weightLbs: Double
)

/** A single point on the bodyweight line graph. */
data class BodyweightPoint(
    val label: String,
    val weightLbs: Double,
    val timestampUtc: Long,
    val date: LocalDate
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
    val entries: List<BodyweightEntry> = emptyList(),
    val error: String? = null
)

// ── ViewModel (UI-shell stub, memory only) ────────────────────────────────────

/**
 * In-memory stub for the bodyweight stats tab.
 * Holds hardcoded preview entries in canonical lbs. No persistence.
 * TODO(DB): collect ChironRepository.observeBodyweights() here after Tier-1 lifted.
 */
class BodyweightViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BodyweightUiState())
    val uiState: StateFlow<BodyweightUiState> = _uiState.asStateFlow()

    private val allEntries = mutableListOf<BodyweightEntry>()
    private var nextId = 1L
    private var firstWeekStart: LocalDate = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    init {
        loadPreview()
    }

    /** Rebuild stub state. TODO(DB): replace with re-collect of observeBodyweights(). */
    fun refresh() {
        rebuild { it.copy(isLoading = false, error = null) }
    }

    fun setMode(mode: BodyweightMode) {
        rebuild { it.copy(mode = mode) }
    }

    fun setWeekCount(count: Int) {
        rebuild { it.copy(weekCount = count.coerceIn(2, 10)) }
    }

    fun goToPreviousWeek() {
        _uiState.update { state ->
            val step = if (state.mode == BodyweightMode.BY_DAY) 1 else state.weekCount
            val newWeek = state.currentWeekStart.minusWeeks(step.toLong())
            if (newWeek < firstWeekStart) return@update state
            state.copy(
                currentWeekStart = newWeek,
                points = buildPoints(state.mode, newWeek, state.weekCount, state.abridgeGaps),
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
            state.copy(
                currentWeekStart = newWeek,
                points = buildPoints(state.mode, newWeek, state.weekCount, state.abridgeGaps),
                isAtFirstWeek = newWeek <= firstWeekStart,
                isAtCurrentWeek = isCurrentWeek(newWeek)
            )
        }
    }

    fun toggleAbridgeGaps() {
        _uiState.update { state ->
            val abridged = !state.abridgeGaps
            state.copy(
                abridgeGaps = abridged,
                points = buildPoints(state.mode, state.currentWeekStart, state.weekCount, abridged)
            )
        }
    }

    /** Log a weigh-in stamped now. TODO(DB): delegate to insertBodyweight() after Tier-1 lifted. */
    fun logWeight(weightLbs: Double) {
        val err = validate(weightLbs) ?: run {
            allEntries.add(BodyweightEntry(nextId++, System.currentTimeMillis(), weightLbs))
            sortAndClampFirstWeek()
            null
        }
        rebuild { it.copy(error = err) }
    }

    /** Edit weight, keep original timestamp. TODO(DB): delegate to updateBodyweight(). */
    fun updateEntry(id: Long, weightLbs: Double) {
        val err = validate(weightLbs) ?: run {
            val idx = allEntries.indexOfFirst { it.id == id }
            if (idx < 0) "Entry not found" else {
                allEntries[idx] = allEntries[idx].copy(weightLbs = weightLbs)
                null
            }
        }
        rebuild { it.copy(error = err) }
    }

    /** Delete a weigh-in. TODO(DB): delegate to deleteBodyweight(). */
    fun deleteEntry(id: Long) {
        allEntries.removeAll { it.id == id }
        rebuild { it.copy(error = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun validate(weightLbs: Double): String? = when {
        !weightLbs.isFinite() || weightLbs <= 0.0 -> "Enter a weight above 0"
        weightLbs < 20.0 || weightLbs > 1500.0 -> "Enter a weight between 20 and 1500 lbs"
        else -> null
    }

    private fun rebuild(transform: (BodyweightUiState) -> BodyweightUiState) {
        _uiState.update { state ->
            val points = buildPoints(state.mode, state.currentWeekStart, state.weekCount, state.abridgeGaps)
            transform(
                state.copy(
                    isLoading = false,
                    points = points,
                    stats = computeStats(points),
                    entries = allEntries.sortedByDescending { it.timestampUtc },
                    isAtFirstWeek = state.currentWeekStart <= firstWeekStart,
                    isAtCurrentWeek = isCurrentWeek(state.currentWeekStart)
                )
            )
        }
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
        val pts = when (mode) {
            BodyweightMode.BY_DAY -> buildDayPoints(weekStart)
            BodyweightMode.BY_WEEK -> buildLongTermPoints(weekStart, weekCount)
        }
        return if (abridgeGaps) pts.filter { it.weightLbs > 0.0 } else pts
    }

    private fun lastEntryOn(date: LocalDate): BodyweightEntry? {
        val zone = ZoneId.systemDefault()
        return allEntries.filter {
            Instant.ofEpochMilli(it.timestampUtc).atZone(zone).toLocalDate() == date
        }.maxByOrNull { it.timestampUtc }
    }

    private fun buildDayPoints(weekStart: LocalDate): List<BodyweightPoint> {
        val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val hit = lastEntryOn(date)
            BodyweightPoint(labels[offset], hit?.weightLbs ?: 0.0, hit?.timestampUtc ?: 0L, date)
        }
    }

    private fun buildLongTermPoints(weekStart: LocalDate, weekCount: Int): List<BodyweightPoint> {
        val clusterStart = weekStart.minusWeeks((weekCount - 1).toLong())
        val fmt = java.time.format.DateTimeFormatter.ofPattern("M/d")
        val out = mutableListOf<BodyweightPoint>()
        for (w in 0 until weekCount) {
            val current = clusterStart.plusWeeks(w.toLong())
            for (d in 0..6) {
                val date = current.plusDays(d.toLong())
                val hit = lastEntryOn(date)
                out.add(BodyweightPoint(if (d == 0) current.format(fmt) else "", hit?.weightLbs ?: 0.0, hit?.timestampUtc ?: 0L, date))
            }
        }
        return out
    }

    private fun sortAndClampFirstWeek() {
        allEntries.sortBy { it.timestampUtc }
        val zone = ZoneId.systemDefault()
        val earliest = allEntries.minOfOrNull {
            Instant.ofEpochMilli(it.timestampUtc).atZone(zone).toLocalDate()
        } ?: LocalDate.now()
        firstWeekStart = earliest.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }

    private fun loadPreview() {
        val day = 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        // Hardcoded preview entries, canonical lbs internally.
        val previewLbs = listOf(182.5, 181.0, 183.2, 180.4, 179.8, 181.6, 180.1)
        val daysAgo = listOf(20L, 16L, 13L, 9L, 6L, 3L, 1L)
        previewLbs.zip(daysAgo).forEach { (lbs, ago) ->
            allEntries.add(BodyweightEntry(nextId++, now - ago * day, lbs))
        }
        sortAndClampFirstWeek()
        rebuild { it.copy() }
    }

    private fun todayWeekStart(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    private fun isCurrentWeek(weekStart: LocalDate): Boolean = weekStart >= todayWeekStart()

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BodyweightViewModel() as T
    }
}
