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

    /**
     * Resolve the workout, exercise-entry, and set index that a given set belongs to.
     * Used to deep-link from a PR row to the exact workout/set where the PR was set.
     */
    @Query("""
        SELECT e.workout_id AS workoutId, e.id AS entryId, s.set_index AS setIndex
        FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        WHERE s.id = :setId
        LIMIT 1
    """)
    suspend fun getWorkoutContextForSet(setId: Long): SetWorkoutContext?

    /**
     * Resolve the workout/entry/setIndex of the most recent set for an exercise
     * performed within a given UTC date range. Used to deep-link from a volume-graph
     * tap to the last performance of that exercise on that day.
     */
    @Query("""
        SELECT e.workout_id AS workoutId, e.id AS entryId, s.set_index AS setIndex
        FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        INNER JOIN workout_session w ON e.workout_id = w.id
        WHERE e.exercise_id = :exerciseId
          AND w.date_utc >= :startUtc
          AND w.date_utc < :endUtc
        ORDER BY s.timestamp_utc DESC, s.id DESC
        LIMIT 1
    """)
    suspend fun getWorkoutContextForExerciseOnDate(
        exerciseId: Long,
        startUtc: Long,
        endUtc: Long
    ): SetWorkoutContext?

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

    /**
     * Get every set for an exercise with no metric filtering (used by category-aware
     * PR rebuilds for time/distance exercises that don't populate reps/weight).
     */
    @Query("""
        SELECT s.* FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        WHERE e.exercise_id = :exerciseId
        ORDER BY s.timestamp_utc DESC
    """)
    suspend fun getAllSetsForExerciseAny(exerciseId: Long): List<SetEntry>

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
     * Returns total volume grouped by workout day.
     *
     * Volume per set = weight × rep-equivalent, where rep-equivalent is:
     *   - distance-based exercises: distance_meters / 5  (5 m ≈ 1 rep)
     *   - time-based exercises:     duration_seconds / 3  (3 s ≈ 1 rep)
     *   - otherwise:                reps
     *
     * Only weighted, non-failed sets contribute — sets without a weight entry
     * produce 0 volume (bodyweight volume is handled separately, later).
     *
     * SUM() is wrapped in COALESCE so an all-NULL group can never map a SQL NULL
     * onto the non-null [DailyVolume.volumeLbs] field (which previously crashed Room).
     */
    @Query("""
        SELECT w.date_utc AS dateUtc,
               COALESCE(SUM(s.weight_lbs * (
                   CASE
                       WHEN ex.is_distance_based = 1 AND ex.is_rep_based = 1 AND s.distance_meters IS NOT NULL AND s.reps IS NOT NULL THEN s.reps * (s.distance_meters * 2.0)
                       WHEN ex.is_distance_based = 1 AND s.distance_meters IS NOT NULL THEN s.distance_meters / 5.0
                       WHEN ex.is_time_based = 1 AND s.duration_seconds IS NOT NULL THEN s.duration_seconds / 3.0
                       WHEN s.reps IS NOT NULL THEN s.reps
                       ELSE NULL
                   END
               )), 0) AS volumeLbs
        FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        INNER JOIN exercise ex ON e.exercise_id = ex.id
        INNER JOIN workout_session w ON e.workout_id = w.id
        WHERE s.weight_lbs IS NOT NULL
          AND s.is_failed = 0
          AND w.archived = 0
        GROUP BY w.id
        ORDER BY w.date_utc ASC
    """)
    suspend fun getVolumeSummaryByDay(): List<DailyVolume>

    @Query("""
        SELECT w.date_utc AS dateUtc,
               COALESCE(SUM(s.weight_lbs * (
                   CASE
                       WHEN ex.is_distance_based = 1 AND ex.is_rep_based = 1 AND s.distance_meters IS NOT NULL AND s.reps IS NOT NULL THEN s.reps * (s.distance_meters * 2.0)
                       WHEN ex.is_distance_based = 1 AND s.distance_meters IS NOT NULL THEN s.distance_meters / 5.0
                       WHEN ex.is_time_based = 1 AND s.duration_seconds IS NOT NULL THEN s.duration_seconds / 3.0
                       WHEN s.reps IS NOT NULL THEN s.reps
                       ELSE NULL
                   END
               )), 0) AS volumeLbs
        FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        INNER JOIN exercise ex ON e.exercise_id = ex.id
        INNER JOIN workout_session w ON e.workout_id = w.id
        WHERE s.weight_lbs IS NOT NULL
          AND s.is_failed = 0
          AND w.archived = 0
          AND e.exercise_id = :exerciseId
        GROUP BY w.id
        ORDER BY w.date_utc ASC
    """)
    suspend fun getVolumeSummaryByDayForExercise(exerciseId: Long): List<DailyVolume>

    /**
     * Returns true if any set_entry row exists for the given exercise (via exercise_entry join).
     * Used to enforce tracking-config immutability.
     */
    @Query("""
        SELECT COUNT(*) > 0
        FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        WHERE e.exercise_id = :exerciseId
        LIMIT 1
    """)
    suspend fun hasHistoryForExercise(exerciseId: Long): Boolean
}

data class DailyVolume(
    val dateUtc: Long,
    val volumeLbs: Double
)

/** Resolved location of a set within a workout (for deep-linking from a PR row). */
data class SetWorkoutContext(
    val workoutId: Long,
    val entryId: Long,
    val setIndex: Int
)
