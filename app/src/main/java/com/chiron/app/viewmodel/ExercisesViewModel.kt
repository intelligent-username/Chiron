package com.chiron.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chiron.app.data.ChironRepository
import com.chiron.app.data.entities.Exercise
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExercisesUiState(
    val exercises: List<Exercise> = emptyList(),
    val archivedExercises: List<Exercise> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val prSearchQuery: String = "",
    val prSearchResults: List<Exercise> = emptyList(),
    val selectedExerciseId: Long? = null,
    val isDetailOpen: Boolean = false,
    val showArchived: Boolean = false,
    val isLoading: Boolean = true
)

class ExercisesViewModel(
    private val repository: ChironRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExercisesUiState())
    val uiState: StateFlow<ExercisesUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var prSearchJob: Job? = null
    private val searchDebounceMs = 300L

    init {
        viewModelScope.launch {
            repository.exercisesFlow.collect { exercises ->
                _uiState.update { it.copy(exercises = exercises, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.archivedExercisesFlow.collect { archived ->
                _uiState.update { it.copy(archivedExercises = archived, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.backfill1rmEstimates()
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(searchDebounceMs)
            if (query.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList()) }
            } else {
                val archived = _uiState.value.showArchived
                val results = repository.searchExercises(query, archived = archived)
                _uiState.update { it.copy(searchResults = results) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    fun updatePrSearchQuery(query: String) {
        _uiState.update { it.copy(prSearchQuery = query) }

        prSearchJob?.cancel()
        prSearchJob = viewModelScope.launch {
            delay(searchDebounceMs)
            if (query.isBlank()) {
                _uiState.update { it.copy(prSearchResults = emptyList()) }
            } else {
                val archived = _uiState.value.showArchived
                val results = repository.searchExercises(query, archived = archived)
                _uiState.update { it.copy(prSearchResults = results) }
            }
        }
    }

    fun clearPrSearch() {
        _uiState.update { it.copy(prSearchQuery = "", prSearchResults = emptyList()) }
    }

    fun openDetail(exerciseId: Long) {
        _uiState.update { it.copy(isDetailOpen = true, selectedExerciseId = exerciseId) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(isDetailOpen = false, selectedExerciseId = null) }
    }

    fun createExercise(name: String, description: String? = null, iconName: String? = null,
                        config: com.chiron.app.ui.exercises.TrackingConfig = com.chiron.app.ui.exercises.TrackingConfig()) {
        viewModelScope.launch {
            val exercise = Exercise(
                name = name.trim(),
                description = description?.trim(),
                iconName = iconName,
                isWeightBased = if (config.isWeightBased) 1 else 0,
                isRepBased = if (config.isRepBased) 1 else 0,
                isTimeBased = if (config.isTimeBased) 1 else 0,
                isDistanceBased = if (config.isDistanceBased) 1 else 0
            )
            repository.insertExercise(exercise)
        }
    }

    fun updateExercise(exercise: Exercise) {
        viewModelScope.launch {
            repository.updateExercise(exercise)
        }
    }

    suspend fun updateExerciseSuspend(exercise: Exercise) {
        repository.updateExercise(exercise)
    }

    fun renameExercise(exerciseId: Long, newName: String) {
        viewModelScope.launch {
            val exercise = repository.getExerciseById(exerciseId) ?: return@launch
            repository.updateExercise(exercise.copy(name = newName.trim()))
        }
    }

    fun updateDescription(exerciseId: Long, description: String?) {
        viewModelScope.launch {
            val exercise = repository.getExerciseById(exerciseId) ?: return@launch
            repository.updateExercise(exercise.copy(description = description?.trim()))
        }
    }

    fun setImage(exerciseId: Long, imageUri: Uri) {
        viewModelScope.launch {
            val exercise = repository.getExerciseById(exerciseId) ?: return@launch

            // Delete old image if exists
            exercise.imageUri?.let { repository.deleteImage(it) }

            // Copy new image
            val newUri = repository.copyImageToStorage(imageUri, exerciseId)
            if (newUri != null) {
                repository.updateExercise(exercise.copy(imageUri = newUri))
            }
        }
    }

    fun deleteImage(exerciseId: Long) {
        viewModelScope.launch {
            val exercise = repository.getExerciseById(exerciseId) ?: return@launch
            exercise.imageUri?.let { repository.deleteImage(it) }
            repository.updateExercise(exercise.copy(imageUri = null))
        }
    }

    fun archiveExercise(exerciseId: Long) {
        viewModelScope.launch {
            repository.archiveExercise(exerciseId)
            closeDetail()
        }
    }

    fun unarchiveExercise(exerciseId: Long) {
        viewModelScope.launch {
            repository.unarchiveExercise(exerciseId)
        }
    }

    fun deleteExercisePermanently(exerciseId: Long) {
        viewModelScope.launch {
            repository.deleteExercisePermanently(exerciseId)
            closeDetail()
        }
    }

    fun toggleShowArchived() {
        _uiState.update { it.copy(showArchived = !it.showArchived) }
    }

    suspend fun getExerciseById(id: Long): Exercise? = repository.getExerciseById(id)

    /** Get all current PRs for an exercise (rep count → best weight). */
    fun getPrsForExerciseFlow(exerciseId: Long) = repository.getPrsForExerciseFlow(exerciseId)

    /** Get 1RM estimate flow for an exercise. */
    fun get1rmEstimateForExerciseFlow(exerciseId: Long) = repository.get1rmEstimateForExerciseFlow(exerciseId)

    /** One-shot fetch of all exercise IDs that have at least one PR on record. */
    suspend fun getExerciseIdsWithPrs(): List<Long> = repository.getExerciseIdsWithPrs()

    // ─────────────────────────────────────────────────────────────────────────
    // Factory
    // ─────────────────────────────────────────────────────────────────────────

    class Factory(
        private val repository: ChironRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExercisesViewModel(repository) as T
        }
    }
}
