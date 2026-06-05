package com.chiron.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chiron.app.data.entities.ExercisePr
import kotlinx.coroutines.flow.Flow

@Dao
interface ExercisePrDao {

    /** Upsert – replaces the row for (exercise_id, reps) when a new best is set. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pr: ExercisePr)

    /** Get the current global PR for a specific (exercise, reps) pair. */
    @Query("SELECT * FROM exercise_pr WHERE exercise_id = :exerciseId AND bucket = :reps")
    suspend fun getForExerciseAndReps(exerciseId: Long, reps: Int): ExercisePr?

    /** Get ALL current PRs for an exercise, ordered by rep count ascending. */
    @Query("SELECT * FROM exercise_pr WHERE exercise_id = :exerciseId ORDER BY bucket ASC")
    suspend fun getAllForExercise(exerciseId: Long): List<ExercisePr>

    /** Observe ALL current PRs for an exercise as a Flow (for reactive UI). */
    @Query("SELECT * FROM exercise_pr WHERE exercise_id = :exerciseId ORDER BY bucket ASC")
    fun getAllForExerciseFlow(exerciseId: Long): Flow<List<ExercisePr>>

    /** Get all exercises that have at least one PR recorded. */
    @Query("SELECT DISTINCT exercise_id FROM exercise_pr")
    suspend fun getExerciseIdsWithPrs(): List<Long>

    /** Remove PR row for one (exercise, reps) bucket. */
    @Query("DELETE FROM exercise_pr WHERE exercise_id = :exerciseId AND bucket = :reps")
    suspend fun deleteForExerciseAndReps(exerciseId: Long, reps: Int)

    /** Wipe all PR rows for an exercise — used before a full rebuild. */
    @Query("DELETE FROM exercise_pr WHERE exercise_id = :exerciseId")
    suspend fun clearAllForExercise(exerciseId: Long)
}
