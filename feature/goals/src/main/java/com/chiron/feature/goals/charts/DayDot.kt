package com.chiron.feature.goals

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.ThinOutline

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
