package com.chiron.feature.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun BodyweightLineGraph(
    points: List<BodyweightPoint>,
    localInKg: Boolean,
    mode: BodyweightMode,
    modifier: Modifier = Modifier,
    onPointTap: (BodyweightPoint) -> Unit = {}
) {
    var triggered by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "bwGraphAnim"
    )
    LaunchedEffect(points) { triggered = true }

    // Calculate nice ticks placed at nearest 0.25, 0.5, or full kg/lb based on display units
    val displayWeights = remember(points, localInKg) {
        points.filter { it.weightLbs > 0.0 }.map {
            if (localInKg) UnitConversion.lbsToKg(it.weightLbs) else it.weightLbs
        }
    }
    val rawMin = displayWeights.minOfOrNull { it } ?: (if (localInKg) 70.0 else 150.0)
    val rawMax = displayWeights.maxOfOrNull { it } ?: rawMin
    val rawSpan = (rawMax - rawMin).coerceAtLeast(if (localInKg) 0.5 else 1.0)

    val stepCandidates = doubleArrayOf(0.25, 0.5, 1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0)
    val targetStep = rawSpan / 4.0
    val niceStep = stepCandidates.firstOrNull { it >= targetStep } ?: ceil(targetStep / 50.0) * 50.0

    val niceMin = floor(rawMin / niceStep) * niceStep
    var niceMax = ceil(rawMax / niceStep) * niceStep
    if (niceMax <= niceMin) {
        niceMax = niceMin + niceStep * 2
    }

    val tickCount = ((niceMax - niceMin) / niceStep).roundToInt().coerceAtLeast(1)
    val ticks = remember(niceMin, niceMax, niceStep, tickCount) {
        (0..tickCount).map { niceMin + it * niceStep }
    }

    val minW = if (localInKg) UnitConversion.kgToLbs(niceMin) else niceMin
    val maxW = if (localInKg) UnitConversion.kgToLbs(niceMax) else niceMax

    val unit = if (localInKg) "kg" else "lbs"
    val textMeasurer = rememberTextMeasurer()
    var hoveredX by remember { mutableStateOf<Float?>(null) }

    val lineColor = ElectricBlue
    val labelColor = CoolGray
    val tipBg = SolidSlate
    val tipBorder = ThinOutline
    val onSurface = MaterialTheme.colorScheme.onSurface

    val measuredTicks = remember(ticks, textMeasurer, labelColor) {
        ticks.map { tickValue ->
            tickValue to textMeasurer.measure(
                UnitConversion.formatNumber(tickValue),
                TextStyle(color = labelColor, fontSize = 10.sp, fontFamily = MonospaceFamily)
            )
        }
    }

    val measuredPointLabels = remember(points, textMeasurer, labelColor) {
        points.map { p ->
            if (p.label.isNotEmpty()) {
                textMeasurer.measure(
                    p.label,
                    TextStyle(color = labelColor, fontSize = 10.sp, fontFamily = MonospaceFamily)
                )
            } else null
        }
    }
    val axisWidth = (measuredTicks.maxOfOrNull { it.second.size.width } ?: 0).toFloat()

    val linePath = remember { Path() }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points, axisWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        down.consume()
                        hoveredX = down.position.x
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                hoveredX = null
                                val dist = (change.position - down.position).getDistance()
                                if (dist < 20f && points.isNotEmpty()) {
                                    val padLeft = axisWidth + 12.dp.toPx()
                                    val padRight = 8.dp.toPx()
                                    val plotInsetX = 8.dp.toPx()
                                    val plotLeft = padLeft + plotInsetX
                                    val plotW = (size.width - padLeft - padRight - plotInsetX * 2f).coerceAtLeast(1f)
                                    onPointTap(nearestPoint(points, down.position.x, plotLeft, plotW))
                                }
                                break
                            }
                            change.consume()
                            hoveredX = change.position.x
                        }
                    }
                }
        ) {
            val padLeft = axisWidth + 12.dp.toPx()
            val padRight = 8.dp.toPx()
            val padTop = 12.dp.toPx()
            val padBottom = 24.dp.toPx()
            val graphW = size.width - padLeft - padRight
            val graphH = size.height - padTop - padBottom

            // Insets inside the grid area so points don't overflow the axes
            val plotInsetX = 8.dp.toPx()
            val plotInsetY = 0f
            val plotLeft = padLeft + plotInsetX
            val plotW = (graphW - plotInsetX * 2f).coerceAtLeast(1f)
            val plotTop = padTop + plotInsetY
            val plotH = (graphH - plotInsetY * 2f).coerceAtLeast(1f)

            drawGrid(measuredTicks, niceMin, niceMax, padLeft, padRight, padTop, graphH, tipBorder)
            if (points.isEmpty()) return@Canvas

            drawLine(linePath, points, maxW, minW, animProgress, plotLeft, plotW, plotTop, plotH, lineColor)
            drawDots(measuredPointLabels, points, maxW, minW, animProgress, plotLeft, plotW, plotTop, plotH, padLeft, padRight, padTop, graphH, lineColor, tipBg)
            hoveredX?.let { hx ->
                drawTooltip(
                    textMeasurer, hx, points, localInKg, unit, maxW, minW, animProgress,
                    plotLeft, plotW, plotTop, plotH, padLeft, padRight, padTop, graphH, labelColor, lineColor, tipBg, tipBorder, onSurface
                )
            }
        }
    }
}

private fun nearestPoint(
    points: List<BodyweightPoint>,
    x: Float,
    plotLeft: Float,
    plotW: Float
): BodyweightPoint {
    if (points.isEmpty()) return BodyweightPoint("", 0.0, 0L, LocalDate.now(), false)
    val n = points.size
    val raw = ((x - plotLeft) / plotW * (n - 1).coerceAtLeast(1)).roundToInt()
    return points[raw.coerceIn(0, n - 1)]
}

private fun DrawScope.drawGrid(
    measuredTicks: List<Pair<Double, androidx.compose.ui.text.TextLayoutResult>>,
    displayMin: Double,
    displayMax: Double,
    padLeft: Float,
    padRight: Float,
    padTop: Float,
    graphH: Float,
    gridColor: Color
) {
    val span = (displayMax - displayMin).takeIf { it > 0.0 } ?: 1.0
    measuredTicks.forEach { (tickValue, layout) ->
        val ratio = ((tickValue - displayMin) / span).toFloat().coerceIn(0f, 1f)
        val y = padTop + graphH * (1f - ratio)
        drawLine(
            color = gridColor.copy(alpha = 0.5f),
            start = Offset(padLeft, y),
            end = Offset(size.width - padRight, y),
            strokeWidth = 1.dp.toPx()
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(padLeft - layout.size.width - 10f, y - layout.size.height / 2f)
        )
    }

}

private fun DrawScope.drawLine(
    path: Path,
    points: List<BodyweightPoint>,
    maxW: Double,
    minW: Double,
    progress: Float,
    plotLeft: Float,
    plotW: Float,
    plotTop: Float,
    plotH: Float,
    color: Color
) {
    val n = points.size
    fun xOf(i: Int) = plotLeft + (i.toFloat() / (n - 1).coerceAtLeast(1)) * plotW
    fun yOf(w: Double) = plotTop + plotH * (1f - yRatio(w, maxW, minW) * progress)

    path.rewind()
    path.moveTo(xOf(0), yOf(points[0].weightLbs))
    // Monotone tangents keep the line smooth without inventing peaks between measurements.
    fun tangent(i: Int): Float {
        if (i == 0 || i == n - 1) return 0f
        val before = yOf(points[i].weightLbs) - yOf(points[i - 1].weightLbs)
        val after = yOf(points[i + 1].weightLbs) - yOf(points[i].weightLbs)
        return if (before * after <= 0f) 0f else 2f * before * after / (before + after)
    }
    for (i in 1 until n) {
        val startX = xOf(i - 1)
        val endX = xOf(i)
        val third = (endX - startX) / 3f
        val startY = yOf(points[i - 1].weightLbs)
        val endY = yOf(points[i].weightLbs)
        path.cubicTo(startX + third, startY + tangent(i - 1) / 3f, endX - third, endY - tangent(i) / 3f, endX, endY)
    }
    drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun yRatio(w: Double, maxW: Double, minW: Double): Float {
    val span = (maxW - minW).takeIf { it > 0.0 } ?: 1.0
    return ((w - minW) / span).toFloat().coerceIn(0f, 1f)
}

private fun DrawScope.drawDots(
    measuredLabels: List<androidx.compose.ui.text.TextLayoutResult?>,
    points: List<BodyweightPoint>,
    maxW: Double,
    minW: Double,
    progress: Float,
    plotLeft: Float,
    plotW: Float,
    plotTop: Float,
    plotH: Float,
    padLeft: Float,
    padRight: Float,
    padTop: Float,
    graphH: Float,
    dotColor: Color,
    dotBackground: Color
) {
    val n = points.size
    var lastLabelEnd = Float.NEGATIVE_INFINITY
    points.forEachIndexed { i, p ->
        val x = plotLeft + (i.toFloat() / (n - 1).coerceAtLeast(1)) * plotW
        val y = plotTop + plotH * (1f - yRatio(p.weightLbs, maxW, minW) * progress)

        if (p.weightLbs > 0.0 && p.isActualInput) {
            drawCircle(color = dotBackground, radius = 4.dp.toPx(), center = Offset(x, y))
            drawCircle(color = dotColor, radius = 2.5.dp.toPx(), center = Offset(x, y))
        }

        val measured = measuredLabels.getOrNull(i)
        if (measured != null) {
            // Clamp X-axis label so it stays nicely bounded
            val maxLabelX = (size.width - padRight - measured.size.width).coerceAtLeast(padLeft)
            val labelX = (x - measured.size.width / 2f).coerceIn(padLeft, maxLabelX)
            if (labelX >= lastLabelEnd + 12.dp.toPx()) {
                drawText(measured, topLeft = Offset(labelX, padTop + graphH + 6.dp.toPx()))
                lastLabelEnd = labelX + measured.size.width
            }
        }
    }
}

private val BodyweightTooltipDateFormatter = DateTimeFormatter.ofPattern("EEEE MMM d")
private val BodyweightTooltipDashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
private val BodyweightTooltipCornerRadius = CornerRadius(8f, 8f)

private fun DrawScope.drawTooltip(
    measurer: androidx.compose.ui.text.TextMeasurer,
    hx: Float,
    points: List<BodyweightPoint>,
    localInKg: Boolean,
    unit: String,
    maxW: Double,
    minW: Double,
    progress: Float,
    plotLeft: Float,
    plotW: Float,
    plotTop: Float,
    plotH: Float,
    padLeft: Float,
    padRight: Float,
    padTop: Float,
    graphH: Float,
    labelColor: Color,
    lineColor: Color,
    tipBg: Color,
    tipBorder: Color,
    onSurface: Color
) {
    val n = points.size
    val idx = (((hx - plotLeft) / plotW) * (n - 1)).roundToInt().coerceIn(0, n - 1)
    val p = points[idx]
    val px = plotLeft + (idx.toFloat() / (n - 1).coerceAtLeast(1)) * plotW
    val py = plotTop + plotH * (1f - yRatio(p.weightLbs, maxW, minW) * progress)

    // Dashed guide line clamped strictly within the grid vertical boundary
    drawLine(
        color = labelColor.copy(alpha = 0.5f),
        start = Offset(px, padTop),
        end = Offset(px, padTop + graphH),
        strokeWidth = 1f,
        pathEffect = BodyweightTooltipDashEffect
    )
    drawCircle(color = lineColor, radius = 6.5f, center = Offset(px, py))
    drawCircle(color = Color.White, radius = 3f, center = Offset(px, py))

    val dateStr = Instant.ofEpochMilli(p.timestampUtc).atZone(ZoneId.systemDefault())
        .format(BodyweightTooltipDateFormatter)
    val displayWeight = if (localInKg) UnitConversion.lbsToKg(p.weightLbs) else p.weightLbs
    val statusTag = if (p.isActualInput) "Logged" else "Estimated"
    val text = "$dateStr\n${UnitConversion.formatNumber(displayWeight)} $unit · $statusTag"
    val layout = measurer.measure(
        text,
        TextStyle(color = onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
    )
    val tw = layout.size.width.toFloat()
    val th = layout.size.height.toFloat()
    val pad = 10f

    val tx = (px - tw / 2f).coerceIn(pad, (size.width - pad - tw).coerceAtLeast(pad))
    val ty = if (py - th - pad * 2 - 12f >= 0f) {
        py - th - pad - 12f
    } else {
        py + pad + 12f
    }

    drawRoundRect(tipBg, Offset(tx - pad, ty - pad), Size(tw + pad * 2, th + pad * 2), BodyweightTooltipCornerRadius)
    drawRoundRect(tipBorder, Offset(tx - pad, ty - pad), Size(tw + pad * 2, th + pad * 2), BodyweightTooltipCornerRadius, style = Stroke(1f))
    drawText(layout, topLeft = Offset(tx, ty))
}
