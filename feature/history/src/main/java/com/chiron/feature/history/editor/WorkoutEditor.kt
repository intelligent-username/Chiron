package com.chiron.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.model.WorkoutSession
import com.chiron.feature.history.editor.WorkoutEditorDialogHost
import com.chiron.feature.history.editor.WorkoutEditorGrid
import kotlinx.coroutines.launch

/**
 * Top-level workout editor screen coordinator.
 */
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

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // ── Finished workout logic ───────────────────────────────────────────────
    val now = System.currentTimeMillis()
    val endTimeUtc = workout.endTimeUtc
    val isOldWorkout = remember(workout.id, endTimeUtc) {
        endTimeUtc != null && (now - endTimeUtc) > 60 * 60 * 1000
    }
    var forceEditMode by remember(workout.id) { mutableStateOf(false) }
    val isEditable = !isOldWorkout || forceEditMode

    // ── Header editable state ──────────────────────────────────────────────────
    var editableDayTag by remember(workout.id) { mutableStateOf(workout.dayTag) }
    var editableDateIso by remember(workout.id) { mutableStateOf(workout.dateIso) }
    var editableDateUtc by remember(workout.id) { mutableStateOf(workout.dateUtc) }
    var editableEndTimeUtc by remember(workout.id) { mutableStateOf(workout.endTimeUtc) }
    var editableLocation by remember(workout.id) { mutableStateOf(workout.locationTag) }
    var editableNotes by remember(workout.id) { mutableStateOf(workout.notes ?: "") }

    LaunchedEffect(workout.id, workout.dayTag, workout.dateIso, workout.dateUtc, workout.endTimeUtc, workout.locationTag, workout.notes) {
        editableDayTag = workout.dayTag
        editableDateIso = workout.dateIso
        editableDateUtc = workout.dateUtc
        editableEndTimeUtc = workout.endTimeUtc
        editableLocation = workout.locationTag
        editableNotes = workout.notes ?: ""
    }

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
        WorkoutEditorGrid(
            gridState = gridState,
            workout = workout,
            isEditable = isEditable,
            onEnableEdit = { forceEditMode = true },
            editableDayTag = editableDayTag,
            onDayTagChange = {
                if (!isEditable) return@WorkoutEditorGrid
                editableDayTag = it
                viewModel.updateWorkout(workout.copy(dayTag = it))
            },
            onWorkoutTimeChange = { dateUtc, endUtc, dateIso ->
                if (!isEditable) return@WorkoutEditorGrid
                editableDateUtc = dateUtc
                editableEndTimeUtc = endUtc
                editableDateIso = dateIso
                viewModel.saveWorkoutImmediate(
                    workout.copy(
                        dateIso = dateIso,
                        dateUtc = dateUtc,
                        endTimeUtc = endUtc
                    )
                )
            },
            editableLocation = editableLocation,
            onLocationChange = {
                if (!isEditable) return@WorkoutEditorGrid
                editableLocation = it
                viewModel.updateWorkout(workout.copy(locationTag = it))
            },
            editableNotes = editableNotes,
            onNotesChange = {
                editableNotes = it
                viewModel.updateWorkout(workout.copy(notes = it.ifBlank { null }))
            },
            dayTags = uiState.dayTags,
            allLocations = allLocations,
            onShowDeleteDialog = { showDeleteConfirmation = true },
            onShowDuplicateDialog = { showDuplicateConfirmation = true },
            onResetTimes = { viewModel.getResetTimingForWorkout(workout.id) },
            onDone = {
                if (!isEditable) return@WorkoutEditorGrid
                viewModel.saveWorkoutImmediate(
                    workout.copy(
                        dayTag = editableDayTag,
                        locationTag = editableLocation,
                        notes = editableNotes.ifBlank { null }
                    )
                )
                onClose()
            },
            exerciseGroups = exerciseGroups,
            supersetNumbersByStartId = supersetNumbersByStartId,
            viewModel = viewModel,
            displayInKg = displayInKg,
            distanceUnit = distanceUnit,
            entries = entries,
            highlightedEntryId = highlightedEntryId,
            highlightedSetIndex = highlightedSetIndex,
            onSetClick = { entryId, setIndex, exerciseId ->
                editingSetEntry = Triple(entryId, setIndex, exerciseId)
            },
            onAddSet = { entryId ->
                if (!isEditable) return@WorkoutEditorGrid
                scope.launch { viewModel.addSet(entryId) }
            },
            onDeleteSuperset = { entryIds ->
                if (!isEditable) return@WorkoutEditorGrid
                scope.launch { viewModel.deleteExerciseEntries(workout.id, entryIds) }
            },
            onDeleteEntry = { entryId ->
                if (!isEditable) return@WorkoutEditorGrid
                scope.launch { viewModel.deleteExerciseEntry(workout.id, entryId) }
            },
            onOpenPrForExercise = onOpenPrForExercise,
            onOpenExerciseDetail = onOpenExerciseDetail,
            onOpenSetInWorkout = onOpenSetInWorkout,
            onRequestAddExercise = { parentEntryId, fromIncrement ->
                if (!isEditable) return@WorkoutEditorGrid
                didAddExerciseInDialog = false
                supersetParentEntryId = parentEntryId
                pendingIncrementSupersetParentEntryId = if (fromIncrement) parentEntryId else null
                showAddExerciseDialog = true
            },
            focusManager = focusManager
        )

        FloatingActionButton(
            onClick = { if (isEditable) showAddExerciseDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "Add Exercise")
        }

        UndoSnackbar(
            viewModel = viewModel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    WorkoutEditorDialogHost(
        workout = workout,
        viewModel = viewModel,
        entries = entries,
        displayInKg = displayInKg,
        distanceUnit = distanceUnit,
        showDeleteConfirmation = showDeleteConfirmation,
        onConfirmDelete = {
            scope.launch {
                if (workout.archived != 0) {
                    viewModel.permanentlyDeleteWorkout(workout.id)
                } else {
                    viewModel.archiveWorkout(workout.id)
                }
                onClose()
            }
        },
        onDismissDelete = { showDeleteConfirmation = false },
        showDuplicateConfirmation = showDuplicateConfirmation,
        onConfirmDuplicate = {
            showDuplicateConfirmation = false
            viewModel.duplicateWorkout(workout.id) { newId ->
                viewModel.openEditor(newId)
            }
        },
        onDismissDuplicate = { showDuplicateConfirmation = false },
        showAddExerciseDialog = showAddExerciseDialog,
        supersetParentEntryId = supersetParentEntryId,
        onExerciseAdded = { didAddExerciseInDialog = true },
        onDismissAddExercise = {
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
        },
        editingSetEntry = editingSetEntry,
        onSaveSet = { updatedSet ->
            scope.launch {
                viewModel.updateSetAndCheckPr(updatedSet)
                editingSetEntry = null
            }
        },
        onDeleteSet = { entryId, setId ->
            scope.launch {
                viewModel.deleteSet(entryId, setId)
                editingSetEntry = null
            }
        },
        onDismissEditSet = { editingSetEntry = null }
    )
}
