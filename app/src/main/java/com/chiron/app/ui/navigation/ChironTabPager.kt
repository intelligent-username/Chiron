package com.chiron.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chiron.core.model.Exercise
import com.chiron.core.ui.components.NavTab
import com.chiron.feature.exercises.ExerciseDetailScreen
import com.chiron.feature.exercises.ExercisesScreen
import com.chiron.feature.exercises.ExercisesViewModel
import com.chiron.feature.goals.GoalsScreen
import com.chiron.feature.goals.GoalsViewModel
import com.chiron.feature.history.HistoryScreen
import com.chiron.feature.history.HistoryViewModel
import com.chiron.feature.history.VolumeScreen
import com.chiron.feature.history.VolumeViewModel
import com.chiron.feature.timer.BodyweightStatsScreen
import com.chiron.feature.timer.BodyweightViewModel
import com.chiron.feature.timer.TimerScreen
import com.chiron.feature.timer.TimerViewModel

/**
 * Manages the main horizontal tab pager and hosts the screens for History, Exercises, and Timer.
 */
@Composable
fun ChironTabPager(
    pagerState: PagerState,
    tabs: Array<NavTab>,
    isEditorOpen: Boolean,
    isVolumeMode: Boolean,
    volumeViewModel: VolumeViewModel,
    displayInKg: Boolean,
    historyViewModel: HistoryViewModel,
    onOpenVolumePoint: (java.time.LocalDate) -> Unit,
    onOpenPrFromHistory: (Long) -> Unit,
    onOpenExerciseDetailFromHistory: (Long) -> Unit,
    onOpenSetInWorkout: (Long) -> Unit,
    isGoalsMode: Boolean,
    goalsViewModel: GoalsViewModel,
    exercisesViewModel: ExercisesViewModel,
    onOpenExerciseDetail: (Long) -> Unit,
    onExercisesSearchQueryChange: (Boolean) -> Unit,
    isExerciseDetailOpen: Boolean,
    activeExerciseId: Long?,
    exercises: List<Exercise>,
    archivedExercises: List<Exercise>,
    onExerciseSave: suspend (Exercise) -> Unit,
    onExerciseDelete: (Long) -> Unit,
    onExerciseUnarchive: (Long) -> Unit,
    onExerciseDeletePermanently: (Long) -> Unit,
    onExerciseOpenPr: (Long) -> Unit,
    onExerciseOpenWorkoutFromDate: (Long, java.time.LocalDate) -> Unit,
    onExerciseDetailClose: () -> Unit,
    isBodyweightMode: Boolean,
    bodyweightViewModel: BodyweightViewModel,
    onOpenBodyweightImport: () -> Unit,
    timerViewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !isEditorOpen,
        beyondViewportPageCount = 0,
        modifier = modifier.fillMaxSize()
    ) { page ->
        when (tabs[page]) {
            NavTab.HISTORY -> {
                if (isVolumeMode) {
                    VolumeScreen(
                        viewModel = volumeViewModel,
                        displayInKg = displayInKg,
                        onPointTap = { point -> onOpenVolumePoint(point.date) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onOpenWorkout = {},
                        onOpenPrForExercise = onOpenPrFromHistory,
                        onOpenExerciseDetail = onOpenExerciseDetailFromHistory,
                        onOpenSetInWorkout = onOpenSetInWorkout
                    )
                }
            }
            NavTab.EXERCISES -> Box(modifier = Modifier.fillMaxSize()) {
                if (isGoalsMode) {
                    GoalsScreen(
                        viewModel = goalsViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ExercisesScreen(
                        viewModel = exercisesViewModel,
                        onOpenDetail = onOpenExerciseDetail,
                        onSearchQueryChange = onExercisesSearchQueryChange
                    )
                    if (isExerciseDetailOpen) {
                        val exercise = exercises.find { it.id == activeExerciseId }
                            ?: archivedExercises.find { it.id == activeExerciseId }
                        ExerciseDetailScreen(
                            exercise = exercise,
                            volumeViewModel = volumeViewModel,
                            displayInKg = displayInKg,
                            onSave = onExerciseSave,
                            onDelete = onExerciseDelete,
                            onUnarchive = onExerciseUnarchive,
                            onDeletePermanently = onExerciseDeletePermanently,
                            onOpenPrForExercise = onExerciseOpenPr,
                            onOpenWorkoutFromDate = onExerciseOpenWorkoutFromDate,
                            onClose = onExerciseDetailClose,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            NavTab.TIMER -> {
                if (isBodyweightMode) {
                    BodyweightStatsScreen(
                        viewModel = bodyweightViewModel,
                        displayInKg = displayInKg,
                        onImportClick = onOpenBodyweightImport,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    TimerScreen(viewModel = timerViewModel)
                }
            }
        }
    }
}
