package com.chiron.app.data.workout

import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.entities.ExercisePr
import com.chiron.app.data.entities.SetEntry
import kotlinx.coroutines.flow.Flow

/**
 * Handles CRUD for [SetEntry] records and drives per-set historical PR evaluation.
 *
 * PR *bucket* sync (global best-per-reps in `exercise_pr`) is delegated to [PrRepository]
 * via the [onSyncGlobalPrBucket] callback to avoid circular dependencies.
 */
class SetEntryRepository(
    private val setEntryDao: SetEntryDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val onSyncGlobalPrBucket: suspend (exerciseId: Long, reps: Int) -> Unit
) {
    fun getSetsForEntry(entryId: Long): Flow<List<SetEntry>> =
        setEntryDao.getSetsForEntry(entryId)

    suspend fun insertSet(set: SetEntry): Long = setEntryDao.insertSet(set)

    suspend fun insertSetAndEvaluateHistoricalPr(set: SetEntry): Long {
        val newSetId = setEntryDao.insertSet(set)
        updateSetAndEvaluateHistoricalPr(set.copy(id = newSetId))
        return newSetId
    }

    suspend fun updateSet(set: SetEntry) = setEntryDao.updateSet(set)

    /**
     * Update one set and evaluate its historical PR flag relative to what existed
     * up to the workout day for the same exercise + reps.
     *
     * Does **not** rebuild or rewrite other sets' `is_pr` flags.
     */
    suspend fun updateSetAndEvaluateHistoricalPr(set: SetEntry) {
        val oldSet = if (set.id > 0) setEntryDao.getById(set.id) else null
        setEntryDao.updateSet(set)

        val isNewlyCompleted = oldSet != null && oldSet.weightLbs == null && oldSet.reps == null && (set.weightLbs != null || set.reps != null)

        // Only infer and update the workout's end time if a set is newly created or newly completed.
        // This prevents overwriting manual date/time edits when merely updating an existing set's reps.
        val workoutId = setEntryDao.getWorkoutIdForEntry(set.exerciseEntryId)

        if (oldSet == null || isNewlyCompleted) {
            if (workoutId != null) {
                val workout = workoutSessionDao.getById(workoutId)
                if (workout != null) {
                    // Only auto-infer if the set timestamp is within 12 hours of the workout start.
                    // This prevents modern set additions from corrupting old historical records.
                    val isWithinSessionWindow = set.timestampUtc >= workout.dateUtc && 
                                               (set.timestampUtc - workout.dateUtc) < 12 * 3600 * 1000L
                    
                    if (isWithinSessionWindow && (workout.endTimeUtc == null || set.timestampUtc > (workout.endTimeUtc ?: 0L))) {
                        workoutSessionDao.updateWorkout(workout.copy(endTimeUtc = set.timestampUtc))
                    }
                }
            }
        }

        val reps = set.reps
        val weight = set.weightLbs
        val shouldCheck = reps != null && weight != null && set.isFailed == 0

        if (!shouldCheck) {
            if (set.isPr != 0) {
                setEntryDao.updateSet(set.copy(isPr = 0))
            }
            return
        }

        val exerciseId = setEntryDao.getExerciseIdForEntry(set.exerciseEntryId) ?: return
        val workout = workoutId?.let { workoutSessionDao.getById(it) } ?: return

        val maxWeightSoFar = setEntryDao.getMaxWeightForExerciseRepsUpToWorkoutDate(
            exerciseId = exerciseId,
            reps = reps!!,
            upToWorkoutDateUtc = workout.dateUtc,
            excludeSetId = set.id
        )

        val isHistoricalPr = maxWeightSoFar == null || weight!! > maxWeightSoFar
        val newIsPr = if (isHistoricalPr) 1 else 0

        if (set.isPr != newIsPr) {
            setEntryDao.updateSet(set.copy(isPr = newIsPr))
        }

        onSyncGlobalPrBucket(exerciseId, reps)
        val oldReps = oldSet?.reps
        if (oldReps != null && oldReps != reps) {
            onSyncGlobalPrBucket(exerciseId, oldReps)
        }
    }

    suspend fun getNextSetIndex(entryId: Long): Int =
        (setEntryDao.getMaxSetIndex(entryId) ?: 0) + 1

    suspend fun deleteSet(
        entryId: Long,
        setId: Long,
        onDeletedSet: suspend (exerciseId: Long, reps: Int) -> Unit
    ) {
        val set = setEntryDao.getById(setId)
        val entry = exerciseEntryDao.getById(entryId)
        setEntryDao.deleteAndReindex(entryId, setId)
        if (entry != null && set != null && set.reps != null) {
            onDeletedSet(entry.exerciseId, set.reps)
        }
    }

    /** Get the last set recorded for an exercise (for autofill). */
    suspend fun getLastSetForExercise(exerciseId: Long): SetEntry? =
        setEntryDao.getLastSetForExercise(exerciseId)

    /** Returns total volume (weight * reps) grouped by workout day. */
    suspend fun getVolumeSummaryByDay() = setEntryDao.getVolumeSummaryByDay()
}
