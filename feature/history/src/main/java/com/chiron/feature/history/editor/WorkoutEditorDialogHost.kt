package com.chiron.feature.history.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chiron.core.common.DistanceUnit
import com.chiron.core.model.Exercise
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.model.SetEntry
import com.chiron.core.model.WorkoutSession
import com.chiron.feature.history.AddExerciseDialog
import com.chiron.feature.history.EditSetDialog
import com.chiron.feature.history.HistoryViewModel
import com.chiron.feature.history.WorkoutDeleteDialog
import com.chiron.feature.history.WorkoutDuplicateDialog

/**
 * Hosts the modal dialogs for WorkoutEditor (delete, duplicate, add-exercise, edit-set).
 */
@Composable
fun WorkoutEditorDialogHost(
    workout: WorkoutSession,
    viewModel: HistoryViewModel,
    entries: List<ExerciseEntry>,
    displayInKg: Boolean,
    distanceUnit: DistanceUnit,
    showDeleteConfirmation: Boolean,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    showDuplicateConfirmation: Boolean,
    onConfirmDuplicate: () -> Unit,
    onDismissDuplicate: () -> Unit,
    showAddExerciseDialog: Boolean,
    supersetParentEntryId: Long?,
    onExerciseAdded: () -> Unit,
    onDismissAddExercise: () -> Unit,
    editingSetEntry: Triple<Long, Int, Long>?,
    onSaveSet: (SetEntry) -> Unit,
    onDeleteSet: (Long, Long) -> Unit,
    onDismissEditSet: () -> Unit
) {
    if (showDeleteConfirmation) {
        WorkoutDeleteDialog(
            workout = workout,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete
        )
    }

    if (showDuplicateConfirmation) {
        WorkoutDuplicateDialog(
            onConfirm = onConfirmDuplicate,
            onDismiss = onDismissDuplicate
        )
    }

    if (showAddExerciseDialog) {
        AddExerciseDialog(
            viewModel = viewModel,
            workoutId = workout.id,
            parentEntryId = supersetParentEntryId,
            entries = entries,
            onExerciseAdded = onExerciseAdded,
            onDismiss = onDismissAddExercise
        )
    }

    editingSetEntry?.let { (entryId, setIndex, exerciseId) ->
        var editingExercise by remember(exerciseId) { mutableStateOf<Exercise?>(null) }
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
                onSave = onSaveSet,
                onDelete = { onDeleteSet(entryId, set.id) },
                onDismiss = onDismissEditSet
            )
        }
    }
}
