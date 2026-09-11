package com.chiron.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chiron.core.model.BodyWeightEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyWeightDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: BodyWeightEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BodyWeightEntry): Long

    @Update
    suspend fun update(entry: BodyWeightEntry)

    @Query("DELETE FROM body_weight_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM body_weight_entry ORDER BY timestamp_utc ASC, id ASC")
    fun getAllFlow(): Flow<List<BodyWeightEntry>>

    @Query("SELECT * FROM body_weight_entry ORDER BY timestamp_utc ASC, id ASC")
    suspend fun getAllSync(): List<BodyWeightEntry>

    @Query("SELECT * FROM body_weight_entry WHERE timestamp_utc <= :timestampUtc ORDER BY timestamp_utc DESC, id DESC LIMIT 1")
    suspend fun getLatestAtOrBefore(timestampUtc: Long): BodyWeightEntry?
}
