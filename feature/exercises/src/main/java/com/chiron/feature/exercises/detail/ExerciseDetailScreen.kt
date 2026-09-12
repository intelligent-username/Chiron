package com.chiron.feature.exercises

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chiron.core.model.Exercise
import com.chiron.core.ui.components.IconPickerDropdown
import com.chiron.feature.history.VolumeViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exercise: Exercise?,
    volumeViewModel: VolumeViewModel,
    displayInKg: Boolean,
    onSave: suspend (Exercise) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onUnarchive: ((Long) -> Unit)? = null,
    onDeletePermanently: ((Long) -> Unit)? = null,
    onOpenPrForExercise: ((Long) -> Unit)? = null,
    onOpenWorkoutFromDate: ((Long, LocalDate) -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (exercise == null) {
        onClose()
        return
    }

    var nameState by remember { mutableStateOf(exercise.name) }
    var descState by remember { mutableStateOf(exercise.description ?: "") }
    var iconState by remember { mutableStateOf(exercise.iconName ?: "default") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showPermanentDeleteConfirmation by remember { mutableStateOf(false) }
    var showImmutabilityError by remember { mutableStateOf(false) }

    var weightEnabled by remember(exercise.id) { mutableStateOf(exercise.isWeightBased == 1) }
    var distanceEnabled by remember(exercise.id) { mutableStateOf(exercise.isDistanceBased == 1) }
    var useReps by remember(exercise.id) { mutableStateOf(exercise.isTimeBased != 1) }
    var bodyweightEnabled by remember(exercise.id) { mutableStateOf(exercise.isBodyweight == 1) }
    var percentText by remember(exercise.id) { mutableStateOf(formatBodyweightPercent(exercise.percentBodyweight)) }
    val initialIsBodyweight = remember(exercise.id) { exercise.isBodyweight == 1 }
    var showPresets by remember(exercise.id) { mutableStateOf(false) }

    val isPrEligible = (exercise.isWeightBased == 1 && exercise.isRepBased == 1) ||
        (exercise.isBodyweight == 1 && exercise.isRepBased == 1)
    val percentValue = percentText.trim().toDoubleOrNull()
    val percentError = if (!bodyweightEnabled) null
        else if (percentValue == null || percentValue <= 0 || percentValue > 200) "Enter a percentage between 1 and 200"
        else null

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = { Text("Edit Exercise", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    if (onOpenPrForExercise != null) {
                        IconButton(onClick = { onOpenPrForExercise(exercise.id) }) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                "Open PR",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (exercise.archived == 0) {
                        if (onDelete != null) {
                            IconButton(onClick = { showDeleteConfirmation = true }) {
                                Icon(
                                    Icons.Default.Archive,
                                    "Archive",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        if (onUnarchive != null) {
                            IconButton(onClick = { onUnarchive(exercise.id); onClose() }) {
                                Icon(
                                    Icons.Default.Unarchive,
                                    "Unarchive",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (onDeletePermanently != null) {
                            IconButton(onClick = { showPermanentDeleteConfirmation = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    "Delete permanently",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    val scope = rememberCoroutineScope()
                    TextButton(
                        onClick = {
                            val newExercise = exercise.copy(
                                name = nameState.trim(),
                                description = descState.trim().ifBlank { null },
                                iconName = iconState,
                                isWeightBased = if (weightEnabled) 1 else 0,
                                isRepBased = if (useReps) 1 else 0,
                                isTimeBased = if (!useReps) 1 else 0,
                                isDistanceBased = if (distanceEnabled) 1 else 0,
                                isBodyweight = if (bodyweightEnabled) 1 else 0,
                                percentBodyweight = if (bodyweightEnabled) (percentValue ?: exercise.percentBodyweight) else exercise.percentBodyweight
                            )
                            scope.launch {
                                try {
                                    onSave(newExercise)
                                    onClose()
                                } catch (e: IllegalStateException) {
                                    showImmutabilityError = true
                                }
                            }
                        },
                        enabled = nameState.trim().isNotBlank() && percentError == null,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        val focusManager = LocalFocusManager.current

        LaunchedEffect(exercise.id) {
            volumeViewModel.setExerciseFilter(exercise.id)
        }
        val volumeState by volumeViewModel.uiState.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextField(
                value = nameState,
                onValueChange = { nameState = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.displaySmall,
                placeholder = { Text("Exercise Name", style = MaterialTheme.typography.displaySmall) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            IconPickerDropdown(
                selectedIcon = iconState,
                onIconSelected = { iconState = it },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = descState,
                onValueChange = { descState = it },
                label = { Text("Description (optional)", fontFamily = FontFamily.SansSerif) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            ExerciseTrackingConfigSection(
                weightEnabled = weightEnabled,
                onWeightEnabledChange = { weightEnabled = it },
                distanceEnabled = distanceEnabled,
                onDistanceEnabledChange = { distanceEnabled = it },
                useReps = useReps,
                onUseRepsChange = { useReps = it },
                bodyweightEnabled = bodyweightEnabled,
                onBodyweightEnabledChange = {
                    bodyweightEnabled = it
                    if (it && !initialIsBodyweight) {
                        showPresets = true
                    }
                },
                percentText = percentText,
                onPercentTextChange = {
                    percentText = it
                    showPresets = false
                },
                percentError = percentError,
                showImmutabilityError = showImmutabilityError,
                showPresets = showPresets,
                onPresetSelected = {
                    percentText = it
                    showPresets = false
                }
            )

            if (isPrEligible) {
                ExerciseVolumeSection(
                    exerciseId = exercise.id,
                    volumeViewModel = volumeViewModel,
                    volumeState = volumeState,
                    displayInKg = displayInKg,
                    onOpenWorkoutFromDate = onOpenWorkoutFromDate
                )
            }
        }

        if (showDeleteConfirmation) {
            ExerciseArchiveDialog(
                exerciseName = exercise.name,
                onConfirm = {
                    onDelete?.invoke(exercise.id)
                    showDeleteConfirmation = false
                    onClose()
                },
                onDismiss = { showDeleteConfirmation = false }
            )
        }
        if (showPermanentDeleteConfirmation) {
            ExercisePermanentDeleteDialog(
                exerciseName = exercise.name,
                onConfirm = {
                    onDeletePermanently?.invoke(exercise.id)
                    showPermanentDeleteConfirmation = false
                    onClose()
                },
                onDismiss = { showPermanentDeleteConfirmation = false }
            )
        }
    }
}

private fun formatBodyweightPercent(value: Double): String {
    if (value <= 0 || value > 200) return "100"
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
