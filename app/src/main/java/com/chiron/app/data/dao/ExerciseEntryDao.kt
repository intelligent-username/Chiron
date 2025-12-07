package com.chiron.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.chiron.app.data.entities.ExerciseEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseEntryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: ExerciseEntry): Long

    @Update
    suspend fun updateEntry(entry: ExerciseEntry)

    @Query("SELECT * FROM exercise_entry WHERE workout_id = :workoutId AND archived = 0 ORDER BY slot_index ASC")
    fun getEntriesForWorkout(workoutId: Long): Flow<List<ExerciseEntry>>

    @Query("SELECT * FROM exercise_entry WHERE workout_id = :workoutId ORDER BY slot_index ASC")
    suspend fun getEntriesForWorkoutSync(workoutId: Long): List<ExerciseEntry>

    @Query("SELECT * FROM exercise_entry WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntry?

    @Query("SELECT MAX(slot_index) FROM exercise_entry WHERE workout_id = :workoutId")
    suspend fun getMaxSlotIndex(workoutId: Long): Int?

    @Query("UPDATE exercise_entry SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM exercise_entry WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Reindex slot_index for all entries in a workout after deletion.
     * Call within a transaction.
     */
    @Query("UPDATE exercise_entry SET slot_index = :newIndex WHERE id = :entryId")
    suspend fun updateSlotIndex(entryId: Long, newIndex: Int)

    @Transaction
    suspend fun deleteAndReindex(workoutId: Long, entryId: Long) {
        delete(entryId)
        val remaining = getEntriesForWorkoutSync(workoutId)
        remaining.forEachIndexed { index, entry ->
            updateSlotIndex(entry.id, index + 1)
        }
    }
}
