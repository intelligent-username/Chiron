package com.chiron.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.chiron.core.model.Exercise
import com.chiron.core.model.ExerciseEntry
import kotlinx.coroutines.launch

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
    var allExercises by remember { mutableStateOf(emptyList<Exercise>()) }
    var isAdding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        allExercises = viewModel.getAllExercises()
    }

    val parent = entries.find { it.id == parentEntryId }
    val groupId = parent?.groupId ?: parent?.id
    val memberExerciseIds = if (parent == null) emptySet() else entries
        .filter { it.id == parent.id || it.groupId == groupId }
        .map { it.exerciseId }.toSet()
    val selectableExercises = allExercises.filter { it.id !in memberExerciseIds }
    val filteredExercises = if (searchQuery.isBlank()) {
        selectableExercises
    } else {
        selectableExercises.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
                                .clickable(enabled = !isAdding) {
                                    isAdding = true
                                    scope.launch {
                                        if (parentEntryId == null) {
                                            viewModel.addExerciseEntry(workoutId, exercise.id)
                                            onExerciseAdded()
                                            onDismiss()
                                            return@launch
                                        }

                                        viewModel.addSupersetExercise(workoutId, parentEntryId, exercise.id)

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
