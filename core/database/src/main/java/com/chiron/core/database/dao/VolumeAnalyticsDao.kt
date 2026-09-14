package com.chiron.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object dedicated strictly to volume analytics and aggregations.
 */
@Dao
interface VolumeAnalyticsDao {

    /**
     * Returns total volume grouped by workout day for weighted, non-bodyweight exercises.
     *
     * Volume per set = weight × rep-equivalent, where rep-equivalent is:
     *   - distance-based exercises: distance_meters / 5  (5 m ≈ 1 rep)
     *   - time-based exercises:     duration_seconds / 3  (3 s ≈ 1 rep)
     *   - otherwise:                reps
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
          AND ex.is_bodyweight = 0
        GROUP BY w.id
        ORDER BY w.date_utc ASC
    """)
    fun getVolumeSummaryByDayFlow(): Flow<List<DailyVolume>>

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
          AND ex.is_bodyweight = 0
          AND e.exercise_id = :exerciseId
        GROUP BY w.id
        ORDER BY w.date_utc ASC
    """)
    fun getVolumeSummaryByDayForExerciseFlow(exerciseId: Long): Flow<List<DailyVolume>>

    /**
     * Unaggregated bodyweight component rows for the Kotlin dynamic-volume pipeline.
     */
    @Query("""
        SELECT e.workout_id AS workoutId,
               w.date_utc AS dateUtc,
               ex.percent_bodyweight AS percentBodyweight,
               s.weight_lbs AS addedWeightLbs,
               s.reps AS reps,
               s.duration_seconds AS durationSeconds,
               s.distance_meters AS distanceMeters
        FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        INNER JOIN exercise ex ON e.exercise_id = ex.id
        INNER JOIN workout_session w ON e.workout_id = w.id
        WHERE ex.is_bodyweight = 1
          AND s.is_failed = 0
          AND w.archived = 0
        ORDER BY w.date_utc ASC
    """)
    fun getBodyweightSetRowsFlow(): Flow<List<BodyweightSetRow>>

    @Query("""
        SELECT e.workout_id AS workoutId,
               w.date_utc AS dateUtc,
               ex.percent_bodyweight AS percentBodyweight,
               s.weight_lbs AS addedWeightLbs,
               s.reps AS reps,
               s.duration_seconds AS durationSeconds,
               s.distance_meters AS distanceMeters
        FROM set_entry s
        INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id
        INNER JOIN exercise ex ON e.exercise_id = ex.id
        INNER JOIN workout_session w ON e.workout_id = w.id
        WHERE ex.is_bodyweight = 1
          AND s.is_failed = 0
          AND w.archived = 0
          AND e.exercise_id = :exerciseId
        ORDER BY w.date_utc ASC
    """)
    fun getBodyweightSetRowsForExerciseFlow(exerciseId: Long): Flow<List<BodyweightSetRow>>
}

data class DailyVolume(
    val dateUtc: Long,
    val volumeLbs: Double
)

/** Unaggregated bodyweight set for Kotlin volume aggregation. */
data class BodyweightSetRow(
    val workoutId: Long,
    val dateUtc: Long,
    val percentBodyweight: Double,
    val addedWeightLbs: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?
)
