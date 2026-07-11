package com.chiron.app.ui.timer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.MediaPlayer
import com.chiron.app.ui.components.WheelPicker
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.viewmodel.TimerTab
import com.chiron.app.viewmodel.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.timerFinished.collect {
            val resId = context.resources.getIdentifier("beep", "raw", context.packageName)
            if (resId != 0) {
                val mediaPlayer = MediaPlayer.create(context, resId)
                mediaPlayer?.setOnCompletionListener { it.release() }
                mediaPlayer?.start()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Custom inline tab strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SolidSlate)
                .border(1.dp, com.chiron.app.ui.theme.ThinOutline, RoundedCornerShape(8.dp))
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TimerTab.entries.forEach { tab ->
                    val isSelected = state.activeTab == tab
                    val label = when (tab) {
                        TimerTab.TIMER -> "Timer"
                        TimerTab.STOPWATCH -> "Stopwatch"
                        TimerTab.METRONOME -> "Metronome"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.selectTab(tab) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) ElectricBlue else com.chiron.app.ui.theme.CoolGray
                        )
                        // Underline indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .width(36.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(if (isSelected) ElectricBlue else Color.Transparent)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1.5f))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (state.activeTab) {
                TimerTab.TIMER -> CountdownContent(viewModel)
                TimerTab.STOPWATCH -> StopwatchContent(viewModel)
                TimerTab.METRONOME -> MetronomeContent(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun CountdownContent(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()

    val arcTrackColor = com.chiron.app.ui.theme.ThinOutline
    val progressColor = com.chiron.app.ui.theme.ElectricBlue
    val isIdle = !state.isCountdownRunning && state.countdownRemaining == state.countdownSeconds

    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(320.dp)
        ) {
            // ── Thin progress arc ────────────────────────────────────────
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val strokeWidth = 3.dp.toPx()
                val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                
                // Track
                drawArc(
                    color = arcTrackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                
                // Progress
                val progress = if (state.countdownSeconds > 0) {
                    state.countdownRemaining.toFloat() / state.countdownSeconds.toFloat()
                } else 1f
                
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = stroke
                )
            }

            // ── Inner Content ────────────────────────────────────────────
            if (isIdle) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    WheelPicker(
                        count = 100,
                        value = state.countdownRemaining / 60,
                        onValueChange = { newMin ->
                            val currentSec = state.countdownRemaining % 60
                            viewModel.setCountdownPreset(newMin * 60 + currentSec)
                        },
                        itemHeight = 80.dp,
                        visibleCount = 3,
                        textStyle = MaterialTheme.typography.displayMedium.copy(fontSize = 54.sp)
                    )

                    Text(
                        ":",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-4).dp)
                    )

                    WheelPicker(
                        count = 60,
                        value = state.countdownRemaining % 60,
                        onValueChange = { newSec ->
                            val currentMin = state.countdownRemaining / 60
                            viewModel.setCountdownPreset(currentMin * 60 + newSec)
                        },
                        itemHeight = 80.dp,
                        visibleCount = 3,
                        textStyle = MaterialTheme.typography.displayMedium.copy(fontSize = 54.sp)
                    )
                }
            } else {
                Text(
                    text = TimerViewModel.formatCountdown(state.countdownRemaining),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-1).sp
                    )
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Checkbox(
                checked = state.isConstantCycling,
                onCheckedChange = { viewModel.toggleConstantCycling() },
                colors = CheckboxDefaults.colors(
                    checkedColor = ElectricBlue,
                    checkmarkColor = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Constant Cycling", 
                style = MaterialTheme.typography.titleMedium,
                color = com.chiron.app.ui.theme.CoolGray
            )
        }

        // ── Flat block buttons ───────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SolidSlate)
                    .border(1.dp, com.chiron.app.ui.theme.ThinOutline, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { viewModel.resetCountdown() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(20.dp), tint = com.chiron.app.ui.theme.CoolGray)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset", style = MaterialTheme.typography.titleMedium, color = com.chiron.app.ui.theme.CoolGray)
                }
            }

            // Start / Pause
            val isRunning = state.isCountdownRunning
            val btnColor = if (isRunning) com.chiron.app.ui.theme.Error else ElectricBlue
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(btnColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (isRunning) viewModel.pauseCountdown() else viewModel.startCountdown()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isRunning) "Pause" else "Start",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
