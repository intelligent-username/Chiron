package com.chiron.core.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One weigh-in. Weight stored canonical lbs, moment as epoch millis UTC. */
@Entity(
    tableName = "body_weight_entry",
    indices = [Index(value = ["timestamp_utc"])]
)
data class BodyWeightEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "timestamp_utc")
    val timestampUtc: Long,

    @ColumnInfo(name = "weight_lbs")
    val weightLbs: Double,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "source")
    val source: String? = "manual"
)
