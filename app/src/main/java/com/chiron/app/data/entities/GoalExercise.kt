package com.chiron.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "goal_exercise",
    primaryKeys = ["goal_id", "exercise_id"],
    foreignKeys = [
        ForeignKey(entity = Goal::class, parentColumns = ["id"], childColumns = ["goal_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exercise_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["exercise_id"])]
)
data class GoalExercise(
    @ColumnInfo(name = "goal_id") val goalId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long
)
