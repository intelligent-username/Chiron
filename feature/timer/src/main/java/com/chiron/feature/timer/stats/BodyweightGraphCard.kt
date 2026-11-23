package com.chiron.feature.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.common.CalorieHistory
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import kotlin.math.roundToInt

@Composable
fun BodyweightGraphCard(
    state: BodyweightUiState,
    localInKg: Boolean,
    unit: String,
    onWeekCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    calorieHistory: CalorieHistory = CalorieHistory()
) {
    var caloriesEnabled by rememberSaveable { mutableStateOf(false) }
    var calorieLag by rememberSaveable { mutableIntStateOf(0) }
    var showExplanation by remember { mutableStateOf(false) }
    var showLagSettings by remember { mutableStateOf(false) }
    var draftLag by remember { mutableIntStateOf(0) }
    val calorieOverlay = remember(state.points, calorieHistory.entries, calorieLag, caloriesEnabled) {
        if (caloriesEnabled) WeightCalorieOverlayCalculator.build(state.points, calorieHistory.entries, calorieLag) else null
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThinOutline)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val current = state.stats.current
                val currentDisplay = if (current != null) {
                    val v = if (localInKg) UnitConversion.lbsToKg(current) else current
                    "${UnitConversion.formatNumber(v)} $unit"
                } else "-- $unit"

                Column {
                    Text(text = "Weight trend", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Touch chart to inspect", color = CoolGray, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currentDisplay,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontFamily = MonospaceFamily,
                        fontSize = 16.sp
                    )
                    Row(
                        modifier = Modifier.heightIn(min = 48.dp).toggleable(
                            value = caloriesEnabled,
                            role = Role.Checkbox,
                            onValueChange = {
                                if (caloriesEnabled) {
                                    draftLag = calorieLag
                                    showLagSettings = true
                                } else showExplanation = true
                            }
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (caloriesEnabled) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (caloriesEnabled) MaterialTheme.colorScheme.primary else CoolGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Calories", color = CoolGray, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                    localInKg = localInKg,
                    mode = state.mode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    calorieOverlay = calorieOverlay
                )
            }

            // Week-range slider: from 1 week up to total weeks spanning earliest entry to today
            val maxWeeks = kotlin.math.max(2, state.maxWeekCount)
            val currentWeekVal = state.weekCount.toFloat().coerceIn(1f, maxWeeks.toFloat())
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Range: ${state.weekCount} week${if (state.weekCount == 1) "" else "s"}",
                    color = CoolGray,
                    fontSize = 12.sp
                )
                if (state.earliestDate != null) {
                    Text(
                        text = "First log: ${state.earliestDate}",
                        color = CoolGray,
                        fontSize = 11.sp
                    )
                }
            }
            if (maxWeeks > 1) {
                Slider(
                    value = currentWeekVal,
                    onValueChange = { onWeekCountChange(it.roundToInt()) },
                    valueRange = 1f..maxWeeks.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }
        }
    }
    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text("Compare calories with weight") },
            text = {
                Text("Add daily calories to this weight chart in orange, with a separate kcal scale on the right. " +
                    "Both lines share the same dates and chart height; the weight scale stays unchanged. " +
                    "Days without calorie entries show zero. Tap Calories again to compare earlier or later calorie days using a lag of −3 to +3 days.")
            },
            confirmButton = {
                Button(onClick = { caloriesEnabled = true; showExplanation = false }) { Text("Enable") }
            },
            dismissButton = { TextButton(onClick = { showExplanation = false }) { Text("Cancel") } }
        )
    }
    if (showLagSettings) {
        val previewOverlay = remember(state.points, calorieHistory.entries, draftLag) {
            WeightCalorieOverlayCalculator.build(state.points, calorieHistory.entries, draftLag)
        }
        val pairs = state.points.zip(previewOverlay.points).map { (weight, calories) -> weight.weightLbs to calories.calories }
        val r = correlation(pairs)
        AlertDialog(
            onDismissRequest = { showLagSettings = false },
            title = { Text("Calorie comparison") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Lag: ${if (draftLag > 0) "+" else ""}$draftLag days", fontFamily = MonospaceFamily)
                    Slider(
                        value = draftLag.toFloat(),
                        onValueChange = { draftLag = it.roundToInt().coerceIn(-3, 3) },
                        valueRange = -3f..3f,
                        steps = 5
                    )
                    Text(if (r == null) "Correlation: not enough variation/data (${pairs.size} pairs)"
                        else "Correlation: ${"%.2f".format(r)} (${pairs.size} pairs)",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { caloriesEnabled = false; showLagSettings = false }) { Text("Disable calories") }
                }
            },
            confirmButton = {
                Button(onClick = { calorieLag = draftLag; showLagSettings = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showLagSettings = false }) { Text("Cancel") } }
        )
    }
}
