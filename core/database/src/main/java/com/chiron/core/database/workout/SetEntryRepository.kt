package com.chiron.core.database.workout

import com.chiron.core.database.bodyweight.BodyweightResolver
import com.chiron.core.database.dao.BodyWeightDao
import com.chiron.core.database.dao.BodyweightSetRow
import com.chiron.core.database.dao.DailyVolume
import com.chiron.core.database.dao.ExerciseDao
import com.chiron.core.database.dao.ExerciseEntryDao
import com.chiron.core.database.dao.SetEntryDao
import com.chiron.core.database.dao.WorkoutSessionDao
import com.chiron.core.database.pr.PrCategory
import com.chiron.core.database.pr.prCategory
import com.chiron.core.model.BodyWeightEntry
import com.chiron.core.model.SetEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

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
    private val onSyncGlobalPrBucket: suspend (exerciseId: Long, reps: Int) -> Unit,
    private val bodyWeightDao: BodyWeightDao? = null
) {
    fun getSetsForEntry(entryId: Long): Flow<List<SetEntry>> =
        setEntryDao.getSetsForEntry(entryId)

    suspend fun insertSet(set: SetEntry): Long {
        val newSetId = setEntryDao.insertSet(set)
        updateWorkoutEndTime(set.exerciseEntryId, set.timestampUtc)
        return newSetId
    }

    suspend fun insertSetAndEvaluateHistoricalPr(set: SetEntry): Long {
        val newSetId = setEntryDao.insertSet(set)
        updateWorkoutEndTime(set.exerciseEntryId, set.timestampUtc)
        updateSetAndEvaluateHistoricalPrInternal(set.copy(id = newSetId), isNewSet = true)
        return newSetId
    }

    suspend fun updateSet(set: SetEntry) = setEntryDao.updateSet(set)

    private suspend fun updateWorkoutEndTime(exerciseEntryId: Long, timestampUtc: Long) {
        val workoutId = setEntryDao.getWorkoutIdForEntry(exerciseEntryId) ?: return
        val workout = workoutSessionDao.getById(workoutId) ?: return
        val currentEndTime = workout.endTimeUtc
        val newEndTime = if (timestampUtc < workout.dateUtc) workout.dateUtc else timestampUtc
        if (currentEndTime == null || newEndTime > currentEndTime) {
            workoutSessionDao.updateWorkout(workout.copy(endTimeUtc = newEndTime))
        }
    }

    /**
     * Update one set and evaluate its historical PR flag relative to what existed
     * up to the workout day for the same exercise + reps.
     *
     * PR evaluation is skipped for exercises that are not weight+reps configured.
     * Does **not** rebuild or rewrite other sets' `is_pr` flags.
     */
    suspend fun updateSetAndEvaluateHistoricalPr(set: SetEntry) {
        updateSetAndEvaluateHistoricalPrInternal(set, isNewSet = false)
    }

    private suspend fun updateSetAndEvaluateHistoricalPrInternal(set: SetEntry, isNewSet: Boolean) {
        val oldSet = if (!isNewSet && set.id > 0) setEntryDao.getById(set.id) else null
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

        // Infer and update the workout's end time if a set is newly created or newly completed.
        if (isNewSet || isNowCompleted) {
            updateWorkoutEndTime(set.exerciseEntryId, set.timestampUtc)
        }

        // ── PR evaluation ──────────────────────────────────────────────────────
        if (exercise == null) return

        val category = exercise.prCategory()
        if (category == PrCategory.NONE) {
            if (set.isPr != 0) {
                setEntryDao.updateSet(set.copy(isPr = 0))
            }
            return
        }

        if (category != PrCategory.WEIGHT_REPS) {
            // For TIME_WEIGHT, DISTANCE_WEIGHT, and DISTANCE_TIME, PR evaluation
            // and per-set is_pr flags are delegated to PrRepository.rebuildPrsForExercise.
            onSyncGlobalPrBucket(exerciseId, 0)
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

        val workoutId = setEntryDao.getWorkoutIdForEntry(set.exerciseEntryId) ?: return
        val workout = workoutSessionDao.getById(workoutId) ?: return

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
        val workoutId = setEntryDao.getWorkoutIdForEntry(entryId)
        val set = setEntryDao.getById(setId)
        val entry = exerciseEntryDao.getById(entryId)
        setEntryDao.deleteAndReindex(entryId, setId)
        val reps = set?.reps
        if (entry != null) {
            onDeletedSet(entry.exerciseId, reps ?: 0)
        }
        if (workoutId != null) {
            val lastTimestamp = setEntryDao.getLastSetTimestampForWorkout(workoutId)
            val workout = workoutSessionDao.getById(workoutId)
            if (workout != null) {
                workoutSessionDao.updateWorkout(workout.copy(endTimeUtc = lastTimestamp))
            }
        }
    }

    suspend fun getLastSetTimestampForWorkout(workoutId: Long): Long? =
        setEntryDao.getLastSetTimestampForWorkout(workoutId)

    suspend fun getFirstSetTimestampForWorkout(workoutId: Long): Long? =
        setEntryDao.getFirstSetTimestampForWorkout(workoutId)

    /** Get the last set recorded for an exercise (for autofill). */
    suspend fun getLastSetForExercise(exerciseId: Long): SetEntry? =
        setEntryDao.getLastSetForExercise(exerciseId)

    /**
     * Total volume grouped by workout day, including dynamic bodyweight share.
     *
     * Base aggregates come from the bodyweight-excluded DAO queries; bodyweight
     * component rows are resolved in Kotlin per day via [BodyweightResolver] and
     * merged in. No stored volume column exists anywhere (Option C rejected);
     * the per-day cache is built once per emission then discarded.
     */
    suspend fun getVolumeSummaryByDay(exerciseId: Long? = null): List<DailyVolume> {
        val bwDao = bodyWeightDao ?: return baseVolumeSync(exerciseId)
        val weights = bwDao.getAllFlow().first()
        val base = baseVolumeSync(exerciseId)
        val rows = bodyweightRowsSync(exerciseId)
        return mergeVolumes(base, rows, weights)
    }

    /** Flow variant combining weights with base volume, re-emitting on edits. */
    fun getVolumeSummaryByDayFlow(exerciseId: Long? = null): Flow<List<DailyVolume>> {
        val bwDao = bodyWeightDao ?: return baseVolumeFlow(exerciseId)
        val weightsFlow = bwDao.getAllFlow()
        return if (exerciseId != null) {
            combine(
                weightsFlow,
                setEntryDao.getVolumeSummaryByDayForExerciseFlow(exerciseId),
                setEntryDao.getBodyweightSetRowsForExerciseFlow(exerciseId)
            ) { weights, base, rows -> mergeVolumes(base, rows, weights) }
                .distinctUntilChanged()
        } else {
            combine(
                weightsFlow,
                setEntryDao.getVolumeSummaryByDayFlow(),
                setEntryDao.getBodyweightSetRowsFlow()
            ) { weights, base, rows -> mergeVolumes(base, rows, weights) }
                .distinctUntilChanged()
        }
    }

    private suspend fun baseVolumeSync(exerciseId: Long?): List<DailyVolume> =
        if (exerciseId != null) setEntryDao.getVolumeSummaryByDayForExercise(exerciseId)
        else setEntryDao.getVolumeSummaryByDay()

    private fun baseVolumeFlow(exerciseId: Long?): Flow<List<DailyVolume>> =
        if (exerciseId != null) setEntryDao.getVolumeSummaryByDayForExerciseFlow(exerciseId)
        else setEntryDao.getVolumeSummaryByDayFlow()

    private suspend fun bodyweightRowsSync(exerciseId: Long?): List<BodyweightSetRow> =
        if (exerciseId != null) setEntryDao.getBodyweightSetRowsForExercise(exerciseId)
        else setEntryDao.getBodyweightSetRows()

    private fun mergeVolumes(
        base: List<DailyVolume>,
        rows: List<BodyweightSetRow>,
        weights: List<BodyWeightEntry>
    ): List<DailyVolume> {
        if (rows.isEmpty()) return base
        val sorted = weights.sortedBy { it.timestampUtc }
        val cache = rows.map { it.dateUtc }.toSet()
            .associateWith { day -> BodyweightResolver.getWeightForTimestamp(day, sorted) }
        val extra = rows.groupBy { it.dateUtc }
            .mapValues { (_, dayRows) -> dayRows.sumOf { effectiveVolume(it, cache[it.dateUtc]) } }
        val totals = base.associate { it.dateUtc to it.volumeLbs }.toMutableMap()
        for ((day, volume) in extra) totals[day] = (totals[day] ?: 0.0) + volume
        return totals.entries.sortedBy { it.key }
            .map { DailyVolume(it.key, it.value) }
    }

    private fun effectiveVolume(row: BodyweightSetRow, bwLbs: Double?): Double {
        val repEq = repEquivalent(row) ?: return 0.0
        if (bwLbs == null) return (row.addedWeightLbs ?: return 0.0) * repEq
        val pct = if (row.percentBodyweight <= 0) 100.0 else row.percentBodyweight
        return (bwLbs * pct / 100.0 + (row.addedWeightLbs ?: 0.0)) * repEq
    }

    private fun repEquivalent(row: BodyweightSetRow): Double? {
        val reps = row.reps
        val duration = row.durationSeconds
        val distance = row.distanceMeters
        return when {
            distance != null && reps != null -> reps * (distance * 2.0)
            distance != null -> distance / 5.0
            duration != null -> duration / 3.0
            reps != null -> reps.toDouble()
            else -> null
        }
    }
}
