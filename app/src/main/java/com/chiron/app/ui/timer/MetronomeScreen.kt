package com.chiron.app.ui.timer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.app.ui.components.WheelPicker
import com.chiron.app.ui.theme.CoolGray
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.Error
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.ThinOutline
import com.chiron.app.viewmodel.TimerViewModel
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MetronomeContent(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pendulumAngle = remember { Animatable(0f) }
    val availableTicks = remember {
        (context.assets.list("audio")
            ?.filter { it.endsWith(".mp3", ignoreCase = true) }
            ?.sortedBy { it.lowercase() }
            ?: listOf("Tick1.mp3", "Tick2.mp3", "Tick3.mp3"))
    }

    LaunchedEffect(state.isMetronomeRunning, state.metronomeBpm) {
        if (!state.isMetronomeRunning) {
            pendulumAngle.snapTo(0f)
            return@LaunchedEffect
        }
        val durationMs = (60_000 / state.metronomeBpm.coerceAtLeast(1)).toInt()
        var target = 45f
        // Start from one side to swing back and forth
        pendulumAngle.snapTo(-target)
        while (isActive && state.isMetronomeRunning) {
            pendulumAngle.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = FastOutSlowInEasing
                )
            )
            target = -target
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopMetronome() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Circular pendulum indicator (Compact: 180.dp) ──────────────────────────────
        val trackColor = ThinOutline
        val pendulumColor = ElectricBlue
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = size.minDimension / 2f - 12f

                // Track arc (semi-circle bottom)
                drawArc(
                    color = trackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = Offset(cx - radius, cy - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )

                // Pendulum line
                val angleRad = Math.toRadians(pendulumAngle.value.toDouble())
                val pendulumLen = radius * 0.85f
                val endX = cx + (pendulumLen * sin(angleRad)).toFloat()
                val endY = cy + (pendulumLen * cos(angleRad)).toFloat()

                drawLine(
                    color = pendulumColor,
                    start = Offset(cx, cy),
                    end = Offset(endX, endY),
                    strokeWidth = 3.dp.toPx()
                )

                // Pendulum bob
                drawCircle(
                    color = pendulumColor,
                    radius = 8.dp.toPx(),
                    center = Offset(endX, endY)
                )
            }
        }

        // BPM Wheel picker (Compact height & text)
        WheelPicker(
            count = 281,
            value = (state.metronomeBpm - 20).coerceIn(0, 280),
            onValueChange = { index -> viewModel.setMetronomeBpm(index + 20) },
            visibleCount = 3,
            itemHeight = 56.dp,
            wheelWidth = 200.dp,
            animateExternalChanges = false,
            textStyle = MaterialTheme.typography.displayLarge.copy(
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold
            ),
            format = { index -> "${index + 20}" }
        )

        // Flat tick sound selector (Horizontal Segmented Selector)
        Row(
            modifier = Modifier
                .width(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SolidSlate)
                .border(1.dp, ThinOutline, RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            availableTicks.forEachIndexed { idx, tick ->
                val isSelected = state.metronomeTickAsset == tick
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(if (isSelected) ElectricBlue else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.setMetronomeTickAsset(tick) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${idx + 1}",
                        style = MaterialTheme.typography.titleMedium.copy(shadow = null),
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else CoolGray
                    )
                }
            }
        }
    }
}
