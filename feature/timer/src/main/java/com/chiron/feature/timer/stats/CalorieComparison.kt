package com.chiron.feature.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.CalorieHistory
import com.chiron.core.common.UnitConversion
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val WeightColor = Color(0xff42a5f5)
private val CalorieColor = Color(0xffffa726)

internal fun correlation(pairs: List<Pair<Double, Double>>): Double? {
    if (pairs.size < 2) return null
    val xMean = pairs.map { it.first }.average()
    val yMean = pairs.map { it.second }.average()
    var xx = 0.0
    var yy = 0.0
    var xy = 0.0
    pairs.forEach { (x, y) ->
        val dx = x - xMean
        val dy = y - yMean
        xx += dx * dx
        yy += dy * dy
        xy += dx * dy
    }
    return if (xx <= 0.0 || yy <= 0.0) null else (xy / sqrt(xx * yy)).coerceIn(-1.0, 1.0)
}

@Composable
internal fun CalorieComparison(
    history: CalorieHistory,
    state: BodyweightUiState,
    end: LocalDate,
    weeks: Int,
    primaryCalories: Boolean
) {
    var overlay by remember(primaryCalories) { mutableStateOf(false) }
    var lag by remember(primaryCalories) { mutableStateOf(0) }
    val count = weeks.coerceIn(1, 520) * 7
    val start = end.minusDays((count - 1).toLong())
    val dates = remember(start, count) { (0 until count).map { start.plusDays(it.toLong()) } }
    val totals = remember(history.entries) { history.entries.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.calories } } }
    val prepared = remember(state.entries) { BodyweightPointCalculator.prepare(state.entries) }
    val weights = remember(prepared, end, weeks) {
        BodyweightPointCalculator.buildPoints(end.plusDays(3), weeks + 1, prepared).associateBy { it.date }
    }
    val calories = dates.map { totals[if (!primaryCalories && overlay) it.plusDays(lag.toLong()) else it] ?: 0.0 }
    val weightPoints = dates.map { weights[if (primaryCalories && overlay) it.plusDays(lag.toLong()) else it] }
    val weight = weightPoints.map { point ->
        point?.weightLbs?.let { if (state.displayInKg) UnitConversion.lbsToKg(it) else it }
    }
    val pairs = calories.zip(weight).mapNotNull { (c, w) -> w?.let { c to it } }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (primaryCalories || overlay) {
            val calorieMin = 0.0
            val calorieMax = (calories.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val validWeights = weight.filterNotNull()
            val weightMin = validWeights.minOrNull() ?: 0.0
            val weightMax = (validWeights.maxOrNull() ?: 0.0).let { if (it <= weightMin) weightMin + 1.0 else it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Calories · kcal", color = CalorieColor, style = MaterialTheme.typography.labelSmall)
                if (overlay || !primaryCalories) {
                    Text("Weight · ${if (state.displayInKg) "kg" else "lbs"}", color = WeightColor,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Canvas(Modifier.fillMaxWidth().height(212.dp).semantics {
                contentDescription = "Daily calorie trend from $start to $end${if (overlay) ", with weight comparison" else ""}"
            }) {
                val left = 40.dp.toPx()
                val right = if (overlay || !primaryCalories) 40.dp.toPx() else 8.dp.toPx()
                val width = (size.width - left - right).coerceAtLeast(1f)
                val height = (size.height - 8.dp.toPx()).coerceAtLeast(1f)
                for (tick in 0..4) {
                    val fraction = tick / 4f
                    val py = 4.dp.toPx() + height * (1f - fraction)
                    drawLine(gridColor, Offset(left, py), Offset(left + width, py), strokeWidth = 1.dp.toPx())
                    val label = textMeasurer.measure("%.0f".format(calorieMax * fraction),
                        TextStyle(fontSize = 10.sp, color = labelColor))
                    drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(),
                        (py - label.size.height / 2).coerceIn(0f, (size.height - label.size.height).coerceAtLeast(0f))))
                    if (overlay || !primaryCalories) {
                        val rightLabel = textMeasurer.measure("%.1f".format(weightMin + (weightMax - weightMin) * fraction),
                            TextStyle(fontSize = 10.sp, color = labelColor))
                        drawText(rightLabel, topLeft = Offset(left + width + 6.dp.toPx(),
                            (py - rightLabel.size.height / 2).coerceIn(0f, (size.height - rightLabel.size.height).coerceAtLeast(0f))))
                    }
                }
                fun x(i: Int) = left + width * i / (count - 1).coerceAtLeast(1)
                fun y(value: Double, min: Double, max: Double) =
                    4.dp.toPx() + height * (1f - ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f))
                fun series(values: List<Double?>, min: Double, max: Double, color: Color, actualOnly: Boolean) {
                    val path = Path()
                    var started = false
                    values.forEachIndexed { index, value ->
                        if (value == null) { started = false; return@forEachIndexed }
                        val px = x(index)
                        val py = y(value, min, max)
                        if (started) path.lineTo(px, py) else { path.moveTo(px, py); started = true }
                    }
                    drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
                    val radius = (width / count / 5f).coerceIn(0.7.dp.toPx(), if (actualOnly) 2.5.dp.toPx() else 1.4.dp.toPx())
                    values.forEachIndexed { index, value ->
                        if (value != null && (!actualOnly || weightPoints[index]?.isActualInput == true)) {
                            drawCircle(color, radius, Offset(x(index), y(value, min, max)))
                        }
                    }
                }
                if (primaryCalories || overlay) series(calories, calorieMin, calorieMax, CalorieColor, false)
                if (!primaryCalories || overlay) series(weight, weightMin, weightMax, WeightColor, true)
            }
            Text("$start – $end", style = MaterialTheme.typography.bodySmall, color = labelColor)
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = overlay, role = Role.Checkbox, onValueChange = { overlay = it }
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = overlay, onCheckedChange = null)
            Text(if (primaryCalories) "Graph Weight" else "Graph Calories", style = MaterialTheme.typography.bodyMedium)
        }
        AnimatedVisibility(visible = overlay, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${if (primaryCalories) "Weight" else "Calories"} lag · $lag days", style = MaterialTheme.typography.labelMedium)
                Slider(value = lag.toFloat(), onValueChange = { lag = it.roundToInt() }, valueRange = 0f..3f, steps = 2)
                Text("Compare ${if (primaryCalories) "calories with weight" else "weight with calories"} $lag days later",
                    style = MaterialTheme.typography.bodySmall, color = labelColor)
                val r = correlation(pairs)
                Text(if (r == null) "Not enough variation/data · ${pairs.size} pairs"
                    else "Correlation ${"%.2f".format(r)} · ${pairs.size} pairs",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Correlation does not imply causation", style = MaterialTheme.typography.bodySmall, color = labelColor)
            }
        }
    }
}
