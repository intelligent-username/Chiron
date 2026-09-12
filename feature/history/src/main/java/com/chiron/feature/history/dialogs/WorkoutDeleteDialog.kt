package com.chiron.feature.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.chiron.core.model.WorkoutSession

/**
 * Delete / Archive confirmation dialog with explicit mode string ("archive" or "permanent").
 */
@Composable
fun WorkoutDeleteDialog(
    workout: WorkoutSession,
    mode: String,
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

/**
 * Delete / Archive confirmation dialog inferred from [workout.archived].
 */
@Composable
fun WorkoutDeleteDialog(
    workout: WorkoutSession,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isPermanentDelete = workout.archived != 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isPermanentDelete) "Delete Workout Permanently?" else "Archive Workout?")
        },
        text = {
            Text(
                if (isPermanentDelete) {
                    "This will permanently remove this workout and all exercises/sets inside it. This cannot be undone."
                } else {
                    "This will move this workout to archived workouts. You can unarchive it later or permanently delete it from Archived."
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    if (isPermanentDelete) "Delete" else "Archive",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
