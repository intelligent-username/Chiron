package com.chiron.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.ui.components.WeekNavigator
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun VolumeContent(
    state: VolumeUiState,
    displayInKg: Boolean,
    onModeChange: (VolumeMode) -> Unit,
    onWeekCountChange: (Int) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToggleAbridgeGaps: () -> Unit,
    onPointTap: (VolumePoint) -> Unit = {},
    scrollable: Boolean = true
) {
    val unit = if (displayInKg) "kg" else "lbs"
    val weekLabel = remember(state.currentWeekStart, state.mode, state.weekCount) {
        if (state.mode == VolumeMode.BY_DAY) {
            val end = state.currentWeekStart.plusDays(6)
            val fmt = DateTimeFormatter.ofPattern("MMM d")
            "${state.currentWeekStart.format(fmt)} – ${end.format(fmt)}"
        } else {
            val start = state.currentWeekStart.minusWeeks((state.weekCount - 1).toLong())
            val end = state.currentWeekStart.plusDays(6)
            val fmt = DateTimeFormatter.ofPattern("MMM d")
            "${start.format(fmt)} – ${end.format(fmt)}"
        }
    }

    val totalVolume = state.points.sumOf { it.volumeLbs }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── Mode Tabs ─────────────────────────────────────────────────────────
        VolumeModeSelector(
            selected = state.mode,
            onSelect = onModeChange
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleAbridgeGaps() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Checkbox(
                checked = state.abridgeGaps,
                onCheckedChange = { onToggleAbridgeGaps() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = Color(0xFF8B949E),
                    checkmarkColor = Color.Black
                ),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abridge Gaps", fontSize = 14.sp, color = Color(0xFF8B949E))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Graph card ────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Total Volume in top right
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${totalVolume.formatVolume(displayInKg)} $unit",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }

                if (state.points.isEmpty() || state.points.all { it.volumeLbs == 0.0 }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No volume data\nfor this period",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    VolumeLineGraph(
                        points = state.points,
                        displayInKg = displayInKg,
                        mode = state.mode,
                        onPointTap = onPointTap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }

                if (state.mode == VolumeMode.BY_WEEK) {
                    val maxWeeks = state.maxWeekCount.coerceAtLeast(2)
                    val currentWeekVal = state.weekCount.toFloat().coerceIn(2f, maxWeeks.toFloat())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Range: ${state.weekCount} weeks (${state.weekCount * 7} days)",
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp
                        )
                    }
                    Slider(
                        value = currentWeekVal,
                        onValueChange = { onWeekCountChange(it.roundToInt()) },
                        valueRange = 2f..maxWeeks.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        WeekNavigator(
            weekLabel = weekLabel,
            canGoPrev = !state.isAtFirstWeek,
            canGoNext = !state.isAtCurrentWeek,
            onPrev = onPrevWeek,
            onNext = onNextWeek
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        VolumeStatsSection(stats = state.stats, displayInKg = displayInKg)
        Spacer(modifier = Modifier.height(120.dp))
    }
}
