package com.chiron.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Stores the current global PR (best weight) for a given (exercise, rep count) pair.
 * One row per (exercise_id, reps). Updated whenever a new PR is set.
 * Historical per-set PRs are tracked by SetEntry.isPr.
 */
@Entity(
    tableName = "exercise_pr",
    primaryKeys = ["exercise_id", "reps"],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["exercise_id"])]
)
data class ExercisePr(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,

    @ColumnInfo(name = "reps")
    val reps: Int,

    /** Best weight recorded for this (exercise, reps) pair, in lbs. */
    @ColumnInfo(name = "weight_lbs")
    val weightLbs: Double,

    /** The set_entry.id that currently holds this PR. */
    @ColumnInfo(name = "set_id")
    val setId: Long,

    /** UTC timestamp of when this PR was set. */
    @ColumnInfo(name = "timestamp_utc")
    val timestampUtc: Long
)
