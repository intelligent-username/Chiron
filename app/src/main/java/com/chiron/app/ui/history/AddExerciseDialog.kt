package com.chiron.app.ui.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Dialog for picking an exercise to add to a workout or to an existing superset.
 *
 * When [parentEntryId] is non-null the selected exercise is appended to
 * the superset group anchored at that entry; otherwise it is added as a
 * standalone exercise.
 */
@Composable
fun AddExerciseDialog(
    viewModel: HistoryViewModel,
    workoutId: Long,
    parentEntryId: Long? = null,
    entries: List<ExerciseEntry> = emptyList(),
    onExerciseAdded: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var allExercises by remember { mutableStateOf(emptyList<com.chiron.app.data.entities.Exercise>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        allExercises = viewModel.getAllExercises()
    }

    val filteredExercises = if (searchQuery.isBlank()) {
        allExercises
    } else {
        allExercises.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parentEntryId != null) "Add to Superset" else "Add Exercise") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search exercises") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filteredExercises) { exercise ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        if (parentEntryId == null) {
                                            viewModel.addExerciseEntry(workoutId, exercise.id)
                                            onExerciseAdded()
                                            onDismiss()
                                            return@launch
                                        }

                                        val parentEntry = entries.find { it.id == parentEntryId }
                                        if (parentEntry == null) {
                                            onDismiss()
                                            return@launch
                                        }

                                        val groupIdentifier = parentEntry.groupId ?: parentEntry.id
                                        val existingSupersetEntries = entries.filter {
                                            it.id == parentEntry.id || it.groupId == groupIdentifier
                                        }

                                        // Prevent adding the same exercise twice to the superset
                                        if (existingSupersetEntries.any { it.exerciseId == exercise.id }) {
                                            return@launch
                                        }

                                        viewModel.updateExerciseEntry(
                                            parentEntry.copy(
                                                sequenceType = "SUPERSET_START",
                                                groupId = groupIdentifier,
                                                numExercisesInSuperset = parentEntry.numExercisesInSuperset.coerceAtLeast(2)
                                            )
                                        )

                                        // Demote the current tail to MIDDLE
                                        val oldTail = existingSupersetEntries
                                            .filter { it.id != parentEntry.id }
                                            .maxByOrNull { it.slotIndex }

                                        if (oldTail != null) {
                                            viewModel.updateExerciseEntry(
                                                oldTail.copy(
                                                    sequenceType = "SUPERSET_MIDDLE",
                                                    groupId = groupIdentifier
                                                )
                                            )
                                        }

                                        val newEntryId = viewModel.addExerciseEntrySuspend(workoutId, exercise.id)
                                        val newSlotIndex = (entries.maxOfOrNull { it.slotIndex } ?: 0) + 1

                                        viewModel.updateExerciseEntry(
                                            ExerciseEntry(
                                                id = newEntryId,
                                                workoutId = workoutId,
                                                exerciseId = exercise.id,
                                                slotIndex = newSlotIndex,
                                                groupId = groupIdentifier,
                                                sequenceType = "SUPERSET_END",
                                                notes = null,
                                                archived = 0,
                                                numExercisesInSuperset = parentEntry.numExercisesInSuperset.coerceAtLeast(2)
                                            )
                                        )

                                        onExerciseAdded()
                                        onDismiss()
                                    }
                                }
                        ) {
                            Text(
                                text = exercise.name,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
