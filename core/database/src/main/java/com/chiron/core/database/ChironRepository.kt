package com.chiron.core.database

import android.content.Context
import android.net.Uri
import com.chiron.core.database.dao.ExerciseDao
import com.chiron.core.database.dao.ExerciseEntryDao
import com.chiron.core.database.dao.ExercisePrDao
import com.chiron.core.database.dao.Exercise1rmEstimateDao
import com.chiron.core.database.dao.GoalDao
import com.chiron.core.database.dao.SetEntryDao
import com.chiron.core.database.dao.SetTimestampRow
import com.chiron.core.database.dao.SetWorkoutContext
import com.chiron.core.model.Exercise1rmEstimate
import com.chiron.core.database.dao.TimerPresetDao
import com.chiron.core.database.dao.WorkoutSessionDao
import com.chiron.core.model.Exercise
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.model.ExercisePr
import com.chiron.core.model.Goal
import com.chiron.core.model.GoalExercise
import com.chiron.core.model.SetEntry
import com.chiron.core.model.TimerPreset
import com.chiron.core.model.WorkoutSession
import com.chiron.core.database.exercise.ExerciseRepository
import com.chiron.core.database.exercise.ImageRepository
import com.chiron.core.database.pr.PrRepository
import com.chiron.core.database.timer.TimerPresetRepository
import com.chiron.core.database.transfer.DataTransferRepository
import com.chiron.core.database.workout.ExerciseEntryRepository
import com.chiron.core.database.workout.SessionPreviewRepository
import com.chiron.core.database.workout.SetEntryRepository
import com.chiron.core.database.workout.WorkoutSessionRepository
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
    private val exercisePrDao: ExercisePrDao,
    private val exercise1rmEstimateDao: Exercise1rmEstimateDao,
    private val goalDao: GoalDao,
    private val onImportLocations: (suspend (List<String>) -> Unit)? = null
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

    private val prRepository = PrRepository(exercisePrDao, setEntryDao, exercise1rmEstimateDao, exerciseDao)

    private val exerciseRepository = ExerciseRepository(exerciseDao, setEntryDao)

    private val sessionPreviewRepository = SessionPreviewRepository(
        exerciseDao, exerciseEntryDao, setEntryDao, workoutSessionDao
    )

    private val setEntryRepository = SetEntryRepository(
        setEntryDao = setEntryDao,
        exerciseEntryDao = exerciseEntryDao,
        workoutSessionDao = workoutSessionDao,
        exerciseDao = exerciseDao,
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
        goalDao = goalDao,
        onRebuildPrs = { exerciseId -> prRepository.rebuildPrsForExercise(exerciseId) },
        onImportLocations = onImportLocations
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Exercise ops
    // ─────────────────────────────────────────────────────────────────────────

    val exercisesFlow: Flow<List<Exercise>> get() = exerciseRepository.exercisesFlow

    val archivedExercisesFlow: Flow<List<Exercise>> get() = exerciseRepository.archivedExercisesFlow

    suspend fun insertExercise(exercise: Exercise): Long =
        exerciseRepository.insertExercise(exercise)

    /**
     * Updates an exercise. If the tracking configuration (isWeightBased, isRepBased,
     * isTimeBased, isDistanceBased) has changed AND historical sets exist, throws
     * [IllegalStateException] with the exact immutability error message.
     */
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

    suspend fun deleteExercisePermanently(id: Long) =
        exerciseRepository.deleteExercisePermanently(id)

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
        exerciseId: Long,
        currentWorkoutId: Long
    ): LastSessionSupersetPreview? {
        val preview = sessionPreviewRepository.getLastSessionSupersetPreview(
            exerciseId, currentWorkoutId
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

    /** Resolve the workout/entry/setIndex for a given set (used to deep-link from a PR row). */
    suspend fun getWorkoutContextForSet(setId: Long): SetWorkoutContext? =
        setEntryDao.getWorkoutContextForSet(setId)

    /** Resolve the most recent set's workout context for an exercise within a UTC date range. */
    suspend fun getWorkoutContextForExerciseOnDate(
        exerciseId: Long,
        startUtc: Long,
        endUtc: Long
    ): SetWorkoutContext? =
        setEntryDao.getWorkoutContextForExerciseOnDate(exerciseId, startUtc, endUtc)

    suspend fun getVolumeSummaryByDay(exerciseId: Long? = null) = setEntryRepository.getVolumeSummaryByDay(exerciseId)
    fun getVolumeSummaryByDayFlow(exerciseId: Long? = null) = setEntryRepository.getVolumeSummaryByDayFlow(exerciseId)

    // ─────────────────────────────────────────────────────────────────────────
    // PR Detection
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun rebuildPrsForExercise(exerciseId: Long) =
        prRepository.rebuildPrsForExercise(exerciseId)

    suspend fun getAllPrsForExercise(exerciseId: Long): List<ExercisePr> =
        prRepository.getAllPrsForExercise(exerciseId)

    fun getPrsForExerciseFlow(exerciseId: Long): Flow<List<ExercisePr>> =
        prRepository.getPrsForExerciseFlow(exerciseId)

    fun get1rmEstimateForExerciseFlow(exerciseId: Long): Flow<Exercise1rmEstimate?> =
        prRepository.get1rmEstimateForExerciseFlow(exerciseId)

    suspend fun getExerciseIdsWithPrs(): List<Long> =
        prRepository.getExerciseIdsWithPrs()

    suspend fun backfill1rmEstimates() =
        prRepository.backfill1rmEstimates()

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
    // Goals
    // ─────────────────────────────────────────────────────────────────────────

    val goalsFlow: Flow<List<Goal>> get() = goalDao.getActiveGoalsFlow()

    val allGoalsFlow: Flow<List<Goal>> get() = goalDao.getAllGoalsFlow()

    val goalJunctionsFlow: Flow<List<GoalExercise>> get() = goalDao.getJunctionsFlow()

    /** Emits whenever set_entry rows change (used to keep goal progress fresh). */
    val setEntryCountFlow: Flow<Int> get() = goalDao.getSetEntryCountFlow()

    suspend fun getJunctionsForGoal(goalId: Long): List<GoalExercise> =
        goalDao.getJunctionsForGoal(goalId)

    suspend fun getGoalById(id: Long): Goal? =
        goalDao.getGoalById(id)

    suspend fun getGoalByName(name: String): Goal? =
        goalDao.getGoalByName(name)

    suspend fun insertGoal(goal: Goal): Long =
        goalDao.insertGoal(goal)

    suspend fun updateGoal(goal: Goal) =
        goalDao.updateGoal(goal)

    suspend fun archiveGoal(id: Long) =
        goalDao.archiveGoal(id)

    suspend fun deleteGoal(id: Long) =
        goalDao.deleteGoal(id)

    suspend fun getSetTimestampsForExercises(exerciseIds: List<Long>): List<SetTimestampRow> =
        if (exerciseIds.isEmpty()) emptyList() else goalDao.getSetTimestampsForExercises(exerciseIds)

    /** Inserts or updates a goal and replaces its junction rows in one transaction. */
    suspend fun saveGoalWithExercises(goal: Goal, exerciseIds: List<Long>) =
        goalDao.saveGoalWithExercises(goal, exerciseIds)

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
