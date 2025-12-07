package com.chiron.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sequence type for exercise entries (supersets, dropsets).
 */
enum class SequenceType {
    NONE,
    SUPERSET_START,
    SUPERSET_MIDDLE,
    SUPERSET_END,
    DROPSET
}

@Entity(
    tableName = "exercise_entry",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workout_id", "slot_index"], unique = true),
        Index(value = ["exercise_id"])
    ]
)
data class ExerciseEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "workout_id")
    val workoutId: Long,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,

    @ColumnInfo(name = "slot_index")
    val slotIndex: Int,

    @ColumnInfo(name = "group_id")
    val groupId: Long? = null,

    @ColumnInfo(name = "sequence_type")
    val sequenceType: String = SequenceType.NONE.name,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Int = 0
)
