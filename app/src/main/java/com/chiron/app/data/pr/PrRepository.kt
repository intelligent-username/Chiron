package com.chiron.app.data.pr

import com.chiron.app.data.dao.ExercisePrDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.entities.ExercisePr
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.data.dao.Exercise1rmEstimateDao
import com.chiron.app.data.entities.Exercise1rmEstimate
import kotlinx.coroutines.flow.Flow

/**
 * Manages the `exercise_pr` table (global best lifts per exercise+reps).
 *
 * Exposes [syncGlobalPrBucket] for incremental updates by [SetEntryRepository]
 * and [rebuildPrsForExercise] for full correctness rebuilds on bulk changes.
 */
class PrRepository(
    private val exercisePrDao: ExercisePrDao,
    private val setEntryDao: SetEntryDao,
    private val exercise1rmEstimateDao: Exercise1rmEstimateDao
) {
    /** Get all current global PRs for an exercise, ordered by rep count. */
    suspend fun getAllPrsForExercise(exerciseId: Long): List<ExercisePr> =
        exercisePrDao.getAllForExercise(exerciseId)

    /** Observe current PRs for an exercise as a reactive Flow. */
    fun getPrsForExerciseFlow(exerciseId: Long): Flow<List<ExercisePr>> =
        exercisePrDao.getAllForExerciseFlow(exerciseId)

    /** Observe the 1RM estimate for an exercise. */
    fun get1rmEstimateForExerciseFlow(exerciseId: Long): Flow<Exercise1rmEstimate?> =
        exercise1rmEstimateDao.getForExerciseFlow(exerciseId)

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
            sync1rmEstimate(exerciseId)
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

        sync1rmEstimate(exerciseId)
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

        sync1rmEstimate(exerciseId)
    }

    private suspend fun sync1rmEstimate(exerciseId: Long) {
        val prs = exercisePrDao.getAllForExercise(exerciseId)
        // 1. Initial filter for relevant rep range
        val initialValid = prs.filter { it.reps in 1..12 }

        if (initialValid.isEmpty()) {
            exercise1rmEstimateDao.deleteForExercise(exerciseId)
            return
        }

        // 2. Monotonicity Filter: Discard lower-rep PRs that have less weight than a higher-rep PR.
        // Since 'initialValid' is ordered by reps ASC, we iterate backwards.
        val monotonicPrs = mutableListOf<ExercisePr>()
        var maxWeightSeen = 0.0
        for (i in initialValid.indices.reversed()) {
            val pr = initialValid[i]
            if (pr.weightLbs >= maxWeightSeen) {
                monotonicPrs.add(0, pr)
                maxWeightSeen = pr.weightLbs
            }
        }

        val components = mutableListOf<Pair<Double, Double>>()

        for (pr in monotonicPrs) {
            val r = pr.reps
            val w = pr.weightLbs
            // 3. Epley Estimate (mHatI)
            val mHatI = if (r == 1) w else w * (1.0 + r / 30.0)
            val rDouble = r.toDouble()
            // 4. Inverse square weighting (1/r^2)
            val alpha = 1.0 / (rDouble * rDouble)
            components.add(alpha to mHatI)
        }

        if (components.isNotEmpty()) {
            // 5. Envelope Filtering: Only keep estimates within 90% of the maximum implied 1RM.
            val maxMHat = components.maxOf { it.second }
            val envelopeThreshold = maxMHat * 0.90
            val envelope = components.filter { (_, mHatI) -> mHatI >= envelopeThreshold }
            val selected = if (envelope.isNotEmpty()) envelope else components
            val selectedAlphaSum = selected.sumOf { it.first }

            if (selectedAlphaSum <= 0.0) {
                exercise1rmEstimateDao.deleteForExercise(exerciseId)
                return
            }

            // 6. Normalized Weighted Average
            var finalEstimate = 0.0
            for ((alpha, mHatI) in selected) {
                val alphaTilde = alpha / selectedAlphaSum
                finalEstimate += alphaTilde * mHatI
            }

            exercise1rmEstimateDao.upsert(
                Exercise1rmEstimate(
                    exerciseId = exerciseId,
                    estimateLbs = finalEstimate
                )
            )
        } else {
            exercise1rmEstimateDao.deleteForExercise(exerciseId)
        }
    }

    suspend fun backfill1rmEstimates() {
        val ids = exercisePrDao.getExerciseIdsWithPrs()
        for (id in ids) {
            sync1rmEstimate(id)
        }
    }
}
