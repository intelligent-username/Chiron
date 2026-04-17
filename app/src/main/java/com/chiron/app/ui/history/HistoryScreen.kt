package com.chiron.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.app.viewmodel.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenWorkout: (Long?) -> Unit,
    onOpenPrForExercise: (Long) -> Unit = {},
    onOpenExerciseDetail: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var workoutToDelete by remember { mutableStateOf<com.chiron.app.data.entities.WorkoutSession?>(null) }
    var expandedWorkoutId by remember { mutableStateOf<Long?>(null) }
    var deleteMode by remember { mutableStateOf("archive") }

    if (state.isEditorOpen && state.editingWorkoutId != null) {
        val workout = (state.workouts + state.archivedWorkouts).find { it.id == state.editingWorkoutId }
        WorkoutEditor(
            workout = workout,
            viewModel = viewModel,
            onClose = { viewModel.closeEditor() },
            onOpenPrForExercise = onOpenPrForExercise,
            onOpenExerciseDetail = onOpenExerciseDetail,
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 0.dp, bottom = 4.dp)) {
                FilterChip(selected = !state.showArchivedWorkouts, onClick = { viewModel.setShowArchivedWorkouts(false) }, label = { Text("Active") })
                FilterChip(selected = state.showArchivedWorkouts, onClick = { viewModel.setShowArchivedWorkouts(true) }, label = { Text("Archived") })
            }

            if (!state.showArchivedWorkouts && state.dayTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    item { FilterChip(selected = state.selectedDayTag == null, onClick = { viewModel.filterByDayTag(null) }, label = { Text("All") }) }
                    items(state.dayTags) { tag ->
                        FilterChip(selected = state.selectedDayTag == tag, onClick = { viewModel.filterByDayTag(tag) }, label = { Text(tag) })
                    }
                }
            }

            val baseWorkouts = if (state.showArchivedWorkouts) state.archivedWorkouts else state.workouts
            val filteredWorkouts = if (!state.showArchivedWorkouts && state.selectedDayTag != null) {
                state.workouts.filter {
                    if (state.selectedDayTag == "Untitled Workout") it.dayTag == "Untitled Workout" || it.dayTag.isBlank()
                    else it.dayTag == state.selectedDayTag
                }
            } else baseWorkouts

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filteredWorkouts) { workout ->
                    Box {
                        WorkoutCard(workout = workout, onClick = { viewModel.openEditor(workout.id) }, onLongClick = { expandedWorkoutId = workout.id })
                        DropdownMenu(expanded = expandedWorkoutId == workout.id, onDismissRequest = { expandedWorkoutId = null }) {
                            if (state.showArchivedWorkouts) {
                                DropdownMenuItem(text = { Text("Unarchive") }, onClick = { expandedWorkoutId = null; viewModel.unarchiveWorkout(workout.id) })
                                DropdownMenuItem(
                                    text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                                    onClick = { expandedWorkoutId = null; deleteMode = "permanent"; workoutToDelete = workout; showDeleteDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Archive", color = MaterialTheme.colorScheme.error) },
                                    onClick = { expandedWorkoutId = null; deleteMode = "archive"; workoutToDelete = workout; showDeleteDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!state.showArchivedWorkouts) {
            FloatingActionButton(onClick = { showCreateDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(Icons.Default.Add, contentDescription = "New workout")
            }
        }

        if (showCreateDialog && !state.showArchivedWorkouts) {
            WorkoutCreationDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { dayTag, locationTag, _ -> viewModel.createNewWorkout(dayTag, locationTag); showCreateDialog = false },
                settingsRepository = viewModel.getSettingsRepository(),
                existingLocations = state.workouts.map { it.locationTag }.distinct(),
                existingDayTags = state.dayTags
            )
        }
    }

    val workout = workoutToDelete
    if (showDeleteDialog && workout != null) {
        WorkoutDeleteDialog(
            workout = workout,
            mode = deleteMode,
            onConfirm = {
                if (deleteMode == "permanent") viewModel.permanentlyDeleteWorkout(workout.id)
                else viewModel.archiveWorkout(workout.id)
                showDeleteDialog = false
                workoutToDelete = null
            },
            onDismiss = { showDeleteDialog = false; workoutToDelete = null }
        )
    }
}
