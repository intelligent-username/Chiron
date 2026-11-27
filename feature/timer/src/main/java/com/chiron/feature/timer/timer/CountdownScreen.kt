package com.chiron.feature.timer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val isIdle = !state.hasCountdownStarted
    val timerTextStyle = MaterialTheme.typography.displayMedium.copy(
        fontSize = 54.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        letterSpacing = (-1).sp
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
        val diameter = minOf(368.dp, maxWidth, maxHeight)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(diameter)
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

            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val strokeWidthTrack = 4.dp.toPx()
                val strokeWidthProgress = 4.dp.toPx()

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

            AnimatedContent(
                targetState = isIdle,
                transitionSpec = {
                    (fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.97f))
                        .togetherWith(fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.97f))
                },
                contentAlignment = Alignment.Center,
                label = "countdownMode"
            ) { idle ->
            if (idle) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    WheelPicker(
                        count = 100,
                        value = state.countdownRemaining / 60,
                        onValueChange = { newMin ->
                            if (isIdle && newMin != state.countdownRemaining / 60) {
                                val currentSec = state.countdownRemaining % 60
                                viewModel.setCountdownPreset(newMin * 60 + currentSec)
                            }
                        },
                        itemHeight = 80.dp,
                        visibleCount = 3,
                        wheelWidth = 96.dp,
                        textStyle = timerTextStyle
                    )

                    Text(
                        ":",
                        style = timerTextStyle,
                        modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-4).dp)
                    )

                    WheelPicker(
                        count = 60,
                        value = state.countdownRemaining % 60,
                        onValueChange = { newSec ->
                            if (isIdle && newSec != state.countdownRemaining % 60) {
                                val currentMin = state.countdownRemaining / 60
                                viewModel.setCountdownPreset(currentMin * 60 + newSec)
                            }
                        },
                        itemHeight = 80.dp,
                        visibleCount = 3,
                        wheelWidth = 96.dp,
                        textStyle = timerTextStyle
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        "%02d".format(state.countdownRemaining / 60),
                        modifier = Modifier.width(96.dp),
                        style = timerTextStyle,
                        textAlign = TextAlign.Center
                    )
                    Text(":", style = timerTextStyle,
                        modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-4).dp))
                    Text(
                        "%02d".format(state.countdownRemaining % 60),
                        modifier = Modifier.width(96.dp),
                        style = timerTextStyle,
                        textAlign = TextAlign.Center
                    )
                }
            }
            }
        }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 16.dp),
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
                "Repeat timer",
                style = MaterialTheme.typography.titleMedium,
                color = CoolGray
            )
        }
    }
}
