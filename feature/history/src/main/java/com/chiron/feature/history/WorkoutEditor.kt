package com.chiron.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.model.SetEntry
import com.chiron.core.model.WorkoutSession
import com.chiron.feature.history.HistoryViewModel
import kotlinx.coroutines.launch

/**
 * Top-level workout editor screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditor(
    workout: WorkoutSession?,
    viewModel: HistoryViewModel,
    onClose: () -> Unit,
    onOpenPrForExercise: (Long) -> Unit = {},
    onOpenExerciseDetail: (Long) -> Unit = {},
    onOpenSetInWorkout: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (workout == null) return

    val entries by viewModel.getEntriesForWorkout(workout.id)
        .collectAsState(initial = emptyList<ExerciseEntry>())
    val uiState by viewModel.uiState.collectAsState()
    val displayInKg = uiState.displayInKg
    val distanceUnit = uiState.distanceUnit

    // ── Dialog visibility ──────────────────────────────────────────────────────
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showDuplicateConfirmation by remember { mutableStateOf(false) }

    // ── Superset add-exercise plumbing ─────────────────────────────────────────
    var supersetParentEntryId by remember { mutableStateOf<Long?>(null) }
    var pendingIncrementSupersetParentEntryId by remember { mutableStateOf<Long?>(null) }
    var didAddExerciseInDialog by remember { mutableStateOf(false) }

    // ── Set editing ────────────────────────────────────────────────────────────
    var editingSetEntry by remember { mutableStateOf<Triple<Long, Int, Long>?>(null) }
    var editingExercise by remember { mutableStateOf<com.chiron.core.model.Exercise?>(null) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // ── Finished workout logic ───────────────────────────────────────────────
    val now = System.currentTimeMillis()
    val endTimeUtc = workout.endTimeUtc
    val isOldWorkout = remember(endTimeUtc) {
        endTimeUtc != null && (now - endTimeUtc) > 60 * 60 * 1000
    }
    var forceEditMode by remember { mutableStateOf(false) }
    val isEditable = !isOldWorkout || forceEditMode

    // ── Header editable state ──────────────────────────────────────────────────
    var editableDayTag by remember { mutableStateOf(workout.dayTag) }
    var editableDateIso by remember { mutableStateOf(workout.dateIso) }
    var editableDateUtc by remember { mutableStateOf(workout.dateUtc) }
    var editableEndTimeUtc by remember { mutableStateOf(workout.endTimeUtc) }
    var editableLocation by remember { mutableStateOf(workout.locationTag) }
    var editableNotes by remember { mutableStateOf(workout.notes ?: "") }

    val allLocations = remember(uiState.workouts) {
        uiState.workouts.map { it.locationTag }.distinct().sorted()
    }

    // ── Exercise grouping ──────────────────────────────────────────────────────
    val exerciseGroups = remember(entries) { groupExercisesBySuperset(entries) }
    val supersetNumbersByStartId = remember(exerciseGroups) {
        val map = mutableMapOf<Long, Int>()
        var count = 0
        exerciseGroups.forEach { group ->
            val first = group.firstOrNull()
            if (first != null && group.size > 1 && first.sequenceType == "SUPERSET_START") {
                map[first.id] = ++count
            }
        }
        map
    }

    // ── Deep-link scroll target (from a PR row) ────────────────────────────────
    val gridState = rememberLazyGridState()
    val scrollTarget by viewModel.scrollTarget.collectAsState()
    val highlightedEntryId = scrollTarget?.entryId
    val highlightedSetIndex = scrollTarget?.setIndex

    LaunchedEffect(scrollTarget, exerciseGroups) {
        val target = scrollTarget ?: return@LaunchedEffect
        val groupIndex = exerciseGroups.indexOfFirst { group ->
            group.any { it.id == target.entryId }
        }
        if (groupIndex >= 0) {
            // Grid item 0 is the header; exercise groups start at index 1.
            gridState.animateScrollToItem(groupIndex + 1)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header
            item(span = { GridItemSpan(2) }) {
                WorkoutEditorHeader(
                    workout = workout,
                    isEditable = isEditable,
                    onEnableEdit = { forceEditMode = true },
                    editableDayTag = editableDayTag,
                    onDayTagChange = { 
                        if (!isEditable) return@WorkoutEditorHeader
                        editableDayTag = it 
                        viewModel.updateWorkout(workout.copy(dayTag = it, locationTag = editableLocation, notes = editableNotes.ifBlank { null }, dateIso = editableDateIso, dateUtc = editableDateUtc, endTimeUtc = editableEndTimeUtc))
                    },
                    onWorkoutTimeChange = { dateUtc, endTimeUtc, dateIso ->
                        if (!isEditable) return@WorkoutEditorHeader
                        editableDateUtc = dateUtc
                        editableEndTimeUtc = endTimeUtc
                        editableDateIso = dateIso
                        viewModel.saveWorkoutImmediate(
                            workout.copy(
                                dateIso = dateIso,
                                dateUtc = dateUtc,
                                endTimeUtc = endTimeUtc,
                                dayTag = editableDayTag,
                                locationTag = editableLocation,
                                notes = editableNotes.ifBlank { null }
                            )
                        )
                    },
                    editableLocation = editableLocation,
                    onLocationChange = { 
                        if (!isEditable) return@WorkoutEditorHeader
                        editableLocation = it
                        viewModel.updateWorkout(workout.copy(locationTag = it, dayTag = editableDayTag, notes = editableNotes.ifBlank { null }, dateIso = editableDateIso, dateUtc = editableDateUtc, endTimeUtc = editableEndTimeUtc))
                    },
                    editableNotes = editableNotes,
                    onNotesChange = { 
                        editableNotes = it 
                        viewModel.updateWorkout(workout.copy(notes = it.ifBlank { null }, dayTag = editableDayTag, locationTag = editableLocation, dateIso = editableDateIso, dateUtc = editableDateUtc, endTimeUtc = editableEndTimeUtc))
                    },
                    dayTags = uiState.dayTags,
                    allLocations = allLocations,
                    onShowDeleteDialog = { showDeleteConfirmation = true },
                    onShowDuplicateDialog = { showDuplicateConfirmation = true },
                    onDone = {
                        if (!isEditable) return@WorkoutEditorHeader
                        viewModel.saveWorkoutImmediate(
                            workout.copy(
                                dayTag = editableDayTag,
                                dateIso = editableDateIso,
                                dateUtc = editableDateUtc,
                                endTimeUtc = editableEndTimeUtc,
                                locationTag = editableLocation,
                                notes = editableNotes.ifBlank { null }
                            )
                        )
                        onClose()
                    }
                )
            }

            // Exercise groups
            items(
                items = exerciseGroups,
                key = { group -> group.firstOrNull()?.id ?: 0 },
                span = { GridItemSpan(2) }
            ) { group ->
                if (group.size > 1 && group[0].sequenceType == "SUPERSET_START") {
                    SupersetCard(
                        entries = group,
                        viewModel = viewModel,
                        displayInKg = displayInKg,
                        distanceUnit = distanceUnit,
                        allEntries = entries,
                        workoutId = workout.id,
                        supersetNumber = supersetNumbersByStartId[group.first().id] ?: 1,
                        highlightedEntryId = highlightedEntryId,
                        highlightedSetIndex = highlightedSetIndex,
                        onSetClick = { entryId, setIndex ->
                            val exerciseId = entries.find { it.id == entryId }?.exerciseId ?: return@SupersetCard
                            editingSetEntry = Triple(entryId, setIndex, exerciseId)
                        },
                        onAddSet = { entryId ->
                            if (!isEditable) return@SupersetCard
                            scope.launch { viewModel.addSet(entryId) }
                        },
                        onDeleteSuperset = {
                            if (!isEditable) return@SupersetCard
                            scope.launch {
                                viewModel.deleteExerciseEntries(workout.id, group.map { it.id })
                            }
                        },
                        onOpenPrForExercise = onOpenPrForExercise,
                        onOpenExerciseDetail = onOpenExerciseDetail,
                        onOpenSetInWorkout = onOpenSetInWorkout,
                        onRequestAddExercise = { fromIncrement ->
                            if (!isEditable) return@SupersetCard
                            didAddExerciseInDialog = false
                            supersetParentEntryId = group.firstOrNull()?.id
                            pendingIncrementSupersetParentEntryId =
                                if (fromIncrement) group.firstOrNull()?.id else null
                            showAddExerciseDialog = true
                        },
                        isEditable = isEditable
                    )
                } else {
                    ExerciseEntryCard(
                        entry = group[0],
                        viewModel = viewModel,
                        displayInKg = displayInKg,
                        distanceUnit = distanceUnit,
                        allEntries = entries,
                        workoutId = workout.id,
                        highlightedEntryId = highlightedEntryId,
                        highlightedSetIndex = highlightedSetIndex,
                        onSetClick = { setIndex ->
                            editingSetEntry = Triple(group[0].id, setIndex, group[0].exerciseId)
                        },
                        onAddSet = {
                            if (!isEditable) return@ExerciseEntryCard
                            scope.launch { viewModel.addSet(group[0].id) }
                        },
                        onDeleteEntry = {
                            if (!isEditable) return@ExerciseEntryCard
                            scope.launch {
                                viewModel.deleteExerciseEntry(workout.id, group[0].id)
                            }
                        },
                        onOpenPrForExercise = onOpenPrForExercise,
                        onOpenExerciseDetail = onOpenExerciseDetail,
                        onOpenSetInWorkout = onOpenSetInWorkout,
                        onRequestAddExercise = {
                            if (!isEditable) return@ExerciseEntryCard
                            supersetParentEntryId = group[0].id
                            showAddExerciseDialog = true
                        },
                        isEditable = isEditable
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { if (isEditable) showAddExerciseDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "Add Exercise")
        }

        com.chiron.feature.history.UndoSnackbar(
            viewModel = viewModel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────

    if (showDeleteConfirmation) {
        WorkoutDeleteDialog(
            workout = workout,
            onConfirm = {
                scope.launch {
                    if (workout.archived != 0) {
                        viewModel.permanentlyDeleteWorkout(workout.id)
                    } else {
                        viewModel.archiveWorkout(workout.id)
                    }
                    onClose()
                }
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }

    if (showDuplicateConfirmation) {
        WorkoutDuplicateDialog(
            onConfirm = {
                showDuplicateConfirmation = false
                viewModel.duplicateWorkout(workout.id) { newId ->
                    viewModel.openEditor(newId)
                }
                onClose()
            },
            onDismiss = { showDuplicateConfirmation = false }
        )
    }

    if (showAddExerciseDialog) {
        AddExerciseDialog(
            viewModel = viewModel,
            workoutId = workout.id,
            parentEntryId = supersetParentEntryId,
            entries = entries,
            onExerciseAdded = { didAddExerciseInDialog = true },
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

    editingSetEntry?.let { (entryId, setIndex, exerciseId) ->
        LaunchedEffect(exerciseId) {
            editingExercise = viewModel.getExerciseById(exerciseId)
        }
        val sets by viewModel.getSetsForEntry(entryId).collectAsState(initial = emptyList<SetEntry>())
        val set = sets.getOrNull(setIndex - 1)
        val exercise = editingExercise
        if (set != null && exercise != null) {
            EditSetDialog(
                set = set,
                exercise = exercise,
                displayInKg = displayInKg,
                distanceUnit = distanceUnit,
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
