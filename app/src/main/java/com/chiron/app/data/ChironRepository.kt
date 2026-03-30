package com.chiron.app.data

import android.content.Context
import android.net.Uri
import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.ExercisePrDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.TimerPresetDao
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.ExercisePr
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.data.entities.TimerPreset
import com.chiron.app.data.entities.WorkoutSession
import com.chiron.app.data.exercise.ExerciseRepository
import com.chiron.app.data.exercise.ImageRepository
import com.chiron.app.data.pr.PrRepository
import com.chiron.app.data.timer.TimerPresetRepository
import com.chiron.app.data.transfer.DataTransferRepository
import com.chiron.app.data.workout.ExerciseEntryRepository
import com.chiron.app.data.workout.SessionPreviewRepository
import com.chiron.app.data.workout.SetEntryRepository
import com.chiron.app.data.workout.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Facade repository that composes all domain-specific sub-repositories.
 *
 * The public API of this class is intentionally unchanged so that all existing
 * ViewModels, UI screens, and the ServiceLocator continue to work without modification.
 *
 * Sub-repository responsibilities:
 *  - [ExerciseRepository]       – Exercise CRUD and Jaccard search
 *  - [SessionPreviewRepository] – Last-session preview aggregates
 *  - [WorkoutSessionRepository] – WorkoutSession CRUD and duplication
 *  - [ExerciseEntryRepository]  – ExerciseEntry CRUD and duplication
 *  - [SetEntryRepository]       – SetEntry CRUD and per-set PR evaluation
 *  - [PrRepository]             – exercise_pr table management and full rebuilds
 *  - [ImageRepository]          – Exercise image file management
 *  - [TimerPresetRepository]    – TimerPreset CRUD
 *  - [DataTransferRepository]   – DB export / import merge
 */
class ChironRepository(
    private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val setEntryDao: SetEntryDao,
    private val timerPresetDao: TimerPresetDao,
    private val exercisePrDao: ExercisePrDao
) {
    // ─── Nested data classes (kept here so existing call-sites don't change) ──

    data class ExportedData(
        val uri: Uri,
        val locationLabel: String,
        val fileName: String
    )

    data class LastSessionPreview(
        val dateLabel: String,
        val sets: List<SetEntry>,
        val notes: String? = null
    )

    data class SupersetExercisePreview(
        val exerciseId: Long,
        val exerciseName: String,
        val iconName: String?,
        val sets: List<SetEntry>
    )

    data class LastSessionSupersetPreview(
        val dateLabel: String,
        val exercises: List<SupersetExercisePreview>,
        val notes: String?
    )

    // ─── Sub-repository construction ──────────────────────────────────────────

    private val prRepository = PrRepository(exercisePrDao, setEntryDao)

    private val exerciseRepository = ExerciseRepository(exerciseDao)

    private val sessionPreviewRepository = SessionPreviewRepository(
        exerciseDao, exerciseEntryDao, setEntryDao, workoutSessionDao
    )

    private val setEntryRepository = SetEntryRepository(
        setEntryDao = setEntryDao,
        exerciseEntryDao = exerciseEntryDao,
        workoutSessionDao = workoutSessionDao,
        onSyncGlobalPrBucket = { exerciseId, reps -> prRepository.syncGlobalPrBucket(exerciseId, reps) }
    )

    private val exerciseEntryRepository = ExerciseEntryRepository(
        exerciseEntryDao = exerciseEntryDao,
        setEntryDao = setEntryDao,
        onRebuildPrs = { exerciseId -> prRepository.rebuildPrsForExercise(exerciseId) }
    )

    private val workoutSessionRepository = WorkoutSessionRepository(
        workoutSessionDao = workoutSessionDao,
        exerciseEntryDao = exerciseEntryDao,
        onRebuildPrs = { exerciseId -> prRepository.rebuildPrsForExercise(exerciseId) }
    )

    private val imageRepository = ImageRepository(context)

    private val timerPresetRepository = TimerPresetRepository(timerPresetDao)

    private val dataTransferRepository = DataTransferRepository(
        context = context,
        exerciseDao = exerciseDao,
        workoutSessionDao = workoutSessionDao,
        exerciseEntryDao = exerciseEntryDao,
        setEntryDao = setEntryDao,
        timerPresetDao = timerPresetDao,
        exercisePrDao = exercisePrDao,
        onRebuildPrs = { exerciseId -> prRepository.rebuildPrsForExercise(exerciseId) }
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Exercise ops
    // ─────────────────────────────────────────────────────────────────────────

    val exercisesFlow: Flow<List<Exercise>> get() = exerciseRepository.exercisesFlow

    val archivedExercisesFlow: Flow<List<Exercise>> get() = exerciseRepository.archivedExercisesFlow

    suspend fun insertExercise(exercise: Exercise): Long =
        exerciseRepository.insertExercise(exercise)

    suspend fun updateExercise(exercise: Exercise) =
        exerciseRepository.updateExercise(exercise)

    suspend fun getExerciseById(id: Long): Exercise? =
        exerciseRepository.getExerciseById(id)

    suspend fun getExerciseByName(name: String): Exercise? =
        exerciseRepository.getExerciseByName(name)

    suspend fun archiveExercise(id: Long) =
        exerciseRepository.archiveExercise(id)

    suspend fun unarchiveExercise(id: Long) =
        exerciseRepository.unarchiveExercise(id)

    suspend fun searchExercises(
        query: String,
        archived: Boolean = false,
        limit: Int = 10
    ): List<Exercise> = exerciseRepository.searchExercises(query, archived, limit)

    suspend fun getAllExercises(): List<Exercise> =
        exerciseRepository.getAllExercises()

    // ─────────────────────────────────────────────────────────────────────────
    // Last session preview
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun getLastSessionPreview(
        exerciseId: Long,
        currentWorkoutId: Long
    ): LastSessionPreview? {
        val preview = sessionPreviewRepository.getLastSessionPreview(exerciseId, currentWorkoutId)
            ?: return null
        return LastSessionPreview(
            dateLabel = preview.dateLabel,
            sets = preview.sets,
            notes = preview.notes
        )
    }

    suspend fun getLastSessionSupersetPreview(
        currentEntryId: Long,
        allCurrentEntries: List<ExerciseEntry>,
        currentWorkoutId: Long
    ): LastSessionSupersetPreview? {
        val preview = sessionPreviewRepository.getLastSessionSupersetPreview(
            currentEntryId, allCurrentEntries, currentWorkoutId
        ) ?: return null
        return LastSessionSupersetPreview(
            dateLabel = preview.dateLabel,
            exercises = preview.exercises.map { e ->
                SupersetExercisePreview(
                    exerciseId = e.exerciseId,
                    exerciseName = e.exerciseName,
                    iconName = e.iconName,
                    sets = e.sets
                )
            },
            notes = preview.notes
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Workout session operations
    // ─────────────────────────────────────────────────────────────────────────

    val workoutsFlow: Flow<List<WorkoutSession>> get() = workoutSessionRepository.workoutsFlow

    val archivedWorkoutsFlow: Flow<List<WorkoutSession>> get() =
        workoutSessionRepository.archivedWorkoutsFlow

    val dayTagsFlow: Flow<List<String>> get() = workoutSessionRepository.dayTagsFlow

    suspend fun insertWorkout(session: WorkoutSession): Long =
        workoutSessionRepository.insertWorkout(session)

    suspend fun updateWorkout(session: WorkoutSession) =
        workoutSessionRepository.updateWorkout(session)

    suspend fun getWorkoutById(id: Long): WorkoutSession? =
        workoutSessionRepository.getWorkoutById(id)

    fun getWorkoutsByDayTag(dayTag: String): Flow<List<WorkoutSession>> =
        workoutSessionRepository.getWorkoutsByDayTag(dayTag)

    suspend fun archiveWorkout(id: Long) =
        workoutSessionRepository.archiveWorkout(id)

    suspend fun unarchiveWorkout(id: Long) =
        workoutSessionRepository.unarchiveWorkout(id)

    suspend fun permanentlyDeleteWorkout(id: Long) =
        workoutSessionRepository.permanentlyDeleteWorkout(id)

    suspend fun duplicateWorkout(sourceWorkoutId: Long): Long =
        workoutSessionRepository.duplicateWorkout(sourceWorkoutId) { srcId, newId, now ->
            exerciseEntryRepository.duplicateEntriesAndSets(srcId, newId, now)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Exercise entry operations
    // ─────────────────────────────────────────────────────────────────────────

    fun getEntriesForWorkout(workoutId: Long): Flow<List<ExerciseEntry>> =
        exerciseEntryRepository.getEntriesForWorkout(workoutId)

    suspend fun insertExerciseEntry(entry: ExerciseEntry): Long =
        exerciseEntryRepository.insertExerciseEntry(entry)

    suspend fun updateExerciseEntry(entry: ExerciseEntry) =
        exerciseEntryRepository.updateExerciseEntry(entry)

    suspend fun getNextSlotIndex(workoutId: Long): Int =
        exerciseEntryRepository.getNextSlotIndex(workoutId)

    suspend fun deleteExerciseEntry(workoutId: Long, entryId: Long) =
        exerciseEntryRepository.deleteExerciseEntry(workoutId, entryId)

    // ─────────────────────────────────────────────────────────────────────────
    // Set entry operations
    // ─────────────────────────────────────────────────────────────────────────

    fun getSetsForEntry(entryId: Long): Flow<List<SetEntry>> =
        setEntryRepository.getSetsForEntry(entryId)

    suspend fun insertSet(set: SetEntry): Long =
        setEntryRepository.insertSet(set)

    suspend fun insertSetAndEvaluateHistoricalPr(set: SetEntry): Long =
        setEntryRepository.insertSetAndEvaluateHistoricalPr(set)

    suspend fun updateSet(set: SetEntry) =
        setEntryRepository.updateSet(set)

    suspend fun updateSetAndEvaluateHistoricalPr(set: SetEntry) =
        setEntryRepository.updateSetAndEvaluateHistoricalPr(set)

    suspend fun getNextSetIndex(entryId: Long): Int =
        setEntryRepository.getNextSetIndex(entryId)

    suspend fun deleteSet(entryId: Long, setId: Long) =
        setEntryRepository.deleteSet(entryId, setId) { exerciseId, reps ->
            prRepository.syncGlobalPrBucket(exerciseId, reps)
        }

    suspend fun getLastSetForExercise(exerciseId: Long): SetEntry? =
        setEntryRepository.getLastSetForExercise(exerciseId)

    // ─────────────────────────────────────────────────────────────────────────
    // PR Detection
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun rebuildPrsForExercise(exerciseId: Long) =
        prRepository.rebuildPrsForExercise(exerciseId)

    suspend fun getAllPrsForExercise(exerciseId: Long): List<ExercisePr> =
        prRepository.getAllPrsForExercise(exerciseId)

    fun getPrsForExerciseFlow(exerciseId: Long): Flow<List<ExercisePr>> =
        prRepository.getPrsForExerciseFlow(exerciseId)

    suspend fun getExerciseIdsWithPrs(): List<Long> =
        prRepository.getExerciseIdsWithPrs()

    // ─────────────────────────────────────────────────────────────────────────
    // Image handling
    // ─────────────────────────────────────────────────────────────────────────

    fun copyImageToStorage(sourceUri: Uri, exerciseId: Long): String? =
        imageRepository.copyImageToStorage(sourceUri, exerciseId)

    fun deleteImage(imageUri: String): Boolean =
        imageRepository.deleteImage(imageUri)

    // ─────────────────────────────────────────────────────────────────────────
    // Timer Presets operations
    // ─────────────────────────────────────────────────────────────────────────

    val timerPresetsFlow: Flow<List<TimerPreset>> get() = timerPresetRepository.timerPresetsFlow

    suspend fun insertTimerPreset(preset: TimerPreset): Long =
        timerPresetRepository.insertTimerPreset(preset)

    suspend fun updateTimerPreset(preset: TimerPreset) =
        timerPresetRepository.updateTimerPreset(preset)

    suspend fun deleteTimerPreset(preset: TimerPreset) =
        timerPresetRepository.deleteTimerPreset(preset)

    suspend fun getTimerPresetById(id: Long): TimerPreset? =
        timerPresetRepository.getTimerPresetById(id)

    // ─────────────────────────────────────────────────────────────────────────
    // Data export / import
    // ─────────────────────────────────────────────────────────────────────────

    fun exportDataSnapshot(): Result<ExportedData> {
        return dataTransferRepository.exportDataSnapshot().map { data ->
            ExportedData(
                uri = data.uri,
                locationLabel = data.locationLabel,
                fileName = data.fileName
            )
        }
    }

    suspend fun importDataFromFile(fileUri: Uri): Result<String> =
        dataTransferRepository.importDataFromFile(fileUri)
}
