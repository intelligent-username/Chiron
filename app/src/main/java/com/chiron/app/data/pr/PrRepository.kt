package com.chiron.app.data.pr

import com.chiron.app.data.dao.ExercisePrDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.ExerciseDao
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
        rebuildPrsForExercise(exerciseId)
    }

    /**
     * Full PR rebuild for one exercise. Safe to call after deletions or bulk fixes.
     * Support all PR categories (WEIGHT_REPS, TIME_WEIGHT, DISTANCE_WEIGHT, DISTANCE_TIME).
     */
    suspend fun rebuildPrsForExercise(exerciseId: Long) {
        val exercise = exerciseDao.getById(exerciseId) ?: return
        val category = exercise.prCategory()

        setEntryDao.clearPrFlagsForExercise(exerciseId)
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
        // 1. Initial filter for relevant rep range
        val initialValid = prs.filter { it.repsInt in 1..12 }

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
            val r = pr.repsInt
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
