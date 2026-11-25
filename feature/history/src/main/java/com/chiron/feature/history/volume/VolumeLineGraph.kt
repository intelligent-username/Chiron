package com.chiron.feature.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VolumeLineGraph(
    points: List<VolumePoint>,
    displayInKg: Boolean,
    mode: VolumeMode,
    onPointTap: (VolumePoint) -> Unit = {},
    modifier: Modifier = Modifier,
    animateEntry: Boolean = true
) {
    // animateEntry remains source-compatible; first composition always shows real geometry.
    val rawMaxLbs = points.maxOfOrNull { it.volumeLbs }?.takeIf { it > 0.0 } ?: 1.0
    val rawMaxDisplay = if (displayInKg) UnitConversion.lbsToKg(rawMaxLbs) else rawMaxLbs

    val stepCandidates = doubleArrayOf(
        50.0, 100.0, 250.0, 500.0, 1000.0, 2000.0, 2500.0, 5000.0,
        10000.0, 20000.0, 25000.0, 50000.0, 100000.0, 200000.0, 250000.0, 500000.0, 1000000.0
    )
    val targetStep = rawMaxDisplay / 4.0
    val niceStep = stepCandidates.firstOrNull { it >= targetStep } ?: (ceil(targetStep / 1000.0) * 1000.0)
    val niceMax = (ceil(rawMaxDisplay / niceStep).coerceAtLeast(1.0) * niceStep)
    val target = remember(points, displayInKg, niceMax) {
        volumeFrame(points, displayInKg, niceMax)
    }
    val motion = remember(displayInKg) { VolumeMotion(target) }
    val progress = remember(displayInKg) { Animatable(1f) }
    LaunchedEffect(target, motion) {
        if (motion.frame == target) return@LaunchedEffect
        val from = motion.frame
        progress.snapTo(0f)
        // Markers interpolate independently of the line and read as a point wandering
        // on its own. Show them only once the line has settled.
        motion.animating = true
        try {
            progress.animateTo(1f, tween(180)) { motion.frame = blendVolumeFrame(from, target, value) }
        } finally {
            motion.animating = false
        }
        motion.frame = target
    }
    val unit = if (displayInKg) "kg" else "lbs"

    val textMeasurer = rememberTextMeasurer()

    var hoveredX by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(points, displayInKg) { hoveredX = null }

    val lineAccentColor = ElectricBlue
    val textLabelColor = CoolGray
    val tooltipBgColor = SolidSlate
    val tooltipBorderColor = ThinOutline


    val measuredPointLabels = remember(points, textMeasurer, textLabelColor) {
        points.map { point ->
            if (point.label.isNotEmpty()) {
                textMeasurer.measure(
                    point.label,
                    TextStyle(color = textLabelColor, fontSize = 10.sp, fontFamily = MonospaceFamily)
                )
            } else null
        }
    }

    val linePath = remember { Path() }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points, motion) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val startTime = down.uptimeMillis
                        val startPos = down.position
                        hoveredX = startPos.x
                        var isDrag = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.isConsumed) break
                                if (!change.pressed) {
                                    hoveredX = null
                                    val elapsed = change.uptimeMillis - startTime
                                    val dist = (change.position - startPos).getDistance()
                                    if (!isDrag && elapsed < 300 && dist < 20f && points.isNotEmpty()) {
                                        val padLeft = 100f
                                        val graphW = (size.width - padLeft - 10f).coerceAtLeast(1f)
                                        points.minByOrNull {
                                            abs(padLeft + (motion.frame.markers[it.date.toString()]?.x ?: 0f) * graphW - startPos.x)
                                        }?.let(onPointTap)
                                    }
                                    break
                                }
                                val dist = (change.position - startPos).getDistance()
                                if (dist > viewConfiguration.touchSlop) {
                                    if (!isDrag && change.uptimeMillis - startTime < 300L) break
                                    isDrag = true
                                }
                                if (isDrag) change.consume()
                                hoveredX = change.position.x
                            }
                        } finally {
                            hoveredX = null
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val padLeft = 100f
            val padRight = 10f
            val padTop = 20f
            val padBottom = 40f
            val graphW = (w - padLeft - padRight).coerceAtLeast(1f)
            val graphH = (h - padTop - padBottom).coerceAtLeast(1f)
            val frame = motion.frame

            // Y-axis grid lines — rounded evenly to nearest thousands/half a thousand
            (0..4).forEach { tick ->
                val tickVal = frame.max * tick / 4
                val measuredText = textMeasurer.measure(formatVolumeTick(tickVal),
                    TextStyle(color = textLabelColor, fontSize = 10.sp, fontFamily = MonospaceFamily))
                val ratio = tick / 4f
                val y = padTop + graphH * (1f - ratio)
                drawLine(
                    color = tooltipBorderColor,
                    start = Offset(padLeft, y),
                    end = Offset(w - padRight, y),
                    strokeWidth = 0.5.dp.toPx()
                )
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset(padLeft - measuredText.size.width - 16f, y - measuredText.size.height / 2f)
                )
            }

            val n = points.size
            fun marker(i: Int) = frame.markers[points[i].date.toString()]
            fun xOf(i: Int) = padLeft + (marker(i)?.x ?: (i.toFloat() / (n - 1).coerceAtLeast(1))) * graphW
            fun yOf(vol: Double) = padTop + graphH * (1.0 - vol / frame.max).toFloat()

            // Solid Electric Blue line — no gradient fill area
            linePath.rewind()
            frame.samples.forEachIndexed { i, value ->
                val x = padLeft + i / 256f * graphW
                val y = yOf(value)
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            clipRect(padLeft - 3f, padTop - 3f, padLeft + graphW + 3f, padTop + graphH + 3f) {
                drawPath(
                    linePath,
                    color = lineAccentColor.copy(alpha = frame.alpha),
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                if (!motion.animating) {
                    val stride = ceil(frame.markers.size * 10f / graphW).toInt().coerceAtLeast(1)
                    frame.markers.values.forEachIndexed { i, marker ->
                        if (marker.value > 0.0 &&
                            ((mode == VolumeMode.BY_DAY && i % stride == 0) ||
                                i % maxOf(7, stride) == 0 || i == frame.markers.size - 1)) {
                            drawCircle(lineAccentColor.copy(alpha = marker.alpha), 3f,
                                Offset(padLeft + marker.x * graphW, yOf(marker.value)))
                        }
                    }
                }
            }

            // Dots + labels
            points.forEachIndexed { i, _ ->
                val x = xOf(i)
                // X-axis label — Monospace
                val measuredText = measuredPointLabels.getOrNull(i)
                if (measuredText != null) {
                    drawText(
                        textLayoutResult = measuredText,
                        topLeft = Offset(x - measuredText.size.width / 2f, h - padBottom + 12f)
                    )
                }
            }

            // Tooltip
            if (hoveredX != null && points.isNotEmpty()) {
                val hx = hoveredX!!
                val closestIndex = points.indices.minByOrNull { abs(xOf(it) - hx) } ?: return@Canvas
                val p = points[closestIndex]
                val px = xOf(closestIndex)
                val py = yOf(marker(closestIndex)?.value ?: (if (displayInKg) UnitConversion.lbsToKg(p.volumeLbs) else p.volumeLbs))

                drawLine(
                    color = textLabelColor.copy(alpha = 0.5f),
                    start = Offset(px, padTop),
                    end = Offset(px, padTop + graphH),
                    strokeWidth = 1f,
                    pathEffect = VolumeTooltipDashEffect
                )

                if (py in padTop..(padTop + graphH)) {
                    drawCircle(color = lineAccentColor, radius = 5f, center = Offset(px, py))
                }

                val dateStr = p.date.format(VolumeTooltipDateFormatter)
                val tooltipText = "$dateStr, ${p.volumeLbs.formatVolume(displayInKg)} $unit"

                val textLayoutResult = textMeasurer.measure(
                    text = tooltipText,
                    style = TextStyle(
                        color = onSurfaceColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonospaceFamily
                    )
                )

                val tw = textLayoutResult.size.width.toFloat()
                val th = textLayoutResult.size.height.toFloat()
                val tooltipPad = 12f

                var tx = px - tw / 2f
                if (tx < padLeft) tx = padLeft
                if (tx + tw > w - padRight) tx = w - padRight - tw

                val ty = padTop - 20f

                // Flat tooltip with SolidSlate background and ThinOutline border
                drawRoundRect(
                    color = tooltipBgColor,
                    topLeft = Offset(tx - tooltipPad, ty - tooltipPad),
                    size = androidx.compose.ui.geometry.Size(tw + tooltipPad * 2, th + tooltipPad * 2),
                    cornerRadius = VolumeTooltipCornerRadius
                )
                drawRoundRect(
                    color = tooltipBorderColor,
                    topLeft = Offset(tx - tooltipPad, ty - tooltipPad),
                    size = androidx.compose.ui.geometry.Size(tw + tooltipPad * 2, th + tooltipPad * 2),
                    cornerRadius = VolumeTooltipCornerRadius,
                    style = Stroke(width = 1f)
                )

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(tx, ty)
                )
            }
        }
    }
}

private val VolumeTooltipDateFormatter = DateTimeFormatter.ofPattern("EEEE MMM d")
private val VolumeTooltipDashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
private val VolumeTooltipCornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)

private data class VolumeMarker(val x: Float, val value: Double, val alpha: Float = 1f)
private data class VolumeFrame(
    val max: Double, val samples: List<Double>, val markers: Map<String, VolumeMarker>, val alpha: Float
)
private class VolumeMotion(initial: VolumeFrame) {
    var frame by mutableStateOf(initial)
    var animating by mutableStateOf(false)
}

private fun volumeFrame(points: List<VolumePoint>, inKg: Boolean, max: Double): VolumeFrame {
    val values = points.map { if (inKg) UnitConversion.lbsToKg(it.volumeLbs) else it.volumeLbs }
    val samples = (0..256).map { index ->
        if (values.isEmpty()) 0.0 else if (values.size == 1) values[0] else {
            val position = index / 256.0 * values.lastIndex
            val i = kotlin.math.floor(position).toInt().coerceAtMost(values.lastIndex - 1)
            val fraction = position - i
            // Sample the original midpoint-control cubic, including its non-linear x(t).
            var low = 0.0
            var high = 1.0
            repeat(18) {
                val t = (low + high) / 2
                val x = 1.5 * t - 1.5 * t * t + t * t * t
                if (x < fraction) low = t else high = t
            }
            val t = when (fraction) {
                0.0 -> 0.0
                1.0 -> 1.0
                else -> (low + high) / 2
            }
            val eased = t * t * (3 - 2 * t)
            values[i] + (values[i + 1] - values[i]) * eased
        }
    }
    return VolumeFrame(max, samples, points.mapIndexed { i, point ->
        point.date.toString() to VolumeMarker(i.toFloat() / points.lastIndex.coerceAtLeast(1), values[i])
    }.toMap(), if (points.size > 1) 1f else 0f)
}

private fun blendVolumeFrame(a: VolumeFrame, b: VolumeFrame, t: Float): VolumeFrame {
    fun d(x: Double, y: Double) = x + (y - x) * t
    fun f(x: Float, y: Float) = x + (y - x) * t
    val samples = if (a.markers.isEmpty()) b.samples else if (b.markers.isEmpty()) a.samples
        else a.samples.zip(b.samples).map { (x, y) -> d(x, y) }
    val markers = (a.markers.keys + b.markers.keys).associateWith { id ->
        val from = a.markers[id] ?: b.markers.getValue(id).copy(alpha = 0f)
        val to = b.markers[id] ?: from.copy(alpha = 0f)
        VolumeMarker(f(from.x, to.x), d(from.value, to.value), f(from.alpha, to.alpha))
    }
    return VolumeFrame(if (a.markers.isEmpty()) b.max else d(a.max, b.max),
        samples, markers, f(a.alpha, b.alpha))
}

private fun formatVolumeTick(v: Double): String {
    return when {
        v == 0.0 -> "0"
        v >= 1_000_000.0 -> {
            if (v % 1_000_000.0 == 0.0) "%.0fM".format(v / 1_000_000.0)
            else "%.1fM".format(v / 1_000_000.0)
        }
        v >= 1_000.0 -> {
            if (v % 1_000.0 == 0.0) "%.0fk".format(v / 1_000.0)
            else "%.1fk".format(v / 1_000.0)
        }
        else -> "%.0f".format(v)
    }
}

