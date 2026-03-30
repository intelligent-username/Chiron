package com.chiron.app.data.timer

import com.chiron.app.data.dao.TimerPresetDao
import com.chiron.app.data.entities.TimerPreset
import kotlinx.coroutines.flow.Flow

/**
 * Handles CRUD for [TimerPreset] entities.
 */
class TimerPresetRepository(private val timerPresetDao: TimerPresetDao) {

    val timerPresetsFlow: Flow<List<TimerPreset>> = timerPresetDao.getPresetsFlow()

    suspend fun insertTimerPreset(preset: TimerPreset): Long =
        timerPresetDao.insertPreset(preset)

    suspend fun updateTimerPreset(preset: TimerPreset) =
        timerPresetDao.updatePreset(preset)

    suspend fun deleteTimerPreset(preset: TimerPreset) =
        timerPresetDao.deletePreset(preset)

    suspend fun getTimerPresetById(id: Long): TimerPreset? =
        timerPresetDao.getPresetById(id)

    /**
     * Find a preset by its label and duration, used for deduplication during import.
     */
    suspend fun findPresetByLabelAndDuration(label: String, duration: Int): TimerPreset? =
        timerPresetDao.getPresetByLabelAndDuration(label, duration)
}
