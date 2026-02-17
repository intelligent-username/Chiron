package com.chiron.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenWorkout: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    
    // Deletion states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var workoutToDelete by remember { mutableStateOf<com.chiron.app.data.entities.WorkoutSession?>(null) }
    var expandedWorkoutId by remember { mutableStateOf<Long?>(null) }
    
    val scope = rememberCoroutineScope()

    // If editor is open, show WorkoutEditor instead
    if (state.isEditorOpen && state.editingWorkoutId != null) {
        val workout = state.workouts.find { it.id == state.editingWorkoutId }
        WorkoutEditor(
            workout = workout,
            viewModel = viewModel,
            onClose = { viewModel.closeEditor() },
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Filter chips for day tags
            if (state.dayTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedDayTag == null,
                            onClick = { viewModel.filterByDayTag(null) },
                            label = { Text("All") }
                        )
                    }
                    items(state.dayTags) { tag ->
                        FilterChip(
                            selected = state.selectedDayTag == tag,
                            onClick = { viewModel.filterByDayTag(tag) },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            // Workout list
            val filteredWorkouts = if (state.selectedDayTag != null) {
                state.workouts.filter { it.dayTag == state.selectedDayTag }
            } else {
                state.workouts
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredWorkouts) { workout ->
                    Box {
                        WorkoutCard(
                            workout = workout,
                            onClick = { viewModel.openEditor(workout.id) },
                            onLongClick = { expandedWorkoutId = workout.id }
                        )
                        
                        DropdownMenu(
                            expanded = expandedWorkoutId == workout.id,
                            onDismissRequest = { expandedWorkoutId = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    expandedWorkoutId = null
                                    workoutToDelete = workout
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete, // Needs import if not present, but verified above
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "New workout")
        }

        if (showCreateDialog) {
            // Extract all unique locations from workout history
            val existingLocations = state.workouts.map { it.locationTag }.distinct()
            
            WorkoutCreationDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { dayTag, locationTag, dateIso ->
                    viewModel.createNewWorkout(dayTag, locationTag)
                    showCreateDialog = false
                },
                settingsRepository = viewModel.getSettingsRepository(),
                existingLocations = existingLocations,
                existingDayTags = state.dayTags
            )
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog && workoutToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                workoutToDelete = null
            },
            title = { Text("Delete Workout?") },
            text = { Text("Are you sure you want to delete '${workoutToDelete?.dayTag}'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        workoutToDelete?.let {
                            viewModel.archiveWorkout(it.id)
                        }
                        showDeleteDialog = false
                        workoutToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteDialog = false
                        workoutToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
