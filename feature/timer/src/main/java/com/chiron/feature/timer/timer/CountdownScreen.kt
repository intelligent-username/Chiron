package com.chiron.feature.timer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.ui.components.WheelPicker
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.ThinOutline

/**
 * Renders the countdown timer display: circular progress ring, wheel pickers when idle,
 * pulsing digital countdown when active, and constant cycling checkbox.
 */
@Composable
fun CountdownContent(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isIdle = !state.isCountdownRunning && state.countdownRemaining == state.countdownSeconds

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(320.dp)
        ) {
            val arcTrackColor = ThinOutline
            val progressColor = ElectricBlue

            val rawProgress = if (state.countdownSeconds > 0) {
                state.countdownRemaining.toFloat() / state.countdownSeconds.toFloat()
            } else 1f

            val animatedProgress by animateFloatAsState(
                targetValue = rawProgress,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
                label = "progress"
            )

            val strokeWidthProgressDp = if (state.isCountdownRunning) {
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
                pulseWidthAnimated.dp
            } else {
                5.dp
            }

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
                PulsingCountdownText(
                    countdownRemaining = state.countdownRemaining,
                    isRunning = state.isCountdownRunning
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
                color = CoolGray
            )
        }
    }
}

@Composable
private fun PulsingCountdownText(
    countdownRemaining: Int,
    isRunning: Boolean
) {
    val text = remember(countdownRemaining) { TimerViewModel.formatCountdown(countdownRemaining) }

    if (isRunning) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        Text(
            text = text,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-1).sp
            ),
            modifier = Modifier.graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha = pulseAlpha
            }
        )
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-1).sp
            )
        )
    }
}
