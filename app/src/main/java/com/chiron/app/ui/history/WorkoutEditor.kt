package com.chiron.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.chiron.app.data.ChironRepository
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

    val entries by viewModel.getEntriesForWorkout(workout.id).collectAsState(initial = emptyList<ExerciseEntry>())
    val uiState by viewModel.uiState.collectAsState()
    val displayInKg = uiState.displayInKg
    
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var supersetParentEntryId by remember { mutableStateOf<Long?>(null) }
    var pendingIncrementSupersetParentEntryId by remember { mutableStateOf<Long?>(null) }
    var didAddExerciseInDialog by remember { mutableStateOf(false) }
    var editingSetEntry by remember { mutableStateOf<Pair<Long, Int>?>(null) } // entryId, setIndex
    
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showDuplicateConfirmation by remember { mutableStateOf(false) }

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
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
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
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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

                        // Actions Row (Duplicate + Delete + Done)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showDuplicateConfirmation = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Duplicate Workout",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                            IconButton(onClick = { showDeleteConfirmation = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = if (workout.archived != 0) "Delete Workout Permanently" else "Archive Workout",
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
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
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
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
                        onRequestAddExercise = { fromIncrement ->
                            didAddExerciseInDialog = false
                            supersetParentEntryId = group.firstOrNull()?.id
                            pendingIncrementSupersetParentEntryId = if (fromIncrement) group.firstOrNull()?.id else null
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
            title = {
                Text(if (workout.archived != 0) "Delete Workout Permanently?" else "Archive Workout?")
            },
            text = {
                Text(
                    if (workout.archived != 0) {
                        "This will permanently remove this workout and all exercises/sets inside it. This cannot be undone."
                    } else {
                        "This will move this workout to archived workouts. You can unarchive it later or permanently delete it from Archived."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (workout.archived != 0) {
                                viewModel.permanentlyDeleteWorkout(workout.id)
                            } else {
                                viewModel.archiveWorkout(workout.id)
                            }
                            onClose()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (workout.archived != 0) "Delete" else "Archive", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Duplicate confirmation dialog
    if (showDuplicateConfirmation) {
        AlertDialog(
            onDismissRequest = { showDuplicateConfirmation = false },
            title = { Text("Duplicate Workout?") },
            text = { Text("This will create an identical copy of this workout with today's date. All exercises and sets will be copied.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDuplicateConfirmation = false
                        viewModel.duplicateWorkout(workout.id) { newId ->
                            viewModel.openEditor(newId)
                        }
                        onClose()
                    }
                ) {
                    Text("Duplicate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateConfirmation = false }) {
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
            onExerciseAdded = {
                didAddExerciseInDialog = true
            },
            onDismiss = { 
                val pendingId = pendingIncrementSupersetParentEntryId
                if (pendingId != null && !didAddExerciseInDialog) {
                    val parent = entries.find { it.id == pendingId }
                    if (parent != null) {
                        val decremented = (parent.numExercisesInSuperset - 1).coerceAtLeast(2)
                        viewModel.updateExerciseEntry(
                            parent.copy(
                                numExercisesInSuperset = decremented,
                                sequenceType = "SUPERSET_START",
                                groupId = parent.groupId ?: parent.id
                            )
                        )
                    }
                }
                showAddExerciseDialog = false
                supersetParentEntryId = null
                pendingIncrementSupersetParentEntryId = null
                didAddExerciseInDialog = false
            }
        )
    }

    // Multi-exercise picker for supersets

    // Edit set dialog
    editingSetEntry?.let { (entryId, setIndex) ->
        val sets by viewModel.getSetsForEntry(entryId).collectAsState(initial = emptyList<com.chiron.app.data.entities.SetEntry>())
        val set = sets.getOrNull(setIndex - 1)
        if (set != null) {
            EditSetDialog(
                set = set,
                displayInKg = displayInKg,
                onSave = { updatedSet ->
                    scope.launch {
                        viewModel.updateSetAndCheckPr(updatedSet)
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
    onRequestAddExercise: (Boolean) -> Unit
) {
    val startEntry = entries.firstOrNull() ?: return
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val supersetKey = startEntry.groupId ?: startEntry.id
    var exerciseNotes by remember(supersetKey) { mutableStateOf(startEntry.notes ?: "") }
    var committedExerciseNotes by remember(supersetKey) { mutableStateOf(startEntry.notes ?: "") }
    var isSupersetEnabled by remember(startEntry.id) { mutableStateOf(true) }
    var isEditingTitle by rememberSaveable(supersetKey) { mutableStateOf(false) }
    var supersetTitle by rememberSaveable(supersetKey) { mutableStateOf("Superset $supersetNumber") }
    var draftSupersetTitle by rememberSaveable(supersetKey) { mutableStateOf("Superset $supersetNumber") }
    var numExercisesInSuperset by remember(startEntry.id, startEntry.numExercisesInSuperset) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = draftSupersetTitle,
                                onValueChange = { draftSupersetTitle = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            TextButton(
                                onClick = {
                                    supersetTitle = draftSupersetTitle.ifBlank { "Superset $supersetNumber" }
                                    draftSupersetTitle = supersetTitle
                                    isEditingTitle = false
                                }
                            ) {
                                Text("Save")
                            }
                            TextButton(
                                onClick = {
                                    draftSupersetTitle = supersetTitle
                                    isEditingTitle = false
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    } else {
                        Text(
                            text = supersetTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable {
                                draftSupersetTitle = supersetTitle
                                isEditingTitle = true
                            }
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
                            workoutId = workoutId,
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
                },
                placeholder = { Text("Notes", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val normalized = exerciseNotes.trim()
                        if (normalized != committedExerciseNotes.trim()) {
                            committedExerciseNotes = normalized
                            scope.launch {
                                viewModel.updateExerciseEntry(startEntry.copy(notes = normalized.ifBlank { null }))
                            }
                        }
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            val normalized = exerciseNotes.trim()
                            if (normalized != committedExerciseNotes.trim()) {
                                committedExerciseNotes = normalized
                                scope.launch {
                                    viewModel.updateExerciseEntry(startEntry.copy(notes = normalized.ifBlank { null }))
                                }
                            }
                        }
                    },
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
                                        onRequestAddExercise(true)
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
    workoutId: Long,
    modifier: Modifier = Modifier,
    onSetClick: (Int) -> Unit,
    onAddSet: () -> Unit
) {
    val sets by viewModel.getSetsForEntry(entry.id).collectAsState(initial = emptyList<com.chiron.app.data.entities.SetEntry>())
    var exercise by remember { mutableStateOf<com.chiron.app.data.entities.Exercise?>(null) }
    var isPreviewingLastSession by remember { mutableStateOf(false) }
    var lastSessionPreview by remember { mutableStateOf<ChironRepository.LastSessionPreview?>(null) }
    val hasHistory = lastSessionPreview != null

    LaunchedEffect(entry.exerciseId) {
        exercise = viewModel.getExerciseById(entry.exerciseId)
    }

    LaunchedEffect(entry.exerciseId, workoutId) {
        lastSessionPreview = viewModel.getLastSessionPreview(entry.exerciseId, workoutId)
    }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isPreviewingLastSession)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else
                    Color.Transparent
            )
            .padding(6.dp),
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
            if (isPreviewingLastSession && lastSessionPreview != null) {
                lastSessionPreview!!.sets.forEach { set ->
                    SetPill(
                        weightLbs = set.weightLbs,
                        reps = set.reps,
                        displayInKg = displayInKg,
                        isPr = set.isPr == 1,
                        onClick = { }
                    )
                }
            } else {
                sets.forEachIndexed { index, set ->
                    SetPill(
                        weightLbs = set.weightLbs,
                        reps = set.reps,
                        displayInKg = displayInKg,
                        isPr = set.isPr == 1,
                        onClick = { onSetClick(index + 1) }
                    )
                }

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

            if (hasHistory) {
                val previewPressSource = remember { MutableInteractionSource() }
                val previewPressed by previewPressSource.collectIsPressedAsState()
                LaunchedEffect(previewPressed) {
                    isPreviewingLastSession = previewPressed
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = previewPressSource,
                            indication = null,
                            onClick = {}
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }
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
    val sets by viewModel.getSetsForEntry(entry.id).collectAsState(initial = emptyList<com.chiron.app.data.entities.SetEntry>())
    var exercise by remember { mutableStateOf<com.chiron.app.data.entities.Exercise?>(null) }
    var exerciseNotes by remember { mutableStateOf(entry.notes ?: "") }
    var committedExerciseNotes by remember(entry.id) { mutableStateOf(entry.notes ?: "") }
    var numExercisesInSuperset by remember { mutableIntStateOf(entry.numExercisesInSuperset) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val notesFocusRequester = remember { FocusRequester() }

    // Last session preview state
    var isPreviewingLastSession by remember { mutableStateOf(false) }
    var lastSessionPreview by remember { mutableStateOf<ChironRepository.LastSessionPreview?>(null) }
    var lastSessionSupersetPreview by remember { mutableStateOf<ChironRepository.LastSessionSupersetPreview?>(null) }

    LaunchedEffect(entry.exerciseId, entry.id, workoutId, allEntries.size) {
        // Check if this exercise is part of a superset
        val isSupersetExercise = entry.groupId != null && entry.sequenceType != "NONE"
        
        if (isSupersetExercise) {
            // Try to load superset preview first
            lastSessionSupersetPreview = viewModel.getLastSessionSupersetPreview(entry.id, allEntries, workoutId)
            // If no superset preview, fall back to single exercise preview
            if (lastSessionSupersetPreview == null) {
                lastSessionPreview = viewModel.getLastSessionPreview(entry.exerciseId, workoutId)
            }
        } else {
            // Not a superset exercise, load single exercise preview
            lastSessionPreview = viewModel.getLastSessionPreview(entry.exerciseId, workoutId)
        }
    }

    val hasHistory = lastSessionPreview != null || lastSessionSupersetPreview != null
    
    // Count how many exercises are actually in this superset
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
    
    // Only treat as superset if there are actually multiple exercises in the group
    var isSupersetEnabled by remember(entry.sequenceType, currentExercisesInSuperset) {
        mutableStateOf(entry.sequenceType == "SUPERSET_START" && currentExercisesInSuperset > 1)
    }
    
    val exercisesNeededInSuperset = numExercisesInSuperset - currentExercisesInSuperset
    
    LaunchedEffect(entry.exerciseId) {
        exercise = viewModel.getExerciseById(entry.exerciseId)
    }

    // Card background and content alpha — instant, no animation to avoid lag
    val cardColor = MaterialTheme.colorScheme.surface
    val contentAlpha = if (isPreviewingLastSession) 0.55f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { notesFocusRequester.freeFocus() }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
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
                     if (isPreviewingLastSession && lastSessionSupersetPreview != null) {
                         // Superset preview - show "Superset" as title
                         Text(
                             text = "Superset",
                             style = MaterialTheme.typography.titleMedium,
                             fontWeight = FontWeight.Bold,
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                         )
                         
                         Spacer(modifier = Modifier.height(8.dp))
                         
                         // Show all exercises in the superset
                         Column(
                             verticalArrangement = Arrangement.spacedBy(12.dp),
                             modifier = Modifier.fillMaxWidth()
                         ) {
                             lastSessionSupersetPreview!!.exercises.forEach { exercisePreview ->
                                 Column(
                                     verticalArrangement = Arrangement.spacedBy(4.dp)
                                 ) {
                                     Text(
                                         text = exercisePreview.exerciseName,
                                         style = MaterialTheme.typography.bodyMedium,
                                         fontWeight = FontWeight.SemiBold,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                     )
                                     
                                     FlowRow(
                                         horizontalArrangement = Arrangement.spacedBy(6.dp),
                                         verticalArrangement = Arrangement.spacedBy(6.dp),
                                         modifier = Modifier.fillMaxWidth()
                                     ) {
                                         exercisePreview.sets.forEach { set ->
                                             SetPill(
                                                 weightLbs = set.weightLbs,
                                                 reps = set.reps,
                                                 displayInKg = displayInKg,
                                                 isPr = set.isPr == 1,
                                                 onClick = { } // Non-interactive in preview
                                             )
                                         }
                                     }
                                 }
                             }
                         }
                     } else if (isPreviewingLastSession && lastSessionPreview != null) {
                         // Single exercise preview title
                         Text(
                             text = exercise?.name ?: "Loading...",
                             style = MaterialTheme.typography.titleMedium,
                             fontWeight = FontWeight.Bold,
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                         )
                         
                         Spacer(modifier = Modifier.height(8.dp))
                         
                         FlowRow(
                             horizontalArrangement = Arrangement.spacedBy(8.dp),
                             verticalArrangement = Arrangement.spacedBy(8.dp),
                             modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
                         ) {
                             // Show prior session's sets
                             lastSessionPreview!!.sets.forEach { set ->
                                 SetPill(
                                     weightLbs = set.weightLbs,
                                     reps = set.reps,
                                     displayInKg = displayInKg,
                                     isPr = set.isPr == 1,
                                     onClick = { } // Non-interactive in preview
                                 )
                             }
                         }
                     } else {
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
                             modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
                         ) {
                             // Normal: show current sets
                             sets.forEachIndexed { index, set ->
                                 SetPill(
                                     weightLbs = set.weightLbs,
                                     reps = set.reps,
                                     displayInKg = displayInKg,
                                     isPr = set.isPr == 1,
                                     onClick = { onSetClick(index + 1) }
                                 )
                             }
                             
                             // Add Set Button (hidden during preview)
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
                 }
                 
                 // Right side buttons (Delete and Preview) stacked vertically
                 Column(
                     horizontalAlignment = Alignment.CenterHorizontally,
                     verticalArrangement = Arrangement.spacedBy(4.dp)
                 ) {
                     // Delete Button (Top)
                     IconButton(
                         onClick = onDeleteEntry,
                         modifier = Modifier.size(40.dp)
                     ) {
                         Icon(
                             Icons.Default.Close,
                             "Remove",
                             tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                             modifier = Modifier.size(20.dp)
                         )
                     }
                     
                     // Preview Button (Below delete if history exists)
                     if (hasHistory) {
                         val previewPressSource = remember { MutableInteractionSource() }
                         val previewPressed by previewPressSource.collectIsPressedAsState()
                         LaunchedEffect(previewPressed) {
                             isPreviewingLastSession = previewPressed
                         }
                         
                         Box(
                             modifier = Modifier
                                 .size(28.dp)
                                 .clip(CircleShape)
                                 .background(
                                     MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                 )
                                 .clickable(
                                     interactionSource = previewPressSource,
                                     indication = null,
                                     onClick = {}
                                 ),
                             contentAlignment = Alignment.Center
                         ) {
                             Box(
                                 modifier = Modifier
                                     .size(10.dp)
                                     .clip(CircleShape)
                                     .background(
                                         MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                     )
                             )
                         }
                     }
                 }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Notes with date display below
            if (isPreviewingLastSession && (lastSessionPreview != null || lastSessionSupersetPreview != null)) {
                // Show prior session notes (read-only, same size as normal)
                val previewNotes = (lastSessionSupersetPreview?.notes ?: lastSessionPreview?.notes) ?: ""
                val dateLabel = lastSessionSupersetPreview?.dateLabel ?: lastSessionPreview?.dateLabel ?: ""
                
                OutlinedTextField(
                    value = previewNotes,
                    onValueChange = { },
                    enabled = false,
                    placeholder = { Text("No notes", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledContainerColor = Color.Transparent,
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Date label below description
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else {
                // Normal: editable notes
                OutlinedTextField(
                    value = exerciseNotes,
                    onValueChange = { 
                        exerciseNotes = it
                    },
                    placeholder = { Text("Notes", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val normalized = exerciseNotes.trim()
                            if (normalized != committedExerciseNotes.trim()) {
                                committedExerciseNotes = normalized
                                scope.launch {
                                    viewModel.updateExerciseEntry(entry.copy(notes = normalized.ifBlank { null }))
                                }
                            }
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(notesFocusRequester)
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                val normalized = exerciseNotes.trim()
                                if (normalized != committedExerciseNotes.trim()) {
                                    committedExerciseNotes = normalized
                                    scope.launch {
                                        viewModel.updateExerciseEntry(entry.copy(notes = normalized.ifBlank { null }))
                                    }
                                }
                            }
                        },
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
            
            Spacer(modifier = Modifier.height(12.dp))

            // Superset Controls (hidden during preview)
            if (!isPreviewingLastSession) Row(
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

            // Number of exercises input (only show when superset is enabled, and not previewing)
            if (isSupersetEnabled && !isPreviewingLastSession) {
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
                    // If we were building a superset but hit a non-superset exercise,
                    // and the current group only has the SUPERSET_START, treat it as a regular exercise
                    if (isBuildingSuperset && currentGroup.size == 1) {
                        // Single SUPERSET_START with no followers - treat as regular
                        groups.add(currentGroup.toList())
                        currentGroup = mutableListOf()
                        isBuildingSuperset = false
                    } else {
                        // Valid superset group
                        groups.add(currentGroup.toList())
                        currentGroup = mutableListOf()
                        isBuildingSuperset = false
                    }
                }
                groups.add(listOf(entry))
            }
        }
    }

    if (currentGroup.isNotEmpty()) {
        // If we end with an incomplete superset (SUPERSET_START with no SUPERSET_END),
        // still add it as a group, but it will be rendered as a single exercise since size==1
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
    onExerciseAdded: () -> Unit = {},
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
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
                                            onExerciseAdded()
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

                                        onExerciseAdded()
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
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = { Text("Reps") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
