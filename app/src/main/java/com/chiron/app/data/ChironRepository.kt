package com.chiron.app.data

import android.content.Context
import android.net.Uri
import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.ExercisePrDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.dao.TimerPresetDao
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.ExercisePr
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.data.entities.WorkoutSession
import com.chiron.app.data.entities.TimerPreset
import com.chiron.app.util.Jaccard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream

class ChironRepository(
    private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val setEntryDao: SetEntryDao,
    private val timerPresetDao: TimerPresetDao,
    private val exercisePrDao: ExercisePrDao
) {
    // ─────────────────────────────────────────────────────────────────────────
    // Exercise ops
    // ─────────────────────────────────────────────────────────────────────────

    val exercisesFlow: Flow<List<Exercise>> = exerciseDao.getExercisesFlow()

    val archivedExercisesFlow: Flow<List<Exercise>> = exerciseDao.getAllExercisesFlow()
        .map { exercises -> exercises.filter { it.archived != 0 } }

    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insertExercise(exercise)

    suspend fun updateExercise(exercise: Exercise) = exerciseDao.updateExercise(exercise)

    suspend fun getExerciseById(id: Long): Exercise? = exerciseDao.getById(id)

    suspend fun getExerciseByName(name: String): Exercise? = exerciseDao.getByName(name)

    suspend fun archiveExercise(id: Long) = exerciseDao.archive(id)

    suspend fun unarchiveExercise(id: Long) = exerciseDao.unarchive(id)

    /**
     * Search exercises using Jaccard similarity on tokenized names.
     * Tie-break by recency (lower ID = older, so prefer higher ID).
     */
    suspend fun searchExercises(query: String, archived: Boolean = false, limit: Int = 10): List<Exercise> {
        if (query.isBlank()) return emptyList()
        val allExercises = if (archived) exerciseDao.getAllArchived() else exerciseDao.getAllNonArchived()
        return Jaccard.rankBySimilarity(query, allExercises, { it.name }, limit)

    }

    suspend fun getAllExercises(): List<Exercise> = exerciseDao.getAllNonArchived()

    // ─────────────────────────────────────────────────────────────────────────
    // Last session preview (for press-and-hold reference)
    // ─────────────────────────────────────────────────────────────────────────

    data class LastSessionPreview(
        val dateLabel: String,
        val sets: List<SetEntry>,
        val notes: String? = null
    )

    suspend fun getLastSessionPreview(exerciseId: Long, currentWorkoutId: Long): LastSessionPreview? {
        val entry = exerciseEntryDao.getMostRecentEntryForExercise(exerciseId, currentWorkoutId)
            ?: return null
        val sets = setEntryDao.getSetsForEntrySync(entry.id)
        if (sets.isEmpty()) return null

        val workout = workoutSessionDao.getById(entry.workoutId) ?: return null
        val dateLabel = try {
            val date = java.time.LocalDate.parse(workout.dateIso)
            val dayOfWeek = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            "$dayOfWeek, $month ${date.dayOfMonth}"
        } catch (e: Exception) {
            workout.dateIso
        }

        return LastSessionPreview(dateLabel = dateLabel, sets = sets, notes = entry.notes)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Workout session operations
    // ─────────────────────────────────────────────────────────────────────────

    val workoutsFlow: Flow<List<WorkoutSession>> = workoutSessionDao.getWorkoutsFlow()

    val dayTagsFlow: Flow<List<String>> = workoutSessionDao.getDistinctDayTagsFlow()

    suspend fun insertWorkout(session: WorkoutSession): Long = workoutSessionDao.insertWorkout(session)

    suspend fun updateWorkout(session: WorkoutSession) = workoutSessionDao.updateWorkout(session)

    suspend fun getWorkoutById(id: Long): WorkoutSession? = workoutSessionDao.getById(id)

    fun getWorkoutsByDayTag(dayTag: String): Flow<List<WorkoutSession>> =
        workoutSessionDao.getByDayTagFlow(dayTag)

    suspend fun archiveWorkout(id: Long) = workoutSessionDao.archive(id)

    /**
     * Deep-copy a workout with today's date. All entries and sets are duplicated by value.
     * Returns the new workout's ID.
     */
    suspend fun duplicateWorkout(sourceWorkoutId: Long): Long {
        val source = workoutSessionDao.getById(sourceWorkoutId) ?: return -1L

        val now = java.time.Instant.now()
        val todayIso = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

        val newSession = source.copy(
            id = 0L,
            dateIso = todayIso,
            dateUtc = now.toEpochMilli()
        )
        val newWorkoutId = workoutSessionDao.insertWorkout(newSession)

        // Copy all exercise entries
        val sourceEntries = exerciseEntryDao.getEntriesForWorkoutSync(sourceWorkoutId)
        // Map old entry IDs to new entry IDs (for groupId references)
        val entryIdMap = mutableMapOf<Long, Long>()

        for (entry in sourceEntries) {
            val newEntry = entry.copy(
                id = 0L,
                workoutId = newWorkoutId,
                groupId = null // will fix after all entries are created
            )
            val newEntryId = exerciseEntryDao.insertEntry(newEntry)
            entryIdMap[entry.id] = newEntryId

            // Copy all sets for this entry
            val sourceSets = setEntryDao.getSetsForEntrySync(entry.id)
            for (set in sourceSets) {
                setEntryDao.insertSet(set.copy(
                    id = 0L,
                    exerciseEntryId = newEntryId,
                    timestampUtc = now.toEpochMilli()
                ))
            }
        }

        // Fix groupId references for superset entries
        for (entry in sourceEntries) {
            val oldGroupId = entry.groupId ?: continue
            val newEntryId = entryIdMap[entry.id] ?: continue
            val newGroupId = entryIdMap[oldGroupId] ?: continue
            val currentEntry = exerciseEntryDao.getById(newEntryId) ?: continue
            exerciseEntryDao.updateEntry(currentEntry.copy(groupId = newGroupId))
        }

        return newWorkoutId
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Exercise entry operations
    // ─────────────────────────────────────────────────────────────────────────

    fun getEntriesForWorkout(workoutId: Long): Flow<List<ExerciseEntry>> =
        exerciseEntryDao.getEntriesForWorkout(workoutId)

    suspend fun insertExerciseEntry(entry: ExerciseEntry): Long = exerciseEntryDao.insertEntry(entry)

    suspend fun updateExerciseEntry(entry: ExerciseEntry) = exerciseEntryDao.updateEntry(entry)

    suspend fun getNextSlotIndex(workoutId: Long): Int =
        (exerciseEntryDao.getMaxSlotIndex(workoutId) ?: 0) + 1

    suspend fun deleteExerciseEntry(workoutId: Long, entryId: Long) {
        val entry = exerciseEntryDao.getById(entryId)
        exerciseEntryDao.deleteAndReindex(workoutId, entryId)
        if (entry != null) rebuildPrsForExercise(entry.exerciseId)
    }

    // ──────────────────────
    // Set entry operations
    // ──────────────────────

    fun getSetsForEntry(entryId: Long): Flow<List<SetEntry>> = setEntryDao.getSetsForEntry(entryId)

    suspend fun insertSet(set: SetEntry): Long = setEntryDao.insertSet(set)

    suspend fun updateSet(set: SetEntry) = setEntryDao.updateSet(set)

    /**
     * Update one set and evaluate its historical PR flag once, relative to what existed
     * up to the workout day for the same exercise + reps.
     *
     * This does not rebuild or rewrite other sets' `is_pr` flags.
     */
    suspend fun updateSetAndEvaluateHistoricalPr(set: SetEntry) {
        val oldSet = if (set.id > 0) setEntryDao.getById(set.id) else null
        setEntryDao.updateSet(set)

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
        val workoutId = setEntryDao.getWorkoutIdForEntry(set.exerciseEntryId) ?: return
        val workout = workoutSessionDao.getById(workoutId) ?: return

        val maxWeightSoFar = setEntryDao.getMaxWeightForExerciseRepsUpToWorkoutDate(
            exerciseId = exerciseId,
            reps = reps,
            upToWorkoutDateUtc = workout.dateUtc,
            excludeSetId = set.id
        )

        val isHistoricalPr = maxWeightSoFar == null || weight > maxWeightSoFar
        val newIsPr = if (isHistoricalPr) 1 else 0

        if (set.isPr != newIsPr) {
            setEntryDao.updateSet(set.copy(isPr = newIsPr))
        }

        syncGlobalPrBucket(exerciseId, reps)
        val oldReps = oldSet?.reps
        if (oldReps != null && oldReps != reps) {
            syncGlobalPrBucket(exerciseId, oldReps)
        }
    }

    private suspend fun syncGlobalPrBucket(exerciseId: Long, reps: Int) {
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

    suspend fun getNextSetIndex(entryId: Long): Int =
        (setEntryDao.getMaxSetIndex(entryId) ?: 0) + 1

    suspend fun deleteSet(entryId: Long, setId: Long) {
        val entry = exerciseEntryDao.getById(entryId)
        setEntryDao.deleteAndReindex(entryId, setId)
        if (entry != null) rebuildPrsForExercise(entry.exerciseId)
    }

    /**
     * Get the last set recorded for an exercise (for autofill).
     */
    suspend fun getLastSetForExercise(exerciseId: Long): SetEntry? =
        setEntryDao.getLastSetForExercise(exerciseId)

    // ─────────────────────────────────────────────────────────────────────────
    // PR Detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full PR rebuild for one exercise. Safe to call after deletions or bulk fixes.
     *
     * 1. Clears all `is_pr` flags on every set belonging to this exercise.
     * 2. Deletes all rows in `exercise_pr` for this exercise.
     * 3. Re-scans all non-failed sets with both weight and reps filled in.
     * 4. For each rep count, finds the single heaviest set and marks it
     *    `is_pr = 1`, then upserts it into `exercise_pr`.
     *
     * This approach is correct regardless of edits, re-orders, or deletions:
     * the truth is always derived from what's currently in the database.
     */
    suspend fun rebuildPrsForExercise(exerciseId: Long) {
        // Step 1 & 2: wipe stale state
        setEntryDao.clearPrFlagsForExercise(exerciseId)
        exercisePrDao.clearAllForExercise(exerciseId)

        // Step 3: load all qualifying sets (existing DAO query, already joins exercise_entry)
        val allSets = setEntryDao.getAllSetsForExercise(exerciseId)

        // Step 4: find max weight per rep count
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

        // Step 5: persist
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

    /** Get all current global PRs for an exercise, ordered by rep count. */
    suspend fun getAllPrsForExercise(exerciseId: Long): List<ExercisePr> =
        exercisePrDao.getAllForExercise(exerciseId)

    /** Observe current PRs for an exercise as a reactive Flow. */
    fun getPrsForExerciseFlow(exerciseId: Long) =
        exercisePrDao.getAllForExerciseFlow(exerciseId)

    /** Get all exercise IDs that have at least one PR. */
    suspend fun getExerciseIdsWithPrs(): List<Long> =
        exercisePrDao.getExerciseIdsWithPrs()

    // ─────────────────────────────────────────────────────────────────────────
    // Image handling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Copy an image from the given URI into app-managed storage.
     * Returns the internal file URI string.
     */
    fun copyImageToStorage(sourceUri: Uri, exerciseId: Long): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val imagesDir = File(context.filesDir, "images/exercises")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val extension = context.contentResolver.getType(sourceUri)
                ?.substringAfter("/") ?: "jpg"
            val fileName = "${exerciseId}_${System.currentTimeMillis()}.$extension"
            val destFile = File(imagesDir, fileName)

            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Delete an image file from app storage.
     */
    fun deleteImage(imageUri: String): Boolean {
        return try {
            val file = File(Uri.parse(imageUri).path ?: return false)
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timer Presets operations
    // ─────────────────────────────────────────────────────────────────────────

    val timerPresetsFlow: Flow<List<TimerPreset>> = timerPresetDao.getPresetsFlow()

    suspend fun insertTimerPreset(preset: TimerPreset): Long = timerPresetDao.insertPreset(preset)

    suspend fun updateTimerPreset(preset: TimerPreset) = timerPresetDao.updatePreset(preset)

    suspend fun deleteTimerPreset(preset: TimerPreset) = timerPresetDao.deletePreset(preset)

    suspend fun getTimerPresetById(id: Long): TimerPreset? = timerPresetDao.getPresetById(id)
}
