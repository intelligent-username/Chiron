package com.chiron.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntry::class,
            parentColumns = ["id"],
            childColumns = ["exercise_entry_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["exercise_entry_id", "set_index"], unique = true),
        Index(value = ["exercise_entry_id"])
    ]
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "exercise_entry_id")
    val exerciseEntryId: Long,

    @ColumnInfo(name = "set_index")
    val setIndex: Int,

    @ColumnInfo(name = "weight_lbs")
    val weightLbs: Double? = null,

    @ColumnInfo(name = "reps")
    val reps: Int? = null,

    @ColumnInfo(name = "is_failed", defaultValue = "0")
    val isFailed: Int = 0,

    @ColumnInfo(name = "tempo")
    val tempo: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "timestamp_utc")
    val timestampUtc: Long
)
