package com.chiron.app.ui.timer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.app.viewmodel.TimerViewModel

@Composable
fun StopwatchContent(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val formatted = TimerViewModel.formatStopwatch(state.stopwatchMillis)
        val parts = formatted.split(".")
        val timePart = parts.getOrElse(0) { "00:00" }
        val millisPart = if (parts.size > 1) ".${parts[1]}" else ""
        
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = timePart,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 100.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.alignByBaseline(),
                maxLines = 1,
                softWrap = false
            )
            if (millisPart.isNotEmpty()) {
                Text(
                    text = millisPart,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.alignByBaseline().padding(start = 4.dp),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            // Left Button: Lap / Reset
            val isRunning = state.isStopwatchRunning
            val hasTime = state.stopwatchMillis > 0
            
            Button(
                onClick = { 
                    if (isRunning) viewModel.recordLap() else viewModel.resetStopwatch() 
                },
                enabled = isRunning || hasTime,
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Text(
                    text = if (isRunning) "Lap" else "Reset",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            // Right Button: Start / Pause
            Button(
                onClick = { 
                    if (isRunning) viewModel.pauseStopwatch() else viewModel.startStopwatch() 
                },
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isRunning) "Pause" else "Start",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        // Laps list (showing most recent first)
        if (state.laps.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Laps", 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Show last 5 laps for UI cleanliness, reversed
                val layoutLaps = state.laps.asReversed().take(5)
                layoutLaps.forEachIndexed { index, lapTime ->
                    // Calculate original index (state.laps.size - 1 - index) 
                    // or just "Lap X" based on loop order?
                    // Usually "Lap #": standard order is better to track.
                    // But displaying reversed.
                    val originalIndex = state.laps.size - index
                    Text(
                        text = "Lap $originalIndex: ${TimerViewModel.formatStopwatch(lapTime)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
