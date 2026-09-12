package com.chiron.feature.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun UndoSnackbar(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val lastDeleted by viewModel.lastDeleted.collectAsState()

    AnimatedVisibility(
        visible = lastDeleted != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val item = lastDeleted ?: return@AnimatedVisibility

        // Auto-dismiss after 5 seconds
        LaunchedEffect(item) {
            delay(5000)
            viewModel.clearLastDeleted()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                action = {
                    TextButton(
                        onClick = { viewModel.undoLastDeleted() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Undo",
                            style = MaterialTheme.typography.labelLarge.copy(
                                shadow = null,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            ) {
                val message = when (item) {
                    is DeletedItem.Set -> "Set deleted"
                    is DeletedItem.ExerciseEntries -> {
                        if (item.entries.size > 1) "Superset deleted" else "Exercise deleted"
                    }
                    is DeletedItem.WorkoutSessionWithEntries -> "Workout deleted"
                }
                Text(message, style = MaterialTheme.typography.bodyMedium.copy(shadow = null))
            }
        }
    }
}
