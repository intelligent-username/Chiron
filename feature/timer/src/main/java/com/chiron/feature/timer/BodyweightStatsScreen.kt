package com.chiron.feature.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.model.BodyWeightEntry
import com.chiron.core.ui.components.WeekNavigator
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Real wiring: log/edit/delete delegate to BodyweightViewModel repository intents.

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Double.formatBodyweight(inKg: Boolean): String {
    val v = if (inKg) UnitConversion.lbsToKg(this) else this
    return UnitConversion.formatNumber(v)
}

private fun formatStamp(timestampUtc: Long): String {
    val fmt = DateTimeFormatter.ofPattern("EEEE MMM d, h:mm a")
    return Instant.ofEpochMilli(timestampUtc).atZone(ZoneId.systemDefault()).format(fmt)
}

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
fun BodyweightStatsScreen(
    viewModel: BodyweightViewModel,
    displayInKg: Boolean,
    onImportClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            BodyweightContent(
                state = state,
                displayInKg = displayInKg,
                onModeChange = viewModel::setMode,
                onWeekCountChange = viewModel::setWeekCount,
                onPrevWeek = viewModel::goToPreviousWeek,
                onNextWeek = viewModel::goToNextWeek,
                onToggleAbridgeGaps = viewModel::toggleAbridgeGaps,
                onLog = viewModel::logWeight,
                onEdit = viewModel::updateEntry,
                onDelete = viewModel::deleteEntry,
                onImportClick = onImportClick
            )
        }
    }
}

@Composable
fun BodyweightContent(
    state: BodyweightUiState,
    displayInKg: Boolean,
    onModeChange: (BodyweightMode) -> Unit,
    onWeekCountChange: (Int) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToggleAbridgeGaps: () -> Unit,
    onLog: (Double) -> Unit,
    onEdit: (Long, Double) -> Unit,
    onDelete: (Long) -> Unit,
    onImportClick: () -> Unit = {}
) {
    val unit = if (displayInKg) "kg" else "lbs"
    val weekLabel = remember(state.currentWeekStart, state.mode, state.weekCount) {
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        if (state.mode == BodyweightMode.BY_DAY) {
            "${state.currentWeekStart.format(fmt)} - ${state.currentWeekStart.plusDays(6).format(fmt)}"
        } else {
            val start = state.currentWeekStart.minusWeeks((state.weekCount - 1).toLong())
            "${start.format(fmt)} - ${state.currentWeekStart.plusDays(6).format(fmt)}"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onImportClick) { Text("Import") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LogInput(displayInKg = displayInKg, onLog = onLog, error = state.error)
        Spacer(modifier = Modifier.height(12.dp))
        ModeSelector(selected = state.mode, onSelect = onModeChange)
        AbridgeRow(checked = state.abridgeGaps, onToggle = onToggleAbridgeGaps)
        Spacer(modifier = Modifier.height(8.dp))
        GraphCard(state = state, displayInKg = displayInKg, unit = unit, onWeekCountChange = onWeekCountChange)
        Spacer(modifier = Modifier.height(12.dp))
        WeekNavigator(
            weekLabel = weekLabel,
            canGoPrev = !state.isAtFirstWeek,
            canGoNext = !state.isAtCurrentWeek,
            onPrev = onPrevWeek,
            onNext = onNextWeek
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatsSection(stats = state.stats, displayInKg = displayInKg, unit = unit)
        Spacer(modifier = Modifier.height(16.dp))
        HistoryList(entries = state.entries, displayInKg = displayInKg, onEdit = onEdit, onDelete = onDelete)
        Spacer(modifier = Modifier.height(120.dp))
    }
}

// ── Log input ─────────────────────────────────────────────────────────────────

@Composable
fun LogInput(
    displayInKg: Boolean,
    onLog: (Double) -> Unit,
    error: String?
) {
    var text by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSubmitMs by rememberSaveable { mutableStateOf(0L) }
    val unit = if (displayInKg) "kg" else "lbs"
    fun submit() {
        val now = System.currentTimeMillis()
        if (now - lastSubmitMs < 500L) return
        val parsed = text.trim().toDoubleOrNull()
        if (parsed == null || !parsed.isFinite()) {
            localError = "Enter a valid number"
            return
        }
        val lbs = if (displayInKg) UnitConversion.kgToLbs(parsed) else parsed
        if (lbs <= 0.0) {
            localError = "Enter a weight above 0"
            return
        }
        if (lbs < 20.0 || lbs > 1500.0) {
            localError = "Enter a weight between 20 and 1500 lbs"
            return
        }
        lastSubmitMs = now
        localError = null
        onLog(lbs)
        text = ""
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ThinOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Weight ($unit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { submit() }, enabled = text.trim().isNotEmpty()) { Text("Log") }
            }
            val msg = localError ?: error
            if (msg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}

// ── Mode selector + abridge row ───────────────────────────────────────────────

@Composable
fun ModeSelector(
    selected: BodyweightMode,
    onSelect: (BodyweightMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SolidSlate)
            .border(1.dp, ThinOutline, RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BodyweightMode.entries.forEach { mode ->
            val label = if (mode == BodyweightMode.BY_DAY) "Short Term" else "Long Term"
            val isSelected = selected == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFF21262D) else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else CoolGray,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun AbridgeRow(checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = CoolGray,
                checkmarkColor = Color.Black
            ),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Abridge Gaps", fontSize = 14.sp, color = CoolGray)
    }
}

// ── Graph card ────────────────────────────────────────────────────────────────

@Composable
private fun GraphCard(
    state: BodyweightUiState,
    displayInKg: Boolean,
    unit: String,
    onWeekCountChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ThinOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val current = state.stats.current
                Text(
                    text = if (current != null) "${current.formatBodyweight(displayInKg)} $unit" else "-- $unit",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
            if (state.points.isEmpty() || state.points.all { it.weightLbs == 0.0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No bodyweight data for this period",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                BodyweightLineGraph(
                    points = state.points,
                    displayInKg = displayInKg,
                    mode = state.mode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
            if (state.mode == BodyweightMode.BY_WEEK) {
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = state.weekCount.toFloat(),
                    onValueChange = { onWeekCountChange(it.roundToInt()) },
                    valueRange = 2f..10f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }
        }
    }
}

// ── Line graph ────────────────────────────────────────────────────────────────

@Composable
fun BodyweightLineGraph(
    points: List<BodyweightPoint>,
    displayInKg: Boolean,
    mode: BodyweightMode,
    onPointTap: (BodyweightPoint) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var triggered by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "bwGraphAnim"
    )
    LaunchedEffect(points) { triggered = true }
    val maxW = points.maxOfOrNull { it.weightLbs }?.takeIf { it > 0.0 } ?: 1.0
    val minW = points.filter { it.weightLbs > 0.0 }.minOfOrNull { it.weightLbs } ?: 0.0
    val unit = if (displayInKg) "kg" else "lbs"
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
                        val down = awaitFirstDown(requireUnconsumed = false)
                        hoveredX = down.position.x
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                hoveredX = null
                                val dist = (change.position - down.position).getDistance()
                                if (dist < 20f && points.isNotEmpty()) {
                                    onPointTap(nearestPoint(points, down.position.x, size.width))
                                }
                                break
                            }
                            hoveredX = change.position.x
                        }
                    }
                }
        ) {
            val padLeft = 100f
            val padRight = 10f
            val padTop = 20f
            val padBottom = 40f
            val graphW = size.width - padLeft - padRight
            val graphH = size.height - padTop - padBottom
            drawGrid(textMeasurer, maxW, minW, displayInKg, padLeft, padRight, padTop, graphH, labelColor, tipBorder)
            if (points.isEmpty()) return@Canvas
            drawLine(points, maxW, minW, animProgress, padLeft, graphW, padTop, graphH, lineColor)
            drawDots(textMeasurer, points, mode, maxW, minW, animProgress, padLeft, graphW, padTop, graphH, lineColor, labelColor)
            hoveredX?.let { hx ->
                drawTooltip(textMeasurer, hx, points, displayInKg, unit, maxW, minW, animProgress, padLeft, padRight, padTop, graphW, graphH, labelColor, lineColor, tipBg, tipBorder, onSurface)
            }
        }
    }
}

private fun nearestPoint(points: List<BodyweightPoint>, x: Float, width: Float): BodyweightPoint {
    val padLeft = 100f
    val padRight = 10f
    val graphW = width - padLeft - padRight
    val n = points.size
    val raw = ((x - padLeft) / graphW * (n - 1).coerceAtLeast(1)).roundToInt()
    return points[raw.coerceIn(0, n - 1)]
}

private fun DrawScope.drawGrid(
    measurer: androidx.compose.ui.text.TextMeasurer,
    maxW: Double,
    minW: Double,
    displayInKg: Boolean,
    padLeft: Float,
    padRight: Float,
    padTop: Float,
    graphH: Float,
    labelColor: Color,
    gridColor: Color
) {
    listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { ratio ->
        val y = padTop + graphH * (1f - ratio)
        drawLine(
            color = gridColor,
            start = Offset(padLeft, y),
            end = Offset(size.width - padRight, y),
            strokeWidth = 0.5.dp.toPx()
        )
        val raw = minW + (maxW - minW) * ratio
        val v = if (displayInKg) UnitConversion.lbsToKg(raw) else raw
        drawText(
            textLayoutResult = measurer.measure(
                UnitConversion.formatNumber(v),
                TextStyle(color = labelColor, fontSize = 10.sp, fontFamily = MonospaceFamily)
            ),
            topLeft = Offset(padLeft - 56f, y - 8f)
        )
    }
}

private fun DrawScope.drawLine(
    points: List<BodyweightPoint>,
    maxW: Double,
    minW: Double,
    progress: Float,
    padLeft: Float,
    graphW: Float,
    padTop: Float,
    graphH: Float,
    color: Color
) {
    val n = points.size
    fun xOf(i: Int) = padLeft + (i.toFloat() / (n - 1).coerceAtLeast(1)) * graphW
    fun yOf(w: Double) = padTop + graphH * (1f - yRatio(w, maxW, minW) * progress)
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
    mode: BodyweightMode,
    maxW: Double,
    minW: Double,
    progress: Float,
    padLeft: Float,
    graphW: Float,
    padTop: Float,
    graphH: Float,
    dotColor: Color,
    labelColor: Color
) {
    val n = points.size
    points.forEachIndexed { i, p ->
        val x = padLeft + (i.toFloat() / (n - 1).coerceAtLeast(1)) * graphW
        val y = padTop + graphH * (1f - yRatio(p.weightLbs, maxW, minW) * progress)
        if (p.weightLbs > 0.0 && (mode == BodyweightMode.BY_DAY || i % 7 == 0 || i == n - 1)) {
            drawCircle(color = dotColor, radius = 3f, center = Offset(x, y))
        }
        if (p.label.isNotEmpty()) {
            val measured = measurer.measure(
                p.label,
                TextStyle(color = labelColor, fontSize = 10.sp, fontFamily = MonospaceFamily)
            )
            drawText(measured, topLeft = Offset(x - measured.size.width / 2f, size.height - 40f + 12f))
        }
    }
}

private fun DrawScope.drawTooltip(
    measurer: androidx.compose.ui.text.TextMeasurer,
    hx: Float,
    points: List<BodyweightPoint>,
    displayInKg: Boolean,
    unit: String,
    maxW: Double,
    minW: Double,
    progress: Float,
    padLeft: Float,
    padRight: Float,
    padTop: Float,
    graphW: Float,
    graphH: Float,
    labelColor: Color,
    lineColor: Color,
    tipBg: Color,
    tipBorder: Color,
    onSurface: Color
) {
    val n = points.size
    fun xOf(i: Int) = padLeft + (i.toFloat() / (n - 1).coerceAtLeast(1)) * graphW
    val idx = (0 until n).minByOrNull { kotlin.math.abs(xOf(it) - hx) } ?: return
    val p = points[idx]
    val px = xOf(idx)
    val py = padTop + graphH * (1f - yRatio(p.weightLbs, maxW, minW) * progress)
    drawLine(
        color = labelColor.copy(alpha = 0.5f),
        start = Offset(px, padTop),
        end = Offset(px, padTop + graphH),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    )
    drawCircle(color = lineColor, radius = 5f, center = Offset(px, py))
    val dateStr = Instant.ofEpochMilli(p.timestampUtc).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEEE MMM d"))
    val text = "$dateStr, ${p.weightLbs.formatBodyweight(displayInKg)} $unit"
    val layout = measurer.measure(
        text,
        TextStyle(color = onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
    )
    val tw = layout.size.width.toFloat()
    val th = layout.size.height.toFloat()
    val pad = 12f
    var tx = (px - tw / 2f).coerceIn(padLeft, size.width - padRight - tw)
    val ty = padTop - 20f
    drawRoundRect(tipBg, Offset(tx - pad, ty - pad), Size(tw + pad * 2, th + pad * 2), CornerRadius(8f, 8f))
    drawRoundRect(tipBorder, Offset(tx - pad, ty - pad), Size(tw + pad * 2, th + pad * 2), CornerRadius(8f, 8f), style = Stroke(1f))
    drawText(layout, topLeft = Offset(tx, ty))
}

// ── Stats section ─────────────────────────────────────────────────────────────

@Composable
fun StatsSection(
    stats: BodyweightStats,
    displayInKg: Boolean,
    unit: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ThinOutline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            StatRow("Current", stats.current?.let { "${it.formatBodyweight(displayInKg)} $unit" } ?: "--")
            val sign = if ((stats.change ?: 0.0) > 0) "+" else ""
            StatRow("Change", stats.change?.let { "$sign${it.formatBodyweight(displayInKg)} $unit" } ?: "--")
            StatRow("Average", stats.average?.let { "${it.formatBodyweight(displayInKg)} $unit" } ?: "--")
            StatRow("Min", stats.min?.let { "${it.formatBodyweight(displayInKg)} $unit" } ?: "--")
            StatRow("Max", stats.max?.let { "${it.formatBodyweight(displayInKg)} $unit" } ?: "--")
            StatRow("Entries", "${stats.count}")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CoolGray, fontSize = 14.sp)
        Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ── History list ──────────────────────────────────────────────────────────────

@Composable
fun HistoryList(
    entries: List<BodyWeightEntry>,
    displayInKg: Boolean,
    onEdit: (Long, Double) -> Unit,
    onDelete: (Long) -> Unit
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var editInKg by rememberSaveable { mutableStateOf(displayInKg) }
    var editError by rememberSaveable { mutableStateOf<String?>(null) }
    val unit = if (displayInKg) "kg" else "lbs"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ThinOutline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text("No weigh-ins yet", color = CoolGray, fontSize = 14.sp)
            }
            entries.forEach { entry ->
                val display = if (displayInKg) UnitConversion.lbsToKg(entry.weightLbs) else entry.weightLbs
                if (editingId == entry.id) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            val parsed = editText.trim().toDoubleOrNull()
                            if (parsed == null || !parsed.isFinite()) {
                                editError = "Enter a valid number"
                                return@TextButton
                            }
                            val lbs = if (editInKg) UnitConversion.kgToLbs(parsed) else parsed
                            if (lbs <= 0.0) {
                                editError = "Enter a weight above 0"
                                return@TextButton
                            }
                            if (lbs < 20.0 || lbs > 1500.0) {
                                editError = "Enter a weight between 20 and 1500 lbs"
                                return@TextButton
                            }
                            editError = null
                            onEdit(entry.id, lbs)
                            editingId = null
                        }) { Text("Save") }
                        TextButton(onClick = { editingId = null; editError = null }) { Text("Cancel") }
                    }
                    if (editError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(editError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${UnitConversion.formatNumber(display)} $unit",
                                color = Color.White,
                                fontFamily = MonospaceFamily,
                                fontSize = 14.sp
                            )
                            Text(formatStamp(entry.timestampUtc), color = CoolGray, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            editingId = entry.id
                            editInKg = displayInKg
                            editError = null
                            editText = UnitConversion.formatNumber(display)
                        }) { Text("Edit") }
                        TextButton(onClick = { onDelete(entry.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
