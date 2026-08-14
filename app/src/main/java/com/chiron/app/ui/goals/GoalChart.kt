package com.chiron.app.ui.goals

import androidx.compose.animation.core.animateFloatAsState
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
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.ThinOutline

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
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "goalDonut"
    )
    // Capture theme colors in composable context (the Canvas draw scope is not @Composable).
    val outlineColor = ThinOutline
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            drawArc(
                color = outlineColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/** Small circle for the Sun–Sat day strip: filled [accentColor] when done, hollow ThinOutline ring otherwise. */
@Composable
fun DayDot(
    done: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = ElectricBlue
) {
    val outlineColor = ThinOutline
    Canvas(modifier = modifier) {
        if (done) {
            drawCircle(color = accentColor)
        } else {
            drawCircle(
                color = outlineColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
