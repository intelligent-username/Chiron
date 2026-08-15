package com.chiron.core.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise",
    indices = [Index(value = ["name"])]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "icon_name")
    val iconName: String? = "default",

    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Int = 0,

    /** 1 = exercise tracks weight (loaded on the bar/dumbbell/machine). */
    @ColumnInfo(name = "is_weight_based", defaultValue = "1")
    val isWeightBased: Int = 1,

    /** 1 = exercise tracks reps. Mutually exclusive with isTimeBased. */
    @ColumnInfo(name = "is_rep_based", defaultValue = "1")
    val isRepBased: Int = 1,

    /** 1 = exercise tracks duration (seconds). Mutually exclusive with isRepBased. */
    @ColumnInfo(name = "is_time_based", defaultValue = "0")
    val isTimeBased: Int = 0,

    /** 1 = exercise tracks distance (meters stored, display-converted). */
    @ColumnInfo(name = "is_distance_based", defaultValue = "0")
    val isDistanceBased: Int = 0
)
