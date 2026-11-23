package com.chiron.feature.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
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
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val WeightColor = Color(0xFF42A5F5)
private val CalorieColor = Color(0xFFFFA726)
private val TargetLineColor = Color(0xFFEF5350)
private val TooltipDateFormatter = DateTimeFormatter.ofPattern("EEEE MMM d")
private val TooltipDashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
private val TooltipCornerRadius = CornerRadius(8f, 8f)

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
    showCalorieGoal: Boolean = true
) {
    var overlay by rememberSaveable { mutableStateOf(false) }
    var lag by rememberSaveable { mutableIntStateOf(0) }
    var explanation by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var draftLag by remember { mutableIntStateOf(0) }
    val points = remember(history.entries, state.entries, end, weeks, lag) {
        CalorieGraphCalculator.build(history.entries, state.entries, end, weeks, lag)
    }
    var selected by remember(points, overlay) { mutableIntStateOf(-1) }
    val weights = remember(points, state.displayInKg) {
        points.map { point -> point.weightLbs?.let { if (state.displayInKg) UnitConversion.lbsToKg(it) else it } }
    }
    val validWeights = weights.filterNotNull()
    val showWeightAxis = overlay && validWeights.isNotEmpty()
    val weightMin = validWeights.minOrNull() ?: 0.0
    val weightMax = (validWeights.maxOrNull() ?: weightMin).coerceAtLeast(weightMin + if (state.displayInKg) 0.5 else 1.0)

    // Missing or zero-calorie days should not determine the visible calorie range.
    val calorieValuesForScale = points.map { it.calories }.filter { it > 0.0 }
    val calorieMin = calorieValuesForScale.minOrNull()?.let { floor(it / 500.0) * 500.0 } ?: 0.0
    val calorieMax = (ceil((calorieValuesForScale.maxOrNull() ?: 0.0) / 500.0) * 500.0)
        .coerceAtLeast(calorieMin + 500.0)

    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val tooltipGuide = CoolGray
    val tooltipBackground = SolidSlate
    val tooltipBorder = ThinOutline
    val axisStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp, fontFamily = MonospaceFamily)
    val calorieTicks = remember(calorieMin, calorieMax, axisStyle, measurer) {
        (0..4).map { measurer.measure("%.0f".format(calorieMin + (calorieMax - calorieMin) * it / 4), axisStyle) }
    }
    val weightTicks = remember(weightMin, weightMax, axisStyle, measurer) {
        (0..4).map { measurer.measure("%.1f".format(weightMin + (weightMax - weightMin) * it / 4), axisStyle) }
    }
    val leftWidth = calorieTicks.maxOf { it.size.width }.toFloat()
    val rightWidth = if (showWeightAxis) weightTicks.maxOf { it.size.width }.toFloat() else 0f

    // Daily target for the target line
    val dailyTarget = history.targetFor(end)?.calories

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Calorie trend", style = MaterialTheme.typography.titleMedium)
                Text("Touch chart to inspect", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatCalorieNumber(points.last().calories)} kcal", fontFamily = MonospaceFamily,
                    style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.heightIn(min = 48.dp).toggleable(overlay, role = Role.Checkbox, onValueChange = {
                        if (overlay) { draftLag = lag; settings = true } else explanation = true
                    }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(if (overlay) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null,
                        tint = if (overlay) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text("Weight", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("kcal", color = CalorieColor, style = MaterialTheme.typography.labelSmall)
            if (showWeightAxis) Text(if (state.displayInKg) "kg" else "lbs", color = WeightColor,
                style = MaterialTheme.typography.labelSmall)
        }
        val selectedPoint = points.getOrNull(selected)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .semantics {
                    contentDescription = (selectedPoint ?: points.lastOrNull())?.let {
                        "${it.date}: ${it.calories} calories, ${it.protein} grams protein"
                    } ?: "Calorie graph"
                }
                .pointerInput(points, leftWidth, rightWidth, showWeightAxis) {
                    awaitEachGesture {
                        val left = leftWidth + 12.dp.toPx()
                        val right = rightWidth + 12.dp.toPx()
                        val width = (size.width - left - right).coerceAtLeast(1f)
                        fun select(x: Float) {
                            selected = (((x - left) / width) * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                        }
                        val down = awaitFirstDown(requireUnconsumed = true)
                        down.consume()
                        select(down.position.x)
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                select(change.position.x)
                            }
                        } finally {
                            selected = -1
                        }
                    }
                }
        ) {
            val left = leftWidth + 12.dp.toPx()
            val right = rightWidth + 12.dp.toPx()
            val plotWidth = (size.width - left - right).coerceAtLeast(1f)
            val top = 12.dp.toPx()
            val plotHeight = (size.height - top - 24.dp.toPx()).coerceAtLeast(1f)
            fun x(index: Int) = left + plotWidth * index / (points.size - 1).coerceAtLeast(1)
            fun y(value: Double, min: Double, max: Double) =
                top + plotHeight * (1f - ((value - min) / (max - min)).toFloat())

            // Grid lines and axis labels
            for (tick in 0..4) {
                val py = top + plotHeight * (1f - tick / 4f)
                drawLine(colors.outline.copy(alpha = 0.6f), Offset(left, py), Offset(left + plotWidth, py), 1.dp.toPx())
                val label = calorieTicks[tick]
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), py - label.size.height / 2f))
                if (showWeightAxis) {
                    val weightLabel = weightTicks[tick]
                    drawText(weightLabel, topLeft = Offset(left + plotWidth + 6.dp.toPx(), py - weightLabel.size.height / 2f))
                }
            }

            // Daily target line (dotted red)
            dailyTarget?.takeIf { showCalorieGoal }?.let { target ->
                val targetY = y(target.toDouble(), calorieMin, calorieMax)
                if (targetY in top..(top + plotHeight)) {
                val dashPath = Path().apply {
                    moveTo(left, targetY)
                    lineTo(left + plotWidth, targetY)
                }
                drawPath(
                    dashPath,
                    TargetLineColor,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                )
                val targetLabel = measurer.measure("Target ${formatCalorieNumber(target)}", axisStyle)
                drawText(targetLabel, topLeft = Offset(left - targetLabel.size.width - 6.dp.toPx(), targetY - targetLabel.size.height / 2f))
                }
            }

            fun series(values: List<Double?>, min: Double, max: Double, color: Color, actualOnly: Boolean) {
                val path = Path()
                var run = mutableListOf<Int>()
                fun flushRun() {
                    if (run.isEmpty()) return
                    val xs = run.map { x(it) }
                    val ys = run.map { y(values[it]!!, min, max) }
                    path.moveTo(xs[0], ys[0])
                    fun tangent(i: Int): Float {
                        if (i == 0 || i == run.size - 1) return 0f
                        val before = ys[i] - ys[i - 1]
                        val after = ys[i + 1] - ys[i]
                        return if (before * after <= 0f) 0f else 2f * before * after / (before + after)
                    }
                    for (i in 1 until run.size) {
                        val third = (xs[i] - xs[i - 1]) / 3f
                        path.cubicTo(xs[i - 1] + third, ys[i - 1] + tangent(i - 1) / 3f,
                            xs[i] - third, ys[i] - tangent(i) / 3f, xs[i], ys[i])
                    }
                    run = mutableListOf()
                }
                values.forEachIndexed { index, value ->
                    if (value == null) flushRun() else run.add(index)
                }
                flushRun()
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val radius = (plotWidth / points.size / 5f).coerceIn(0.6.dp.toPx(), if (actualOnly) 2.5.dp.toPx() else 1.25.dp.toPx())
                values.forEachIndexed { index, value ->
                    if (value != null && (!actualOnly || points[index].actualWeight)) {
                        drawCircle(color, radius, Offset(x(index), y(value, min, max)))
                    }
                }
            }

            clipRect(left, top, left + plotWidth, top + plotHeight) {
                if (showWeightAxis) series(weights, weightMin, weightMax, WeightColor, true)
                series(points.map { it.calories }, calorieMin, calorieMax, CalorieColor, false)
            }

            val dateStyle = axisStyle.copy(fontSize = 9.sp)
            listOf(0, points.lastIndex).forEach { index ->
                val label = measurer.measure(points[index].date.toString(), dateStyle)
                drawText(label, topLeft = Offset((x(index) - label.size.width / 2f).coerceIn(0f,
                    (size.width - label.size.width).coerceAtLeast(0f)), top + plotHeight + 6.dp.toPx()))
            }

            // Selected point tooltip (same style as weight tab)
            points.getOrNull(selected)?.let { point ->
                val px = x(selected)
                val py = y(point.calories, calorieMin, calorieMax).coerceIn(top, top + plotHeight)

                // Dashed vertical guide line
                drawLine(
                    color = tooltipGuide.copy(alpha = 0.5f),
                    start = Offset(px, top),
                    end = Offset(px, top + plotHeight),
                    strokeWidth = 1f,
                    pathEffect = TooltipDashEffect
                )
                // Highlight circles
                if (point.calories >= calorieMin) {
                    drawCircle(color = CalorieColor, radius = 6.5f, center = Offset(px, py))
                    drawCircle(color = Color.White, radius = 3f, center = Offset(px, py))
                }
                // Weight overlay point if applicable
                if (showWeightAxis && weights[selected] != null) {
                    val weightPy = y(weights[selected]!!, weightMin, weightMax)
                    drawCircle(color = WeightColor, radius = 6.5f, center = Offset(px, weightPy))
                    drawCircle(color = Color.White, radius = 3f, center = Offset(px, weightPy))
                }

                val text = buildString {
                    append("${point.date.format(TooltipDateFormatter)}\n${formatCalorieNumber(point.calories)} kcal · ${formatCalorieNumber(point.protein)} g protein")
                    if (showWeightAxis && weights[selected] != null) {
                        append("\n${formatCalorieNumber(weights[selected]!!)} ${if (state.displayInKg) "kg" else "lbs"} · ${point.weightDate}")
                    }
                }
                val layout = measurer.measure(
                    text,
                    TextStyle(color = colors.onSurface, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontFamily = MonospaceFamily)
                )
                val tw = layout.size.width.toFloat()
                val th = layout.size.height.toFloat()
                val pad = 10f

                val tx = (px - tw / 2f).coerceIn(pad, (size.width - pad - tw).coerceAtLeast(pad))
                val ty = (if (py - th - pad * 2 - 12f >= 0f) {
                    py - th - pad - 12f
                } else {
                    py + pad + 12f
                }).coerceIn(pad, (size.height - th - pad).coerceAtLeast(pad))

                drawRoundRect(tooltipBackground, Offset(tx - pad, ty - pad), Size(tw + pad * 2, th + pad * 2), TooltipCornerRadius)
                drawRoundRect(tooltipBorder, Offset(tx - pad, ty - pad), Size(tw + pad * 2, th + pad * 2), TooltipCornerRadius, style = Stroke(1f))
                drawText(layout, topLeft = Offset(tx, ty))
            }
        }
        if (overlay && !showWeightAxis) Text("No weight history to overlay", style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
    }
    if (explanation) {
        AlertDialog(onDismissRequest = { explanation = false }, title = { Text("Overlay weight") },
            text = { Text("Show weight on this calorie chart using a separate scale on the right. Missing weight readings use the same estimates as the Weight tab. Tap Weight again to set a lag from −3 to +3 days. Negative lag uses earlier weight dates; positive lag uses later dates.") },
            confirmButton = { Button(onClick = { overlay = true; explanation = false }) { Text("Enable") } },
            dismissButton = { TextButton(onClick = { explanation = false }) { Text("Cancel") } })
    }
    if (settings) {
        val preview = remember(history.entries, state.entries, end, weeks, draftLag) {
            CalorieGraphCalculator.build(history.entries, state.entries, end, weeks, draftLag)
        }
        val pairs = preview.mapNotNull { p -> p.weightLbs?.let { p.calories to it } }
        val r = correlation(pairs)
        AlertDialog(onDismissRequest = { settings = false }, title = { Text("Weight comparison") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Lag: ${if (draftLag > 0) "+" else ""}$draftLag days", fontFamily = MonospaceFamily)
                    Slider(value = draftLag.toFloat(), onValueChange = { draftLag = it.roundToInt().coerceIn(-3, 3) },
                        valueRange = -3f..3f, steps = 5)
                    Text(if (r == null) "Not enough variation/data · ${pairs.size} pairs"
                        else "Correlation ${"%.2f".format(r)} · ${pairs.size} pairs",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { overlay = false; settings = false }) { Text("Disable weight") }
                }
            },
            confirmButton = { Button(onClick = { lag = draftLag; settings = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { settings = false }) { Text("Cancel") } })
    }
}
