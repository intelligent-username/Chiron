package com.chiron.app.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiron.app.viewmodel.TimerViewModel

@Composable
fun StopwatchContent(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = TimerViewModel.formatStopwatch(state.stopwatchMillis),
            style = MaterialTheme.typography.displayMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.startStopwatch() }) { Text("Start") }
            Button(onClick = { viewModel.pauseStopwatch() }) { Text("Pause") }
            Button(onClick = { viewModel.resetStopwatch() }) { Text("Reset") }
        }

        // Lap button (separate row for emphasis)
        Button(
            onClick = { viewModel.recordLap() },
            enabled = state.isStopwatchRunning || state.stopwatchMillis > 0
        ) {
            Text("Lap")
        }

        Spacer(Modifier.height(8.dp))
        
        if (state.laps.isNotEmpty()) {
            Text("Laps", style = MaterialTheme.typography.titleSmall)
            state.laps.forEachIndexed { index, lap ->
                Text("Lap ${index + 1}: ${TimerViewModel.formatStopwatch(lap)}")
            }
        }
    }
}
