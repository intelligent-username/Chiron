package com.chiron.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.chiron.core.model.ExerciseEntry
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

    @Query("SELECT * FROM exercise_entry WHERE workout_id = :workoutId AND exercise_id = :exerciseId AND slot_index = :slotIndex LIMIT 1")
    suspend fun findMatchingEntry(workoutId: Long, exerciseId: Long, slotIndex: Int): ExerciseEntry?

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

    /**
     * Find the most recent ExerciseEntry for a given exercise,
     * excluding the current workout, looking only at workouts whose date
     * is at or before [currentWorkoutDateUtc]. This ensures the preview
     * shows the "previous performance relative to the viewed workout",
     * not relative to today.
     */
    @Query("""
        SELECT ee.* FROM exercise_entry ee
        INNER JOIN workout_session ws ON ee.workout_id = ws.id
        WHERE ee.exercise_id = :exerciseId
          AND ee.workout_id != :currentWorkoutId
          AND ee.archived = 0
          AND ws.archived = 0
          AND ws.date_utc <= :currentWorkoutDateUtc
        ORDER BY ws.date_utc DESC
        LIMIT 1
    """)
    suspend fun getMostRecentEntryForExercise(exerciseId: Long, currentWorkoutId: Long, currentWorkoutDateUtc: Long): ExerciseEntry?

    /** Fetch all entries in a specific workout that share the same superset group_id. */
    @Query("""
        SELECT * FROM exercise_entry
        WHERE workout_id = :workoutId
          AND group_id = :groupId
          AND archived = 0
        ORDER BY slot_index ASC
    """)
    suspend fun getEntriesByGroupInWorkout(workoutId: Long, groupId: Long): List<ExerciseEntry>

    @Transaction
    suspend fun deleteAndReindex(workoutId: Long, entryId: Long) {
        delete(entryId)
        val remaining = getEntriesForWorkoutSync(workoutId)
        remaining.forEachIndexed { index, entry ->
            updateSlotIndex(entry.id, index + 1)
        }
    }
}
