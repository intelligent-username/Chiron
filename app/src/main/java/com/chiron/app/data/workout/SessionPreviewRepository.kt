package com.chiron.app.data.workout

import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.SetEntry

/**
 * Provides "last session preview" snapshots for the press-and-hold reference UI.
 * These are read-only aggregates built from existing DAO queries.
 */
class SessionPreviewRepository(
    private val exerciseDao: ExerciseDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val setEntryDao: SetEntryDao,
    private val workoutSessionDao: WorkoutSessionDao
) {
    data class LastSessionPreview(
        val dateLabel: String,
        val sets: List<SetEntry>,
        val notes: String? = null
    )

    data class SupersetExercisePreview(
        val exerciseId: Long,
        val exerciseName: String,
        val iconName: String?,
        val sets: List<SetEntry>
    )

    data class LastSessionSupersetPreview(
        val dateLabel: String,
        val exercises: List<SupersetExercisePreview>,
        val notes: String?
    )

    suspend fun getLastSessionPreview(
        exerciseId: Long,
        currentWorkoutId: Long
    ): LastSessionPreview? {
        val currentWorkout = workoutSessionDao.getById(currentWorkoutId) ?: return null
        val entry = exerciseEntryDao.getMostRecentEntryForExercise(
            exerciseId, currentWorkoutId, currentWorkout.dateUtc
        ) ?: return null
        val sets = setEntryDao.getSetsForEntrySync(entry.id)
        if (sets.isEmpty()) return null

        val workout = workoutSessionDao.getById(entry.workoutId) ?: return null
        val dateLabel = formatWorkoutDateLabel(workout.dateIso)

        return LastSessionPreview(dateLabel = dateLabel, sets = sets, notes = entry.notes)
    }

    suspend fun getLastSessionSupersetPreview(
        currentEntryId: Long,
        allCurrentEntries: List<ExerciseEntry>,
        currentWorkoutId: Long
    ): LastSessionSupersetPreview? {
        val currentEntry = allCurrentEntries.firstOrNull { it.id == currentEntryId } ?: return null

        if (currentEntry.groupId == null || currentEntry.sequenceType == "NONE") return null

        val supersetGroupId = currentEntry.groupId
        val supersetEntries = allCurrentEntries
            .filter { it.groupId == supersetGroupId }
            .sortedBy { it.slotIndex }

        if (supersetEntries.isEmpty()) return null

        val currentWorkout = workoutSessionDao.getById(currentWorkoutId) ?: return null
        val exercises = mutableListOf<SupersetExercisePreview>()
        var dateLabel = ""
        var notes: String? = null

        for (entry in supersetEntries) {
            val prevEntry = exerciseEntryDao.getMostRecentEntryForExercise(
                entry.exerciseId, currentWorkoutId, currentWorkout.dateUtc
            ) ?: continue
            val sets = setEntryDao.getSetsForEntrySync(prevEntry.id)
            if (sets.isEmpty()) continue

            val exercise = exerciseDao.getById(entry.exerciseId) ?: continue

            if (dateLabel.isEmpty()) {
                val workout = workoutSessionDao.getById(prevEntry.workoutId) ?: continue
                dateLabel = formatWorkoutDateLabel(workout.dateIso)
                notes = prevEntry.notes
            }

            exercises.add(
                SupersetExercisePreview(
                    exerciseId = entry.exerciseId,
                    exerciseName = exercise.name,
                    iconName = exercise.iconName,
                    sets = sets
                )
            )
        }

        return if (exercises.size > 1) {
            LastSessionSupersetPreview(
                dateLabel = dateLabel,
                exercises = exercises,
                notes = notes
            )
        } else {
            null
        }
    }

    private fun formatWorkoutDateLabel(dateIso: String): String {
        return try {
            val date = java.time.LocalDate.parse(dateIso)
            val dayOfWeek = date.dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            val month = date.month
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            "$dayOfWeek, $month ${date.dayOfMonth}"
        } catch (e: Exception) {
            dateIso
        }
    }
}
