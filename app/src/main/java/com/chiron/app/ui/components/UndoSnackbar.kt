package com.chiron.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiron.app.viewmodel.HistoryViewModel
import com.chiron.app.viewmodel.DeletedItem
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
                .padding(16.dp),
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
                        Text("UNDO")
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
                Text(message)
            }
        }
    }
}
