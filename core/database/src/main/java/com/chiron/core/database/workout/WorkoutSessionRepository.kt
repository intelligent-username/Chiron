package com.chiron.core.database.workout

import com.chiron.core.database.dao.ExerciseEntryDao
import com.chiron.core.database.dao.WorkoutSessionDao
import com.chiron.core.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import java.time.format.DateTimeFormatter

/**
 * Handles all CRUD operations for [WorkoutSession] entities,
 * including duplication and cascaded deletion with PR rebuild delegation.
 */
class WorkoutSessionRepository(
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val onRebuildPrs: suspend (exerciseId: Long) -> Unit
) {
    val workoutsFlow: Flow<List<WorkoutSession>> = workoutSessionDao.getWorkoutsFlow()

    val archivedWorkoutsFlow: Flow<List<WorkoutSession>> = workoutSessionDao.getArchivedWorkoutsFlow()

    val dayTagsFlow: Flow<List<String>> = workoutSessionDao.getDistinctDayTagsFlow()

    suspend fun insertWorkout(session: WorkoutSession): Long =
        workoutSessionDao.insertWorkout(session)

    suspend fun updateWorkout(session: WorkoutSession) =
        workoutSessionDao.updateWorkout(session)

    suspend fun getWorkoutById(id: Long): WorkoutSession? = workoutSessionDao.getById(id)

    fun getWorkoutsByDayTag(dayTag: String): Flow<List<WorkoutSession>> =
        workoutSessionDao.getByDayTagFlow(dayTag)

    suspend fun archiveWorkout(id: Long) = workoutSessionDao.archive(id)

    suspend fun unarchiveWorkout(id: Long) = workoutSessionDao.unarchive(id)

    suspend fun permanentlyDeleteWorkout(id: Long) {
        val affectedExerciseIds = exerciseEntryDao.getEntriesForWorkoutSync(id)
            .map { it.exerciseId }
            .distinct()

        workoutSessionDao.deleteById(id)

        affectedExerciseIds.forEach { exerciseId ->
            onRebuildPrs(exerciseId)
        }
    }

    /**
     * Deep-copy a workout with today's date. All entries and sets are duplicated by value.
     * Returns the new workout's ID.
     */
    suspend fun duplicateWorkout(
        sourceWorkoutId: Long,
        onDuplicate: suspend (sourceWorkoutId: Long, newWorkoutId: Long, now: java.time.Instant) -> Unit
    ): Long {
        val source = workoutSessionDao.getById(sourceWorkoutId) ?: return -1L

        val now = java.time.Instant.now()
        val todayIso = java.time.LocalDate.now()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        val newSession = source.copy(
            id = 0L,
            dateIso = todayIso,
            dateUtc = now.toEpochMilli()
        )
        val newWorkoutId = workoutSessionDao.insertWorkout(newSession)

        onDuplicate(sourceWorkoutId, newWorkoutId, now)

        return newWorkoutId
    }
}
