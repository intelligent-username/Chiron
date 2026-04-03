package com.chiron.app.ui.timer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.MediaPlayer
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
    val context = LocalContext.current

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
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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

        Spacer(modifier = Modifier.weight(1.5f))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (state.activeTab) {
                TimerTab.TIMER -> CountdownContent(viewModel)
                TimerTab.STOPWATCH -> StopwatchContent(viewModel)
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun CountdownContent(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()

    // Use Material 3 Theme colors for guaranteed contrast
    val arcColor = MaterialTheme.colorScheme.primary
    val arcTrackColor = MaterialTheme.colorScheme.surfaceVariant
    
    val isIdle = !state.isCountdownRunning && state.countdownRemaining == state.countdownSeconds

    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(320.dp) // Adjusted size for smaller viewports
        ) {
            // ── The Circle: Always Visible ───────────────────────────────
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val strokeWidth = 14.dp.toPx() // Slightly thicker stroke for the larger circle
                val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                
                // Track
                drawArc(
                    color = arcTrackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                
                // Progress - Full when idle, counting down when active
                val progress = if (state.countdownSeconds > 0) {
                    state.countdownRemaining.toFloat() / state.countdownSeconds.toFloat()
                } else 1f
                
                drawArc(
                    color = arcColor,
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
                        fontSize = 80.sp, // Slightly larger font for larger circle
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
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
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Constant Cycling", 
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── High Contrast Buttons ────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset - Now on the Left, equal weight
            FilledTonalButton(
                onClick = { viewModel.resetCountdown() },
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Reset", style = MaterialTheme.typography.titleMedium)
            }

            // Start / Pause - Now on the Right, equal weight
            val buttonColor = if (state.isCountdownRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            val onButtonColor = if (state.isCountdownRunning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

            Button(
                onClick = {
                    if (state.isCountdownRunning) viewModel.pauseCountdown() else viewModel.startCountdown()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = onButtonColor
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = if (state.isCountdownRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.isCountdownRunning) "Pause" else "Start",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
