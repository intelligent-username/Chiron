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
    val selectedDayTag: String? = null,
    val showArchivedWorkouts: Boolean = false,
    val isEditorOpen: Boolean = false,
    val editingWorkoutId: Long? = null,
    val displayInKg: Boolean = false
)

class HistoryViewModel(
    private val repository: ChironRepository,
    private val settingsRepository: UserSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    private var debounceJob: Job? = null
    private val debounceDelayMs = 750L

    init {
        viewModelScope.launch { repository.workoutsFlow.collect { w -> _uiState.update { it.copy(workouts = w) } } }
        viewModelScope.launch { repository.dayTagsFlow.collect { tags ->
            _uiState.update { it.copy(dayTags = tags.map { t -> t.ifBlank { "Untitled Workout" } }.distinct().sorted()) }
        }}
        viewModelScope.launch { repository.archivedWorkoutsFlow.collect { w -> _uiState.update { it.copy(archivedWorkouts = w) } } }
        viewModelScope.launch { settingsRepository.displayInKgFlow.collect { v -> _uiState.update { it.copy(displayInKg = v) } } }
    }

    // ── Workout operations ────────────────────────────────────────────────────
    fun filterByDayTag(dayTag: String?) = _uiState.update { it.copy(selectedDayTag = dayTag) }
    fun setShowArchivedWorkouts(show: Boolean) = _uiState.update { it.copy(showArchivedWorkouts = show, selectedDayTag = null) }
    fun openEditor(workoutId: Long?) = _uiState.update { it.copy(isEditorOpen = true, editingWorkoutId = workoutId) }
    fun closeEditor() { forceSync(); _uiState.update { it.copy(isEditorOpen = false, editingWorkoutId = null) } }

    fun createNewWorkout(dayTag: String, locationTag: String) {
        viewModelScope.launch {
            val id = repository.insertWorkout(WorkoutSession(dayTag = dayTag, dateIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), dateUtc = Instant.now().toEpochMilli(), locationTag = locationTag))
            openEditor(id)
        }
    }

    fun updateWorkout(workout: WorkoutSession) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch { delay(debounceDelayMs); runCatching { repository.updateWorkout(workout) } }
    }

    fun saveWorkoutImmediate(workout: WorkoutSession) { debounceJob?.cancel(); viewModelScope.launch { runCatching { repository.updateWorkout(workout) } } }
    fun forceSync() { debounceJob?.cancel() }
    fun archiveWorkout(workoutId: Long) { viewModelScope.launch { repository.archiveWorkout(workoutId) } }
    fun unarchiveWorkout(workoutId: Long) { viewModelScope.launch { repository.unarchiveWorkout(workoutId) } }
    fun permanentlyDeleteWorkout(workoutId: Long) { viewModelScope.launch { repository.permanentlyDeleteWorkout(workoutId) } }
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
    fun deleteExerciseEntry(workoutId: Long, entryId: Long) { viewModelScope.launch { repository.deleteExerciseEntry(workoutId, entryId) } }
    suspend fun getExerciseName(exerciseId: Long): String? = repository.getExerciseById(exerciseId)?.name
    suspend fun getExerciseById(exerciseId: Long): com.chiron.app.data.entities.Exercise? = repository.getExerciseById(exerciseId)
    suspend fun getAllExercises(): List<com.chiron.app.data.entities.Exercise> = repository.getAllExercises()
    suspend fun getLastSessionPreview(exerciseId: Long, currentWorkoutId: Long): ChironRepository.LastSessionPreview? = repository.getLastSessionPreview(exerciseId, currentWorkoutId)
    suspend fun getLastSessionSupersetPreview(currentEntryId: Long, allCurrentEntries: List<ExerciseEntry>, currentWorkoutId: Long): ChironRepository.LastSessionSupersetPreview? = repository.getLastSessionSupersetPreview(currentEntryId, allCurrentEntries, currentWorkoutId)

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

    fun deleteSet(entryId: Long, setId: Long) { viewModelScope.launch { repository.deleteSet(entryId, setId) } }
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
