package com.chiron.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chiron.app.data.entities.Exercise1rmEstimate
import kotlinx.coroutines.flow.Flow

@Dao
interface Exercise1rmEstimateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(estimate: Exercise1rmEstimate)

    @Query("SELECT * FROM exercise_1rm_estimate WHERE exercise_id = :exerciseId")
    suspend fun getForExercise(exerciseId: Long): Exercise1rmEstimate?

    @Query("SELECT * FROM exercise_1rm_estimate WHERE exercise_id = :exerciseId")
    fun getForExerciseFlow(exerciseId: Long): Flow<Exercise1rmEstimate?>

    @Query("DELETE FROM exercise_1rm_estimate WHERE exercise_id = :exerciseId")
    suspend fun deleteForExercise(exerciseId: Long)
}
