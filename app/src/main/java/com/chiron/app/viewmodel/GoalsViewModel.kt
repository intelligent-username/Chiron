package com.chiron.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chiron.app.data.ChironRepository
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.Goal
import com.chiron.app.data.entities.GoalExercise
import com.chiron.app.util.Jaccard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// ── Public data types ─────────────────────────────────────────────────────────

data class GoalWithProgress(
    val id: Long,
    val name: String,
    val weeklyTarget: Int,
    val daysDone: Int,
    val exerciseCount: Int,
    val dayStatus: Map<LocalDate, Boolean>
)

data class GoalsUiState(
    val isLoading: Boolean = true,
    val goals: List<GoalWithProgress> = emptyList(),
    val currentWeekStart: LocalDate = currentSunday(),
    val isAtFirstWeek: Boolean = false,
    val isAtCurrentWeek: Boolean = true,
    val isDetailOpen: Boolean = false,
    val selectedGoal: GoalWithProgress? = null,
    val selectedGoalExercises: List<Exercise> = emptyList(),
    val showEditDialog: Boolean = false
) {
    companion object {
        fun currentSunday(): LocalDate =
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }
}

data class GoalFormState(
    val name: String = "",
    val weeklyTarget: Int = 2,
    val selectedExerciseIds: Set<Long> = emptySet(),
    val exerciseSearchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val allExercises: List<Exercise> = emptyList()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class GoalsViewModel(
    private val repository: ChironRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    private val _goalForm = MutableStateFlow(GoalFormState())
    val goalForm: StateFlow<GoalFormState> = _goalForm.asStateFlow()

    /** date -> exercise ids with >= 1 logged set on that date (device zone). */
    private var allDailySets: Map<LocalDate, Set<Long>> = emptyMap()

    /** The first Sunday on or before the earliest date with any set data. */
    private var firstWeekStart: LocalDate = GoalsUiState.currentSunday()

    private var goals: List<Goal> = emptyList()
    private var junctions: List<GoalExercise> = emptyList()
    private var exercises: List<Exercise> = emptyList()

    private var progressJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                repository.goalsFlow,
                repository.goalJunctionsFlow,
                repository.exercisesFlow,
                repository.setEntryCountFlow
            ) { goals, junctions, exercises, _ -> Triple(goals, junctions, exercises) }
                .collect { (g, j, e) ->
                    this@GoalsViewModel.goals = g
                    this@GoalsViewModel.junctions = j
                    this@GoalsViewModel.exercises = e
                    updateFormExercises()
                    refreshProgress()
                }
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private fun refreshProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val allExerciseIds = junctions.map { it.exerciseId }.distinct()
            val dailyMap = mutableMapOf<LocalDate, MutableSet<Long>>()
            if (allExerciseIds.isNotEmpty()) {
                val zone = ZoneId.systemDefault()
                repository.getSetTimestampsForExercises(allExerciseIds).forEach { row ->
                    val date = Instant.ofEpochMilli(row.timestampUtc).atZone(zone).toLocalDate()
                    dailyMap.getOrPut(date) { mutableSetOf() }.add(row.exerciseId)
                }
            }
            allDailySets = dailyMap
            firstWeekStart = (dailyMap.keys.minOrNull() ?: LocalDate.now())
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            _uiState.update { buildGoals(it.copy(isLoading = false)) }
        }
    }

    private fun updateFormExercises() {
        _goalForm.update { form ->
            val results = if (form.exerciseSearchQuery.isBlank()) {
                exercises
            } else {
                Jaccard.rankBySimilarity(form.exerciseSearchQuery, exercises, { it.name }, limit = 100)
            }
            form.copy(allExercises = exercises, searchResults = results)
        }
    }

    private fun buildGoals(state: GoalsUiState): GoalsUiState {
        val goalExercises = junctions.groupBy({ it.goalId }, { it.exerciseId })
        val goalList = goals.map { goal ->
            val ids = goalExercises[goal.id].orEmpty().toSet()
            val dayStatus = (0..6).associate { offset ->
                val date = state.currentWeekStart.plusDays(offset.toLong())
                date to (allDailySets[date]?.any { it in ids } == true)
            }
            GoalWithProgress(
                id = goal.id,
                name = goal.name,
                weeklyTarget = goal.weeklyTarget,
                daysDone = dayStatus.values.count { it },
                exerciseCount = ids.size,
                dayStatus = dayStatus
            )
        }
        val selected = state.selectedGoal
        val selectedGoalExercises = if (selected == null) {
            emptyList()
        } else {
            val ids = goalExercises[selected.id].orEmpty()
            exercises.filter { it.id in ids }
        }
        return state.copy(
            goals = goalList,
            selectedGoal = selected?.let { goalList.find { g -> g.id == it.id } },
            selectedGoalExercises = selectedGoalExercises,
            isAtFirstWeek = state.currentWeekStart <= firstWeekStart,
            isAtCurrentWeek = isCurrentWeek(state.currentWeekStart)
        )
    }

    private fun isCurrentWeek(weekStart: LocalDate): Boolean {
        return weekStart >= GoalsUiState.currentSunday()
    }

    // ── Week navigation ───────────────────────────────────────────────────────

    fun goToPreviousWeek() {
        _uiState.update { state ->
            val newWeek = state.currentWeekStart.minusWeeks(1)
            if (newWeek < firstWeekStart) return@update state
            buildGoals(state.copy(currentWeekStart = newWeek))
        }
    }

    fun goToNextWeek() {
        _uiState.update { state ->
            val newWeek = state.currentWeekStart.plusWeeks(1)
            if (newWeek > GoalsUiState.currentSunday()) return@update state
            buildGoals(state.copy(currentWeekStart = newWeek))
        }
    }

    // ── Detail / dialog ───────────────────────────────────────────────────────

    fun openDetail(goal: GoalWithProgress) {
        _uiState.update { state ->
            buildGoals(state.copy(isDetailOpen = true, selectedGoal = goal))
        }
    }

    fun closeDetail() {
        _uiState.update { it.copy(isDetailOpen = false, selectedGoal = null, selectedGoalExercises = emptyList()) }
    }

    fun openCreateDialog() {
        _goalForm.value = GoalFormState(
            allExercises = _goalForm.value.allExercises,
            searchResults = _goalForm.value.allExercises
        )
        _uiState.update { it.copy(showEditDialog = true) }
    }

    fun openEditDialog(goal: GoalWithProgress) {
        viewModelScope.launch {
            val junctionIds = repository.getJunctionsForGoal(goal.id).map { it.exerciseId }.toSet()
            val all = _goalForm.value.allExercises
            _goalForm.value = GoalFormState(
                name = goal.name,
                weeklyTarget = goal.weeklyTarget,
                selectedExerciseIds = junctionIds,
                allExercises = all,
                searchResults = all
            )
            _uiState.update { it.copy(showEditDialog = true) }
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showEditDialog = false) }
    }

    fun saveGoal() {
        val form = _goalForm.value
        if (form.name.isNotBlank() && form.weeklyTarget in 1..7 && form.selectedExerciseIds.isNotEmpty()) {
            viewModelScope.launch {
                val editing = _uiState.value.selectedGoal
                val goal = Goal(
                    id = editing?.id ?: 0L,
                    name = form.name.trim(),
                    weeklyTarget = form.weeklyTarget
                )
                repository.saveGoalWithExercises(goal, form.selectedExerciseIds.toList())
                _uiState.update { it.copy(showEditDialog = false) }
                if (editing != null) closeDetail()
            }
        }
    }

    fun archiveSelected() {
        val goal = _uiState.value.selectedGoal ?: return
        viewModelScope.launch {
            repository.archiveGoal(goal.id)
            _uiState.update { it.copy(isDetailOpen = false, selectedGoal = null) }
        }
    }

    fun deleteSelected() {
        val goal = _uiState.value.selectedGoal ?: return
        viewModelScope.launch {
            repository.deleteGoal(goal.id)
            _uiState.update { it.copy(isDetailOpen = false, selectedGoal = null) }
        }
    }

    // ── Form updates ──────────────────────────────────────────────────────────

    fun updateFormName(name: String) {
        _goalForm.update { it.copy(name = name) }
    }

    fun updateFormTarget(target: Int) {
        _goalForm.update { it.copy(weeklyTarget = target) }
    }

    fun toggleExerciseSelection(exerciseId: Long) {
        _goalForm.update { form ->
            val newSet = if (exerciseId in form.selectedExerciseIds) {
                form.selectedExerciseIds - exerciseId
            } else {
                form.selectedExerciseIds + exerciseId
            }
            form.copy(selectedExerciseIds = newSet)
        }
    }

    fun updateExerciseSearchQuery(query: String) {
        _goalForm.update { form ->
            val results = if (query.isBlank()) {
                form.allExercises
            } else {
                Jaccard.rankBySimilarity(query, form.allExercises, { it.name }, limit = 100)
            }
            form.copy(exerciseSearchQuery = query, searchResults = results)
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val repository: ChironRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GoalsViewModel(repository) as T
        }
    }
}
