package com.chiron.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chiron.app.data.entities.WorkoutSession
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkout(session: WorkoutSession): Long

    @Update
    suspend fun updateWorkout(session: WorkoutSession)

    @Query("SELECT * FROM workout_session WHERE archived = 0 ORDER BY date_utc DESC")
    fun getWorkoutsFlow(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_session ORDER BY date_utc DESC")
    fun getAllWorkoutsFlow(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSession?

    @Query("SELECT DISTINCT day_tag FROM workout_session WHERE archived = 0 ORDER BY day_tag ASC")
    fun getDistinctDayTagsFlow(): Flow<List<String>>

    @Query("SELECT * FROM workout_session WHERE day_tag = :dayTag AND archived = 0 ORDER BY date_utc DESC")
    fun getByDayTagFlow(dayTag: String): Flow<List<WorkoutSession>>

    @Query("UPDATE workout_session SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE workout_session SET archived = 0 WHERE id = :id")
    suspend fun unarchive(id: Long)
}
