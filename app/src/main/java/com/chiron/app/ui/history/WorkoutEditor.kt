package com.chiron.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.chiron.app.data.entities.WorkoutSession
import com.chiron.app.ui.components.ExerciseAsyncIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.chiron.app.ui.components.SetPill
import com.chiron.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditor(
    workout: WorkoutSession?,
    viewModel: HistoryViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (workout == null) return

    val entries by viewModel.getEntriesForWorkout(workout.id).collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()
    val displayInKg = uiState.displayInKg
    
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var editingSetEntry by remember { mutableStateOf<Pair<Long, Int>?>(null) } // entryId, setIndex
    
    val scope = rememberCoroutineScope()
    
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    var isEditingDetails by remember { mutableStateOf(false) }
    
    // DayTag (Name) State
    var editableDayTag by remember { mutableStateOf(workout.dayTag) }
    var queryDayTag by remember { mutableStateOf(workout.dayTag) } // Debounced search term
    LaunchedEffect(editableDayTag) {
        kotlinx.coroutines.delay(300)
        queryDayTag = editableDayTag
    }

    // Date State
    var editableDate by remember { mutableStateOf(workout.dateIso) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Location State
    var editableLocation by remember { mutableStateOf(workout.locationTag) }
    var queryLocation by remember { mutableStateOf(workout.locationTag) } // Debounced search term
    LaunchedEffect(editableLocation) {
        kotlinx.coroutines.delay(300)
        queryLocation = editableLocation
    }
    
    // Notes State
    var editableNotes by remember { mutableStateOf(workout.notes ?: "") }

    val allLocations = remember(uiState.workouts) { uiState.workouts.map { it.locationTag }.distinct().sorted() }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
        ) {
            // Custom Header Section (Spans Full Width)
            item(span = { GridItemSpan(2) }) {
                var expandedName by remember { mutableStateOf(false) }
                var expandedLocation by remember { mutableStateOf(false) }

                // Date Picker Dialog Logic
                if (showDatePicker) {
                    val initialMillis = remember(editableDate) {
                        try {
                            LocalDate.parse(editableDate).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
                    }
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val millis = datePickerState.selectedDateMillis
                                    if (millis != null) {
                                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                                        editableDate = newDate
                                    }
                                    showDatePicker = false
                                }
                            ) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1: Workout Name (Left) + Done Button (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Editable Workout Name (Name Field + Dropdown, Simplified)
                        val filteredDayTags = remember(queryDayTag, uiState.dayTags) {
                            uiState.dayTags
                                .filter { it.contains(queryDayTag, ignoreCase = true) && it != queryDayTag }
                                .take(5)
                        }

                        // Simple Box + TextField + DropdownMenu (No Laggy ExposedDropdownMenuBox)
                        Box(modifier = Modifier.weight(1f)) {
                            TextField(
                                value = editableDayTag,
                                onValueChange = { 
                                    editableDayTag = it
                                    expandedName = true
                                },
                                textStyle = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                placeholder = { 
                                    Text(
                                        "Untitled Workout", 
                                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                    ) 
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            DropdownMenu(
                                expanded = expandedName && filteredDayTags.isNotEmpty(),
                                onDismissRequest = { expandedName = false },
                                properties = PopupProperties(focusable = false)
                            ) {
                                filteredDayTags.forEach { selection ->
                                    DropdownMenuItem(
                                        text = { Text(selection) },
                                        onClick = {
                                            editableDayTag = selection
                                            expandedName = false
                                        }
                                    )
                                }
                            }
                        }

                        // Actions Row (Delete + Done)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showDeleteConfirmation = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Workout",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                )
                            }
                            
                            TextButton(onClick = {
                                viewModel.saveWorkoutImmediate(workout.copy(
                                    dayTag = editableDayTag,
                                    dateIso = editableDate,
                                    locationTag = editableLocation,
                                    notes = editableNotes.ifBlank { null }
                                ))
                                onClose()
                            }) {
                                Text("Done", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    // Row 2: Date (Left) + Location (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Date Field
                        TextField(
                            value = editableDate,
                            onValueChange = { editableDate = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.width(160.dp),
                            singleLine = true,
                            placeholder = { Text("YYYY-MM-DD") },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Select Date",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )

                        // Location Field with Simplified Dropdown
                        val filteredLocations = remember(queryLocation, allLocations) {
                             allLocations.filter { it.contains(queryLocation, ignoreCase = true) }.take(5)
                        }

                        Box(modifier = Modifier.width(160.dp)) {
                            TextField(
                                value = editableLocation,
                                onValueChange = { 
                                    editableLocation = it
                                    expandedLocation = true
                                },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    textAlign = TextAlign.End,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                                singleLine = true,
                                placeholder = { 
                                    Text(
                                        "Location", 
                                        textAlign = TextAlign.End, 
                                        modifier = Modifier.fillMaxWidth()
                                    ) 
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            DropdownMenu(
                                expanded = expandedLocation && filteredLocations.isNotEmpty(),
                                onDismissRequest = { expandedLocation = false },
                                properties = PopupProperties(focusable = false)
                            ) {
                                filteredLocations.forEach { loc ->
                                    DropdownMenuItem(
                                        text = { Text(loc) },
                                        onClick = {
                                            editableLocation = loc
                                            expandedLocation = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Notes (Clean styling)
                    TextField(
                        value = editableNotes,
                        onValueChange = { editableNotes = it },
                        placeholder = { Text("Add notes...", style = MaterialTheme.typography.bodyMedium) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3
                    )
                }
            }

            // Exercise section header (Span = 2)
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Exercises",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Exercise entries (Grid Items)
            itemsIndexed(
                items = entries,
                key = { _, entry -> entry.id },
                span = { _, _ -> GridItemSpan(2) }
            ) { index, entry ->
                ExerciseEntryCard(
                    entry = entry,
                    viewModel = viewModel,
                    displayInKg = displayInKg,
                    onSetClick = { setIndex ->
                        editingSetEntry = Pair(entry.id, setIndex)
                    },
                    onAddSet = {
                        scope.launch {
                            viewModel.addSet(entry.id)
                        }
                    },
                    onDeleteEntry = {
                        scope.launch {
                            viewModel.deleteExerciseEntry(workout.id, entry.id)
                        }
                    }
                )
            }
        }
        
        // FAB
        FloatingActionButton(
            onClick = { showAddExerciseDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "Add Exercise")
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Workout?") },
            text = { Text("This will permanently remove this workout session and all its exercises. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.archiveWorkout(workout.id)
                            onClose()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add exercise dialog
    if (showAddExerciseDialog) {
        AddExerciseDialog(
            viewModel = viewModel,
            workoutId = workout.id,
            onDismiss = { showAddExerciseDialog = false }
        )
    }

    // Edit set dialog
    editingSetEntry?.let { (entryId, setIndex) ->
        val sets by viewModel.getSetsForEntry(entryId).collectAsState(initial = emptyList())
        val set = sets.getOrNull(setIndex - 1)
        if (set != null) {
            EditSetDialog(
                set = set,
                displayInKg = displayInKg,
                onSave = { updatedSet ->
                    scope.launch {
                        viewModel.updateSet(updatedSet)
                        editingSetEntry = null
                    }
                },
                onDelete = {
                    scope.launch {
                        viewModel.deleteSet(entryId, set.id)
                        editingSetEntry = null
                    }
                },
                onDismiss = { editingSetEntry = null }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseEntryCard(
    entry: com.chiron.app.data.entities.ExerciseEntry,
    viewModel: HistoryViewModel,
    displayInKg: Boolean,
    onSetClick: (Int) -> Unit,
    onAddSet: () -> Unit,
    onDeleteEntry: () -> Unit
) {
    val sets by viewModel.getSetsForEntry(entry.id).collectAsState(initial = emptyList())
    var exercise by remember { mutableStateOf<com.chiron.app.data.entities.Exercise?>(null) }
    var exerciseNotes by remember { mutableStateOf(entry.notes ?: "") }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(entry.exerciseId) {
        exercise = viewModel.getExerciseById(entry.exerciseId)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                 // Icon (Left)
                 com.chiron.app.ui.components.ExerciseAsyncIcon(
                     iconName = exercise?.iconName,
                     contentDescription = exercise?.name,
                     modifier = Modifier.size(48.dp),
                     tint = Color.Unspecified
                 )
                 
                 Spacer(modifier = Modifier.width(16.dp))
                 
                 // Name and Sets (Right)
                 Column(modifier = Modifier.weight(1f)) {
                     Text(
                         text = exercise?.name ?: "Loading...",
                         style = MaterialTheme.typography.titleMedium,
                         fontWeight = FontWeight.Bold,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                     
                     Spacer(modifier = Modifier.height(8.dp))
                     
                     FlowRow(
                         horizontalArrangement = Arrangement.spacedBy(8.dp),
                         verticalArrangement = Arrangement.spacedBy(8.dp),
                         modifier = Modifier.fillMaxWidth()
                     ) {
                         sets.forEachIndexed { index, set ->
                             SetPill(
                                 weightLbs = set.weightLbs,
                                 reps = set.reps,
                                 displayInKg = displayInKg,
                                 onClick = { onSetClick(index + 1) }
                             )
                         }
                         
                         // Add Set Button
                         OutlinedButton(
                             onClick = onAddSet,
                             contentPadding = PaddingValues(horizontal = 12.dp),
                             modifier = Modifier.height(32.dp),
                             colors = ButtonDefaults.outlinedButtonColors(
                                  contentColor = MaterialTheme.colorScheme.primary
                             )
                         ) {
                             Icon(Icons.Default.Add, "Add", modifier = Modifier.size(16.dp))
                         }
                     }
                 }
                 
                 // Delete Button (Top Right)
                 IconButton(
                     onClick = onDeleteEntry,
                     modifier = Modifier.offset(x = 8.dp, y = (-8).dp) // Adjust position to corner
                 ) {
                     Icon(
                         Icons.Default.Close,
                         "Remove",
                         tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                         modifier = Modifier.size(20.dp)
                     )
                 }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Notes (Full width)
            OutlinedTextField(
                value = exerciseNotes,
                onValueChange = { 
                    exerciseNotes = it
                    scope.launch {
                        viewModel.updateExerciseEntry(entry.copy(notes = it.ifBlank { null }))
                    }
                },
                placeholder = { Text("Notes", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
private fun AddExerciseDialog(
    viewModel: HistoryViewModel,
    workoutId: Long,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var allExercises by remember { mutableStateOf(emptyList<com.chiron.app.data.entities.Exercise>()) }
    val scope = rememberCoroutineScope()

    // Load all exercises
    LaunchedEffect(Unit) {
        allExercises = viewModel.getAllExercises()
    }

    // Filter exercises based on search
    val filteredExercises = if (searchQuery.isBlank()) {
        allExercises.take(10) // Show first 10 when no search
    } else {
        allExercises.filter {
            it.name.contains(searchQuery, ignoreCase = true)
        }.take(10)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exercise") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search exercises") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredExercises) { exercise ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        viewModel.addExerciseEntry(workoutId, exercise.id)
                                        onDismiss()
                                    }
                                }
                        ) {
                            Text(
                                text = exercise.name,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditSetDialog(
    set: com.chiron.app.data.entities.SetEntry,
    displayInKg: Boolean,
    onSave: (com.chiron.app.data.entities.SetEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var weight by remember { mutableStateOf(set.weightLbs?.toString() ?: "") }
    var reps by remember { mutableStateOf(set.reps?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text(if (displayInKg) "Weight (kg)" else "Weight (lbs)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = { Text("Reps") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val weightLbs = weight.toDoubleOrNull()?.let {
                        if (displayInKg) it * 2.2046226218 else it
                    }
                    val repsInt = reps.toIntOrNull()
                    onSave(set.copy(weightLbs = weightLbs, reps = repsInt))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
