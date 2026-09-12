package com.chiron.feature.history

import com.chiron.core.common.DistanceUnit
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.model.SetEntry
import com.chiron.core.model.WorkoutSession

data class HistoryUiState(
    val workouts: List<WorkoutSession> = emptyList(),
    val archivedWorkouts: List<WorkoutSession> = emptyList(),
    val dayTags: List<String> = emptyList(),
    val locationTags: List<String> = emptyList(),
    val selectedDayTag: String? = null,
    val selectedLocationTag: String? = null,
    val showArchivedWorkouts: Boolean = false,
    val isEditorOpen: Boolean = false,
    val editingWorkoutId: Long? = null,
    val displayInKg: Boolean = false,
    val distanceUnit: DistanceUnit = DistanceUnit.METERS
)

sealed class DeletedItem {
    data class Set(val entryId: Long, val set: SetEntry) : DeletedItem()
    data class ExerciseEntries(val workoutId: Long, val entries: List<Pair<ExerciseEntry, List<SetEntry>>>) : DeletedItem()
    data class WorkoutSessionWithEntries(val workout: WorkoutSession, val entries: List<ExerciseEntry>, val sets: Map<Long, List<SetEntry>>) : DeletedItem()
}

/** Deep-link target: scroll the workout editor to a specific exercise entry and set. */
data class WorkoutScrollTarget(
    val entryId: Long,
    val setIndex: Int
)
