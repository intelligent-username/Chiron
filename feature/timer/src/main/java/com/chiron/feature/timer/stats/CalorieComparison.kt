package com.chiron.feature.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.abs
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
    maxWeeks: Int,
    onWeeksChange: (Int) -> Unit,
    showCalorieGoal: Boolean = true,
    modifier: Modifier = Modifier
) {
    var overlay by rememberSaveable { mutableStateOf(false) }
    var lag by rememberSaveable { mutableIntStateOf(0) }
    var explanation by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var draftLag by remember { mutableIntStateOf(0) }
    val points = remember(history.entries, state.entries, end, weeks, lag) {
        CalorieGraphCalculator.build(history.entries, state.entries, end, weeks, lag)
    }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(points) {
        if (points.none { it.date == selectedDate }) selectedDate = null
    }
    val selected = points.indexOfFirst { it.date == selectedDate }
    val weights = remember(points, state.displayInKg) {
        points.map { point -> point.weightLbs?.let { if (state.displayInKg) UnitConversion.lbsToKg(it) else it } }
    }
    val validWeights = weights.filterNotNull()
    val showWeightAxis = overlay && validWeights.isNotEmpty()
    val weightMin = validWeights.minOrNull() ?: 0.0
    val weightMax = (validWeights.maxOrNull() ?: weightMin).coerceAtLeast(weightMin + if (state.displayInKg) 0.5 else 1.0)

    val dailyTarget = history.globalTarget()?.calories
    val calorieValuesForScale = points.map { it.calories }.filter { it > 0.0 } +
        listOfNotNull(dailyTarget.takeIf { showCalorieGoal })
    val calorieMin = calorieValuesForScale.minOrNull()?.let { floor(it / 500.0) * 500.0 } ?: 0.0
    val calorieMax = (ceil((calorieValuesForScale.maxOrNull() ?: 0.0) / 500.0) * 500.0)
        .coerceAtLeast(calorieMin + 500.0)

    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val tooltipGuide = CoolGray
    val tooltipBackground = SolidSlate
    val tooltipBorder = ThinOutline
    val axisStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp, fontFamily = MonospaceFamily)
    val targetCalorieTicks = remember(calorieMin, calorieMax, axisStyle, measurer) {
        (0..4).map { measurer.measure("%.0f".format(calorieMin + (calorieMax - calorieMin) * it / 4), axisStyle) }
    }
    val targetWeightTicks = remember(weightMin, weightMax, axisStyle, measurer) {
        (0..4).map { measurer.measure("%.1f".format(weightMin + (weightMax - weightMin) * it / 4), axisStyle) }
    }
    val target = remember(points, weights, calorieMin, calorieMax, weightMin, weightMax,
        showWeightAxis, targetCalorieTicks, targetWeightTicks) {
        val ids = points.map { it.date.toString() }
        ChartFrame(calorieMin, calorieMax, weightMin, weightMax,
            targetCalorieTicks.maxOf { it.size.width }.toFloat(),
            if (showWeightAxis) targetWeightTicks.maxOf { it.size.width }.toFloat() else 0f,
            if (showWeightAxis) 1f else 0f,
            chartSeries(points.map { it.calories }, ids),
            chartSeries(if (showWeightAxis) weights else emptyList(), ids,
                points.filter { it.actualWeight }.map { it.date.toString() }.toSet()))
    }
    val motion = rememberChartMotion(target, state.displayInKg)
    val latestTarget = points.lastOrNull()?.calories ?: 0.0
    val latestCalories by animateFloatAsState(
        targetValue = latestTarget.toFloat(),
        animationSpec = tween(180), label = "currentCalories")

    StatsChartCard(
        modifier = modifier,
        header = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Calorie trend", style = MaterialTheme.typography.titleMedium)
                Text("Touch chart to inspect", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatCalorieNumber(if (latestCalories == latestTarget.toFloat()) latestTarget else latestCalories.toDouble())} kcal", fontFamily = MonospaceFamily,
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
        },
        units = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("kcal", color = CalorieColor, style = MaterialTheme.typography.labelSmall)
            if (motion.frame.overlayAlpha > 0f) Text(if (state.displayInKg) "kg" else "lbs", color = WeightColor,
                modifier = Modifier.graphicsLayer { alpha = motion.frame.overlayAlpha },
                style = MaterialTheme.typography.labelSmall)
        }
        },
        chart = {
        val selectedPoint = points.getOrNull(selected)
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = (selectedPoint ?: points.lastOrNull())?.let {
                        "${it.date}: ${it.calories} calories, ${it.protein} grams protein"
                    } ?: "Calorie graph"
                }
                .pointerInput(points, motion) {
                    awaitEachGesture {
                        fun select(x: Float) {
                            val frame = motion.frame
                            val left = frame.left + 12.dp.toPx()
                            val right = frame.right + 12.dp.toPx()
                            val width = (size.width - left - right).coerceAtLeast(1f)
                            selectedDate = points.minByOrNull {
                                abs(left + (frame.primary.markers[it.date.toString()]?.x ?: 0f) * width - x)
                            }?.date
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
                            selectedDate = null
                        }
                    }
                }
        ) {
            val frame = motion.frame
            val left = frame.left + 12.dp.toPx()
            val right = frame.right + 12.dp.toPx()
            val plotWidth = (size.width - left - right).coerceAtLeast(1f)
            val top = 12.dp.toPx()
            val plotHeight = (size.height - top - 24.dp.toPx()).coerceAtLeast(1f)
            fun x(index: Int) = left + plotWidth * (frame.primary.markers[points[index].date.toString()]?.x
                ?: (index.toFloat() / (points.size - 1).coerceAtLeast(1)))
            fun y(value: Double, min: Double, max: Double) =
                top + plotHeight * (1f - ((value - min) / (max - min)).toFloat())

            // Grid lines and axis labels
            for (tick in 0..4) {
                val py = top + plotHeight * (1f - tick / 4f)
                drawLine(colors.outline.copy(alpha = 0.6f), Offset(left - 8.dp.toPx(), py), Offset(left + plotWidth + 8.dp.toPx(), py), 1.dp.toPx())
                val label = measurer.measure("%.0f".format(frame.min + (frame.max - frame.min) * tick / 4), axisStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), py - label.size.height / 2f))
                if (frame.overlayAlpha > 0f) {
                    val weightLabel = measurer.measure("%.1f".format(frame.secondaryMin +
                        (frame.secondaryMax - frame.secondaryMin) * tick / 4), axisStyle.copy(color = WeightColor))
                    drawText(weightLabel, topLeft = Offset(left + plotWidth + 14.dp.toPx(), py - weightLabel.size.height / 2f),
                        alpha = frame.overlayAlpha)
                }
            }

            // Daily target line (dotted red)
            dailyTarget?.takeIf { showCalorieGoal }?.let { target ->
                val targetY = y(target.toDouble(), frame.min, frame.max)
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
                }
            }

            clipRect(left - 3.dp.toPx(), top - 3.dp.toPx(), left + plotWidth + 3.dp.toPx(), top + plotHeight + 3.dp.toPx()) {
                drawChartSeries(frame.primary, frame.min, frame.max, left, top, plotWidth, plotHeight,
                    CalorieColor, tooltipBackground)
                drawChartSeries(frame.secondary, frame.secondaryMin, frame.secondaryMax, left, top, plotWidth, plotHeight,
                    WeightColor, tooltipBackground, frame.overlayAlpha, dashed = true)
                val stride = ceil(points.size * 12.dp.toPx() / plotWidth).toInt().coerceAtLeast(1)
                frame.primary.markers.entries.forEachIndexed { index, (id, marker) ->
                    if (index % stride == 0 || id == selectedDate?.toString() || frame.primary.markers.size == 1) {
                        drawCircle(CalorieColor.copy(alpha = marker.alpha), 1.5.dp.toPx(),
                            Offset(left + marker.x * plotWidth, y(marker.value, frame.min, frame.max)))
                    }
                }
                val actualWeightMarkers = frame.secondary.markers.entries.filter { it.value.actualAlpha > 0f }
                val weightStride = ceil(actualWeightMarkers.size * 12.dp.toPx() / plotWidth).toInt().coerceAtLeast(1)
                actualWeightMarkers.forEachIndexed { index, (id, marker) ->
                    if (index % weightStride == 0 || id == selectedDate?.toString() || index == actualWeightMarkers.lastIndex) {
                        val p = Offset(left + marker.x * plotWidth, y(marker.value, frame.secondaryMin, frame.secondaryMax))
                        val alpha = marker.alpha * marker.actualAlpha * frame.overlayAlpha
                        drawCircle(tooltipBackground.copy(alpha = alpha), 3.dp.toPx(), p)
                        drawCircle(WeightColor.copy(alpha = alpha), 2.dp.toPx(), p)
                    }
                }
            }

            val dateStyle = axisStyle.copy(fontSize = 9.sp)
            listOf(0, points.lastIndex).distinct().filter { it in points.indices }.forEach { index ->
                val label = measurer.measure(points[index].date.toString(), dateStyle)
                drawText(label, topLeft = Offset((x(index) - label.size.width / 2f).coerceIn(0f,
                    (size.width - label.size.width).coerceAtLeast(0f)), top + plotHeight + 6.dp.toPx()))
            }

            // Selected point tooltip (same style as weight tab)
            points.getOrNull(selected)?.let { point ->
                val px = x(selected)
                val primary = frame.primary.markers[point.date.toString()]
                val py = y(primary?.value ?: point.calories, frame.min, frame.max)

                // Dashed vertical guide line
                drawLine(
                    color = tooltipGuide.copy(alpha = 0.5f),
                    start = Offset(px, top),
                    end = Offset(px, top + plotHeight),
                    strokeWidth = 1f,
                    pathEffect = TooltipDashEffect
                )
                // Highlight circles
                if (py in top..(top + plotHeight)) {
                    drawCircle(color = CalorieColor, radius = 6.5f, center = Offset(px, py))
                    drawCircle(color = Color.White, radius = 3f, center = Offset(px, py))
                }
                // Weight overlay point if applicable
                if (showWeightAxis && weights[selected] != null) {
                    val marker = frame.secondary.markers[point.date.toString()]
                    val weightPy = y(marker?.value ?: weights[selected]!!, frame.secondaryMin, frame.secondaryMax)
                    if (weightPy in top..(top + plotHeight)) {
                        drawCircle(color = WeightColor, radius = 6.5f, center = Offset(px, weightPy))
                        drawCircle(color = Color.White, radius = 3f, center = Offset(px, weightPy))
                    }
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
        },
        footer = {
            Column {
                if (overlay && !showWeightAxis) Text("No weight history to overlay", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Range: $weeks week${if (weeks == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Slider(
                    value = weeks.toFloat(),
                    onValueChange = { onWeeksChange(it.roundToInt()) },
                    valueRange = 1f..maxWeeks.toFloat(),
                    steps = (maxWeeks - 2).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }
        }
    )
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