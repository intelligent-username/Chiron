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
import androidx.compose.ui.unit.sp
import com.chiron.app.ui.components.WheelPicker
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
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

        Spacer(modifier = Modifier.weight(1.5f)) // Push content down
        
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (state.activeTab) {
                TimerTab.TIMER -> CountdownContent(viewModel)
                TimerTab.STOPWATCH -> StopwatchContent(viewModel)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f)) // Push content up slightly from bottom to be "lower down" but not bottom
    }
}

@Composable
fun CountdownContent(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isCountdownRunning) {
            Text(
                text = TimerViewModel.formatCountdown(state.countdownRemaining),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 80.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Minutes
                WheelPicker(
                    count = 100, // Allow up to 99 minutes
                    value = state.countdownRemaining / 60,
                    onValueChange = { newMin ->
                        val currentSec = state.countdownRemaining % 60
                        viewModel.setCountdownPreset(newMin * 60 + currentSec)
                    },
                    itemHeight = 120.dp,
                    textStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp)
                )
                
                Text(
                    ":",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 80.sp, 
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp).offset(y = (-8).dp) // Visual alignment
                )

                // Seconds
                WheelPicker(
                    count = 60,
                    value = state.countdownRemaining % 60,
                    onValueChange = { newSec ->
                        val currentMin = state.countdownRemaining / 60
                        viewModel.setCountdownPreset(currentMin * 60 + newSec)
                    },
                    itemHeight = 120.dp,
                    textStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp)
                )
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Start/Pause Button (Main Action)
            Button(
                onClick = { 
                    if (state.isCountdownRunning) viewModel.pauseCountdown() else viewModel.startCountdown() 
                },
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (state.isCountdownRunning) "Pause" else "Start",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            // Reset Button (Secondary Action)
            Button(
                onClick = { viewModel.resetCountdown() },
                modifier = Modifier
                    .weight(1f) // Changed from 0.5f to 1f for equal size
                    .height(80.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
            ) {
                Text("Reset", style = MaterialTheme.typography.headlineSmall) // Match style
            }
        }
    }
}
