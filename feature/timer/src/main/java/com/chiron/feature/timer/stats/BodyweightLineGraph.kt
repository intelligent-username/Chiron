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
    val niceStep = stepCandidates.firstOrNull { it >= targetStep } ?: 10.0

    val niceMin = floor(rawMin / niceStep) * niceStep
    var niceMax = ceil(rawMax / niceStep) * niceStep
    if (niceMax <= niceMin) {
        niceMax = niceMin + niceStep * 2
    }

    val tickCount = ((niceMax - niceMin) / niceStep).roundToInt().coerceIn(2, 6)
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

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
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
                                    val padLeft = 105f
                                    val padRight = 16f
                                    val plotInsetX = 14f
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
            val padLeft = 105f
            val padRight = 16f
            val padTop = 22f
            val padBottom = 38f
            val graphW = size.width - padLeft - padRight
            val graphH = size.height - padTop - padBottom

            // Insets inside the grid area so points don't overflow the axes
            val plotInsetX = 14f
            val plotInsetY = 10f
            val plotLeft = padLeft + plotInsetX
            val plotW = (graphW - plotInsetX * 2f).coerceAtLeast(1f)
            val plotTop = padTop + plotInsetY
            val plotH = (graphH - plotInsetY * 2f).coerceAtLeast(1f)

            drawGrid(textMeasurer, ticks, niceMin, niceMax, padLeft, padRight, padTop, graphH, labelColor, tipBorder)
            if (points.isEmpty()) return@Canvas

            drawLine(points, maxW, minW, animProgress, plotLeft, plotW, plotTop, plotH, lineColor)
            drawDots(textMeasurer, points, maxW, minW, animProgress, plotLeft, plotW, plotTop, plotH, padLeft, padRight, padTop, graphH, lineColor, labelColor)
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
    measurer: androidx.compose.ui.text.TextMeasurer,
    ticks: List<Double>,
    displayMin: Double,
    displayMax: Double,
    padLeft: Float,
    padRight: Float,
    padTop: Float,
    graphH: Float,
    labelColor: Color,
    gridColor: Color
) {
    val span = (displayMax - displayMin).takeIf { it > 0.0 } ?: 1.0
    ticks.forEach { tickValue ->
        val ratio = ((tickValue - displayMin) / span).toFloat().coerceIn(0f, 1f)
        val y = padTop + graphH * (1f - ratio)
        drawLine(
            color = gridColor.copy(alpha = 0.5f),
            start = Offset(padLeft, y),
            end = Offset(size.width - padRight, y),
            strokeWidth = 0.5.dp.toPx()
        )
        val layout = measurer.measure(
            UnitConversion.formatNumber(tickValue),
            TextStyle(color = labelColor, fontSize = 10.sp, fontFamily = MonospaceFamily)
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(padLeft - layout.size.width - 10f, y - layout.size.height / 2f)
        )
    }

    // Explicit Y-axis line separating labels from data area
    drawLine(
        color = gridColor,
        start = Offset(padLeft, padTop),
        end = Offset(padLeft, padTop + graphH),
        strokeWidth = 1.dp.toPx()
    )

    // Explicit X-axis bottom baseline
    drawLine(
        color = gridColor,
        start = Offset(padLeft, padTop + graphH),
        end = Offset(size.width - padRight, padTop + graphH),
        strokeWidth = 1.dp.toPx()
    )
}

private fun DrawScope.drawLine(
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

    val path = Path()
    path.moveTo(xOf(0), yOf(points[0].weightLbs))
    for (i in 1 until n) {
        val mid = (xOf(i - 1) + xOf(i)) / 2f
        path.cubicTo(mid, yOf(points[i - 1].weightLbs), mid, yOf(points[i].weightLbs), xOf(i), yOf(points[i].weightLbs))
    }
    drawPath(path, color = color, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun yRatio(w: Double, maxW: Double, minW: Double): Float {
    val span = (maxW - minW).takeIf { it > 0.0 } ?: 1.0
    return ((w - minW) / span).toFloat().coerceIn(0f, 1f)
}

private fun DrawScope.drawDots(
    measurer: androidx.compose.ui.text.TextMeasurer,
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
    labelColor: Color
) {
    val n = points.size
    points.forEachIndexed { i, p ->
        val x = plotLeft + (i.toFloat() / (n - 1).coerceAtLeast(1)) * plotW
        val y = plotTop + plotH * (1f - yRatio(p.weightLbs, maxW, minW) * progress)

        if (p.weightLbs > 0.0 && p.isActualInput) {
            // Prominent dots for actual logged weigh-ins inside plot bounds
            drawCircle(color = dotColor.copy(alpha = 0.25f), radius = 7.5f, center = Offset(x, y))
            drawCircle(color = dotColor, radius = 5f, center = Offset(x, y))
            drawCircle(color = Color.White, radius = 2f, center = Offset(x, y))
        }

        if (p.label.isNotEmpty()) {
            val measured = measurer.measure(
                p.label,
                TextStyle(color = labelColor, fontSize = 10.sp, fontFamily = MonospaceFamily)
            )
            // Clamp X-axis label so it stays nicely bounded
            val labelX = (x - measured.size.width / 2f).coerceIn(padLeft, size.width - padRight - measured.size.width)
            drawText(measured, topLeft = Offset(labelX, padTop + graphH + 8f))
        }
    }
}

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
    fun xOf(i: Int) = plotLeft + (i.toFloat() / (n - 1).coerceAtLeast(1)) * plotW
    val idx = (0 until n).minByOrNull { kotlin.math.abs(xOf(it) - hx) } ?: return
    val p = points[idx]
    val px = xOf(idx)
    val py = plotTop + plotH * (1f - yRatio(p.weightLbs, maxW, minW) * progress)

    // Dashed guide line clamped strictly within the grid vertical boundary
    drawLine(
        color = labelColor.copy(alpha = 0.5f),
        start = Offset(px, padTop),
        end = Offset(px, padTop + graphH),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
    )
    drawCircle(color = lineColor, radius = 6.5f, center = Offset(px, py))
    drawCircle(color = Color.White, radius = 3f, center = Offset(px, py))

    val dateStr = Instant.ofEpochMilli(p.timestampUtc).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEEE MMM d"))
    val displayWeight = if (localInKg) UnitConversion.lbsToKg(p.weightLbs) else p.weightLbs
    val statusTag = if (p.isActualInput) " (Logged)" else " (Est)"
    val text = "$dateStr: ${UnitConversion.formatNumber(displayWeight)} $unit$statusTag"
    val layout = measurer.measure(
        text,
        TextStyle(color = onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
    )
    val tw = layout.size.width.toFloat()
    val th = layout.size.height.toFloat()
    val pad = 10f

    val tx = (px - tw / 2f).coerceIn(padLeft, size.width - padRight - tw)
    val ty = if (py - th - pad * 2 - 12f >= 0f) {
        py - th - pad - 12f
    } else {
        py + pad + 12f
    }

    drawRoundRect(tipBg, Offset(tx - pad, ty - pad), Size(tw + pad * 2, th + pad * 2), CornerRadius(8f, 8f))
    drawRoundRect(tipBorder, Offset(tx - pad, ty - pad), Size(tw + pad * 2, th + pad * 2), CornerRadius(8f, 8f), style = Stroke(1f))
    drawText(layout, topLeft = Offset(tx, ty))
}
