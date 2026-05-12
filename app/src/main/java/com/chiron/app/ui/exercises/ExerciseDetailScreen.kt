package com.chiron.app.ui.exercises

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.chiron.app.data.entities.Exercise
import com.chiron.app.ui.components.IconPickerDropdown
import com.chiron.app.viewmodel.VolumeViewModel
import com.chiron.app.ui.volume.VolumeContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exercise: Exercise?,
    volumeViewModel: VolumeViewModel,
    displayInKg: Boolean,
    onSave: (Exercise) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onUnarchive: ((Long) -> Unit)? = null,
    onDeletePermanently: ((Long) -> Unit)? = null,
    onOpenPrForExercise: ((Long) -> Unit)? = null,
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
                    TextButton(
                        onClick = {
                            onSave(exercise.copy(
                                name = nameState.trim(),
                                description = descState.trim().ifBlank { null },
                                iconName = iconState
                            ))
                            onClose()
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
                label = { Text("Description (optional)", fontFamily = FontFamily.Cursive) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Cursive,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("Volume Trend", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            
            VolumeContent(
                state = volumeState,
                displayInKg = displayInKg,
                onModeChange = volumeViewModel::setMode,
                onWeekCountChange = volumeViewModel::setWeekCount,
                onPrevWeek = volumeViewModel::goToPreviousWeek,
                onNextWeek = volumeViewModel::goToNextWeek,
                onToggleAbridgeGaps = volumeViewModel::toggleAbridgeGaps
            )
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
