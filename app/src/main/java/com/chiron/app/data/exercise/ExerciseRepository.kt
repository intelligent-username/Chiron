package com.chiron.app.data.exercise

import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.entities.Exercise
import com.chiron.app.util.Jaccard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Handles all CRUD and search operations for [Exercise] entities.
 */
class ExerciseRepository(
    private val exerciseDao: ExerciseDao
) {
    val exercisesFlow: Flow<List<Exercise>> = exerciseDao.getExercisesFlow()

    val archivedExercisesFlow: Flow<List<Exercise>> = exerciseDao.getAllExercisesFlow()
        .map { exercises -> exercises.filter { it.archived != 0 } }

    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insertExercise(exercise)

    suspend fun updateExercise(exercise: Exercise) = exerciseDao.updateExercise(exercise)

    suspend fun getExerciseById(id: Long): Exercise? = exerciseDao.getById(id)

    suspend fun getExerciseByName(name: String): Exercise? = exerciseDao.getByName(name)

    suspend fun archiveExercise(id: Long) = exerciseDao.archive(id)

    suspend fun unarchiveExercise(id: Long) = exerciseDao.unarchive(id)

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
