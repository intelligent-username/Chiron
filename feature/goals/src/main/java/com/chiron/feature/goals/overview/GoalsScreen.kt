package com.chiron.feature.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val form by viewModel.goalForm.collectAsState()

    androidx.activity.compose.BackHandler(enabled = state.isDetailOpen || state.showEditDialog) {
        if (state.showEditDialog) {
            viewModel.dismissDialog()
        } else {
            viewModel.closeDetail()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            state.isDetailOpen -> {
                state.selectedGoal?.let { goal ->
                    GoalDetailScreen(
                        goal = goal,
                        exercises = state.selectedGoalExercises,
                        weekStart = state.currentWeekStart,
                        onEdit = { viewModel.openEditDialog(goal) },
                        onArchive = viewModel::archiveSelected,
                        onDelete = viewModel::deleteSelected,
                        onBack = viewModel::closeDetail,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            else -> {
                GoalsContent(
                    state = state,
                    onPrevWeek = viewModel::goToPreviousWeek,
                    onNextWeek = viewModel::goToNextWeek,
                    onOpenGoal = viewModel::openDetail
                )
            }
        }

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
