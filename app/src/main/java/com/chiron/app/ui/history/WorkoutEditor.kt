package com.chiron.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.WorkoutSession
import com.chiron.app.ui.components.SetPill
import com.chiron.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    var supersetParentEntryId by remember { mutableStateOf<Long?>(null) }
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
    
    // Group exercises by superset
    val exerciseGroups = remember(entries) { groupExercisesBySuperset(entries) }
    val supersetNumbersByStartId = remember(exerciseGroups) {
        val map = mutableMapOf<Long, Int>()
        var count = 0
        exerciseGroups.forEach { group ->
            val first = group.firstOrNull()
            if (first != null && group.size > 1 && first.sequenceType == "SUPERSET_START") {
                count++
                map[first.id] = count
            }
        }
        map
    }

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
            
            itemsIndexed(
                items = exerciseGroups,
                key = { _, group -> group.firstOrNull()?.id ?: 0 },
                span = { _, _ -> GridItemSpan(2) }
            ) { groupIndex, group ->
                if (group.size > 1 && group[0].sequenceType == "SUPERSET_START") {
                    // Display as superset
                   SupersetCard(
                        entries = group,
                        viewModel = viewModel,
                        displayInKg = displayInKg,
                        allEntries = entries,
                        workoutId = workout.id,
                        supersetNumber = supersetNumbersByStartId[group.first().id] ?: 1,
                        onSetClick = { entryId, setIndex ->
                            editingSetEntry = Pair(entryId, setIndex)
                        },
                        onAddSet = { entryId ->
                            scope.launch {
                                viewModel.addSet(entryId)
                            }
                        },
                        onDeleteSuperset = {
                            scope.launch {
                                group.forEach { entry ->
                                    viewModel.deleteExerciseEntry(workout.id, entry.id)
                                }
                            }
                        },
                        onRequestAddExercise = {
                            supersetParentEntryId = group.firstOrNull()?.id
                            showAddExerciseDialog = true
                        }
                    )
                } else {
                    // Display as single exercise
                    ExerciseEntryCard(
                        entry = group[0],
                        viewModel = viewModel,
                        displayInKg = displayInKg,
                        allEntries = entries,
                        workoutId = workout.id,
                        onSetClick = { setIndex ->
                            editingSetEntry = Pair(group[0].id, setIndex)
                        },
                        onAddSet = {
                            scope.launch {
                                viewModel.addSet(group[0].id)
                            }
                        },
                        onDeleteEntry = {
                            scope.launch {
                                viewModel.deleteExerciseEntry(workout.id, group[0].id)
                            }
                        },
                        onRequestAddExercise = { 
                            supersetParentEntryId = group[0].id
                            showAddExerciseDialog = true 
                        }
                    )
                }
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
            parentEntryId = supersetParentEntryId,
            entries = entries,
            onDismiss = { 
                showAddExerciseDialog = false
                supersetParentEntryId = null
            }
        )
    }

    // Multi-exercise picker for supersets

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


@Composable
private fun SupersetCard(
    entries: List<ExerciseEntry>,
    viewModel: HistoryViewModel,
    displayInKg: Boolean,
    allEntries: List<ExerciseEntry>,
    workoutId: Long,
    supersetNumber: Int,
    onSetClick: (Long, Int) -> Unit,
    onAddSet: (Long) -> Unit,
    onDeleteSuperset: () -> Unit,
    onRequestAddExercise: () -> Unit
) {
    val startEntry = entries.firstOrNull() ?: return
    val scope = rememberCoroutineScope()
    var exerciseNotes by remember(startEntry.id) { mutableStateOf(startEntry.notes ?: "") }
    var isSupersetEnabled by remember(startEntry.id) { mutableStateOf(true) }
    var isEditingTitle by rememberSaveable(startEntry.id) { mutableStateOf(false) }
    var supersetTitle by rememberSaveable(startEntry.id) { mutableStateOf("Superset $supersetNumber") }
    var numExercisesInSuperset by remember(startEntry.id) {
        mutableIntStateOf(startEntry.numExercisesInSuperset.coerceAtLeast(2))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Superset header with delete button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.chiron.app.ui.components.ExerciseAsyncIcon(
                        iconName = "link",
                        contentDescription = "Superset",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    if (isEditingTitle) {
                        OutlinedTextField(
                            value = supersetTitle,
                            onValueChange = { supersetTitle = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(
                            text = supersetTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { isEditingTitle = true }
                        )
                    }
                }
                
                IconButton(
                    onClick = onDeleteSuperset,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        "Delete superset",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val maxVisibleColumns = 3
                val spacing = 12.dp
                val columnWidth = (maxWidth - spacing * (maxVisibleColumns - 1)) / maxVisibleColumns

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        SupersetExerciseColumn(
                            entry = entry,
                            viewModel = viewModel,
                            displayInKg = displayInKg,
                            modifier = Modifier.width(columnWidth),
                            onSetClick = { setIndex ->
                                onSetClick(entry.id, setIndex)
                            },
                            onAddSet = {
                                onAddSet(entry.id)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = exerciseNotes,
                onValueChange = {
                    exerciseNotes = it
                    scope.launch {
                        viewModel.updateExerciseEntry(startEntry.copy(notes = it.ifBlank { null }))
                    }
                },
                enabled = !isEditingTitle,
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Superset",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Switch(
                    checked = isSupersetEnabled,
                    onCheckedChange = { enabled ->
                        isSupersetEnabled = enabled
                        scope.launch {
                            val groupIdentifier = startEntry.groupId ?: startEntry.id
                            if (!enabled) {
                                val linkedEntries = allEntries.filter {
                                    it.id == startEntry.id || it.groupId == groupIdentifier
                                }
                                linkedEntries.forEach { linkedEntry ->
                                    viewModel.updateExerciseEntry(
                                        linkedEntry.copy(
                                            sequenceType = "NONE",
                                            groupId = null,
                                            numExercisesInSuperset = 2
                                        )
                                    )
                                }
                            } else {
                                viewModel.updateExerciseEntry(
                                    startEntry.copy(
                                        sequenceType = "SUPERSET_START",
                                        groupId = groupIdentifier,
                                        numExercisesInSuperset = numExercisesInSuperset.coerceAtLeast(2)
                                    )
                                )
                            }
                        }
                    }
                )
            }

            if (isSupersetEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Exercises in superset:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (numExercisesInSuperset > 2) {
                                    val newCount = numExercisesInSuperset - 1
                                    numExercisesInSuperset = newCount
                                    scope.launch {
                                        val groupIdentifier = startEntry.groupId ?: startEntry.id
                                        val sortedEntries = entries.sortedBy { it.slotIndex }

                                        if (sortedEntries.size > newCount) {
                                            val entriesToRemove = sortedEntries.drop(newCount)
                                            entriesToRemove.forEach { overflowEntry ->
                                                viewModel.deleteExerciseEntry(workoutId, overflowEntry.id)
                                            }
                                        }

                                        val keptEntries = sortedEntries.take(newCount)
                                        keptEntries.forEachIndexed { index, keptEntry ->
                                            val normalizedType = when {
                                                index == 0 -> "SUPERSET_START"
                                                index == keptEntries.lastIndex -> "SUPERSET_END"
                                                else -> "SUPERSET_MIDDLE"
                                            }
                                            viewModel.updateExerciseEntry(
                                                keptEntry.copy(
                                                    sequenceType = normalizedType,
                                                    groupId = groupIdentifier,
                                                    numExercisesInSuperset = newCount
                                                )
                                            )
                                        }

                                        viewModel.updateExerciseEntry(
                                            startEntry.copy(
                                                numExercisesInSuperset = newCount,
                                                sequenceType = "SUPERSET_START",
                                                groupId = groupIdentifier
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-")
                        }

                        Text(
                            text = numExercisesInSuperset.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.width(24.dp),
                            textAlign = TextAlign.Center
                        )

                        OutlinedButton(
                            onClick = {
                                if (numExercisesInSuperset < 5) {
                                    val newCount = numExercisesInSuperset + 1
                                    numExercisesInSuperset = newCount
                                    if (entries.size < newCount) {
                                        onRequestAddExercise()
                                    }
                                    scope.launch {
                                        val groupIdentifier = startEntry.groupId ?: startEntry.id

                                        entries.forEachIndexed { index, currentEntry ->
                                            val normalizedType = when {
                                                index == 0 -> "SUPERSET_START"
                                                index == entries.lastIndex -> "SUPERSET_END"
                                                else -> "SUPERSET_MIDDLE"
                                            }
                                            viewModel.updateExerciseEntry(
                                                currentEntry.copy(
                                                    sequenceType = normalizedType,
                                                    groupId = groupIdentifier,
                                                    numExercisesInSuperset = newCount
                                                )
                                            )
                                        }

                                        viewModel.updateExerciseEntry(
                                            startEntry.copy(
                                                numExercisesInSuperset = newCount,
                                                sequenceType = "SUPERSET_START",
                                                groupId = groupIdentifier
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupersetExerciseColumn(
    entry: ExerciseEntry,
    viewModel: HistoryViewModel,
    displayInKg: Boolean,
    modifier: Modifier = Modifier,
    onSetClick: (Int) -> Unit,
    onAddSet: () -> Unit
) {
    val sets by viewModel.getSetsForEntry(entry.id).collectAsState(initial = emptyList())
    var exercise by remember { mutableStateOf<com.chiron.app.data.entities.Exercise?>(null) }

    LaunchedEffect(entry.exerciseId) {
        exercise = viewModel.getExerciseById(entry.exerciseId)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Exercise name
        Text(
            text = exercise?.name ?: "Loading...",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        )

        // Exercise icon
        com.chiron.app.ui.components.ExerciseAsyncIcon(
            iconName = exercise?.iconName,
            contentDescription = exercise?.name,
            modifier = Modifier.size(40.dp),
            tint = Color.Unspecified
        )

        // Sets in a column
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .height(28.dp)
                    .width(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, "Add", modifier = Modifier.size(14.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseEntryCard(
    entry: ExerciseEntry,
    viewModel: HistoryViewModel,
    displayInKg: Boolean,
    allEntries: List<ExerciseEntry>,
    onSetClick: (Int) -> Unit,
    onAddSet: () -> Unit,
    onDeleteEntry: () -> Unit,
    workoutId: Long,
    onRequestAddExercise: () -> Unit
) {
    val sets by viewModel.getSetsForEntry(entry.id).collectAsState(initial = emptyList())
    var exercise by remember { mutableStateOf<com.chiron.app.data.entities.Exercise?>(null) }
    var exerciseNotes by remember { mutableStateOf(entry.notes ?: "") }
    var isSupersetEnabled by remember { mutableStateOf(entry.sequenceType == "SUPERSET_START") }
    var numExercisesInSuperset by remember { mutableIntStateOf(entry.numExercisesInSuperset) }
    val scope = rememberCoroutineScope()
    
    // Count how many exercises are already in this superset
    val currentExercisesInSuperset = remember(allEntries, entry.id) {
        var count = 1 // Include the current exercise
        val currentIndex = allEntries.indexOfFirst { it.id == entry.id }
        if (currentIndex >= 0) {
            for (i in (currentIndex + 1) until allEntries.size) {
                val nextEntry = allEntries[i]
                if (nextEntry.sequenceType == "SUPERSET_MIDDLE") {
                    count++
                } else if (nextEntry.sequenceType == "SUPERSET_END") {
                    count++
                    break
                } else {
                    break
                }
            }
        }
        count
    }
    
    val exercisesNeededInSuperset = numExercisesInSuperset - currentExercisesInSuperset
    
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
                     iconName = if (isSupersetEnabled) "link" else exercise?.iconName,
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

            Spacer(modifier = Modifier.height(12.dp))

            // Superset Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Superset",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Switch(
                    checked = isSupersetEnabled,
                    onCheckedChange = { newValue ->
                        if (newValue) {
                            // Enable superset mode and request additional exercise selection
                            isSupersetEnabled = true
                            val groupIdentifier = entry.groupId ?: entry.id
                            scope.launch {
                                viewModel.updateExerciseEntry(
                                    entry.copy(
                                        sequenceType = "SUPERSET_START",
                                        groupId = groupIdentifier,
                                        numExercisesInSuperset = numExercisesInSuperset.coerceAtLeast(2)
                                    )
                                )
                            }
                            onRequestAddExercise()
                        } else {
                            // Disable superset
                            isSupersetEnabled = false
                            scope.launch {
                                val groupIdentifier = entry.groupId
                                if (entry.sequenceType == "SUPERSET_START" && groupIdentifier != null) {
                                    val linkedEntries = allEntries.filter { it.groupId == groupIdentifier }
                                    linkedEntries.forEach { linkedEntry ->
                                        viewModel.updateExerciseEntry(
                                            linkedEntry.copy(
                                                sequenceType = "NONE",
                                                groupId = null,
                                                numExercisesInSuperset = 2
                                            )
                                        )
                                    }
                                }
                                viewModel.updateExerciseEntry(
                                    entry.copy(
                                        sequenceType = "NONE",
                                        groupId = null,
                                        numExercisesInSuperset = 2
                                    )
                                )
                            }
                        }
                    }
                )
            }

            // Number of exercises input (only show when superset is enabled)
            if (isSupersetEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Exercises in superset:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (numExercisesInSuperset > 2) {
                                    numExercisesInSuperset--
                                    scope.launch {
                                        viewModel.updateExerciseEntry(
                                            entry.copy(
                                                numExercisesInSuperset = numExercisesInSuperset,
                                                sequenceType = "SUPERSET_START",
                                                groupId = entry.groupId ?: entry.id
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-")
                        }
                        
                        Text(
                            text = numExercisesInSuperset.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.width(24.dp),
                            textAlign = TextAlign.Center
                        )
                        
                        OutlinedButton(
                            onClick = {
                                if (numExercisesInSuperset < 5) {
                                    numExercisesInSuperset++
                                    scope.launch {
                                        viewModel.updateExerciseEntry(
                                            entry.copy(
                                                numExercisesInSuperset = numExercisesInSuperset,
                                                sequenceType = "SUPERSET_START",
                                                groupId = entry.groupId ?: entry.id
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+")
                        }
                    }
                }

                // Show a button to add the remaining exercises if they're missing
                val exercisesNeededInSuperset = numExercisesInSuperset - currentExercisesInSuperset
                if (exercisesNeededInSuperset > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onRequestAddExercise()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        Text("Add ${exercisesNeededInSuperset} Exercise${if (exercisesNeededInSuperset > 1) "s" else ""}")
                    }
                }
            }
        }
    }
}

/**
 * Groups exercise entries by superset, handling sequence types.
 * Returns list of lists where each sublist represents a superset or single exercise.
 */
private fun groupExercisesBySuperset(
    entries: List<ExerciseEntry>
): List<List<ExerciseEntry>> {
    val groups = mutableListOf<List<ExerciseEntry>>()
    var currentGroup = mutableListOf<ExerciseEntry>()
    var isBuildingSuperset = false

    for (entry in entries) {
        when (entry.sequenceType) {
            "SUPERSET_START" -> {
                if (currentGroup.isNotEmpty()) {
                    groups.add(currentGroup.toList())
                    currentGroup = mutableListOf()
                }
                currentGroup.add(entry)
                isBuildingSuperset = true
            }
            "SUPERSET_MIDDLE", "SUPERSET_END" -> {
                currentGroup.add(entry)
                if (entry.sequenceType == "SUPERSET_END") {
                    isBuildingSuperset = false
                    groups.add(currentGroup.toList())
                    currentGroup = mutableListOf()
                }
            }
            else -> {
                if (currentGroup.isNotEmpty()) {
                    groups.add(currentGroup.toList())
                    currentGroup = mutableListOf()
                }
                groups.add(listOf(entry))
                isBuildingSuperset = false
            }
        }
    }

    if (currentGroup.isNotEmpty()) {
        groups.add(currentGroup.toList())
    }

    return groups
}

@Composable
private fun AddExerciseDialog(
    viewModel: HistoryViewModel,
    workoutId: Long,
    parentEntryId: Long? = null,
    entries: List<ExerciseEntry> = emptyList(),
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
        allExercises
    } else {
        allExercises.filter {
            it.name.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(if (parentEntryId != null) "Add to Superset" else "Add Exercise") 
        },
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
                                        if (parentEntryId == null) {
                                            viewModel.addExerciseEntry(workoutId, exercise.id)
                                            onDismiss()
                                            return@launch
                                        }

                                        val parentEntry = entries.find { it.id == parentEntryId }
                                        if (parentEntry == null) {
                                            onDismiss()
                                            return@launch
                                        }

                                        val groupIdentifier = parentEntry.groupId ?: parentEntry.id
                                        val existingSupersetEntries = entries.filter {
                                            it.id == parentEntry.id || it.groupId == groupIdentifier
                                        }

                                        val isDuplicateExercise = existingSupersetEntries.any { it.exerciseId == exercise.id }
                                        if (isDuplicateExercise) {
                                            return@launch
                                        }

                                        viewModel.updateExerciseEntry(
                                            parentEntry.copy(
                                                sequenceType = "SUPERSET_START",
                                                groupId = groupIdentifier,
                                                numExercisesInSuperset = parentEntry.numExercisesInSuperset.coerceAtLeast(2)
                                            )
                                        )

                                        val oldTail = existingSupersetEntries
                                            .filter { it.id != parentEntry.id }
                                            .maxByOrNull { it.slotIndex }

                                        if (oldTail != null) {
                                            viewModel.updateExerciseEntry(
                                                oldTail.copy(
                                                    sequenceType = "SUPERSET_MIDDLE",
                                                    groupId = groupIdentifier
                                                )
                                            )
                                        }

                                        val newEntryId = viewModel.addExerciseEntrySuspend(workoutId, exercise.id)
                                        val newSlotIndex = (entries.maxOfOrNull { it.slotIndex } ?: 0) + 1

                                        val newEntry = ExerciseEntry(
                                            id = newEntryId,
                                            workoutId = workoutId,
                                            exerciseId = exercise.id,
                                            slotIndex = newSlotIndex,
                                            groupId = groupIdentifier,
                                            sequenceType = "SUPERSET_END",
                                            notes = null,
                                            archived = 0,
                                            numExercisesInSuperset = parentEntry.numExercisesInSuperset.coerceAtLeast(2)
                                        )
                                        viewModel.updateExerciseEntry(newEntry)

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
