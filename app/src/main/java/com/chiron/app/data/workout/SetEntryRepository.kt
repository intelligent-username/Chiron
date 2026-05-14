package com.chiron.app.data.workout

import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.entities.SetEntry
import kotlinx.coroutines.flow.Flow

/**
 * Handles CRUD for [SetEntry] records and drives per-set historical PR evaluation.
 *
 * PR *bucket* sync (global best-per-reps in `exercise_pr`) is delegated to [PrRepository]
 * via the [onSyncGlobalPrBucket] callback to avoid circular dependencies.
 *
 * PR evaluation is gated: only exercises with `isWeightBased == 1 && isRepBased == 1`
 * are eligible for PR tracking.
 */
class SetEntryRepository(
    private val setEntryDao: SetEntryDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseDao: ExerciseDao,
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
     * PR evaluation is skipped for exercises that are not weight+reps configured.
     * Does **not** rebuild or rewrite other sets' `is_pr` flags.
     */
    suspend fun updateSetAndEvaluateHistoricalPr(set: SetEntry) {
        val oldSet = if (set.id > 0) setEntryDao.getById(set.id) else null
        setEntryDao.updateSet(set)

        // Config-driven placeholder detection: a set is newly completed if all
        // enabled-metric columns transitioned from null → non-null for any metric.
        val exerciseId = setEntryDao.getExerciseIdForEntry(set.exerciseEntryId) ?: return
        val exercise = exerciseDao.getById(exerciseId)

        val wasPlaceholder = if (oldSet != null && exercise != null) {
            val wasWeightNull = exercise.isWeightBased != 1 || oldSet.weightLbs == null
            val wasRepsNull = exercise.isRepBased != 1 || oldSet.reps == null
            val wasTimeNull = exercise.isTimeBased != 1 || oldSet.durationSeconds == null
            val wasDistNull = exercise.isDistanceBased != 1 || oldSet.distanceMeters == null
            wasWeightNull && wasRepsNull && wasTimeNull && wasDistNull
        } else false

        val isNowCompleted = wasPlaceholder && (
            set.weightLbs != null || set.reps != null ||
            set.durationSeconds != null || set.distanceMeters != null
        )

        // Only infer and update the workout's end time if a set is newly created or newly completed.
        val workoutId = setEntryDao.getWorkoutIdForEntry(set.exerciseEntryId)

        if (oldSet == null || isNowCompleted) {
            if (workoutId != null) {
                val workout = workoutSessionDao.getById(workoutId)
                if (workout != null) {
                    val isWithinSessionWindow = set.timestampUtc >= workout.dateUtc &&
                                               (set.timestampUtc - workout.dateUtc) < 12 * 3600 * 1000L
                    if (isWithinSessionWindow && (workout.endTimeUtc == null || set.timestampUtc > (workout.endTimeUtc ?: 0L))) {
                        workoutSessionDao.updateWorkout(workout.copy(endTimeUtc = set.timestampUtc))
                    }
                }
            }
        }

        // ── PR evaluation ──────────────────────────────────────────────────────
        // Only weight+reps exercises are eligible for PR tracking.
        val isPrEligible = exercise != null &&
            exercise.isWeightBased == 1 &&
            exercise.isRepBased == 1

        if (!isPrEligible) {
            // Ensure is_pr = 0 for non-eligible exercises
            if (set.isPr != 0) {
                setEntryDao.updateSet(set.copy(isPr = 0))
            }
            return
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
    suspend fun getVolumeSummaryByDay(exerciseId: Long? = null) =
        if (exerciseId != null) setEntryDao.getVolumeSummaryByDayForExercise(exerciseId)
        else setEntryDao.getVolumeSummaryByDay()
}
