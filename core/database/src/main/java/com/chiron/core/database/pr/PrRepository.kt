package com.chiron.core.database.pr

import com.chiron.core.database.dao.ExercisePrDao
import com.chiron.core.database.dao.SetEntryDao
import com.chiron.core.database.dao.ExerciseDao
import com.chiron.core.model.Exercise
import com.chiron.core.model.SetEntry
import com.chiron.core.database.dao.Exercise1rmEstimateDao
import com.chiron.core.model.Exercise1rmEstimate
import com.chiron.core.model.ExercisePr
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
    private val exercise1rmEstimateDao: Exercise1rmEstimateDao,
    private val exerciseDao: ExerciseDao
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
     * Delegates to rebuildPrsForExercise to maintain all PR categories consistently.
     */
    suspend fun syncGlobalPrBucket(exerciseId: Long, reps: Int) {
        val exercise = exerciseDao.getById(exerciseId) ?: return
        when (exercise.prCategory()) {
            PrCategory.WEIGHT_REPS -> {
                // Incremental update for weight+reps PRs – preserve historic is_pr flags
                val bestSet = setEntryDao.getBestSetForExerciseAndReps(exerciseId, reps)
                if (bestSet != null) {
                    // Ensure the best set has the PR flag (historical PRs stay untouched)
                    if (bestSet.isPr == 0) {
                        setEntryDao.updateSet(bestSet.copy(isPr = 1))
                    }
                    // Upsert the global PR record for this rep count
                    exercisePrDao.upsert(
                        ExercisePr(
                            exerciseId = exerciseId,
                            reps = bestSet.reps!!,
                            weightLbs = bestSet.weightLbs!!,
                            setId = bestSet.id,
                            timestampUtc = bestSet.timestampUtc
                        )
                    )
                } else {
                    // No qualifying set – remove any existing PR entry for this rep count
                    exercisePrDao.deleteForExerciseAndReps(exerciseId, reps)
                }
            }
            else -> {
                // For all other categories retain the full rebuild (necessary for time/distance PRs)
                rebuildPrsForExercise(exerciseId)
            }
        }
    }

    /**
     * Full PR rebuild for one exercise. Safe to call after deletions or bulk fixes.
     * Support all PR categories (WEIGHT_REPS, TIME_WEIGHT, DISTANCE_WEIGHT, DISTANCE_TIME).
     */
    suspend fun rebuildPrsForExercise(exerciseId: Long) {
        val exercise = exerciseDao.getById(exerciseId) ?: return
        val category = exercise.prCategory()

        // Clear per‑set PR flags only for categories that use them. WEIGHT_REPS retains historic flags.
        if (category != PrCategory.WEIGHT_REPS) {
            setEntryDao.clearPrFlagsForExercise(exerciseId)
        }
        exercisePrDao.clearAllForExercise(exerciseId)

        val allSets = setEntryDao.getAllSetsForExerciseAny(exerciseId)

        when (category) {
            PrCategory.WEIGHT_REPS -> {
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
            PrCategory.TIME_WEIGHT -> {
                // bucket = weight (lbs), record = duration (seconds) (higher is better)
                val bestPerWeight = mutableMapOf<Double, SetEntry>()
                for (set in allSets) {
                    val weight = set.weightLbs ?: continue
                    val duration = set.durationSeconds ?: continue
                    if (set.isFailed != 0) continue
                    val current = bestPerWeight[weight]
                    if (current == null || duration > (current.durationSeconds ?: 0)) {
                        bestPerWeight[weight] = set
                    }
                }
                for ((weight, set) in bestPerWeight) {
                    exercisePrDao.upsert(
                        ExercisePr(
                            exerciseId = exerciseId,
                            bucket = weight,
                            record = set.durationSeconds!!.toDouble(),
                            setId = set.id,
                            timestampUtc = set.timestampUtc
                        )
                    )
                }
            }
            PrCategory.DISTANCE_WEIGHT -> {
                // If isRepBased == 1 (e.g. box jumps):
                // bucket = distance * 100000 + reps, record = weight (lbs) (higher is better)
                if (exercise.isRepBased == 1) {
                    val bestPerDistReps = mutableMapOf<Double, SetEntry>()
                    for (set in allSets) {
                        val distance = set.distanceMeters ?: continue
                        if (distance <= 0.0) continue  // skip sets with no real distance recorded
                        val reps = set.reps ?: continue
                        val weight = set.weightLbs ?: continue
                        if (set.isFailed != 0) continue
                        val key = distance * 100000.0 + reps.toDouble()
                        val current = bestPerDistReps[key]
                        if (current == null || weight > (current.weightLbs ?: 0.0)) {
                            bestPerDistReps[key] = set
                        }
                    }
                    for ((key, set) in bestPerDistReps) {
                        exercisePrDao.upsert(
                            ExercisePr(
                                exerciseId = exerciseId,
                                bucket = key,
                                record = set.weightLbs!!,
                                setId = set.id,
                                timestampUtc = set.timestampUtc
                            )
                        )
                    }
                } else {
                    // If isRepBased == 0:
                    // bucket = weight (lbs), record = distance (meters) (higher is better)
                    val bestPerWeight = mutableMapOf<Double, SetEntry>()
                    for (set in allSets) {
                        val weight = set.weightLbs ?: continue
                        val distance = set.distanceMeters ?: continue
                        if (set.isFailed != 0) continue
                        val current = bestPerWeight[weight]
                        if (current == null || distance > (current.distanceMeters ?: 0.0)) {
                            bestPerWeight[weight] = set
                        }
                    }
                    for ((weight, set) in bestPerWeight) {
                        exercisePrDao.upsert(
                            ExercisePr(
                                exerciseId = exerciseId,
                                bucket = weight,
                                record = set.distanceMeters!!,
                                setId = set.id,
                                timestampUtc = set.timestampUtc
                            )
                        )
                    }
                }
            }
            PrCategory.DISTANCE_TIME -> {
                // bucket = distance (meters), record = duration (seconds) (lower is better)
                val bestPerDistance = mutableMapOf<Double, SetEntry>()
                for (set in allSets) {
                    val distance = set.distanceMeters ?: continue
                    val duration = set.durationSeconds ?: continue
                    if (set.isFailed != 0) continue
                    val current = bestPerDistance[distance]
                    if (current == null || duration < (current.durationSeconds ?: Int.MAX_VALUE)) {
                        bestPerDistance[distance] = set
                    }
                }
                for ((distance, set) in bestPerDistance) {
                    exercisePrDao.upsert(
                        ExercisePr(
                            exerciseId = exerciseId,
                            bucket = distance,
                            record = set.durationSeconds!!.toDouble(),
                            setId = set.id,
                            timestampUtc = set.timestampUtc
                        )
                    )
                }
            }
            PrCategory.NONE -> {}
        }

        sync1rmEstimate(exerciseId)
    }

    private suspend fun sync1rmEstimate(exerciseId: Long) {
        val exercise = exerciseDao.getById(exerciseId)
        if (exercise == null || exercise.prCategory() != PrCategory.WEIGHT_REPS) {
            exercise1rmEstimateDao.deleteForExercise(exerciseId)
            return
        }

        val prs = exercisePrDao.getAllForExercise(exerciseId)
        // 1. Filter to rep range where Epley formula is most reliable.
        val initialValid = prs.filter { it.repsInt in 1..8 }

        if (initialValid.isEmpty()) {
            exercise1rmEstimateDao.deleteForExercise(exerciseId)
            return
        }

        // 2. Monotonicity filter: discard lower-rep PRs with less weight than a higher-rep PR.
        // PRs are ordered by reps ASC, so iterate backwards (high reps → low reps).
        val monotonicPrs = mutableListOf<ExercisePr>()
        var maxWeightSeen = 0.0
        for (i in initialValid.indices.reversed()) {
            val pr = initialValid[i]
            if (pr.weightLbs >= maxWeightSeen) {
                monotonicPrs.add(0, pr)
                maxWeightSeen = pr.weightLbs
            }
        }

        // 3. Take the maximum Epley estimate across all remaining PRs.
        // Epley: 1RM = w * (1 + r/30). For r=1, use raw weight (it's already the 1RM).
        var finalEstimate = 0.0
        for (pr in monotonicPrs) {
            val mHat = if (pr.repsInt == 1) pr.weightLbs else pr.weightLbs * (1.0 + pr.repsInt / 30.0)
            if (mHat > finalEstimate) finalEstimate = mHat
        }

        if (finalEstimate > 0.0) {
            exercise1rmEstimateDao.upsert(
                Exercise1rmEstimate(exerciseId = exerciseId, estimateLbs = finalEstimate)
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
