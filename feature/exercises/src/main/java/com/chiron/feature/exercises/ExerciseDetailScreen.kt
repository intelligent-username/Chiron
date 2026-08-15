package com.chiron.feature.exercises

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.chiron.core.model.Exercise
import com.chiron.core.ui.components.IconPickerDropdown
import com.chiron.feature.history.VolumeViewModel
import com.chiron.feature.history.VolumeContent
import kotlinx.coroutines.launch

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
    onOpenWorkoutFromDate: ((Long, java.time.LocalDate) -> Unit)? = null,
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

    // Tracking config state — mirrors the exercise's current values
    var weightEnabled by remember(exercise.id) { mutableStateOf(exercise.isWeightBased == 1) }
    var distanceEnabled by remember(exercise.id) { mutableStateOf(exercise.isDistanceBased == 1) }
    var useReps by remember(exercise.id) { mutableStateOf(exercise.isTimeBased != 1) }

    // Whether this exercise is eligible for PR / volume tracking (weight + reps)
    val isPrEligible = exercise.isWeightBased == 1 && exercise.isRepBased == 1

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Edit Exercise", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    // PR trophy — redirects to the PR screen for this exercise
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
                                isDistanceBased = if (distanceEnabled) 1 else 0
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
                        enabled = nameState.trim().isNotBlank(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Save", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
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
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            // Icon picker: big current icon acting as a button
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

            // ── Tracking section ──────────────────────────────────────────────
            Text(
                "Tracking",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )

            if (showImmutabilityError) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "This exercise already contains historical entries. Changing its tracking " +
                            "configuration would create incompatible historical data, so this change " +
                            "cannot be applied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Track Weight", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = weightEnabled, onCheckedChange = { weightEnabled = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Track Distance", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = distanceEnabled, onCheckedChange = { distanceEnabled = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Count by", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Reps",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (useReps) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = !useReps,
                        onCheckedChange = { useReps = !it },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Text(
                        "Time",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!useReps) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Volume Trend — only for weight+reps ───────────────────────────
            if (isPrEligible) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Volume Trend",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                VolumeContent(
                    state = volumeState,
                    displayInKg = displayInKg,
                    onModeChange = volumeViewModel::setMode,
                    onWeekCountChange = volumeViewModel::setWeekCount,
                    onPrevWeek = volumeViewModel::goToPreviousWeek,
                    onNextWeek = volumeViewModel::goToNextWeek,
                    onToggleAbridgeGaps = volumeViewModel::toggleAbridgeGaps,
                    onPointTap = { point ->
                        onOpenWorkoutFromDate?.invoke(exercise.id, point.date)
                    },
                    scrollable = false
                )
            }
        }

        // Archive confirmation dialog
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Archive Exercise?") },
                text = { Text("Archive \"${exercise.name}\"? It will be hidden from the list but can be recovered anytime from the archive.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDelete?.invoke(exercise.id)
                            showDeleteConfirmation = false
                            onClose()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Archive")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        if (showPermanentDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showPermanentDeleteConfirmation = false },
                title = { Text("Delete Permanently?") },
                text = { Text("Permanently delete \"${exercise.name}\"? This cannot be undone and all history will be lost.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeletePermanently?.invoke(exercise.id)
                            showPermanentDeleteConfirmation = false
                            onClose()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermanentDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
