package com.chiron.app.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiron.app.data.entities.WorkoutSession

@Composable
fun WorkoutEditor(
    workout: WorkoutSession?,
    onSave: (WorkoutSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayTagState = remember(workout) { mutableStateOf(workout?.dayTag ?: "") }
    val dateIsoState = remember(workout) { mutableStateOf(workout?.dateIso ?: "") }
    val locationState = remember(workout) { mutableStateOf(workout?.locationTag ?: "") }
    val notesState = remember(workout) { mutableStateOf(workout?.notes ?: "") }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Workout", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = dayTagState.value,
                onValueChange = { dayTagState.value = it },
                label = { Text("Day tag") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = dateIsoState.value,
                onValueChange = { dateIsoState.value = it },
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = locationState.value,
                onValueChange = { locationState.value = it },
                label = { Text("Location") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notesState.value,
                onValueChange = { notesState.value = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth()
            )

            // Placeholder: onSave invoked externally when needed
        }
    }
}
