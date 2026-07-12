package com.chiron.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chiron.app.data.ChironRepository
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.SequenceType
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.data.entities.WorkoutSession
import com.chiron.app.prefs.UserSettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class HistoryUiState(
    val workouts: List<WorkoutSession> = emptyList(),
    val archivedWorkouts: List<WorkoutSession> = emptyList(),
    val dayTags: List<String> = emptyList(),
    val locationTags: List<String> = emptyList(),
    val selectedDayTag: String? = null,
    val selectedLocationTag: String? = null,
    val showArchivedWorkouts: Boolean = false,
    val isEditorOpen: Boolean = false,
    val editingWorkoutId: Long? = null,
    val displayInKg: Boolean = false,
    val distanceUnit: com.chiron.app.prefs.DistanceUnit = com.chiron.app.prefs.DistanceUnit.METERS
)

sealed class DeletedItem {
    data class Set(val entryId: Long, val set: SetEntry) : DeletedItem()
    data class ExerciseEntries(val workoutId: Long, val entries: List<Pair<ExerciseEntry, List<SetEntry>>>) : DeletedItem()
    data class WorkoutSessionWithEntries(val workout: WorkoutSession, val entries: List<ExerciseEntry>, val sets: Map<Long, List<SetEntry>>) : DeletedItem()
}

class HistoryViewModel(
    private val repository: ChironRepository,
    private val settingsRepository: UserSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    private var debounceJob: Job? = null
    private val debounceDelayMs = 750L

    private val _lastDeleted = MutableStateFlow<DeletedItem?>(null)
    val lastDeleted: StateFlow<DeletedItem?> = _lastDeleted.asStateFlow()

    fun clearLastDeleted() {
        _lastDeleted.value = null
    }

    fun undoLastDeleted() {
        val item = _lastDeleted.value ?: return
        viewModelScope.launch {
            when (item) {
                is DeletedItem.Set -> {
                    repository.insertSetAndEvaluateHistoricalPr(item.set.copy(id = 0L))
                }
                is DeletedItem.ExerciseEntries -> {
                    val oldToNewEntryId = mutableMapOf<Long, Long>()
                    for ((entry, sets) in item.entries) {
                        val newEntryId = repository.insertExerciseEntry(
                            entry.copy(id = 0L, groupId = null)
                        )
                        oldToNewEntryId[entry.id] = newEntryId
                        for (set in sets) {
                            repository.insertSet(set.copy(id = 0L, exerciseEntryId = newEntryId))
                        }
                    }
                    // Restore superset group mappings if any
                    for ((entry, _) in item.entries) {
                        val oldGroupId = entry.groupId ?: continue
                        val newEntryId = oldToNewEntryId[entry.id] ?: continue
                        val newGroupId = oldToNewEntryId[oldGroupId] ?: continue
                        // Directly restore groupId without relying on async fetch
                        repository.updateExerciseEntry(entry.copy(id = newEntryId, groupId = newGroupId))
                    }
                }
                is DeletedItem.WorkoutSessionWithEntries -> {
                    val newWorkoutId = repository.insertWorkout(item.workout.copy(id = 0L))
                    val oldToNewEntryId = mutableMapOf<Long, Long>()
                    for (entry in item.entries) {
                        val newEntryId = repository.insertExerciseEntry(
                            entry.copy(id = 0L, workoutId = newWorkoutId, groupId = null)
                        )
                        oldToNewEntryId[entry.id] = newEntryId
                        val sets = item.sets[entry.id] ?: emptyList()
                        for (set in sets) {
                            repository.insertSet(set.copy(id = 0L, exerciseEntryId = newEntryId))
                        }
                    }
                    // Restore superset group mappings if any
                    for (entry in item.entries) {
                        val oldGroupId = entry.groupId ?: continue
                        val newEntryId = oldToNewEntryId[entry.id] ?: continue
                        val newGroupId = oldToNewEntryId[oldGroupId] ?: continue
                        // Directly restore groupId without relying on async fetch
                        repository.updateExerciseEntry(entry.copy(id = newEntryId, workoutId = newWorkoutId, groupId = newGroupId))
                    }
                }
            }
            _lastDeleted.value = null
        }
    }

    init {
        viewModelScope.launch {
            repository.workoutsFlow.collect { w ->
                val locations = w.map { it.locationTag }.filter { it.isNotBlank() }.distinct().sorted()
                _uiState.update { it.copy(workouts = w, locationTags = locations) }
            }
        }
        viewModelScope.launch { repository.dayTagsFlow.collect { tags ->
            _uiState.update { it.copy(dayTags = tags.map { t -> t.ifBlank { "Untitled Workout" } }.distinct().sorted()) }
        }}
        viewModelScope.launch { repository.archivedWorkoutsFlow.collect { w -> _uiState.update { it.copy(archivedWorkouts = w) } } }
        viewModelScope.launch { settingsRepository.displayInKgFlow.collect { v -> _uiState.update { it.copy(displayInKg = v) } } }
        viewModelScope.launch { settingsRepository.distanceUnitFlow.collect { v -> _uiState.update { it.copy(distanceUnit = v) } } }
        viewModelScope.launch {
            settingsRepository.editingWorkoutIdFlow.collect { id ->
                _uiState.update { it.copy(isEditorOpen = id != null, editingWorkoutId = id) }
            }
        }
    }

    // ── Workout operations ────────────────────────────────────────────────────
    fun filterByDayTag(dayTag: String?) = _uiState.update { it.copy(selectedDayTag = dayTag) }
    fun filterByLocationTag(locationTag: String?) = _uiState.update { it.copy(selectedLocationTag = locationTag) }
    fun setShowArchivedWorkouts(show: Boolean) = _uiState.update { it.copy(showArchivedWorkouts = show, selectedDayTag = null, selectedLocationTag = null) }
    fun openEditor(workoutId: Long?) {
        viewModelScope.launch {
            settingsRepository.setEditingWorkoutId(workoutId)
        }
    }
    fun closeEditor() {
        forceSync()
        viewModelScope.launch {
            settingsRepository.setEditingWorkoutId(null)
        }
    }

    fun createNewWorkout(dayTag: String, locationTag: String) {
        viewModelScope.launch {
            val id = repository.insertWorkout(WorkoutSession(dayTag = dayTag, dateIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), dateUtc = Instant.now().toEpochMilli(), locationTag = locationTag))
            openEditor(id)
        }
    }

    private var pendingWorkout: WorkoutSession? = null

    fun updateWorkout(workout: WorkoutSession) {
        pendingWorkout = workout
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(debounceDelayMs)
            saveWorkoutImmediate(workout)
        }
    }

    fun saveWorkoutImmediate(workout: WorkoutSession) {
        pendingWorkout = null
        debounceJob?.cancel()
        viewModelScope.launch {
            runCatching { repository.updateWorkout(workout) }
        }
    }

    fun forceSync() {
        val pending = pendingWorkout
        if (pending != null) {
            saveWorkoutImmediate(pending)
        } else {
            debounceJob?.cancel()
        }
    }
    fun archiveWorkout(workoutId: Long) { viewModelScope.launch { repository.archiveWorkout(workoutId) } }
    fun unarchiveWorkout(workoutId: Long) { viewModelScope.launch { repository.unarchiveWorkout(workoutId) } }
    fun permanentlyDeleteWorkout(workoutId: Long) {
        viewModelScope.launch {
            val baseWorkouts = _uiState.value.workouts + _uiState.value.archivedWorkouts
            val workoutToDelete = baseWorkouts.find { it.id == workoutId }
            if (workoutToDelete != null) {
                val entries = repository.getEntriesForWorkout(workoutId).first()
                val setsMap = mutableMapOf<Long, List<SetEntry>>()
                for (entry in entries) {
                    setsMap[entry.id] = repository.getSetsForEntry(entry.id).first()
                }
                repository.permanentlyDeleteWorkout(workoutId)
                _lastDeleted.value = DeletedItem.WorkoutSessionWithEntries(workoutToDelete, entries, setsMap)
            }
        }
    }
    fun duplicateWorkout(workoutId: Long, onDuplicated: (Long) -> Unit = {}) {
        viewModelScope.launch { val newId = repository.duplicateWorkout(workoutId); if (newId > 0) onDuplicated(newId) }
    }

    // ── Exercise Entry operations ─────────────────────────────────────────────
    fun getEntriesForWorkout(workoutId: Long): Flow<List<ExerciseEntry>> = repository.getEntriesForWorkout(workoutId)

    fun addExerciseEntry(workoutId: Long, exerciseId: Long) {
        viewModelScope.launch {
            repository.insertExerciseEntry(ExerciseEntry(workoutId = workoutId, exerciseId = exerciseId, slotIndex = repository.getNextSlotIndex(workoutId), sequenceType = SequenceType.NONE.name))
        }
    }

    suspend fun addExerciseEntrySuspend(workoutId: Long, exerciseId: Long): Long =
        repository.insertExerciseEntry(ExerciseEntry(workoutId = workoutId, exerciseId = exerciseId, slotIndex = repository.getNextSlotIndex(workoutId), sequenceType = SequenceType.NONE.name))

    fun updateExerciseEntry(entry: ExerciseEntry) { viewModelScope.launch { runCatching { repository.updateExerciseEntry(entry) } } }
    fun deleteExerciseEntry(workoutId: Long, entryId: Long) {
        viewModelScope.launch {
            val entries = repository.getEntriesForWorkout(workoutId).first()
            val entryToDelete = entries.find { it.id == entryId }
            if (entryToDelete != null) {
                val sets = repository.getSetsForEntry(entryId).first()
                repository.deleteExerciseEntry(workoutId, entryId)
                _lastDeleted.value = DeletedItem.ExerciseEntries(workoutId, listOf(entryToDelete to sets))
            }
        }
    }
    fun deleteExerciseEntries(workoutId: Long, entryIds: List<Long>) {
        viewModelScope.launch {
            val entries = repository.getEntriesForWorkout(workoutId).first()
            val toDelete = entries.filter { it.id in entryIds }
            val pairs = mutableListOf<Pair<ExerciseEntry, List<SetEntry>>>()
            for (entry in toDelete) {
                val sets = repository.getSetsForEntry(entry.id).first()
                pairs.add(entry to sets)
                repository.deleteExerciseEntry(workoutId, entry.id)
            }
            if (pairs.isNotEmpty()) {
                _lastDeleted.value = DeletedItem.ExerciseEntries(workoutId, pairs)
            }
        }
    }
    suspend fun getExerciseName(exerciseId: Long): String? = repository.getExerciseById(exerciseId)?.name
    suspend fun getExerciseById(exerciseId: Long): com.chiron.app.data.entities.Exercise? = repository.getExerciseById(exerciseId)
    suspend fun getAllExercises(): List<com.chiron.app.data.entities.Exercise> = repository.getAllExercises()
    suspend fun getLastSessionPreview(exerciseId: Long, currentWorkoutId: Long): ChironRepository.LastSessionPreview? = repository.getLastSessionPreview(exerciseId, currentWorkoutId)
    suspend fun getLastSessionSupersetPreview(exerciseId: Long, currentWorkoutId: Long): ChironRepository.LastSessionSupersetPreview? = repository.getLastSessionSupersetPreview(exerciseId, currentWorkoutId)

    // ── Set operations ────────────────────────────────────────────────────────
    fun getSetsForEntry(entryId: Long): Flow<List<SetEntry>> = repository.getSetsForEntry(entryId)

    fun addSet(entryId: Long, weightLbs: Double? = null, reps: Int? = null) {
        viewModelScope.launch {
            repository.insertSetAndEvaluateHistoricalPr(SetEntry(exerciseEntryId = entryId, setIndex = repository.getNextSetIndex(entryId), weightLbs = weightLbs, reps = reps, timestampUtc = System.currentTimeMillis()))
        }
    }

    fun updateSet(set: SetEntry) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch { delay(debounceDelayMs); runCatching { repository.updateSet(set) } }
    }

    fun deleteSet(entryId: Long, setId: Long) {
        viewModelScope.launch {
            val sets = repository.getSetsForEntry(entryId).first()
            val setToDelete = sets.find { it.id == setId }
            if (setToDelete != null) {
                repository.deleteSet(entryId, setId)
                _lastDeleted.value = DeletedItem.Set(entryId, setToDelete)
            }
        }
    }
    suspend fun getAutofillSuggestion(exerciseId: Long): SetEntry? = repository.getLastSetForExercise(exerciseId)
    fun updateSetAndCheckPr(set: SetEntry) { viewModelScope.launch { runCatching { repository.updateSetAndEvaluateHistoricalPr(set) } } }
    suspend fun getAllPrsForExercise(exerciseId: Long) = repository.getAllPrsForExercise(exerciseId)
    fun getPrsForExerciseFlow(exerciseId: Long) = repository.getPrsForExerciseFlow(exerciseId)
    fun getSettingsRepository(): UserSettingsRepository = settingsRepository

    class Factory(private val repository: ChironRepository, private val settingsRepository: UserSettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel(repository, settingsRepository) as T
    }
}
