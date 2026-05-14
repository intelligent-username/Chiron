package com.chiron.app.data.exercise

import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.entities.Exercise
import com.chiron.app.util.Jaccard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Handles all CRUD and search operations for [Exercise] entities.
 *
 * Enforces tracking-config immutability: if [isWeightBased], [isRepBased],
 * [isTimeBased], or [isDistanceBased] changes and the exercise already has set
 * history, [updateExercise] throws [IllegalStateException] with the exact message
 * required by the spec.
 */
class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val setEntryDao: SetEntryDao
) {
    val exercisesFlow: Flow<List<Exercise>> = exerciseDao.getExercisesFlow()

    val archivedExercisesFlow: Flow<List<Exercise>> = exerciseDao.getAllExercisesFlow()
        .map { exercises -> exercises.filter { it.archived != 0 } }

    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insertExercise(exercise)

    /**
     * Updates an exercise.
     *
     * If the tracking configuration fields differ from the currently persisted exercise
     * AND history exists, this throws [IllegalStateException] with the canonical message.
     */
    suspend fun updateExercise(exercise: Exercise) {
        val current = exerciseDao.getById(exercise.id)
        if (current != null) {
            val configChanged = current.isWeightBased != exercise.isWeightBased ||
                current.isRepBased    != exercise.isRepBased    ||
                current.isTimeBased   != exercise.isTimeBased   ||
                current.isDistanceBased != exercise.isDistanceBased

            if (configChanged && setEntryDao.hasHistoryForExercise(exercise.id)) {
                throw IllegalStateException(
                    "This exercise already contains historical entries. Changing its tracking " +
                        "configuration would create incompatible historical data, so this change " +
                        "cannot be applied."
                )
            }
        }
        exerciseDao.updateExercise(exercise)
    }

    suspend fun getExerciseById(id: Long): Exercise? = exerciseDao.getById(id)

    suspend fun getExerciseByName(name: String): Exercise? = exerciseDao.getByName(name)

    suspend fun archiveExercise(id: Long) = exerciseDao.archive(id)

    suspend fun unarchiveExercise(id: Long) = exerciseDao.unarchive(id)

    suspend fun deleteExercisePermanently(id: Long) = exerciseDao.deleteExercise(id)

    suspend fun getAllExercises(): List<Exercise> = exerciseDao.getAllNonArchived()

    /**
     * Search exercises using Jaccard similarity on tokenized names.
     * Tie-break by recency (lower ID = older, so prefer higher ID).
     */
    suspend fun searchExercises(
        query: String,
        archived: Boolean = false,
        limit: Int = 10
    ): List<Exercise> {
        if (query.isBlank()) return emptyList()
        val allExercises = if (archived) exerciseDao.getAllArchived() else exerciseDao.getAllNonArchived()
        return Jaccard.rankBySimilarity(query, allExercises, { it.name }, limit)
    }
}
