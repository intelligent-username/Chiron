package com.chiron.app.data

import android.content.Context
import android.net.Uri
import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.dao.TimerPresetDao
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.ExerciseEntry
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
    private val timerPresetDao: TimerPresetDao
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
    suspend fun searchExercises(query: String, limit: Int = 10): List<Exercise> {
        if (query.isBlank()) return emptyList()
        val allExercises = exerciseDao.getAllNonArchived()
        return Jaccard.rankBySimilarity(query, allExercises, { it.name }, limit)

    }

    suspend fun getAllExercises(): List<Exercise> = exerciseDao.getAllNonArchived()

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

    // ─────────────────────────────────────────────────────────────────────────
    // Exercise entry operations
    // ─────────────────────────────────────────────────────────────────────────

    fun getEntriesForWorkout(workoutId: Long): Flow<List<ExerciseEntry>> =
        exerciseEntryDao.getEntriesForWorkout(workoutId)

    suspend fun insertExerciseEntry(entry: ExerciseEntry): Long = exerciseEntryDao.insertEntry(entry)

    suspend fun updateExerciseEntry(entry: ExerciseEntry) = exerciseEntryDao.updateEntry(entry)

    suspend fun getNextSlotIndex(workoutId: Long): Int =
        (exerciseEntryDao.getMaxSlotIndex(workoutId) ?: 0) + 1

    suspend fun deleteExerciseEntry(workoutId: Long, entryId: Long) =
        exerciseEntryDao.deleteAndReindex(workoutId, entryId)

    // ─────────────────────────────────────────────────────────────────────────
    // Set entry operations
    // ─────────────────────────────────────────────────────────────────────────

    fun getSetsForEntry(entryId: Long): Flow<List<SetEntry>> = setEntryDao.getSetsForEntry(entryId)

    suspend fun insertSet(set: SetEntry): Long = setEntryDao.insertSet(set)

    suspend fun updateSet(set: SetEntry) = setEntryDao.updateSet(set)

    suspend fun getNextSetIndex(entryId: Long): Int =
        (setEntryDao.getMaxSetIndex(entryId) ?: 0) + 1

    suspend fun deleteSet(entryId: Long, setId: Long) = setEntryDao.deleteAndReindex(entryId, setId)

    /**
     * Get the last set recorded for an exercise (for autofill).
     */
    suspend fun getLastSetForExercise(exerciseId: Long): SetEntry? =
        setEntryDao.getLastSetForExercise(exerciseId)

    // ─────────────────────────────────────────────────────────────────────────
    // PR Detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Check if this set is a new PR for the exercise at the given rep count.
     * PR = highest weight ever recorded for that exact rep count.
     */
    suspend fun isNewPr(exerciseId: Long, weightLbs: Double, reps: Int): Boolean {
        val allSets = setEntryDao.getAllSetsForExercise(exerciseId)
        val maxWeightAtReps = allSets
            .filter { it.reps == reps && it.weightLbs != null }
            .maxOfOrNull { it.weightLbs!! }
        return maxWeightAtReps == null || weightLbs > maxWeightAtReps
    }

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
