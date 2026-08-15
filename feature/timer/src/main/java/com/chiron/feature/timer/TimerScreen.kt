package com.chiron.feature.timer

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
import androidx.compose.material.icons.filled.FlagCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
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
import com.chiron.core.ui.components.WheelPicker
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.feature.timer.TimerTab
import com.chiron.feature.timer.TimerViewModel

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
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Custom inline tab strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SolidSlate)
                .border(1.dp, com.chiron.core.ui.theme.ThinOutline, RoundedCornerShape(8.dp))
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
                            style = MaterialTheme.typography.titleMedium.copy(shadow = null),
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) ElectricBlue else com.chiron.core.ui.theme.CoolGray
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

        // Content Box taking up the middle area and centering items vertically
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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

        // Shared buttons at the bottom (aligned & sized consistently, keeping them as low as possible)
        val isRunning: Boolean
        val leftText: String
        val leftIcon: androidx.compose.ui.graphics.vector.ImageVector
        val leftOnClick: () -> Unit

        val rightText: String
        val rightIcon: androidx.compose.ui.graphics.vector.ImageVector
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
                rightBtnColor = if (isRunning) com.chiron.core.ui.theme.Error else if (rightText == "Resume") ElectricBlue else com.chiron.core.ui.theme.Green
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
                rightBtnColor = if (isRunning) com.chiron.core.ui.theme.Error else if (rightText == "Resume") ElectricBlue else com.chiron.core.ui.theme.Green
            }
            TimerTab.METRONOME -> {
                isRunning = state.isMetronomeRunning
                leftText = "Reset"
                leftIcon = Icons.Default.Refresh
                leftOnClick = { viewModel.setMetronomeBpm(60) }

                rightText = if (isRunning) "Pause" else "Start"
                rightIcon = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                rightOnClick = { viewModel.toggleMetronome() }
                rightBtnColor = if (isRunning) com.chiron.core.ui.theme.Error else com.chiron.core.ui.theme.Green
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
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
                        .border(1.dp, com.chiron.core.ui.theme.ThinOutline, RoundedCornerShape(8.dp))
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
                            tint = com.chiron.core.ui.theme.CoolGray
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = leftText,
                            style = MaterialTheme.typography.titleMedium,
                            color = com.chiron.core.ui.theme.CoolGray
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
}

@Composable
fun CountdownContent(viewModel: TimerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val isIdle = !state.isCountdownRunning && state.countdownRemaining == state.countdownSeconds

    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(320.dp)
        ) {
            val arcTrackColor = com.chiron.core.ui.theme.ThinOutline
            val progressColor = com.chiron.core.ui.theme.ElectricBlue

            val rawProgress = if (state.countdownSeconds > 0) {
                state.countdownRemaining.toFloat() / state.countdownSeconds.toFloat()
            } else 1f

            val animatedProgress by animateFloatAsState(
                targetValue = rawProgress,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
                label = "progress"
            )

            val infiniteTransition = rememberInfiniteTransition(label = "circlePulse")
            val pulseWidthAnimated by infiniteTransition.animateFloat(
                initialValue = 4f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "width"
            )
            val strokeWidthProgressDp = if (state.isCountdownRunning) pulseWidthAnimated.dp else 5.dp

            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val strokeWidthTrack = 2.dp.toPx()
                val strokeWidthProgress = strokeWidthProgressDp.toPx()
                
                // Track (thin and semi-transparent)
                drawArc(
                    color = arcTrackColor.copy(alpha = 0.5f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthTrack, cap = StrokeCap.Round)
                )
                
                // Progress
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidthProgress, cap = StrokeCap.Round)
                )
            }

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
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScaleAnimated by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                val pulseAlphaAnimated by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                val pulseScale = if (state.isCountdownRunning) pulseScaleAnimated else 1f
                val pulseAlpha = if (state.isCountdownRunning) pulseAlphaAnimated else 1f

                Text(
                    text = TimerViewModel.formatCountdown(state.countdownRemaining),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-1).sp
                    ),
                    modifier = Modifier
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
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
                color = com.chiron.core.ui.theme.CoolGray
            )
        }
    }
}
