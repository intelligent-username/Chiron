package com.chiron.app.ui.exercises

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.app.data.entities.Exercise
import com.chiron.app.ui.components.ExerciseAsyncIcon

@Composable
fun ExerciseGridItem(
    exercise: Exercise,
    onClick: () -> Unit,
    showArchived: Boolean = false,
    onUnarchive: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showUnarchiveConfirm by remember { mutableStateOf(false) }

    if (showUnarchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showUnarchiveConfirm = false },
            title = { Text("Unarchive Exercise") },
            text = { Text("Restore \"${exercise.name}\" to active exercises?") },
            confirmButton = {
                TextButton(
                    onClick = { onUnarchive?.invoke(); showUnarchiveConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Unarchive") }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        onClick = if (showArchived) ({ showUnarchiveConfirm = true }) else onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(0.8f),
        colors = CardDefaults.cardColors(
            containerColor = if (showArchived)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ExerciseAsyncIcon(
                iconName = exercise.iconName,
                contentDescription = exercise.name,
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp
            )
            if (showArchived) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap to unarchive",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
