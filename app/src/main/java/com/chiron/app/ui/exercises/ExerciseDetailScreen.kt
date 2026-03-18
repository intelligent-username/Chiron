package com.chiron.app.ui.exercises

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import com.chiron.app.data.entities.Exercise
import com.chiron.app.ui.components.IconPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exercise: Exercise?,
    onSave: (Exercise) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Edit Exercise") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(
                                Icons.Default.Archive,
                                "Archive",
                                tint = MaterialTheme.colorScheme.primary
                            )
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
                        enabled = nameState.trim().isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = nameState,
                onValueChange = { nameState = it },
                label = { Text("Exercise Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            OutlinedTextField(
                value = descState,
                onValueChange = { descState = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            
            // Icon picker
            IconPicker(
                selectedIcon = iconState,
                onIconSelected = { iconState = it },
                modifier = Modifier.fillMaxWidth().weight(1f)
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
    }
}
