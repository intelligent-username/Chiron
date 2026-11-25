package com.chiron.feature.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val form by viewModel.goalForm.collectAsState()
    val gridState = rememberLazyGridState()
    val motion = rememberGoalMotionState()
    val closeDetail: () -> Unit = {
        state.selectedGoal?.let { motion.begin(it.id, fromDetail = true) }
        viewModel.closeDetail()
    }

    androidx.activity.compose.BackHandler(enabled = state.isDetailOpen || state.showEditDialog) {
        if (state.showEditDialog) {
            viewModel.dismissDialog()
        } else {
            closeDetail()
        }
    }

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { motion.host = it }) {
        GoalMotionOverlay(motion, ringOnly = false)
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            state.isDetailOpen -> {
                state.selectedGoal?.let { goal ->
                    val currentIndex = state.goals.indexOfFirst { it.id == goal.id }
                    GoalDetailScreen(
                        goal = goal,
                        allGoals = state.goals,
                        currentIndex = currentIndex,
                        exercises = state.selectedGoalExercises,
                        weekStart = state.currentWeekStart,
                        onEdit = { viewModel.openEditDialog(goal) },
                        onArchive = { motion.cancel(); viewModel.archiveSelected() },
                        onDelete = { motion.cancel(); viewModel.deleteSelected() },
                        onBack = closeDetail,
                        onNavigate = { motion.cancel(); viewModel.openDetailAtIndex(it) },
                        modifier = Modifier.fillMaxSize(),
                        motion = motion
                    )
                }
            }
            else -> {
                GoalsContent(
                    state = state,
                    onPrevWeek = { motion.cancel(); viewModel.goToPreviousWeek() },
                    onNextWeek = { motion.cancel(); viewModel.goToNextWeek() },
                    onOpenGoal = {
                        motion.begin(it.id, fromDetail = false)
                        viewModel.openDetail(it)
                    },
                    gridState = gridState,
                    motion = motion
                )
            }
        }
        GoalMotionOverlay(motion, ringOnly = true)

        if (!state.isDetailOpen && !state.isLoading) {
            FloatingActionButton(
                onClick = viewModel::openCreateDialog,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New goal")
            }
        }

        if (state.showEditDialog) {
            GoalEditDialog(
                form = form,
                isEditing = state.selectedGoal != null,
                onNameChange = viewModel::updateFormName,
                onTargetChange = viewModel::updateFormTarget,
                onToggleExercise = viewModel::toggleExerciseSelection,
                onSearchChange = viewModel::updateExerciseSearchQuery,
                onSave = viewModel::saveGoal,
                onDismiss = viewModel::dismissDialog
            )
        }
    }
}
