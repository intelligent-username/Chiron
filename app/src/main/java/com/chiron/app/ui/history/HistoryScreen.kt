package com.chiron.app.ui.history

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.ThinOutline
import com.chiron.app.ui.theme.CoolGray
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

            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
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
                onCreate = { dayTag, locationTag, _ -> viewModel.createNewWorkout(dayTag, locationTag); showCreateDialog = false },
                settingsRepository = viewModel.getSettingsRepository(),
                existingLocations = state.workouts.map { it.locationTag }.distinct(),
                existingDayTags = state.dayTags
            )
        }

        com.chiron.app.ui.components.UndoSnackbar(
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

@Composable
private fun SegmentedButtonItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ElectricBlue else SolidSlate)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else CoolGray
        )
    }
}

@Composable
private fun LocationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) ElectricBlue else SolidSlate)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = ThinOutline,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (isLarge) 14.dp else 12.dp, vertical = if (isLarge) 9.5.dp else 6.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (isLarge) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else CoolGray,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
