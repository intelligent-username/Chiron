package com.chiron.app.data.pr

import com.chiron.app.data.dao.ExercisePrDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.entities.ExercisePr
import com.chiron.app.data.entities.SetEntry
import kotlinx.coroutines.flow.Flow

/**
 * Manages the `exercise_pr` table (global best lifts per exercise+reps).
 *
 * Exposes [syncGlobalPrBucket] for incremental updates by [SetEntryRepository]
 * and [rebuildPrsForExercise] for full correctness rebuilds on bulk changes.
 */
class PrRepository(
    private val exercisePrDao: ExercisePrDao,
    private val setEntryDao: SetEntryDao
) {
    /** Get all current global PRs for an exercise, ordered by rep count. */
    suspend fun getAllPrsForExercise(exerciseId: Long): List<ExercisePr> =
        exercisePrDao.getAllForExercise(exerciseId)

    /** Observe current PRs for an exercise as a reactive Flow. */
    fun getPrsForExerciseFlow(exerciseId: Long): Flow<List<ExercisePr>> =
        exercisePrDao.getAllForExerciseFlow(exerciseId)

    /** Get all exercise IDs that have at least one PR. */
    suspend fun getExerciseIdsWithPrs(): List<Long> =
        exercisePrDao.getExerciseIdsWithPrs()

    /**
     * Sync the global PR bucket for a single (exerciseId, reps) pairing.
     * Upserts the current best set, or deletes the row if no qualifying set exists.
     */
    suspend fun syncGlobalPrBucket(exerciseId: Long, reps: Int) {
        val bestSet = setEntryDao.getBestSetForExerciseAndReps(exerciseId, reps)
        if (bestSet == null || bestSet.weightLbs == null) {
            exercisePrDao.deleteForExerciseAndReps(exerciseId, reps)
            return
        }

        exercisePrDao.upsert(
            ExercisePr(
                exerciseId = exerciseId,
                reps = reps,
                weightLbs = bestSet.weightLbs,
                setId = bestSet.id,
                timestampUtc = bestSet.timestampUtc
            )
        )
    }

    /**
     * Full PR rebuild for one exercise. Safe to call after deletions or bulk fixes.
     *
     * 1. Clears all `is_pr` flags on every set belonging to this exercise.
     * 2. Deletes all rows in `exercise_pr` for this exercise.
     * 3. Re-scans all non-failed sets with both weight and reps filled in.
     * 4. For each rep count, finds the single heaviest set and marks it
     *    `is_pr = 1`, then upserts it into `exercise_pr`.
     */
    suspend fun rebuildPrsForExercise(exerciseId: Long) {
        setEntryDao.clearPrFlagsForExercise(exerciseId)
        exercisePrDao.clearAllForExercise(exerciseId)

        val allSets = setEntryDao.getAllSetsForExercise(exerciseId)

        val bestPerReps = mutableMapOf<Int, SetEntry>()
        for (set in allSets) {
            val reps = set.reps ?: continue
            val weight = set.weightLbs ?: continue
            if (set.isFailed != 0) continue
            val current = bestPerReps[reps]
            if (current == null || weight > current.weightLbs!!) {
                bestPerReps[reps] = set
            }
        }

        for ((_, set) in bestPerReps) {
            setEntryDao.updateSet(set.copy(isPr = 1))
            exercisePrDao.upsert(
                ExercisePr(
                    exerciseId = exerciseId,
                    reps = set.reps!!,
                    weightLbs = set.weightLbs!!,
                    setId = set.id,
                    timestampUtc = set.timestampUtc
                )
            )
        }
    }
}
