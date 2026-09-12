package com.chiron.feature.history

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VolumeLineGraph(
    points: List<VolumePoint>,
    displayInKg: Boolean,
    mode: VolumeMode,
    onPointTap: (VolumePoint) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Animate in on first composition
    var triggered by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "graphAnim"
    )
    LaunchedEffect(points) { triggered = true }

    val maxVol = points.maxOfOrNull { it.volumeLbs }?.takeIf { it > 0.0 } ?: 1.0
    val unit = if (displayInKg) "kg" else "lbs"

    val textMeasurer = rememberTextMeasurer()

    var hoveredX by remember { mutableStateOf<Float?>(null) }

    val lineAccentColor = ElectricBlue
    val textLabelColor = CoolGray
    val tooltipBgColor = SolidSlate
    val tooltipBorderColor = ThinOutline

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startTime = down.uptimeMillis
                        val startPos = down.position
                        hoveredX = startPos.x
                        var isDrag = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                hoveredX = null
                                val duration = change.uptimeMillis - startTime
                                val dist = (change.position - startPos).getDistance()
                                if (!isDrag && duration < 500L && dist < 20f) {
                                    if (points.isNotEmpty()) {
                                        val n = points.size
                                        val padLeft = 100f
                                        val padRight = 10f
                                        val graphW = size.width - padLeft - padRight
                                        val rawIndex = ((startPos.x - padLeft) / graphW * (n - 1).coerceAtLeast(1))
                                            .roundToInt()
                                        val index = rawIndex.coerceIn(0, n - 1)
                                        val point = points[index]
                                        onPointTap(point)
                                    }
                                }
                                break
                            } else {
                                val dist = (change.position - startPos).getDistance()
                                if (dist >= 10f) {
                                    isDrag = true
                                }
                                hoveredX = change.position.x
                            }
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
            val graphW = w - padLeft - padRight
            val graphH = h - padTop - padBottom

            // Y-axis grid lines — solid, very thin
            val gridLevels = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            gridLevels.forEach { ratio ->
                val y = padTop + graphH * (1f - ratio)
                drawLine(
                    color = tooltipBorderColor,
                    start = Offset(padLeft, y),
                    end = Offset(w - padRight, y),
                    strokeWidth = 0.5.dp.toPx()
                )

                val labelVal = maxVol * ratio
                val v = if (displayInKg) labelVal * 0.453592 else labelVal
                val labelStr = if (v >= 1000) "%.1fk".format(v / 1000) else "%.0f".format(v)
                val measuredText = textMeasurer.measure(
                    labelStr,
                    TextStyle(color = textLabelColor, fontSize = 10.sp, fontFamily = MonospaceFamily)
                )
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset(padLeft - measuredText.size.width - 16f, y - measuredText.size.height / 2f)
                )
            }

            if (points.isEmpty()) return@Canvas

            val n = points.size
            fun xOf(i: Int) = padLeft + (i.toFloat() / (n - 1).coerceAtLeast(1)) * graphW
            fun yOf(vol: Double) = padTop + graphH * (1.0 - (vol / maxVol)).toFloat()

            // Solid Electric Blue line — no gradient fill area
            val linePath = Path()
            val firstX = xOf(0)
            val firstY = yOf(points[0].volumeLbs * animProgress)
            linePath.moveTo(firstX, firstY)
            for (i in 1 until n) {
                val cx1 = xOf(i - 1) + (xOf(i) - xOf(i - 1)) / 2f
                val cy1 = yOf(points[i - 1].volumeLbs * animProgress)
                val cx2 = cx1
                val cy2 = yOf(points[i].volumeLbs * animProgress)
                linePath.cubicTo(cx1, cy1, cx2, cy2, xOf(i), yOf(points[i].volumeLbs * animProgress))
            }
            drawPath(
                linePath,
                color = lineAccentColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Dots + labels
            points.forEachIndexed { i, point ->
                val x = xOf(i)
                val y = yOf(point.volumeLbs * animProgress)
                if (point.volumeLbs > 0.0) {
                    if (mode == VolumeMode.BY_DAY) {
                        drawCircle(color = lineAccentColor, radius = 3f, center = Offset(x, y))
                    } else if (i % 7 == 0 || i == n - 1) {
                        drawCircle(color = lineAccentColor, radius = 3f, center = Offset(x, y))
                    }
                }

                // X-axis label — Monospace
                if (point.label.isNotEmpty()) {
                    val measuredText = textMeasurer.measure(
                        point.label,
                        TextStyle(color = textLabelColor, fontSize = 10.sp, fontFamily = MonospaceFamily)
                    )
                    drawText(
                        textLayoutResult = measuredText,
                        topLeft = Offset(x - measuredText.size.width / 2f, h - padBottom + 12f)
                    )
                }
            }

            // Tooltip
            if (hoveredX != null && points.isNotEmpty()) {
                val hx = hoveredX!!
                val closestIndex = (0 until n).minByOrNull { abs(xOf(it) - hx) }
                if (closestIndex != null) {
                    val p = points[closestIndex]
                    val px = xOf(closestIndex)
                    val py = yOf(p.volumeLbs * animProgress)

                    drawLine(
                        color = textLabelColor.copy(alpha = 0.5f),
                        start = Offset(px, padTop),
                        end = Offset(px, padTop + graphH),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )

                    drawCircle(color = lineAccentColor, radius = 5f, center = Offset(px, py))

                    val dateFmt = DateTimeFormatter.ofPattern("EEEE MMM d")
                    val dateStr = p.date.format(dateFmt)
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
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )
                    drawRoundRect(
                        color = tooltipBorderColor,
                        topLeft = Offset(tx - tooltipPad, ty - tooltipPad),
                        size = androidx.compose.ui.geometry.Size(tw + tooltipPad * 2, th + tooltipPad * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
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
}
