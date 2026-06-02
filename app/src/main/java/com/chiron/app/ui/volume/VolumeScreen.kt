package com.chiron.app.ui.volume

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import com.chiron.app.viewmodel.VolumeMode
import com.chiron.app.viewmodel.VolumePoint
import com.chiron.app.viewmodel.VolumeStats
import com.chiron.app.viewmodel.VolumeUiState
import com.chiron.app.viewmodel.VolumeViewModel
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ── Palette ───────────────────────────────────────────────────────────────────
private val DotColor = Color(0xFFFFFFFF)

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Double.formatVolume(inKg: Boolean): String {
    val v = if (inKg) this * 0.453592 else this
    return when {
        v >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
        v >= 1_000     -> "%.1fk".format(v / 1_000)
        else           -> "%.0f".format(v)
    }
}

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
fun VolumeScreen(
    viewModel: VolumeViewModel,
    displayInKg: Boolean,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            VolumeContent(
                state = state,
                displayInKg = displayInKg,
                onModeChange = viewModel::setMode,
                onWeekCountChange = viewModel::setWeekCount,
                onPrevWeek = viewModel::goToPreviousWeek,
                onNextWeek = viewModel::goToNextWeek,
                onToggleAbridgeGaps = viewModel::toggleAbridgeGaps
            )
        }
    }
}

@Composable
fun VolumeContent(
    state: VolumeUiState,
    displayInKg: Boolean,
    onModeChange: (VolumeMode) -> Unit,
    onWeekCountChange: (Int) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToggleAbridgeGaps: () -> Unit
) {
    val unit = if (displayInKg) "kg" else "lbs"
    val weekLabel = remember(state.currentWeekStart, state.mode, state.weekCount) {
        if (state.mode == VolumeMode.BY_DAY) {
            val end = state.currentWeekStart.plusDays(6)
            val fmt = DateTimeFormatter.ofPattern("MMM d")
            "${state.currentWeekStart.format(fmt)} – ${end.format(fmt)}"
        } else {
            val start = state.currentWeekStart.minusWeeks((state.weekCount - 1).toLong())
            val end = state.currentWeekStart.plusDays(6)
            val fmt = DateTimeFormatter.ofPattern("MMM d")
            "${start.format(fmt)} – ${end.format(fmt)}"
        }
    }

    val totalVolume = state.points.sumOf { it.volumeLbs }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── Mode Tabs ─────────────────────────────────────────────────────────
        ModeSelector(
            selected = state.mode,
            onSelect = onModeChange
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleAbridgeGaps() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Checkbox(
                checked = state.abridgeGaps,
                onCheckedChange = { onToggleAbridgeGaps() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = Color(0xFF8B949E),
                    checkmarkColor = Color.Black
                ),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abridge Gaps", fontSize = 14.sp, color = Color(0xFF8B949E))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Graph card ────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Total Volume in top right
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${totalVolume.formatVolume(displayInKg)} $unit",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }

                if (state.points.isEmpty() || state.points.all { it.volumeLbs == 0.0 }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No volume data\nfor this period",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    VolumeLineGraph(
                        points = state.points,
                        displayInKg = displayInKg,
                        mode = state.mode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }

                if (state.mode == VolumeMode.BY_WEEK) {
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

        Spacer(modifier = Modifier.height(12.dp))
        WeekNavigator(
            weekLabel = weekLabel,
            canGoPrev = !state.isAtFirstWeek,
            canGoNext = !state.isAtCurrentWeek,
            onPrev = onPrevWeek,
            onNext = onNextWeek
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        VolumeStatsSection(stats = state.stats, displayInKg = displayInKg)
        Spacer(modifier = Modifier.height(120.dp))
    }
}

// ── Mode Selector ─────────────────────────────────────────────────────────────

@Composable
private fun ModeSelector(
    selected: VolumeMode,
    onSelect: (VolumeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161B22))
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        VolumeMode.entries.forEach { mode ->
            val label = if (mode == VolumeMode.BY_DAY) "Short Term" else "Long Term"
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
                    color = if (isSelected) Color.White else Color(0xFF8B949E),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ── Line Graph ────────────────────────────────────────────────────────────────

@Composable
private fun VolumeLineGraph(
    points: List<VolumePoint>,
    displayInKg: Boolean,
    mode: VolumeMode,
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
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    var hoveredX by remember { mutableStateOf<Float?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectDragGestures(
                        onDragEnd = { hoveredX = null },
                        onDragCancel = { hoveredX = null }
                    ) { change, _ ->
                        hoveredX = change.position.x
                    }
                }
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = { offset ->
                            hoveredX = offset.x
                            tryAwaitRelease()
                            hoveredX = null
                        }
                    )
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

            // Y-axis grid lines
            val gridLevels = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            gridLevels.forEach { ratio ->
                val y = padTop + graphH * (1f - ratio)
                drawLine(
                    color = Color(0xFF30363D).copy(alpha = 0.4f),
                    start = Offset(padLeft, y),
                    end = Offset(w - padRight, y),
                    strokeWidth = 1f
                )
                
                val labelVal = maxVol * ratio
                val v = if (displayInKg) labelVal * 0.453592 else labelVal
                val labelStr = if (v >= 1000) "%.1fk".format(v / 1000) else "%.0f".format(v)
                val measuredText = textMeasurer.measure(labelStr, TextStyle(color = Color(0xFF8B949E), fontSize = 10.sp))
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset(padLeft - measuredText.size.width - 16f, y - measuredText.size.height / 2f)
                )
            }

            if (points.isEmpty()) return@Canvas

            val n = points.size
            fun xOf(i: Int) = padLeft + (i.toFloat() / (n - 1).coerceAtLeast(1)) * graphW
            fun yOf(vol: Double) = padTop + graphH * (1.0 - (vol / maxVol)).toFloat()

            val contrastBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(Color(0xFFFFD700), Color(0xFFFF5252), Color(0xFF4CAF50))
            )

            // Fill area under line
            val fillPath = Path()
            val firstX = xOf(0)
            val firstY = yOf(points[0].volumeLbs * animProgress)
            fillPath.moveTo(firstX, padTop + graphH)
            fillPath.lineTo(firstX, firstY)
            for (i in 1 until n) {
                val cx1 = xOf(i - 1) + (xOf(i) - xOf(i - 1)) / 2f
                val cy1 = yOf(points[i - 1].volumeLbs * animProgress)
                val cx2 = cx1
                val cy2 = yOf(points[i].volumeLbs * animProgress)
                fillPath.cubicTo(cx1, cy1, cx2, cy2, xOf(i), yOf(points[i].volumeLbs * animProgress))
            }
            fillPath.lineTo(xOf(n - 1), padTop + graphH)
            fillPath.close()
            drawPath(fillPath, brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0xFFFFD700).copy(alpha = 0.2f), Color.Transparent),
                startY = padTop,
                endY = padTop + graphH
            ))

            // Line stroke
            val linePath = Path()
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
                brush = contrastBrush,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Dots + labels
            points.forEachIndexed { i, point ->
                val x = xOf(i)
                val y = yOf(point.volumeLbs * animProgress)
                // Dot
                if (point.volumeLbs > 0.0) {
                    val dotColor = if (mode == VolumeMode.BY_DAY) Color(0xFF4CAF50) else Color.Transparent
                    if (mode == VolumeMode.BY_DAY) {
                        drawCircle(color = dotColor, radius = 5f, center = Offset(x, y))
                        drawCircle(color = DotColor, radius = 2.5f, center = Offset(x, y))
                    } else if (i % 7 == 0 || i == n - 1) { // just draw dots for week boundaries to not clutter
                        drawCircle(color = Color(0xFFFFD700), radius = 3f, center = Offset(x, y))
                    }
                }
                
                // Draw X-axis label
                if (point.label.isNotEmpty()) {
                    val measuredText = textMeasurer.measure(point.label, TextStyle(color = Color(0xFF8B949E), fontSize = 10.sp))
                    drawText(
                        textLayoutResult = measuredText,
                        topLeft = Offset(x - measuredText.size.width / 2f, h - padBottom + 12f)
                    )
                }
            }

            if (hoveredX != null && points.isNotEmpty()) {
                val hx = hoveredX!!
                val closestIndex = (0 until n).minByOrNull { kotlin.math.abs(xOf(it) - hx) }
                if (closestIndex != null) {
                    val p = points[closestIndex]
                    val px = xOf(closestIndex)
                    val py = yOf(p.volumeLbs * animProgress)
                    
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(px, padTop),
                        end = Offset(px, padTop + graphH),
                        strokeWidth = 2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                    
                    drawCircle(color = Color.White, radius = 6f, center = Offset(px, py))
                    
                    val dateFmt = java.time.format.DateTimeFormatter.ofPattern("EEEE MMM d")
                    val dateStr = p.date.format(dateFmt)
                    val tooltipText = "$dateStr, ${p.volumeLbs.formatVolume(displayInKg)} $unit"
                    
                    val textLayoutResult = textMeasurer.measure(
                        text = tooltipText,
                        style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    
                    val tw = textLayoutResult.size.width.toFloat()
                    val th = textLayoutResult.size.height.toFloat()
                    val tooltipPad = 12f
                    
                    var tx = px - tw / 2f
                    if (tx < padLeft) tx = padLeft
                    if (tx + tw > w - padRight) tx = w - padRight - tw
                    
                    val ty = padTop - 20f
                    
                    drawRoundRect(
                        color = Color(0xFF21262D),
                        topLeft = Offset(tx - tooltipPad, ty - tooltipPad),
                        size = androidx.compose.ui.geometry.Size(tw + tooltipPad * 2, th + tooltipPad * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )
                    
                    drawRoundRect(
                        color = Color(0xFF30363D),
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

// ── Week Navigator ────────────────────────────────────────────────────────────

@Composable
private fun WeekNavigator(
    weekLabel: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrev,
            enabled = canGoPrev
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous week",
                tint = if (canGoPrev) MaterialTheme.colorScheme.primary else Color(0xFF30363D)
            )
        }

        Text(
            text = weekLabel,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onNext,
            enabled = canGoNext
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next week",
                tint = if (canGoNext) MaterialTheme.colorScheme.primary else Color(0xFF30363D)
            )
        }
    }
}

@Composable
private fun VolumeStatsSection(stats: VolumeStats, displayInKg: Boolean) {
    val unit = if (displayInKg) "kg" else "lbs"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            StatRow("This week", "${stats.thisWeek.formatVolume(displayInKg)} $unit")
            StatRow("Last week", "${stats.lastWeek.formatVolume(displayInKg)} $unit")
            StatRow("Rolling weekly avg", "${stats.rollingWeeklyAvg.formatVolume(displayInKg)} $unit")
            
            val sign = if (stats.rollingVolChange > 0) "+" else ""
            StatRow("Rolling vol change", "$sign${stats.rollingVolChange.formatVolume(displayInKg)} $unit")
            
            StatRow("Highest ever weekly", "${stats.highestEver.formatVolume(displayInKg)} $unit")
            StatRow("Lowest ever weekly", "${stats.lowestEver.formatVolume(displayInKg)} $unit")
            StatRow("All time total", "${stats.allTimeTotal.formatVolume(displayInKg)} $unit")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF8B949E), fontSize = 14.sp)
        Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
