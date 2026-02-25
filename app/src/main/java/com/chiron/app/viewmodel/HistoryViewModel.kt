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
        // Observe workouts
        viewModelScope.launch {
            repository.workoutsFlow.collect { workouts ->
                _uiState.update { it.copy(workouts = workouts) }
            }
        }

        // Observe day tags
        viewModelScope.launch {
            repository.dayTagsFlow.collect { tags ->
                val normalizedTags = tags.map { it.ifBlank { "Untitled Workout" } }.distinct().sorted()
                _uiState.update { it.copy(dayTags = normalizedTags) }
            }
        }

        viewModelScope.launch {
            repository.archivedWorkoutsFlow.collect { archived ->
                _uiState.update { it.copy(archivedWorkouts = archived) }
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

    fun setShowArchivedWorkouts(showArchived: Boolean) {
        _uiState.update {
            it.copy(
                showArchivedWorkouts = showArchived,
                selectedDayTag = null
            )
        }
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
            try {
                repository.updateWorkout(workout)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveWorkoutImmediate(workout: WorkoutSession) {
        debounceJob?.cancel()
        viewModelScope.launch {
            try {
                repository.updateWorkout(workout)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun unarchiveWorkout(workoutId: Long) {
        viewModelScope.launch {
            repository.unarchiveWorkout(workoutId)
        }
    }

    fun permanentlyDeleteWorkout(workoutId: Long) {
        viewModelScope.launch {
            repository.permanentlyDeleteWorkout(workoutId)
        }
    }

    /**
     * Duplicate a workout with today's date, then open the new copy in the editor.
     */
    fun duplicateWorkout(workoutId: Long, onDuplicated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val newId = repository.duplicateWorkout(workoutId)
            if (newId > 0) {
                onDuplicated(newId)
            }
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

    suspend fun addExerciseEntrySuspend(workoutId: Long, exerciseId: Long): Long {
        val slotIndex = repository.getNextSlotIndex(workoutId)
        val entry = ExerciseEntry(
            workoutId = workoutId,
            exerciseId = exerciseId,
            slotIndex = slotIndex,
            sequenceType = SequenceType.NONE.name
        )
        return repository.insertExerciseEntry(entry)
    }

    fun updateExerciseEntry(entry: ExerciseEntry) {
        viewModelScope.launch {
            try {
                repository.updateExerciseEntry(entry)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    suspend fun getLastSessionPreview(exerciseId: Long, currentWorkoutId: Long): ChironRepository.LastSessionPreview? =
        repository.getLastSessionPreview(exerciseId, currentWorkoutId)

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
            repository.insertSetAndEvaluateHistoricalPr(set)
        }
    }

    fun updateSet(set: SetEntry) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(debounceDelayMs)
            try {
                repository.updateSet(set)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
     * Save one finalized set and evaluate its historical PR flag at write-time only.
     * Uses "highest weight so far up to workout day" for the same rep count.
     */
    fun updateSetAndCheckPr(set: SetEntry) {
        viewModelScope.launch {
            try {
                repository.updateSetAndEvaluateHistoricalPr(set)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Get current global PRs for an exercise (one-shot, for the PR screen). */
    suspend fun getAllPrsForExercise(exerciseId: Long) =
        repository.getAllPrsForExercise(exerciseId)

    /** Observe current PRs for an exercise reactively. */
    fun getPrsForExerciseFlow(exerciseId: Long) =
        repository.getPrsForExerciseFlow(exerciseId)

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
