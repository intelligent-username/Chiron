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

    @Query("SELECT exercise_id FROM exercise_entry WHERE id = :entryId LIMIT 1")
    suspend fun getExerciseIdForEntry(entryId: Long): Long?

    @Query("SELECT workout_id FROM exercise_entry WHERE id = :entryId LIMIT 1")
    suspend fun getWorkoutIdForEntry(entryId: Long): Long?

    @Query("SELECT MAX(set_index) FROM set_entry WHERE exercise_entry_id = :entryId")
    suspend fun getMaxSetIndex(entryId: Long): Int?

    @Query("""
        SELECT MAX(s.weight_lbs)
        FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        INNER JOIN workout_session w ON e.workout_id = w.id
        WHERE e.exercise_id = :exerciseId
          AND s.reps = :reps
          AND s.weight_lbs IS NOT NULL
          AND s.is_failed = 0
          AND w.date_utc <= :upToWorkoutDateUtc
          AND s.id != :excludeSetId
    """)
    suspend fun getMaxWeightForExerciseRepsUpToWorkoutDate(
        exerciseId: Long,
        reps: Int,
        upToWorkoutDateUtc: Long,
        excludeSetId: Long
    ): Double?

    @Query("""
        SELECT s.*
        FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        WHERE e.exercise_id = :exerciseId
          AND s.reps = :reps
          AND s.weight_lbs IS NOT NULL
          AND s.is_failed = 0
        ORDER BY s.weight_lbs DESC, s.timestamp_utc ASC, s.id ASC
        LIMIT 1
    """)
    suspend fun getBestSetForExerciseAndReps(exerciseId: Long, reps: Int): SetEntry?

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

    /**
     * Reset is_pr on all sets for a given exercise.
     * Call this before a full PR rebuild so stale flags are cleared.
     */
    @Query("""
        UPDATE set_entry SET is_pr = 0
        WHERE exercise_entry_id IN (
            SELECT id FROM exercise_entry WHERE exercise_id = :exerciseId
        )
    """)
    suspend fun clearPrFlagsForExercise(exerciseId: Long)

    /**
     * Returns total volume (weight * reps) grouped by workout day.
     * Only counts non-failed sets that have both weight and reps filled in.
     * Result ordered by date ascending.
     */
    @Query("""
        SELECT w.date_utc AS dateUtc, SUM(s.weight_lbs * s.reps) AS volumeLbs
        FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        INNER JOIN workout_session w ON e.workout_id = w.id
        WHERE s.weight_lbs IS NOT NULL
          AND s.reps IS NOT NULL
          AND s.is_failed = 0
          AND w.archived = 0
        GROUP BY w.id
        ORDER BY w.date_utc ASC
    """)
    suspend fun getVolumeSummaryByDay(): List<DailyVolume>
}

data class DailyVolume(
    val dateUtc: Long,
    val volumeLbs: Double
)
