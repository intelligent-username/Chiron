package com.chiron.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_session",
    indices = [
        Index(value = ["date_utc"]),
        Index(value = ["day_tag", "date_iso", "location_tag"], unique = true)
    ]
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "day_tag")
    val dayTag: String,

    @ColumnInfo(name = "date_iso")
    val dateIso: String,

    @ColumnInfo(name = "date_utc")
    val dateUtc: Long,

    @ColumnInfo(name = "location_tag")
    val locationTag: String,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Int = 0
)
