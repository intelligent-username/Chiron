package com.chiron.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.data.entities.WorkoutSession
import com.chiron.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

/**
 * Top-level workout editor screen.
 *
 * Orchestrates all sub-composables:
 *  - [WorkoutEditorHeader]   — name, date, location, notes, done/delete/duplicate actions
 *  - [SupersetCard]          — a grouped superset entry
 *  - [ExerciseEntryCard]     — a standalone exercise entry
 *  - [WorkoutDeleteDialog]   — archive / permanent-delete confirmation
 *  - [WorkoutDuplicateDialog] — duplicate confirmation
 *  - [AddExerciseDialog]     — exercise picker (standalone or superset)
 *  - [EditSetDialog]         — weight/reps editor for a single set
 *
 * No business logic lives here — all mutations go through [viewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditor(
    workout: WorkoutSession?,
    viewModel: HistoryViewModel,
    onClose: () -> Unit,
    onOpenPrForExercise: (Long) -> Unit = {},
    onOpenExerciseDetail: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (workout == null) return

    val entries by viewModel.getEntriesForWorkout(workout.id)
        .collectAsState(initial = emptyList<ExerciseEntry>())
    val uiState by viewModel.uiState.collectAsState()
    val displayInKg = uiState.displayInKg

    // ── Dialog visibility ──────────────────────────────────────────────────────
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showDuplicateConfirmation by remember { mutableStateOf(false) }

    // ── Superset add-exercise plumbing ─────────────────────────────────────────
    var supersetParentEntryId by remember { mutableStateOf<Long?>(null) }
    var pendingIncrementSupersetParentEntryId by remember { mutableStateOf<Long?>(null) }
    var didAddExerciseInDialog by remember { mutableStateOf(false) }

    // ── Set editing ────────────────────────────────────────────────────────────
    var editingSetEntry by remember { mutableStateOf<Pair<Long, Int>?>(null) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

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

    // ─────────────────────────────────────────────────────────────────────────
    // Main content
    // ─────────────────────────────────────────────────────────────────────────

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header
            item(span = { GridItemSpan(2) }) {
                WorkoutEditorHeader(
                    workout = workout,
                    editableDayTag = editableDayTag,
                    onDayTagChange = { editableDayTag = it },
                    onWorkoutTimeChange = { dateUtc, endTimeUtc, dateIso ->
                        editableDateUtc = dateUtc
                        editableEndTimeUtc = endTimeUtc
                        editableDateIso = dateIso
                    },
                    editableLocation = editableLocation,
                    onLocationChange = { editableLocation = it },
                    editableNotes = editableNotes,
                    onNotesChange = { editableNotes = it },
                    dayTags = uiState.dayTags,
                    allLocations = allLocations,
                    onShowDeleteDialog = { showDeleteConfirmation = true },
                    onShowDuplicateDialog = { showDuplicateConfirmation = true },
                    onDone = {
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

            // Section label
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Exercises",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Exercise groups
            itemsIndexed(
                items = exerciseGroups,
                key = { _, group -> group.firstOrNull()?.id ?: 0 },
                span = { _, _ -> GridItemSpan(2) }
            ) { _, group ->
                if (group.size > 1 && group[0].sequenceType == "SUPERSET_START") {
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
                            scope.launch { viewModel.addSet(entryId) }
                        },
                        onDeleteSuperset = {
                            scope.launch {
                                group.forEach { entry ->
                                    viewModel.deleteExerciseEntry(workout.id, entry.id)
                                }
                            }
                        },
                        onOpenPrForExercise = onOpenPrForExercise,
                        onOpenExerciseDetail = onOpenExerciseDetail,
                        onRequestAddExercise = { fromIncrement ->
                            didAddExerciseInDialog = false
                            supersetParentEntryId = group.firstOrNull()?.id
                            pendingIncrementSupersetParentEntryId =
                                if (fromIncrement) group.firstOrNull()?.id else null
                            showAddExerciseDialog = true
                        }
                    )
                } else {
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
                            scope.launch { viewModel.addSet(group[0].id) }
                        },
                        onDeleteEntry = {
                            scope.launch {
                                viewModel.deleteExerciseEntry(workout.id, group[0].id)
                            }
                        },
                        onOpenPrForExercise = onOpenPrForExercise,
                        onOpenExerciseDetail = onOpenExerciseDetail,
                        onRequestAddExercise = {
                            supersetParentEntryId = group[0].id
                            showAddExerciseDialog = true
                        }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddExerciseDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "Add Exercise")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dialogs
    // ─────────────────────────────────────────────────────────────────────────

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

    editingSetEntry?.let { (entryId, setIndex) ->
        val sets by viewModel.getSetsForEntry(entryId).collectAsState(initial = emptyList<SetEntry>())
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
