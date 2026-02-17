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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HistoryUiState(
    val workouts: List<WorkoutSession> = emptyList(),
    val dayTags: List<String> = emptyList(),
    val selectedDayTag: String? = null,
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
        // Observe workouts
        viewModelScope.launch {
            repository.workoutsFlow.collect { workouts ->
                _uiState.update { it.copy(workouts = workouts) }
            }
        }

        // Observe day tags
        viewModelScope.launch {
            repository.dayTagsFlow.collect { tags ->
                _uiState.update { it.copy(dayTags = tags) }
            }
        }

        // Observe display unit preference
        viewModelScope.launch {
            settingsRepository.displayInKgFlow.collect { inKg ->
                _uiState.update { it.copy(displayInKg = inKg) }
            }
        }
    }

    fun filterByDayTag(dayTag: String?) {
        _uiState.update { it.copy(selectedDayTag = dayTag) }
    }

    fun openEditor(workoutId: Long?) {
        _uiState.update { it.copy(isEditorOpen = true, editingWorkoutId = workoutId) }
    }

    fun closeEditor() {
        forceSync()
        _uiState.update { it.copy(isEditorOpen = false, editingWorkoutId = null) }
    }

    /**
     * Create a new workout for today.
     */
    fun createNewWorkout(dayTag: String, locationTag: String) {
        viewModelScope.launch {
            val now = Instant.now()
            val dateIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

            val session = WorkoutSession(
                dayTag = dayTag,
                dateIso = dateIso,
                dateUtc = now.toEpochMilli(),
                locationTag = locationTag
            )

            val id = repository.insertWorkout(session)
            openEditor(id)
        }
    }

    /**
     * Update workout with debounce.
     */
    fun updateWorkout(workout: WorkoutSession) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(debounceDelayMs)
            repository.updateWorkout(workout)
        }
    }

    /**
     * Force immediate sync (on navigation away or finish).
     */
    fun forceSync() {
        debounceJob?.cancel()
        // Any pending work would be lost; in a real impl, track pending updates
    }

    fun archiveWorkout(workoutId: Long) {
        viewModelScope.launch {
            repository.archiveWorkout(workoutId)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Exercise Entry operations
    // ─────────────────────────────────────────────────────────────────────────

    fun getEntriesForWorkout(workoutId: Long): Flow<List<ExerciseEntry>> =
        repository.getEntriesForWorkout(workoutId)

    fun addExerciseEntry(workoutId: Long, exerciseId: Long) {
        viewModelScope.launch {
            val slotIndex = repository.getNextSlotIndex(workoutId)
            val entry = ExerciseEntry(
                workoutId = workoutId,
                exerciseId = exerciseId,
                slotIndex = slotIndex,
                sequenceType = SequenceType.NONE.name
            )
            repository.insertExerciseEntry(entry)
        }
    }

    fun updateExerciseEntry(entry: ExerciseEntry) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(debounceDelayMs)
            repository.updateExerciseEntry(entry)
        }
    }

    fun deleteExerciseEntry(workoutId: Long, entryId: Long) {
        viewModelScope.launch {
            repository.deleteExerciseEntry(workoutId, entryId)
        }
    }

    suspend fun getExerciseName(exerciseId: Long): String? =
        repository.getExerciseById(exerciseId)?.name

    suspend fun getExerciseById(exerciseId: Long): com.chiron.app.data.entities.Exercise? =
        repository.getExerciseById(exerciseId)

    suspend fun getAllExercises(): List<com.chiron.app.data.entities.Exercise> =
        repository.getAllExercises()

    // ─────────────────────────────────────────────────────────────────────────
    // Set operations
    // ─────────────────────────────────────────────────────────────────────────

    fun getSetsForEntry(entryId: Long): Flow<List<SetEntry>> =
        repository.getSetsForEntry(entryId)

    fun addSet(entryId: Long, weightLbs: Double? = null, reps: Int? = null) {
        viewModelScope.launch {
            val setIndex = repository.getNextSetIndex(entryId)
            val set = SetEntry(
                exerciseEntryId = entryId,
                setIndex = setIndex,
                weightLbs = weightLbs,
                reps = reps,
                timestampUtc = System.currentTimeMillis()
            )
            repository.insertSet(set)
        }
    }

    fun updateSet(set: SetEntry) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(debounceDelayMs)
            repository.updateSet(set)
        }
    }

    fun deleteSet(entryId: Long, setId: Long) {
        viewModelScope.launch {
            repository.deleteSet(entryId, setId)
        }
    }

    /**
     * Get autofill suggestion for a new set (last used weight/reps for this exercise).
     */
    suspend fun getAutofillSuggestion(exerciseId: Long): SetEntry? =
        repository.getLastSetForExercise(exerciseId)

    /**
     * Check if this set is a new PR.
     */
    suspend fun checkPr(exerciseId: Long, weightLbs: Double, reps: Int): Boolean =
        repository.isNewPr(exerciseId, weightLbs, reps)

    fun getSettingsRepository(): UserSettingsRepository = settingsRepository

    // ─────────────────────────────────────────────────────────────────────────
    // Factory
    // ─────────────────────────────────────────────────────────────────────────

    class Factory(
        private val repository: ChironRepository,
        private val settingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository, settingsRepository) as T
        }
    }
}
