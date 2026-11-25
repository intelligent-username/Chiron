package com.chiron.feature.goals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.ThinOutline

/**
 * Stroke-only progress ring. ThinOutline background arc, [accentColor] progress
 * arc, round caps. [progress] is animated on change.
 */
@Composable
fun GoalDonut(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    accentColor: Color = ElectricBlue,
    drawRing: Boolean = true,
    animateProgress: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val target = progress.coerceIn(0f, 1f)
    val displayedColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 180),
        label = "goalDonutColor"
    )
    val displayed = if (animateProgress && drawRing) {
        val animated by animateFloatAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 180, delayMillis = 0),
            label = "goalDonut"
        )
        animated
    } else target
    val outlineColor = ThinOutline
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!drawRing) return@Canvas
            val stroke = strokeWidth.toPx()
            drawArc(
                color = outlineColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = displayedColor,
                startAngle = -90f,
                sweepAngle = 360f * displayed,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}
