package com.chiron.app.ui.timer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiron.app.viewmodel.TimerTab
import com.chiron.app.viewmodel.TimerViewModel

@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SegmentedButtonRow {
            SegmentedButton(
                selected = state.activeTab == TimerTab.TIMER,
                onClick = { viewModel.selectTab(TimerTab.TIMER) }
            ) { Text("Timer") }
            SegmentedButton(
                selected = state.activeTab == TimerTab.STOPWATCH,
                onClick = { viewModel.selectTab(TimerTab.STOPWATCH) }
            ) { Text("Stopwatch") }
        }

        when (state.activeTab) {
            TimerTab.TIMER -> CountdownContent(viewModel)
            TimerTab.STOPWATCH -> StopwatchContent(viewModel)
        }
    }
}

@Composable
private fun CountdownContent(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = TimerViewModel.formatCountdown(state.countdownRemaining),
            style = MaterialTheme.typography.displayMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.startCountdown() }) { Text("Start") }
            Button(onClick = { viewModel.pauseCountdown() }) { Text("Pause") }
            Button(onClick = { viewModel.resetCountdown() }) { Text("Reset") }
        }
    }
}
