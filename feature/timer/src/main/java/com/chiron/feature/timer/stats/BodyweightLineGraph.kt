package com.chiron.feature.timer

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.staticCompositionLocalOf
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
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun BodyweightLineGraph(
    points: List<BodyweightPoint>,
    localInKg: Boolean,
    mode: BodyweightMode,
    modifier: Modifier = Modifier,
    onPointTap: (BodyweightPoint) -> Unit = {},
    calorieOverlay: WeightCalorieOverlay? = null
) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = CoolGray, fontSize = 10.sp, fontFamily = MonospaceFamily)
    val target = remember(points, localInKg, calorieOverlay, measurer, style) {
        val weights = points.map { p ->
            p.weightLbs.takeIf { it > 0.0 }?.let { if (localInKg) UnitConversion.lbsToKg(it) else it }
        }
        val low = weights.filterNotNull().minOrNull() ?: if (localInKg) 70.0 else 150.0
        val high = weights.filterNotNull().maxOrNull() ?: low
        val span = (high - low).coerceAtLeast(if (localInKg) 0.5 else 1.0)
        val steps = doubleArrayOf(.25, .5, 1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0)
        val step = steps.firstOrNull { it >= span / 4 } ?: ceil(span / 200) * 50
        val min = floor(low / step) * step
        val max = (ceil(high / step) * step).coerceAtLeast(min + step * 2)
        val ids = points.map { it.date.toString() }
        val overlayMax = calorieOverlay?.axisMaximum ?: 1.0
        val left = (0..4).maxOf {
            measurer.measure(UnitConversion.formatNumber(min + (max - min) * it / 4), style).size.width
        }.toFloat()
        val right = calorieOverlay?.let {
            (0..4).maxOf { i ->
                measurer.measure("%.0f".format(overlayMax * i / 4) + if (i == 4) " kcal" else "", style).size.width
            }.toFloat()
        } ?: 0f
        ChartFrame(
            min, max, 0.0, overlayMax, left, right,
            if (calorieOverlay != null) 1f else 0f,
            chartSeries(weights, ids, points.filter { it.isActualInput }.map { it.date.toString() }.toSet()),
            chartSeries(calorieOverlay?.points?.map { it.calories }.orEmpty(), ids)
        )
    }
    val motion = rememberChartMotion(target, localInKg)
    var selectedDate by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(points) {
        if (points.none { it.date.toString() == selectedDate }) selectedDate = null
    }
    val colors = MaterialTheme.colorScheme
    val solidSlate = SolidSlate
    val thinOutline = ThinOutline
    val electricBlue = LocalStatsChartColor.current ?: ElectricBlue
    val coolGray = CoolGray
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().pointerInput(points, motion) {
            awaitEachGesture {
                fun select(x: Float) {
                    val frame = motion.frame
                    val left = 64.dp.toPx()
                    val right = (16 + 48 * frame.overlayAlpha).dp.toPx()
                    val width = (size.width - left - right).coerceAtLeast(1f)
                    selectedDate = points.filter { it.weightLbs > 0 }.minByOrNull { p ->
                        abs(left + (frame.primary.markers[p.date.toString()]?.x ?: 0f) * width - x)
                    }?.date?.toString()
                }
                val down = awaitFirstDown(requireUnconsumed = true)
                down.consume()
                select(down.position.x)
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if ((change.position - down.position).getDistance() < 20f) {
                                points.firstOrNull { it.date.toString() == selectedDate }?.let(onPointTap)
                            }
                            break
                        }
                        change.consume()
                        select(change.position.x)
                    }
                } finally { selectedDate = null }
            }
        }) {
            val frame = motion.frame
            val left = 64.dp.toPx()
            val right = (16 + 48 * frame.overlayAlpha).dp.toPx()
            val width = (size.width - left - right).coerceAtLeast(1f)
            val top = 12.dp.toPx()
            val height = (size.height - top - 24.dp.toPx()).coerceAtLeast(1f)
            fun position(marker: ChartMarker) = Offset(left + marker.x * width,
                top + height * (1f - ((marker.value - frame.min) / (frame.max - frame.min)).toFloat()))
            for (i in 0..4) {
                val y = top + height * (1f - i / 4f)
                drawLine(thinOutline.copy(alpha = .5f), Offset(left - 8.dp.toPx(), y),
                    Offset(left + width + 8.dp.toPx(), y), 1.dp.toPx())
                val label = measurer.measure(UnitConversion.formatNumber(frame.min + (frame.max - frame.min) * i / 4), style)
                drawText(label, topLeft = Offset(left - label.size.width - 12.dp.toPx(), y - label.size.height / 2))
                if (frame.overlayAlpha > 0f) {
                    val secondary = measurer.measure("%.0f".format(frame.secondaryMax * i / 4) + if (i == 4) " kcal" else "",
                        style.copy(color = Color(0xFFFFA726)))
                    drawText(secondary, topLeft = Offset(left + width + 14.dp.toPx(), y - secondary.size.height / 2),
                        alpha = frame.overlayAlpha)
                }
            }
            clipRect(left - 4.dp.toPx(), top - 4.dp.toPx(), left + width + 4.dp.toPx(), top + height + 4.dp.toPx()) {
                drawChartSeries(frame.primary, frame.min, frame.max, left, top, width, height,
                    electricBlue, solidSlate)
                drawChartSeries(frame.secondary, frame.secondaryMin, frame.secondaryMax, left, top, width, height,
                    Color(0xFFFFA726), solidSlate, frame.overlayAlpha, dashed = true)
                if (!motion.animating) {
                val secondaryStride = ceil(frame.secondary.markers.size * 16.dp.toPx() / width).toInt().coerceAtLeast(1)
                frame.secondary.markers.entries.forEachIndexed { index, (id, marker) ->
                    if (frame.overlayAlpha > 0f &&
                        (index % secondaryStride == 0 || id == selectedDate || index == frame.secondary.markers.size - 1)) {
                        val p = Offset(left + marker.x * width, top + height *
                            (1f - ((marker.value - frame.secondaryMin) / (frame.secondaryMax - frame.secondaryMin)).toFloat()))
                        drawCircle(Color(0xFFFFA726).copy(alpha = marker.alpha * frame.overlayAlpha),
                            1.25.dp.toPx(), p)
                    }
                }
                val actualMarkers = frame.primary.markers.entries.filter { it.value.actualAlpha > 0f }
                val stride = ceil(actualMarkers.size * 12.dp.toPx() / width).toInt().coerceAtLeast(1)
                actualMarkers.forEachIndexed { index, (id, marker) ->
                    if (index % stride == 0 || id == selectedDate || index == actualMarkers.lastIndex) {
                        val p = position(marker)
                        drawCircle(solidSlate.copy(alpha = marker.alpha * marker.actualAlpha), 4.dp.toPx(), p)
                        drawCircle(electricBlue.copy(alpha = marker.alpha * marker.actualAlpha), 2.5.dp.toPx(), p)
                    }
                }
                }
            }
            var lastLabelEnd = Float.NEGATIVE_INFINITY
            points.forEachIndexed { index, point ->
                if (point.label.isNotEmpty()) {
                    val marker = frame.primary.markers[point.date.toString()]
                    val x = left + (marker?.x ?: index.toFloat() / (points.size - 1).coerceAtLeast(1)) * width
                    val label = measurer.measure(point.label, style)
                    val lx = (x - label.size.width / 2).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
                    if (lx >= lastLabelEnd + 12.dp.toPx()) {
                        drawText(label, topLeft = Offset(lx, top + height + 6.dp.toPx()))
                        lastLabelEnd = lx + label.size.width
                    }
                }
            }
            val index = points.indexOfFirst { it.date.toString() == selectedDate }
            points.getOrNull(index)?.let { point ->
                val marker = frame.primary.markers[point.date.toString()] ?: return@let
                val p = position(marker)
                drawLine(coolGray.copy(alpha = .5f), Offset(p.x, top), Offset(p.x, top + height),
                    1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                if (!motion.animating) drawCircle(electricBlue, 4.dp.toPx(), p)
                val weight = if (localInKg) UnitConversion.lbsToKg(point.weightLbs) else point.weightLbs
                val text = buildString {
                    append("${point.date.format(DateTimeFormatter.ofPattern("EEEE MMM d"))}\n")
                    append("${UnitConversion.formatNumber(weight)} ${if (localInKg) "kg" else "lbs"} · ${if (point.isActualInput) "Logged" else "Estimated"}")
                    calorieOverlay?.points?.getOrNull(index)?.let {
                        append("\n${UnitConversion.formatNumber(it.calories)} kcal · ${it.calorieDate}")
                    }
                }
                drawChartTooltip(measurer, text, p, colors.onSurface, solidSlate, thinOutline)
            }
        }
    }
}

// Chart-only presentation helpers. All values originate in the existing calculators.
// A fixed sampling lattice permits shape changes without replaying a draw/reveal.
internal data class ChartSample(val value: Double, val alpha: Float, val edgeAlpha: Float)
internal data class ChartMarker(
    val x: Float, val value: Double, val alpha: Float = 1f, val actualAlpha: Float = 1f
)
internal data class ChartSeries(val samples: List<ChartSample>, val markers: Map<String, ChartMarker>)
internal data class ChartFrame(
    val min: Double, val max: Double, val secondaryMin: Double, val secondaryMax: Double,
    val left: Float, val right: Float, val overlayAlpha: Float,
    val primary: ChartSeries, val secondary: ChartSeries
)
internal class ChartMotion(initial: ChartFrame) {
    var frame by mutableStateOf(initial)
    var animating by mutableStateOf(false)
}

internal class SharedStatsChartMotion {
    var motion: ChartMotion? = null
    var mode: Int? = null
}

internal val LocalStatsChartMotion = staticCompositionLocalOf<SharedStatsChartMotion?> { null }
internal val LocalStatsChartMode = staticCompositionLocalOf { 0 }
internal val LocalStatsChartColor = staticCompositionLocalOf<Color?> { null }

@Composable
internal fun rememberChartMotion(target: ChartFrame, unitKey: Any): ChartMotion {
    val shared = LocalStatsChartMotion.current
    val mode = LocalStatsChartMode.current
    val motion = remember(shared, unitKey) {
        shared?.motion ?: ChartMotion(target).also { shared?.motion = it; shared?.mode = mode }
    }
    val progress = remember(motion) { Animatable(1f) }
    LaunchedEffect(target, motion, mode) {
        if (shared != null && shared.mode != mode) {
            val old = motion.frame
            val span = (old.max - old.min).coerceAtLeast(.000001)
            fun convert(value: Double) = target.min + (value - old.min) / span * (target.max - target.min)
            val series = old.primary.copy(
                samples = old.primary.samples.map { it.copy(value = convert(it.value)) },
                markers = old.primary.markers.mapValues { (_, marker) -> marker.copy(value = convert(marker.value)) }
            )
            motion.frame = target.copy(primary = series)
            shared.mode = mode
        }
        if (motion.frame == target) return@LaunchedEffect
        val from = motion.frame
        motion.animating = true
        try {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(180)) {
                motion.frame = blendChartFrame(from, target, value)
            }
            motion.frame = target
        } finally {
            motion.animating = false
        }
    }
    return motion
}

internal fun chartSeries(values: List<Double?>, ids: List<String>, actualIds: Set<String>? = null): ChartSeries {
    fun tangent(i: Int): Double {
        val value = values[i] ?: return 0.0
        val before = values.getOrNull(i - 1) ?: return 0.0
        val after = values.getOrNull(i + 1) ?: return 0.0
        val a = value - before
        val b = after - value
        return if (a * b <= 0.0) 0.0 else 2 * a * b / (a + b)
    }
    fun sample(x: Double): Double? {
        if (values.isEmpty()) return null
        if (values.size == 1) return values[0]
        val p = x * values.lastIndex
        val i = floor(p).toInt().coerceIn(0, values.lastIndex)
        if (abs(p - i) < .000001) return values[i]
        val a = values[i] ?: return null
        val b = values.getOrNull(i + 1) ?: return null
        val t = p - i
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * a + (t3 - 2 * t2 + t) * tangent(i) +
            (-2 * t3 + 3 * t2) * b + (t3 - t2) * tangent(i + 1)
    }
    val samples = (0..256).map { i ->
        val x = i / 256.0
        val value = sample(x)
        val previousX = (i - 1).coerceAtLeast(0) / 256.0
        val lo = floor(previousX * values.lastIndex.coerceAtLeast(0)).toInt()
        val hi = ceil(x * values.lastIndex.coerceAtLeast(0)).toInt()
        val connected = i > 0 && values.size > 1 && (lo..hi).all { values.getOrNull(it) != null }
        ChartSample(value ?: 0.0, if (value != null) 1f else 0f, if (connected && value != null) 1f else 0f)
    }
    val markers = values.mapIndexedNotNull { i, value ->
        value?.let { weight -> ids.getOrNull(i)?.let { id ->
            id to ChartMarker(i.toFloat() / values.lastIndex.coerceAtLeast(1), weight,
                actualAlpha = if (actualIds == null || id in actualIds) 1f else 0f)
        } }
    }.toMap()
    return ChartSeries(samples, markers)
}

private fun blendChartFrame(a: ChartFrame, b: ChartFrame, t: Float): ChartFrame {
    fun d(x: Double, y: Double) = x + (y - x) * t
    fun f(x: Float, y: Float) = x + (y - x) * t
    fun series(x: ChartSeries, y: ChartSeries): ChartSeries {
        val samples = x.samples.zip(y.samples).map { (old, next) ->
            val start = if (old.alpha == 0f) next.value else old.value
            val end = if (next.alpha == 0f) old.value else next.value
            ChartSample(d(start, end), f(old.alpha, next.alpha), f(old.edgeAlpha, next.edgeAlpha))
        }
        val markers = (x.markers.keys + y.markers.keys).associateWith { id ->
            val old = x.markers[id] ?: y.markers.getValue(id).copy(alpha = 0f)
            val next = y.markers[id] ?: old.copy(alpha = 0f)
            ChartMarker(f(old.x, next.x), d(old.value, next.value), f(old.alpha, next.alpha),
                f(old.actualAlpha, next.actualAlpha))
        }
        return ChartSeries(samples, markers)
    }
    val secondary = if (b.overlayAlpha == 0f) a.secondary else b.secondary
    val secondaryMin = if (b.overlayAlpha == 0f) a.secondaryMin else b.secondaryMin
    val secondaryMax = if (b.overlayAlpha == 0f) a.secondaryMax else b.secondaryMax
    val fromSecondaryMin = if (a.overlayAlpha == 0f) secondaryMin else a.secondaryMin
    val fromSecondaryMax = if (a.overlayAlpha == 0f) secondaryMax else a.secondaryMax
    val fromMin = if (a.primary.markers.isEmpty()) b.min else a.min
    val fromMax = if (a.primary.markers.isEmpty()) b.max else a.max
    return ChartFrame(d(fromMin, b.min), d(fromMax, b.max), d(fromSecondaryMin, secondaryMin),
        d(fromSecondaryMax, secondaryMax), f(a.left, b.left), f(a.right, b.right),
        f(a.overlayAlpha, b.overlayAlpha), series(a.primary, b.primary), series(a.secondary, secondary))
}

internal fun DrawScope.drawChartSeries(
    series: ChartSeries, min: Double, max: Double, left: Float, top: Float, width: Float, height: Float,
    color: Color, background: Color, alpha: Float = 1f, dashed: Boolean = false
) {
    if (alpha <= 0f) return
    val span = (max - min).coerceAtLeast(.000001)
    fun p(i: Int) = Offset(left + width * i / 256f,
        top + height * (1f - ((series.samples[i].value - min) / span).toFloat()))
    var index = 1
    while (index < series.samples.size) {
        val opacity = (alpha * series.samples[index].edgeAlpha *
            minOf(series.samples[index - 1].alpha, series.samples[index].alpha)).coerceIn(0f, 1f)
        if (opacity <= 0f) { index++; continue }
        val path = Path().apply {
            val start = p(index - 1)
            moveTo(start.x, start.y)
        }
        do {
            val end = p(index)
            path.lineTo(end.x, end.y)
            index++
        } while (index < series.samples.size &&
            abs(alpha * series.samples[index].edgeAlpha *
                minOf(series.samples[index - 1].alpha, series.samples[index].alpha) - opacity) < .001f)
        val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
        drawPath(path, background.copy(alpha = opacity * .8f),
            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
        drawPath(path, color.copy(alpha = opacity),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    }
}

internal fun DrawScope.drawChartTooltip(
    measurer: androidx.compose.ui.text.TextMeasurer, text: String, point: Offset,
    foreground: Color, background: Color, border: Color
) {
    val layout = measurer.measure(text, TextStyle(color = foreground, fontSize = 12.sp,
        fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily))
    val pad = 8.dp.toPx()
    val x = (point.x - layout.size.width / 2).coerceIn(pad, (size.width - layout.size.width - pad).coerceAtLeast(pad))
    val y = (point.y - layout.size.height - pad * 2).coerceIn(pad, (size.height - layout.size.height - pad).coerceAtLeast(pad))
    val origin = Offset(x - pad, y - pad)
    val extent = Size(layout.size.width + pad * 2, layout.size.height + pad * 2)
    drawRoundRect(background, origin, extent, CornerRadius(pad, pad))
    drawRoundRect(border, origin, extent, CornerRadius(pad, pad), style = Stroke(1f))
    drawText(layout, topLeft = Offset(x, y))
}
