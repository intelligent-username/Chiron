package com.chiron.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chiron.app.data.entities.TimerPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerPresetDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPreset(preset: TimerPreset): Long

    @Update
    suspend fun updatePreset(preset: TimerPreset)

    @Delete
    suspend fun deletePreset(preset: TimerPreset)

    @Query("SELECT * FROM timer_presets WHERE archived = 0 ORDER BY duration_seconds ASC")
    fun getPresetsFlow(): Flow<List<TimerPreset>>

    @Query("SELECT * FROM timer_presets WHERE archived = 0 ORDER BY duration_seconds ASC")
    suspend fun getAllPresets(): List<TimerPreset>

    @Query("SELECT * FROM timer_presets WHERE id = :id")
    suspend fun getPresetById(id: Long): TimerPreset?

    @Query("UPDATE timer_presets SET archived = 1 WHERE id = :id")
    suspend fun archivePreset(id: Long)

    @Query("UPDATE timer_presets SET archived = 0 WHERE id = :id")
    suspend fun unarchivePreset(id: Long)
}
