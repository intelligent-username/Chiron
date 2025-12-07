package com.chiron.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.chiron.app.data.entities.SetEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SetEntryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(set: SetEntry): Long

    @Update
    suspend fun updateSet(set: SetEntry)

    @Query("SELECT * FROM set_entry WHERE exercise_entry_id = :entryId ORDER BY set_index ASC")
    fun getSetsForEntry(entryId: Long): Flow<List<SetEntry>>

    @Query("SELECT * FROM set_entry WHERE exercise_entry_id = :entryId ORDER BY set_index ASC")
    suspend fun getSetsForEntrySync(entryId: Long): List<SetEntry>

    @Query("SELECT * FROM set_entry WHERE id = :id")
    suspend fun getById(id: Long): SetEntry?

    @Query("SELECT MAX(set_index) FROM set_entry WHERE exercise_entry_id = :entryId")
    suspend fun getMaxSetIndex(entryId: Long): Int?

    @Query("DELETE FROM set_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE set_entry SET set_index = :newIndex WHERE id = :setId")
    suspend fun updateSetIndex(setId: Long, newIndex: Int)

    /**
     * Get the most recent set for an exercise (across all workouts).
     * Used for autofill suggestions.
     */
    @Query("""
        SELECT s.* FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        WHERE e.exercise_id = :exerciseId
        ORDER BY s.timestamp_utc DESC
        LIMIT 1
    """)
    suspend fun getLastSetForExercise(exerciseId: Long): SetEntry?

    /**
     * Get all sets for an exercise (for PR computation).
     */
    @Query("""
        SELECT s.* FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        WHERE e.exercise_id = :exerciseId
          AND s.weight_lbs IS NOT NULL
          AND s.reps IS NOT NULL
        ORDER BY s.timestamp_utc DESC
    """)
    suspend fun getAllSetsForExercise(exerciseId: Long): List<SetEntry>

    @Transaction
    suspend fun deleteAndReindex(entryId: Long, setId: Long) {
        delete(setId)
        val remaining = getSetsForEntrySync(entryId)
        remaining.forEachIndexed { index, set ->
            updateSetIndex(set.id, index + 1)
        }
    }
}
