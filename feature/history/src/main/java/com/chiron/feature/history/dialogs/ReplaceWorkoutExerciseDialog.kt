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

@Composable
internal fun ReplaceWorkoutExerciseDialog(
    entry: ExerciseEntry,
    currentExercise: Exercise?,
    viewModel: HistoryViewModel,
    onDismiss: () -> Unit
) {
    var exercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Exercise?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        runCatching { viewModel.getAllExercises() }
            .onSuccess { exercises = it }
            .onFailure { error = "Could not load exercises" }
    }
    val candidates = remember(exercises, search, entry.exerciseId) {
        exercises.filter { it.id != entry.exerciseId && it.name.contains(search, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (selected == null) "Edit Exercise" else "Edit exercise?") },
        text = {
            Column(
                modifier = if (selected == null) Modifier.fillMaxWidth().height(400.dp) else Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selected == null) {
                    OutlinedTextField(value = search, onValueChange = { search = it },
                        label = { Text("Search exercises") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(candidates, key = { it.id }) { exercise ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { selected = exercise; error = null }) {
                                Text(exercise.name, modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                } else {
                    Text("Change to ${selected!!.name}? All sets, values, and timestamps will be kept. Personal records will be recalculated.")
                    if (currentExercise != null &&
                        (currentExercise.isWeightBased != selected!!.isWeightBased ||
                            currentExercise.isRepBased != selected!!.isRepBased ||
                            currentExercise.isTimeBased != selected!!.isTimeBased ||
                            currentExercise.isDistanceBased != selected!!.isDistanceBased)) {
                        Text("These exercises track different fields. Saved values remain, but some may not be shown.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            selected?.let { replacement ->
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        try {
                            viewModel.replaceWorkoutExercise(entry.id, entry.exerciseId, replacement.id)
                            onDismiss()
                        } catch (e: Exception) {
                            error = e.message ?: "Could not replace the exercise"
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(if (saving) "Saving…" else "Edit") }
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = {
                if (selected != null) { selected = null; error = null } else onDismiss()
            }) { Text(if (selected == null) "Cancel" else "Back") }
        }
    )
}
