package com.chiron.feature.exercises

import com.chiron.core.model.Exercise

data class TrackingConfig(
    val isWeightBased: Boolean = true,
    val isRepBased: Boolean = true,
    val isTimeBased: Boolean = false,
    val isDistanceBased: Boolean = false,
    val isBodyweight: Boolean = false,
    val percentBodyweight: Double = 100.0
)

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
