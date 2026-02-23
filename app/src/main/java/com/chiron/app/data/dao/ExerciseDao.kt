package com.chiron.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chiron.app.data.entities.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercise(exercise: Exercise): Long

    @Update
    suspend fun updateExercise(exercise: Exercise)

    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name ASC")
    fun getExercisesFlow(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise ORDER BY name ASC")
    fun getAllExercisesFlow(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name ASC")
    suspend fun getAllNonArchived(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE archived = 1 ORDER BY name ASC")
    suspend fun getAllArchived(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM exercise WHERE name = :name AND archived = 0 LIMIT 1")
    suspend fun getByName(name: String): Exercise?

    @Query("UPDATE exercise SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE exercise SET archived = 0 WHERE id = :id")
    suspend fun unarchive(id: Long)
}
