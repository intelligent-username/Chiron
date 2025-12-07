package com.chiron.app.ui.exercises

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiron.app.data.entities.Exercise

@Composable
fun ExerciseDetailScreen(
    exercise: Exercise?,
    onSave: (Exercise) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nameState = remember(exercise) { mutableStateOf(exercise?.name ?: "") }
    val descState = remember(exercise) { mutableStateOf(exercise?.description ?: "") }

    LaunchedEffect(exercise?.id) {
        // Placeholder for future side effects (load image, history)
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text("Exercise Detail", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = nameState.value,
            onValueChange = { nameState.value = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = descState.value,
            onValueChange = { descState.value = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            if (exercise != null) {
                onSave(exercise.copy(name = nameState.value.trim(), description = descState.value.trim()))
            }
            onClose()
        }) {
            Text("Save")
        }
    }
}
