package com.chiron.app.ui.timer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiron.app.viewmodel.TimerTab
import com.chiron.app.viewmodel.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = state.activeTab == TimerTab.TIMER,
                onClick = { viewModel.selectTab(TimerTab.TIMER) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Timer") }
            SegmentedButton(
                selected = state.activeTab == TimerTab.STOPWATCH,
                onClick = { viewModel.selectTab(TimerTab.STOPWATCH) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
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

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
