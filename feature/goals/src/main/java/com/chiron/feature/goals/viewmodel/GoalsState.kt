package com.chiron.feature.goals

import com.chiron.core.model.Exercise
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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
