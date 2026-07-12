package com.chiron.app.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlagCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.app.ui.theme.CoolGray
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.Error
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.ThinOutline
import com.chiron.app.viewmodel.TimerViewModel

@Composable
fun StopwatchContent(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()

    val isRunning = state.isStopwatchRunning
    val hasTime = state.stopwatchMillis > 0

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // ── Monospace time display with gray milliseconds ────────────────
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
                        color = CoolGray
                    ),
                    modifier = Modifier.alignByBaseline().padding(start = 2.dp),
                    maxLines = 1,
                    softWrap = false
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
                    color = ElectricBlue,
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
                        .heightIn(max = (maxLaps * 44).dp)
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

                        if (index < reversedLaps.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = ThinOutline,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dot indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isFastest -> Color(0xFF4CAF50)
                        isSlowest -> Error
                        else -> Color.Transparent
                    }
                )
        )

        Spacer(Modifier.width(8.dp))

        // Lap number
        Text(
            text = "$lapNumber",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CoolGray,
            modifier = Modifier.width(20.dp)
        )

        // Timestamp
        Text(
            text = lapTimestamp,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        )

        // Duration
        Text(
            text = "+$lapDuration",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = when {
                isFastest -> Color(0xFF4CAF50)
                isSlowest -> Error
                else -> CoolGray
            }
        )
    }
}
