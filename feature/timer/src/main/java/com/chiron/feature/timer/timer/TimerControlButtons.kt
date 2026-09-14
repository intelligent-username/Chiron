package com.chiron.feature.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlagCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.Error
import com.chiron.core.ui.theme.Green
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

/**
 * Bottom control buttons (Reset / Lap on left, Start / Pause / Resume on right)
 * shared by Timer, Stopwatch, and Metronome tabs.
 */
@Composable
fun TimerControlButtons(
    state: TimerUiState,
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val isRunning: Boolean
    val leftText: String
    val leftIcon: ImageVector
    val leftOnClick: () -> Unit

    val rightText: String
    val rightIcon: ImageVector
    val rightOnClick: () -> Unit
    val rightBtnColor: Color

    when (state.activeTab) {
        TimerTab.TIMER -> {
            isRunning = state.isCountdownRunning
            leftText = "Reset"
            leftIcon = Icons.Default.Refresh
            leftOnClick = { viewModel.resetCountdown() }

            rightText = if (isRunning) "Pause" else if (state.countdownRemaining < state.countdownSeconds) "Resume" else "Start"
            rightIcon = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
            rightOnClick = { if (isRunning) viewModel.pauseCountdown() else viewModel.startCountdown() }
            rightBtnColor = if (isRunning) Error else if (rightText == "Resume") ElectricBlue else Green
        }
        TimerTab.STOPWATCH -> {
            isRunning = state.isStopwatchRunning
            leftText = if (isRunning) "Lap" else "Reset"
            leftIcon = if (isRunning) Icons.Default.FlagCircle else Icons.Default.Refresh
            leftOnClick = { if (isRunning) viewModel.recordLap() else viewModel.resetStopwatch() }

            rightText = if (isRunning) {
                "Pause"
            } else if (state.stopwatchMillis > 0L) {
                "Resume"
            } else {
                "Start"
            }
            rightIcon = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
            rightOnClick = { if (isRunning) viewModel.pauseStopwatch() else viewModel.startStopwatch() }
            rightBtnColor = if (isRunning) Error else if (rightText == "Resume") ElectricBlue else Green
        }
        TimerTab.METRONOME -> {
            isRunning = state.isMetronomeRunning
            leftText = "Reset"
            leftIcon = Icons.Default.Refresh
            leftOnClick = { viewModel.setMetronomeBpm(60) }

            rightText = if (isRunning) "Pause" else "Start"
            rightIcon = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
            rightOnClick = { viewModel.toggleMetronome() }
            rightBtnColor = if (isRunning) Error else Green
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Spacer for centering single button in Metronome
        if (state.activeTab == TimerTab.METRONOME) {
            Spacer(modifier = Modifier.weight(0.25f))
        }

        // Left Button (Reset / Lap)
        if (state.activeTab != TimerTab.METRONOME) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SolidSlate)
                    .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = leftOnClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = leftIcon,
                        contentDescription = leftText,
                        modifier = Modifier.size(20.dp),
                        tint = CoolGray
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = leftText,
                        style = MaterialTheme.typography.titleMedium,
                        color = CoolGray
                    )
                }
            }
        }

        // Right Button (Start / Pause / Resume)
        Box(
            modifier = Modifier
                .weight(if (state.activeTab == TimerTab.METRONOME) 0.5f else 1f)
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(rightBtnColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = rightOnClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = rightIcon,
                    contentDescription = rightText,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = rightText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Right Spacer for centering single button in Metronome
        if (state.activeTab == TimerTab.METRONOME) {
            Spacer(modifier = Modifier.weight(0.25f))
        }
    }
}
