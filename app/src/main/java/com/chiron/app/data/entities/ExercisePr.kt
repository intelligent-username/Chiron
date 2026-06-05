package com.chiron.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Ignore

/**
 * Stores the current global PR for a given (exercise, bucket) pair.
 *
 * The meaning of [bucket] and [record] depends on the exercise's PR category:
 *
 *  | Category         | bucket            | record (best)        | better |
 *  |------------------|-------------------|----------------------|--------|
 *  | WEIGHT_REPS      | rep count         | weight (lbs)         | higher |
 *  | TIME_WEIGHT      | weight (lbs)      | duration (seconds)   | higher |
 *  | DISTANCE_WEIGHT  | weight (lbs)      | distance (meters)    | higher |
 *  | DISTANCE_TIME    | distance (meters) | duration (seconds)   | lower  |
 *
 * One row per (exercise_id, bucket). Historical per-set PRs are tracked by
 * SetEntry.isPr (weight+reps only).
 *
 * For WEIGHT_REPS exercises use the convenience accessors:
 *   [repsInt] = bucket.toInt()
 *   [weightLbs] = record
 */
@Entity(
    tableName = "exercise_pr",
    primaryKeys = ["exercise_id", "bucket"],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SetEntry::class,
            parentColumns = ["id"],
            childColumns = ["set_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["exercise_id"]),
        Index(value = ["set_id"])
    ]
)
data class ExercisePr(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,

    /** Partition key for this PR (reps / weight / distance — see class docs). */
    @ColumnInfo(name = "bucket")
    val bucket: Double,

    /** Best value recorded for this bucket (weight / duration / distance — see class docs). */
    @ColumnInfo(name = "record")
    val record: Double,

    /** The set_entry.id that currently holds this PR. */
    @ColumnInfo(name = "set_id")
    val setId: Long,

    /** UTC timestamp of when this PR was set. */
    @ColumnInfo(name = "timestamp_utc")
    val timestampUtc: Long
) {
    /**
     * Convenience constructor for WEIGHT_REPS PRs — maps reps→bucket, weightLbs→record.
     * Annotated @Ignore so Room only uses the primary constructor for reading.
     */
    @Ignore
    constructor(
        exerciseId: Long,
        reps: Int,
        weightLbs: Double,
        setId: Long,
        timestampUtc: Long
    ) : this(
        exerciseId = exerciseId,
        bucket = reps.toDouble(),
        record = weightLbs,
        setId = setId,
        timestampUtc = timestampUtc
    )

    // Non-annotated helpers — plain Kotlin properties, not visible to Room at all
    // because they have no backing fields or @ColumnInfo annotations.
    val repsInt: Int get() = bucket.toInt()
    val weightLbs: Double get() = record
}
