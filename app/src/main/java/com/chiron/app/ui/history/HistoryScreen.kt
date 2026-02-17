package com.chiron.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

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
                    WorkoutCard(
                        workout = workout,
                        onClick = { viewModel.openEditor(workout.id) }
                    )
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
                existingLocations = existingLocations
            )
        }
    }
}
