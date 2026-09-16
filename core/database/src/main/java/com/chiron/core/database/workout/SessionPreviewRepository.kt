package com.chiron.core.database.workout

import com.chiron.core.common.DateUtils
import com.chiron.core.database.ChironRepository.LastSessionPreview
import com.chiron.core.database.ChironRepository.LastSessionSupersetPreview
import com.chiron.core.database.ChironRepository.SupersetExercisePreview
import com.chiron.core.database.dao.ExerciseDao
import com.chiron.core.database.dao.ExerciseEntryDao
import com.chiron.core.database.dao.SetEntryDao
import com.chiron.core.database.dao.WorkoutSessionDao
import com.chiron.core.model.SetEntry

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
        val dateLabel = DateUtils.formatWorkoutDateLabel(workout.dateIso)

        return LastSessionPreview(dateLabel = dateLabel, sets = sets, notes = entry.notes)
    }

    /**
     * Returns a superset preview if the exercise was last performed as part of a superset.
     * Completely independent of the current session's grouping state — it looks at what
     * the *previous* session looked like for this exercise.
     */
    suspend fun getLastSessionSupersetPreview(
        exerciseId: Long,
        currentWorkoutId: Long
    ): LastSessionSupersetPreview? {
        val currentWorkout = workoutSessionDao.getById(currentWorkoutId) ?: return null

        // Find the most recent past entry for this exercise
        val prevEntry = exerciseEntryDao.getMostRecentEntryForExercise(
            exerciseId, currentWorkoutId, currentWorkout.dateUtc
        ) ?: return null

        // If the past entry wasn't in a superset, there's nothing to show
        val groupId = prevEntry.groupId ?: return null
        if (prevEntry.sequenceType == "NONE") return null

        // Fetch all entries in that past workout that shared the same superset group
        val supersetEntries = exerciseEntryDao.getEntriesByGroupInWorkout(prevEntry.workoutId, groupId)
        if (supersetEntries.size < 2) return null

        val workout = workoutSessionDao.getById(prevEntry.workoutId) ?: return null
        val dateLabel = DateUtils.formatWorkoutDateLabel(workout.dateIso)

        val exercises = mutableListOf<SupersetExercisePreview>()
        for (entry in supersetEntries) {
            val sets = setEntryDao.getSetsForEntrySync(entry.id)
            if (sets.isEmpty()) continue
            val exercise = exerciseDao.getById(entry.exerciseId) ?: continue
            exercises.add(
                SupersetExercisePreview(
                    exerciseId = entry.exerciseId,
                    exerciseName = exercise.name,
                    iconName = exercise.iconName,
                    sets = sets
                )
            )
        }

        return if (exercises.size >= 2) {
            LastSessionSupersetPreview(
                dateLabel = dateLabel,
                exercises = exercises,
                notes = prevEntry.notes
            )
        } else null
    }

    /**
     * Unified single-pass lookup: retrieves either superset or solo last-session preview
     * in a single query pass without redundant workout or entry lookups.
     */
    suspend fun getLastSessionCombinedPreview(
        exerciseId: Long,
        currentWorkoutId: Long
    ): Pair<LastSessionPreview?, LastSessionSupersetPreview?> {
        val currentWorkout = workoutSessionDao.getById(currentWorkoutId) ?: return Pair(null, null)
        val prevEntry = exerciseEntryDao.getMostRecentEntryForExercise(
            exerciseId, currentWorkoutId, currentWorkout.dateUtc
        ) ?: return Pair(null, null)

        val workout = workoutSessionDao.getById(prevEntry.workoutId) ?: return Pair(null, null)
        val dateLabel = DateUtils.formatWorkoutDateLabel(workout.dateIso)

        val groupId = prevEntry.groupId
        if (groupId != null && prevEntry.sequenceType != "NONE") {
            val supersetEntries = exerciseEntryDao.getEntriesByGroupInWorkout(prevEntry.workoutId, groupId)
            if (supersetEntries.size >= 2) {
                val exercises = mutableListOf<SupersetExercisePreview>()
                for (entry in supersetEntries) {
                    val sets = setEntryDao.getSetsForEntrySync(entry.id)
                    if (sets.isEmpty()) continue
                    val exercise = exerciseDao.getById(entry.exerciseId) ?: continue
                    exercises.add(
                        SupersetExercisePreview(
                            exerciseId = entry.exerciseId,
                            exerciseName = exercise.name,
                            iconName = exercise.iconName,
                            sets = sets
                        )
                    )
                }
                if (exercises.size >= 2) {
                    return Pair(null, LastSessionSupersetPreview(dateLabel = dateLabel, exercises = exercises, notes = prevEntry.notes))
                }
            }
        }

        val sets = setEntryDao.getSetsForEntrySync(prevEntry.id)
        if (sets.isEmpty()) return Pair(null, null)
        return Pair(LastSessionPreview(dateLabel = dateLabel, sets = sets, notes = prevEntry.notes), null)
    }
}
