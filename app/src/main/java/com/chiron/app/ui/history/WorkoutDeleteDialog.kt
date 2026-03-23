package com.chiron.app.ui.history

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.chiron.app.data.entities.WorkoutSession

@Composable
fun WorkoutDeleteDialog(
    workout: WorkoutSession,
    mode: String, // "archive" or "permanent"
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isPermanent = mode == "permanent"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPermanent) "Delete Workout Permanently?" else "Archive Workout?") },
        text = {
            Text(
                if (isPermanent)
                    "This will permanently remove '${workout.dayTag}' and all its sets. This cannot be undone."
                else
                    "Move '${workout.dayTag}' to archived workouts?"
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (isPermanent) "Delete" else "Archive", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
