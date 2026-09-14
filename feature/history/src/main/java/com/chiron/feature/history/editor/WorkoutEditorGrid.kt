package com.chiron.feature.history.editor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.unit.dp
import com.chiron.core.common.DistanceUnit
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.model.WorkoutSession
import com.chiron.feature.history.ExerciseEntryCard
import com.chiron.feature.history.HistoryViewModel
import com.chiron.feature.history.SupersetCard
import com.chiron.feature.history.WorkoutEditorHeader

/**
 * Grid rendering the workout header followed by individual exercise cards and supersets.
 */
@Composable
fun WorkoutEditorGrid(
    gridState: LazyGridState,
    workout: WorkoutSession,
    isEditable: Boolean,
    onEnableEdit: () -> Unit,
    editableDayTag: String,
    onDayTagChange: (String) -> Unit,
    onWorkoutTimeChange: (Long, Long?, String) -> Unit,
    editableLocation: String,
    onLocationChange: (String) -> Unit,
    editableNotes: String,
    onNotesChange: (String) -> Unit,
    dayTags: List<String>,
    allLocations: List<String>,
    onShowDeleteDialog: () -> Unit,
    onShowDuplicateDialog: () -> Unit,
    onResetTimes: (suspend () -> HistoryViewModel.WorkoutTimingReset?)?,
    onDone: () -> Unit,
    exerciseGroups: List<List<ExerciseEntry>>,
    supersetNumbersByStartId: Map<Long, Int>,
    viewModel: HistoryViewModel,
    displayInKg: Boolean,
    distanceUnit: DistanceUnit,
    entries: List<ExerciseEntry>,
    highlightedEntryId: Long?,
    highlightedSetIndex: Int?,
    onSetClick: (entryId: Long, setIndex: Int, exerciseId: Long) -> Unit,
    onAddSet: (entryId: Long) -> Unit,
    onDeleteSuperset: (List<Long>) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onOpenPrForExercise: (Long) -> Unit,
    onOpenExerciseDetail: (Long) -> Unit,
    onOpenSetInWorkout: (Long) -> Unit,
    onRequestAddExercise: (parentEntryId: Long?, fromIncrement: Boolean) -> Unit,
    focusManager: FocusManager,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            WorkoutEditorHeader(
                workout = workout,
                isEditable = isEditable,
                onEnableEdit = onEnableEdit,
                editableDayTag = editableDayTag,
                onDayTagChange = onDayTagChange,
                onWorkoutTimeChange = onWorkoutTimeChange,
                editableLocation = editableLocation,
                onLocationChange = onLocationChange,
                editableNotes = editableNotes,
                onNotesChange = onNotesChange,
                dayTags = dayTags,
                allLocations = allLocations,
                onShowDeleteDialog = onShowDeleteDialog,
                onShowDuplicateDialog = onShowDuplicateDialog,
                onResetTimes = onResetTimes,
                onDone = onDone
            )
        }

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
                        onSetClick(entryId, setIndex, exerciseId)
                    },
                    onAddSet = { onAddSet(it) },
                    onDeleteSuperset = { onDeleteSuperset(group.map { it.id }) },
                    onOpenPrForExercise = onOpenPrForExercise,
                    onOpenExerciseDetail = onOpenExerciseDetail,
                    onOpenSetInWorkout = onOpenSetInWorkout,
                    onRequestAddExercise = { fromIncrement ->
                        onRequestAddExercise(group.firstOrNull()?.id, fromIncrement)
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
                        onSetClick(group[0].id, setIndex, group[0].exerciseId)
                    },
                    onAddSet = { onAddSet(group[0].id) },
                    onDeleteEntry = { onDeleteEntry(group[0].id) },
                    onOpenPrForExercise = onOpenPrForExercise,
                    onOpenExerciseDetail = onOpenExerciseDetail,
                    onOpenSetInWorkout = onOpenSetInWorkout,
                    onRequestAddExercise = { onRequestAddExercise(group[0].id, false) },
                    isEditable = isEditable
                )
            }
        }
    }
}
