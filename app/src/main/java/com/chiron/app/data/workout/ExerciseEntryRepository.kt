package com.chiron.app.data.workout

import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.SetEntry
import kotlinx.coroutines.flow.Flow

/**
 * Handles CRUD for [ExerciseEntry] records, including superset group-ID remapping
 * used during workout duplication.
 */
class ExerciseEntryRepository(
    private val exerciseEntryDao: ExerciseEntryDao,
    private val setEntryDao: SetEntryDao,
    private val onRebuildPrs: suspend (exerciseId: Long) -> Unit
) {
    fun getEntriesForWorkout(workoutId: Long): Flow<List<ExerciseEntry>> =
        exerciseEntryDao.getEntriesForWorkout(workoutId)

    suspend fun insertExerciseEntry(entry: ExerciseEntry): Long =
        exerciseEntryDao.insertEntry(entry)

    suspend fun updateExerciseEntry(entry: ExerciseEntry) =
        exerciseEntryDao.updateEntry(entry)

    suspend fun getNextSlotIndex(workoutId: Long): Int =
        (exerciseEntryDao.getMaxSlotIndex(workoutId) ?: 0) + 1

    suspend fun deleteExerciseEntry(workoutId: Long, entryId: Long) {
        val entry = exerciseEntryDao.getById(entryId)
        exerciseEntryDao.deleteAndReindex(workoutId, entryId)
        if (entry != null) onRebuildPrs(entry.exerciseId)
    }

    /**
     * Duplicates all entries (and their sets) from [sourceWorkoutId] into [newWorkoutId].
     * Remaps superset groupId references so they point to the new entry IDs.
     * Returns a map of old entry ID → new entry ID.
     */
    suspend fun duplicateEntriesAndSets(
        sourceWorkoutId: Long,
        newWorkoutId: Long,
        now: java.time.Instant
    ): Map<Long, Long> {
        val sourceEntries = exerciseEntryDao.getEntriesForWorkoutSync(sourceWorkoutId)
        val entryIdMap = mutableMapOf<Long, Long>()

        for (entry in sourceEntries) {
            val newEntry = entry.copy(
                id = 0L,
                workoutId = newWorkoutId,
                groupId = null // fixed below after all entries are inserted
            )
            val newEntryId = exerciseEntryDao.insertEntry(newEntry)
            entryIdMap[entry.id] = newEntryId

            val sourceSets = setEntryDao.getSetsForEntrySync(entry.id)
            for (set in sourceSets) {
                setEntryDao.insertSet(
                    set.copy(
                        id = 0L,
                        exerciseEntryId = newEntryId,
                        timestampUtc = now.toEpochMilli()
                    )
                )
            }
        }

        // Remap groupId references for superset entries
        for (entry in sourceEntries) {
            val oldGroupId = entry.groupId ?: continue
            val newEntryId = entryIdMap[entry.id] ?: continue
            val newGroupId = entryIdMap[oldGroupId] ?: continue
            val currentEntry = exerciseEntryDao.getById(newEntryId) ?: continue
            exerciseEntryDao.updateEntry(currentEntry.copy(groupId = newGroupId))
        }

        return entryIdMap
    }
}
