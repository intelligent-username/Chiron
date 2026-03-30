package com.chiron.app.ui.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.chiron.app.data.entities.WorkoutSession

/**
 * Delete / Archive confirmation dialog.
 *
 * If [workout] is already archived the action is a permanent delete;
 * otherwise it archives the workout.
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

/**
 * Duplicate confirmation dialog.
 */
@Composable
fun WorkoutDuplicateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate Workout?") },
        text = {
            Text("This will create an identical copy of this workout with today's date. All exercises and sets will be copied.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Duplicate", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
