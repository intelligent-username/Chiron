package com.chiron.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chiron.core.model.WorkoutSession
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenWorkout: (Long?) -> Unit,
    onOpenPrForExercise: (Long) -> Unit = {},
    onOpenExerciseDetail: (Long) -> Unit = {},
    onOpenSetInWorkout: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var workoutToDelete by remember { mutableStateOf<WorkoutSession?>(null) }
    var expandedWorkoutId by remember { mutableStateOf<Long?>(null) }
    var deleteMode by remember { mutableStateOf("archive") }

    val listState = rememberLazyListState()

    if (state.isEditorOpen && state.editingWorkoutId != null) {
        val workout = (state.workouts + state.archivedWorkouts).find { it.id == state.editingWorkoutId }
        key(state.editingWorkoutId) {
            WorkoutEditor(
                workout = workout,
                viewModel = viewModel,
                onClose = { viewModel.closeEditor() },
                onOpenPrForExercise = onOpenPrForExercise,
                onOpenExerciseDetail = onOpenExerciseDetail,
                onOpenSetInWorkout = onOpenSetInWorkout,
                modifier = modifier
            )
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Day tag filters (whole row for more space)
            if (state.dayTags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        LocationChip(
                            label = "All",
                            selected = state.selectedDayTag == null,
                            onClick = { viewModel.filterByDayTag(null) },
                            isLarge = true
                        )
                    }
                    items(state.dayTags) { tag ->
                        LocationChip(
                            label = tag,
                            selected = state.selectedDayTag == tag,
                            onClick = { viewModel.filterByDayTag(tag) },
                            isLarge = true
                        )
                    }
                }
            }

            // Location + Active/Archived segmented controller row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left half: Locations
                Box(modifier = Modifier.weight(1f)) {
                    if (state.locationTags.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                LocationChip(
                                    label = "All",
                                    selected = state.selectedLocationTag == null,
                                    onClick = { viewModel.filterByLocationTag(null) }
                                )
                            }
                            items(state.locationTags) { loc ->
                                LocationChip(
                                    label = loc,
                                    selected = state.selectedLocationTag == loc,
                                    onClick = { viewModel.filterByLocationTag(loc) }
                                )
                            }
                        }
                    }
                }

                // Right half: Active/Archived segmented controller
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SolidSlate)
                        .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButtonItem(
                            label = "Active",
                            selected = !state.showArchivedWorkouts,
                            onClick = { viewModel.setShowArchivedWorkouts(false) },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedButtonItem(
                            label = "Archived",
                            selected = state.showArchivedWorkouts,
                            onClick = { viewModel.setShowArchivedWorkouts(true) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            val baseWorkouts = if (state.showArchivedWorkouts) state.archivedWorkouts else state.workouts
            val filteredWorkouts = baseWorkouts.filter { workout ->
                val dayMatch = state.selectedDayTag == null ||
                    (state.selectedDayTag == "Untitled Workout" && (workout.dayTag == "Untitled Workout" || workout.dayTag.isBlank())) ||
                    workout.dayTag == state.selectedDayTag
                val locMatch = state.selectedLocationTag == null || workout.locationTag == state.selectedLocationTag
                dayMatch && locMatch
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
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
                onCreate = { dayTag, locationTag, dateIso -> 
                    showCreateDialog = false
                    viewModel.createNewWorkout(dayTag, locationTag, dateIso) 
                },
                settingsRepository = viewModel.getSettingsRepository(),
                existingLocations = state.workouts.map { it.locationTag }.distinct(),
                existingDayTags = state.dayTags
            )
        }

        UndoSnackbar(
            viewModel = viewModel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
