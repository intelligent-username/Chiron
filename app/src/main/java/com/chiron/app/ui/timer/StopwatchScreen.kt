package com.chiron.app.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlagCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.app.viewmodel.TimerViewModel

@Composable
fun StopwatchContent(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()

    val startColor = Color(0xFF43A047)
    val pauseColor = MaterialTheme.colorScheme.error
    val onPauseColor = MaterialTheme.colorScheme.onError
    val isRunning = state.isStopwatchRunning
    val hasTime = state.stopwatchMillis > 0

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // ── Time display ─────────────────────────────────────────────────
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
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-2).sp
                ),
                modifier = Modifier.alignByBaseline(),
                maxLines = 1,
                softWrap = false
            )
            if (millisPart.isNotEmpty()) {
                Text(
                    text = millisPart,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.alignByBaseline().padding(start = 2.dp),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // ── Buttons ──────────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            // Left: Lap / Reset
            FilledTonalButton(
                onClick = {
                    if (isRunning) viewModel.recordLap() else viewModel.resetStopwatch()
                },
                enabled = isRunning || hasTime,
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.FlagCircle else Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "Lap" else "Reset",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Right: Start / Pause
            Button(
                onClick = {
                    if (isRunning) viewModel.pauseStopwatch() else viewModel.startStopwatch()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) pauseColor else startColor,
                    contentColor = if (isRunning) onPauseColor else Color.White
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "Pause" else "Start",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Laps ─────────────────────────────────────────────────────────
        if (state.laps.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Laps",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val reversedLaps = state.laps.asReversed()
                val maxLaps = 6

                val lapDurations = state.laps.mapIndexed { index, time ->
                    time - if (index == 0) 0L else state.laps[index - 1]
                }
                val minLapIndex = if (lapDurations.size > 1) lapDurations.indices.minByOrNull { lapDurations[it] } else null
                val maxLapIndex = if (lapDurations.size > 1) lapDurations.indices.maxByOrNull { lapDurations[it] } else null

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (maxLaps * 44).dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(reversedLaps) { index, lapTime ->
                        val originalIndex = state.laps.size - 1 - index
                        val lapDuration = lapDurations[originalIndex]
                        val lapNumber = state.laps.size - index
                        val isFastest = originalIndex == minLapIndex
                        val isSlowest = originalIndex == maxLapIndex

                        LapRow(
                            lapNumber = lapNumber,
                            lapDuration = TimerViewModel.formatStopwatch(lapDuration),
                            lapTimestamp = TimerViewModel.formatStopwatch(lapTime),
                            isFastest = isFastest,
                            isSlowest = isSlowest
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LapRow(
    lapNumber: Int,
    lapDuration: String,
    lapTimestamp: String,
    isFastest: Boolean = false,
    isSlowest: Boolean = false
) {
    val durationColor = when {
        isFastest -> Color(0xFF4CAF50)
        isSlowest -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Numbered disc
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$lapNumber",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Timestamp
        Text(
            text = lapTimestamp,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        )

        // Duration
        Text(
            text = "+$lapDuration",
            modifier = Modifier.align(Alignment.CenterEnd),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = durationColor
        )
    }
}
